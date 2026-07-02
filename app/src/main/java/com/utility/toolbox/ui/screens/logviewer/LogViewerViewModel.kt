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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    private val searchQuery = MutableStateFlow("")
    private val selectedLevels = MutableStateFlow(setOf("E", "W", "I", "D"))
    private val selectedTag = MutableStateFlow<String?>(null)
    private val triggerRefresh = MutableStateFlow(0L) // used to force refresh

    init {
        loadLogs()
        loadTags()
    }

    private fun loadTags() {
        viewModelScope.launch {
            LogManager.getAllTags().collectLatest { tags ->
                _uiState.update { it.copy(availableTags = tags) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadLogs() {
        viewModelScope.launch {
            // Combine all filter states into a single log flow
            val logFlow = combine(
                searchQuery,
                selectedLevels,
                selectedTag,
                triggerRefresh
            ) { query, levels, tag, _ ->
                FilterParams(query, levels, tag)
            }.flatMapLatest { params ->
                val query = params.query.trim()
                val levelsList = params.levels.toList().ifEmpty { listOf("E", "W", "I", "D", "V") }
                val tag = params.tag

                if (query.isBlank() && tag == null) {
                    if (levelsList.size == 5) {
                        LogManager.getAllLogs()
                    } else {
                        LogManager.getLogsByLevel(levelsList)
                    }
                } else if (tag != null) {
                    LogManager.searchLogsFiltered(tag, levelsList)
                } else {
                    LogManager.searchLogsFiltered(query, levelsList)
                }
            }

            logFlow.collectLatest { logs ->
                _uiState.update {
                    it.copy(
                        logs = logs,
                        logCount = logs.size,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleLevel(level: String) {
        selectedLevels.update { current ->
            if (current.contains(level)) {
                if (current.size > 1) current - level else current
            } else {
                current + level
            }
        }
        _uiState.update { it.copy(selectedLevels = selectedLevels.value) }
    }

    fun selectTag(tag: String?) {
        selectedTag.value = tag
        _uiState.update { it.copy(selectedTag = tag) }
    }

    fun setAutoScroll(enabled: Boolean) {
        _uiState.update { it.copy(autoScroll = enabled) }
    }

    fun clearAllLogs() {
        LogManager.clearAllLogs()
        triggerRefresh.value = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                snackbarMessage = "All logs cleared",
                logs = emptyList(),
                logCount = 0
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** Export logs as a text file and share via Android share sheet. */
    fun shareLogs() {
        viewModelScope.launch {
            val logs = _uiState.value.logs
            if (logs.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No logs to share") }
                return@launch
            }

            try {
                val text = LogManager.formatLogsForExport(logs)
                val file = File(context.cacheDir, "dualspace_logs_${System.currentTimeMillis()}.txt")
                file.writeText(text)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "DualSpace Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Share Logs")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )

                _uiState.update { it.copy(snackbarMessage = "Logs exported (${logs.size} entries)") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Export failed: ${e.message}") }
            }
        }
    }

    /** Copy logs to clipboard. */
    fun copyToClipboard() {
        val logs = _uiState.value.logs
        if (logs.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "No logs to copy") }
            return
        }

        val text = LogManager.formatLogsForExport(logs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("DualSpace Logs", text)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(snackbarMessage = "${logs.size} log entries copied to clipboard") }
    }

    private data class FilterParams(
        val query: String,
        val levels: Set<String>,
        val tag: String?
    )
}
