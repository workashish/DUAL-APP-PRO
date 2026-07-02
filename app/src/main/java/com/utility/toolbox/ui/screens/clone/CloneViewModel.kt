package com.utility.toolbox.ui.screens.clone

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.domain.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CloneUiState(
    val installableApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val selectedApps: Set<String> = emptySet(),
    val isCloning: Boolean = false,
    val cloneProgress: Float = 0f,
    val cloneStatus: String = "",
    val snackbarMessage: String? = null,
    val cloneErrors: List<String> = emptyList()
)

@HiltViewModel
class CloneViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloneUiState())
    val uiState: StateFlow<CloneUiState> = _uiState.asStateFlow()

    init { loadApps() }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = appRepository.getInstallableApps()
            _uiState.update { it.copy(installableApps = apps, filteredApps = apps) }
        }
    }

    fun search(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) state.installableApps
            else state.installableApps.filter { it.appName.contains(query, true) || it.packageName.contains(query, true) }
            state.copy(searchQuery = query, filteredApps = filtered)
        }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val s = state.selectedApps.toMutableSet()
            if (s.contains(packageName)) s.remove(packageName) else s.add(packageName)
            state.copy(selectedApps = s)
        }
    }

    fun selectAll() { _uiState.update { it.copy(selectedApps = it.filteredApps.map { a -> a.packageName }.toSet()) } }
    fun deselectAll() { _uiState.update { it.copy(selectedApps = emptySet()) } }

    fun cloneSelectedApps() {
        val selected = _uiState.value.selectedApps
        if (selected.isEmpty()) return
        _uiState.update { it.copy(isCloning = true, cloneStatus = "Preparing...", cloneErrors = emptyList()) }
        viewModelScope.launch {
            val apps = _uiState.value.installableApps.filter { it.packageName in selected }
            var completed = 0; var failed = 0; val errors = mutableListOf<String>()
            for ((i, app) in apps.withIndex()) {
                _uiState.update { it.copy(cloneStatus = "Cloning ${app.appName}... (${i + 1}/${apps.size})", cloneProgress = (i + 1).toFloat() / apps.size) }
                try {
                    val id = appRepository.cloneApp(app)
                    if (id > 0) completed++ else { failed++; errors.add("${app.appName}: failed") }
                } catch (e: Exception) { failed++; errors.add("${app.appName}: ${e.message}") }
            }
            _uiState.update { it.copy(isCloning = false, cloneProgress = 1f, cloneStatus = "$completed cloned, $failed failed", selectedApps = emptySet(), snackbarMessage = "$completed apps cloned", cloneErrors = errors) }
        }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }
}
