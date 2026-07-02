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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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

    private fun loadLogs() {
        viewModelScope.launch {
            val state = _uiState.value
            val query = state.searchQuery.trim()
            val levels = state.selectedLevels
            val tag = state.selectedTag

            val flow = when {
                query.isNotBlank() && tag != null -> LogManager.searchLogsFiltered(query, levels.toList())
                query.isNotBlank() -> LogManager.searchLogsFiltered(query, levels.toList())
                tag != null -> LogManager.searchLogsFiltered(tag, levels.toList())
                levels.size < 5 -> LogManager.getLogsByLevel(levels.toList())
                else -> LogManager.getAllLogs()
            }

            flow.collect { logs ->
                _uiState.update { it.copy(logs = logs, logCount = logs.size, isLoading = false) }
            }
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

    fun setAutoScroll(enabled: Boolean) {
        _uiState.update { it.copy(autoScroll = enabled) }
    }

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
