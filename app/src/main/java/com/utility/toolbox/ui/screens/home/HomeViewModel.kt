package com.utility.toolbox.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.data.repository.WorkspaceRepository
import com.utility.toolbox.domain.model.AppInfo
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.domain.model.Workspace
import com.utility.toolbox.service.BlackBoxEngine
import com.utility.toolbox.service.BubbleService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspace: Workspace? = null,
    val clonedApps: List<ClonedApp> = emptyList(),
    val installableApps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val appToDelete: ClonedApp? = null,
    val showContextMenu: Boolean = false,
    val contextMenuApp: ClonedApp? = null,
    val snackbarMessage: String? = null,
    val blackBoxAvailable: Boolean = false,
    val isBubbleEnabled: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val appRepository: AppRepository,
    private val blackBoxEngine: BlackBoxEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                blackBoxAvailable = blackBoxEngine.isInitialized(),
                isBubbleEnabled = BubbleService.isRunning(context)
            )
        }
        loadData()
    }

    private fun loadData() {
        // Observe workspaces
        viewModelScope.launch {
            workspaceRepository.getAllWorkspaces().collect { workspaces ->
                _uiState.update { state ->
                    state.copy(
                        workspaces = workspaces,
                        activeWorkspace = workspaces.firstOrNull { it.isActive }
                    )
                }
            }
        }

        // Observe cloned apps for active workspace
        // Use flatMapLatest to cancel inner collector when workspace changes
        viewModelScope.launch {
            workspaceRepository.getActiveWorkspaceFlow()
                .flatMapLatest { workspace ->
                    if (workspace != null) {
                        appRepository.getClonedApps(workspace.id)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { apps ->
                    _uiState.update { state ->
                        state.copy(
                            clonedApps = apps,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // ─── App Launching (via BlackBox virtual engine) ──────────────────────

    fun launchApp(app: ClonedApp) {
        viewModelScope.launch {
            val success = appRepository.launchApp(app)
            if (success) {
                appRepository.updateLastLaunch(app.id)
                appRepository.updateRunningStatus(app.id, true)
            } else {
                _uiState.update {
                    it.copy(snackbarMessage = "Failed to launch ${app.displayName}")
                }
            }
        }
    }

    // ─── App Lifecycle Management ────────────────────────────────────────

    fun stopApp(app: ClonedApp) {
        val success = appRepository.stopApp(app)
        if (success) {
            viewModelScope.launch { appRepository.updateRunningStatus(app.id, false) }
            _uiState.update { it.copy(snackbarMessage = "${app.displayName} stopped") }
        } else {
            _uiState.update { it.copy(snackbarMessage = "Failed to stop ${app.displayName}") }
        }
    }

    fun clearAppData(app: ClonedApp) {
        viewModelScope.launch {
            val success = appRepository.clearCloneData(app.id)
            _uiState.update {
                it.copy(
                    snackbarMessage = if (success) "Data cleared for ${app.displayName}" else "Failed to clear data"
                )
            }
        }
    }

    // ─── Cloning ──────────────────────────────────────────────────────────

    fun cloneApp(appInfo: AppInfo) {
        val workspace = _uiState.value.activeWorkspace ?: return
        viewModelScope.launch {
            val id = appRepository.cloneApp(workspace.id, appInfo)
            if (id > 0) {
                _uiState.update { it.copy(snackbarMessage = "${appInfo.appName} cloned successfully") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Failed to clone ${appInfo.appName}") }
            }
        }
    }

    // ─── GMS Management ──────────────────────────────────────────────────

    fun installGms(workspace: Workspace) {
        viewModelScope.launch {
            val success = appRepository.installGms(workspace.id)
            _uiState.update {
                it.copy(snackbarMessage = if (success) "GMS installed for ${workspace.name}" else "GMS install failed")
            }
        }
    }

    fun uninstallGms(workspace: Workspace) {
        viewModelScope.launch {
            val success = appRepository.uninstallGms(workspace.id)
            _uiState.update {
                it.copy(snackbarMessage = if (success) "GMS removed from ${workspace.name}" else "GMS removal failed")
            }
        }
    }

    // ─── Context Menu ─────────────────────────────────────────────────────

    fun showContextMenu(app: ClonedApp) {
        _uiState.update { it.copy(showContextMenu = true, contextMenuApp = app) }
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(showContextMenu = false, contextMenuApp = null) }
    }

    // ─── Icon Fake ─────────────────────────────────────────────────────────

    suspend fun setFakeIcon(appId: Long, iconPath: String) {
        appRepository.setFakeIcon(appId, iconPath)
    }

    // ─── Device Spoofing ───────────────────────────────────────────────────

    fun getDeviceIdentity(workspaceId: Long): BlackBoxEngine.DeviceIdentity {
        return appRepository.getDeviceIdentity(workspaceId)
    }

    fun resetDeviceInfo(app: ClonedApp) {
        viewModelScope.launch {
            appRepository.resetDeviceInfo(app.id)
            _uiState.update {
                it.copy(snackbarMessage = "Device identity reset for ${app.displayName}")
            }
        }
    }

    fun resetGsfLicense(app: ClonedApp) {
        viewModelScope.launch {
            appRepository.resetGsfLicense(app.id)
            _uiState.update {
                it.copy(snackbarMessage = "GSF license reset for ${app.displayName}")
            }
        }
    }

    fun setCustomGsfLicense(app: ClonedApp, gsfId: String) {
        viewModelScope.launch {
            appRepository.setCustomGsfLicense(app.id, gsfId)
            _uiState.update {
                it.copy(snackbarMessage = "Custom GSF license set for ${app.displayName}")
            }
        }
    }

    // ─── Customization ─────────────────────────────────────────────────────

    fun customizeApp(app: ClonedApp) {
        _uiState.update { it.copy(showContextMenu = false) }
    }

    // ─── Shortcuts ─────────────────────────────────────────────────────────

    fun createShortcut(app: ClonedApp) {
        viewModelScope.launch {
            val success = appRepository.createShortcut(app)
            if (success) {
                appRepository.updateShortcutStatus(app.id, true)
                _uiState.update { it.copy(snackbarMessage = "Shortcut created for ${app.displayName}") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Failed to create shortcut") }
            }
            _uiState.update { it.copy(showContextMenu = false) }
        }
    }

    // ─── Bubble ─────────────────────────────────────────────────────────

    fun toggleBubble(enabled: Boolean) {
        if (enabled) {
            BubbleService.start(context)
        } else {
            BubbleService.stop(context)
        }
        _uiState.update {
            it.copy(
                isBubbleEnabled = enabled,
                snackbarMessage = if (enabled) "Quick Switch bubble enabled" else "Quick Switch bubble disabled"
            )
        }
    }

    // ─── Delete ─────────────────────────────────────────────────────────

    fun showDeleteConfirmation(app: ClonedApp) {
        _uiState.update {
            it.copy(showDeleteDialog = true, appToDelete = app, showContextMenu = false)
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, appToDelete = null) }
    }

    fun confirmDelete() {
        val app = _uiState.value.appToDelete ?: return
        viewModelScope.launch {
            appRepository.deleteClone(app.id)
            _uiState.update {
                it.copy(
                    showDeleteDialog = false,
                    appToDelete = null,
                    snackbarMessage = "${app.displayName} deleted"
                )
            }
        }
    }

    // ─── Workspace Management ───────────────────────────────────────────

    fun createWorkspace(name: String) {
        viewModelScope.launch {
            workspaceRepository.createWorkspace(name)
            _uiState.update { it.copy(snackbarMessage = "Workspace '$name' created") }
        }
    }

    fun switchWorkspace(id: Long) {
        viewModelScope.launch {
            workspaceRepository.switchWorkspace(id)
        }
    }

    fun deleteWorkspace(id: Long) {
        viewModelScope.launch {
            workspaceRepository.deleteWorkspace(id)
            _uiState.update { it.copy(snackbarMessage = "Workspace deleted") }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }
}
