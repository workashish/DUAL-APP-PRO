package com.utility.toolbox.ui.screens.logviewer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.local.entity.LogEntry
import com.utility.toolbox.service.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class LogViewerUiState(
    val logs: List<LogEntry> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedLevels: Set<String> = setOf("E", "W", "I", "D", "V"),
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val autoScroll: Boolean = true,
    val logCount: Int = 0,
    val snackbarMessage: String? = null
) {
    val isFiltered: Boolean
        get() = selectedLevels.size < 5 || searchQuery.isNotBlank() || selectedTag != null
}

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    init {
        loadLogs()
        loadTags()
    }

    private fun loadTags() {
        viewModelScope.launch {
            LogManager.getAllTags().collect { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
    }

    /**
     * Load logs from BOTH sources:
     * 1. LogManager database (host app logs)
     * 2. System logcat (clone process logs)
     * Merge and sort by timestamp.
     */
    private fun loadLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val levels = state.selectedLevels
            val query = state.searchQuery.trim()
            val tag = state.selectedTag

            // Source 1: LogManager database
            val dbFlow = when {
                query.isNotBlank() && tag != null -> LogManager.searchLogsFiltered(query, levels.toList())
                query.isNotBlank() -> LogManager.searchLogsFiltered(query, levels.toList())
                tag != null -> LogManager.searchLogsFiltered(tag, levels.toList())
                levels.size < 5 -> LogManager.getLogsByLevel(levels.toList())
                else -> LogManager.getAllLogs()
            }

            // Source 2: System logcat (captures clone process logs)
            val logcatLogs = readSystemLogcat(levels, query, tag)

            // Merge both sources
            kotlinx.coroutines.coroutineScope {
                dbFlow.collect { dbLogs ->
                    val merged = (dbLogs + logcatLogs).sortedByDescending { it.timestamp }.distinctBy { "${it.timestamp}_${it.tag}_${it.message}" }
                    _uiState.update { it.copy(logs = merged, logCount = merged.size, isLoading = false) }
                }
            }
        }
    }

    /**
     * Read system logcat to capture logs from clone processes.
     * Cloned apps run in separate processes whose logs don't go through LogManager.
     */
    private fun readSystemLogcat(levels: Set<String>, query: String, tag: String?): List<LogEntry> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logs = mutableListOf<LogEntry>()
            val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val hostPkg = context.packageName

            reader.forEachLine { line ->
                // Parse logcat format: "MM-DD HH:mm:ss.mmm  PID  TID LEVEL TAG: MESSAGE"
                val match = Regex("""(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)""").find(line)
                if (match != null) {
                    val (timeStr, pid, tid, level, tagStr, message) = match.destructured

                    // Filter by level
                    if (level !in levels) return@forEachLine

                    // Filter by tag if specified
                    if (tag != null && tagStr != tag) return@forEachLine

                    // Filter by search query
                    if (query.isNotBlank() && !tagStr.contains(query, true) && !message.contains(query, true)) return@forEachLine

                    // Include logs from ALL processes (host + clones)
                    val timestamp = try { timeFmt.parse(timeStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }

                    logs.add(LogEntry(
                        id = logs.size.toLong(),
                        level = level,
                        tag = tagStr,
                        message = message,
                        timestamp = timestamp,
                        source = if (pid.toInt() == android.os.Process.myPid()) "host" else "clone:$pid"
                    ))
                }
            }
            process.destroy()
            logs
        } catch (e: Exception) {
            LogManager.w("LogViewer", "Failed to read logcat: ${e.message}")
            emptyList()
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadLogs()
    }

    fun toggleLevel(level: String) {
        _uiState.update { state ->
            val newLevels = if (state.selectedLevels.contains(level)) {
                if (state.selectedLevels.size > 1) state.selectedLevels - level else state.selectedLevels
            } else state.selectedLevels + level
            state.copy(selectedLevels = newLevels)
        }
        loadLogs()
    }

    fun selectTag(tag: String?) {
        _uiState.update { it.copy(selectedTag = tag) }
        loadLogs()
    }

    fun setAutoScroll(enabled: Boolean) { _uiState.update { it.copy(autoScroll = enabled) } }

    fun clearAllLogs() {
        LogManager.clearAllLogs()
        _uiState.update { it.copy(snackbarMessage = "All logs cleared", logs = emptyList(), logCount = 0) }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }

    fun shareLogs() {
        viewModelScope.launch {
            val logs = _uiState.value.logs
            if (logs.isEmpty()) { _uiState.update { it.copy(snackbarMessage = "No logs to share") }; return@launch }
            try {
                val text = LogManager.formatLogsForExport(logs)
                val file = File(context.cacheDir, "dualspace_logs_${System.currentTimeMillis()}.txt")
                file.writeText(text)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "DualSpace Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                _uiState.update { it.copy(snackbarMessage = "Logs exported (${logs.size} entries)") }
            } catch (e: Exception) { _uiState.update { it.copy(snackbarMessage = "Export failed: ${e.message}") } }
        }
    }

    fun copyToClipboard() {
        val logs = _uiState.value.logs
        if (logs.isEmpty()) { _uiState.update { it.copy(snackbarMessage = "No logs to copy") }; return }
        val text = LogManager.formatLogsForExport(logs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DualSpace Logs", text))
        _uiState.update { it.copy(snackbarMessage = "${logs.size} log entries copied") }
    }
}
