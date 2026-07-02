package com.utility.toolbox.ui.screens.clone

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.data.repository.WorkspaceRepository
import com.utility.toolbox.domain.model.AppInfo
import com.utility.toolbox.service.AppClonerService
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
    private val workspaceRepository: WorkspaceRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloneUiState())
    val uiState: StateFlow<CloneUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = appRepository.getInstallableApps()
            _uiState.update { state ->
                state.copy(
                    installableApps = apps,
                    filteredApps = apps
                )
            }
        }
    }

    fun search(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.installableApps
            } else {
                state.installableApps.filter { app ->
                    app.appName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                }
            }
            state.copy(searchQuery = query, filteredApps = filtered)
        }
    }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val newSelected = state.selectedApps.toMutableSet()
            if (newSelected.contains(packageName)) {
                newSelected.remove(packageName)
            } else {
                newSelected.add(packageName)
            }
            state.copy(selectedApps = newSelected)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedApps = state.filteredApps.map { it.packageName }.toSet()
            )
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedApps = emptySet()) }
    }

    fun cloneSelectedApps() {
        val selected = _uiState.value.selectedApps
        if (selected.isEmpty()) return

        _uiState.update { it.copy(isCloning = true, cloneStatus = "Preparing…", cloneErrors = emptyList()) }

        viewModelScope.launch {
            var workspace = workspaceRepository.getActiveWorkspace()
            if (workspace == null) {
                workspace = workspaceRepository.createWorkspace("Default")
            }

            val apps = _uiState.value.installableApps.filter { it.packageName in selected }
            val total = apps.size
            var completed = 0
            var failed = 0
            val errors = mutableListOf<String>()

            for ((index, app) in apps.withIndex()) {
                _uiState.update {
                    it.copy(
                        cloneStatus = "Cloning ${app.appName}… (${index + 1}/$total)",
                        cloneProgress = (index + 1).toFloat() / total
                    )
                }

                try {
                    val dbId = appRepository.cloneApp(workspace.id, app)
                    if (dbId > 0) {
                        completed++
                    } else {
                        failed++
                        errors.add("${app.appName}: installation failed")
                    }
                } catch (e: Exception) {
                    failed++
                    errors.add("${app.appName}: ${e.message ?: "unknown error"}")
                }
            }

            val message = when {
                failed == 0 -> "$completed apps cloned successfully"
                completed == 0 -> "All $failed apps failed to clone"
                else -> "$completed cloned, $failed failed"
            }

            _uiState.update {
                it.copy(
                    isCloning = false,
                    cloneProgress = 1f,
                    cloneStatus = message,
                    selectedApps = emptySet(),
                    snackbarMessage = message,
                    cloneErrors = errors
                )
            }
        }
    }

    fun loadAppIcon(packageName: String): Drawable? {
        return appRepository.getAppIcon(packageName)
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
