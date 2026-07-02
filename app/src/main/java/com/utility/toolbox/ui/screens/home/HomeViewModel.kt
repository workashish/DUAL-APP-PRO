package com.utility.toolbox.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.service.LogManager
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.domain.model.AppInfo
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.service.BlackBoxEngine
import com.utility.toolbox.service.BubbleService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val clonedApps: List<ClonedApp> = emptyList(),
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
    private val appRepository: AppRepository,
    private val blackBoxEngine: BlackBoxEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(blackBoxAvailable = blackBoxEngine.isInitialized(), isBubbleEnabled = BubbleService.isRunning(context)) }
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            appRepository.getAllClonedApps().collect { apps ->
                _uiState.update { it.copy(clonedApps = apps, isLoading = false) }
            }
        }
    }

    fun launchApp(app: ClonedApp) {
        LogManager.i("Home", "Launching ${app.displayName} (user=${app.userId}, pkg=${app.clonePackage})")
        viewModelScope.launch {
            val success = appRepository.launchApp(app)
            if (success) {
                appRepository.updateLastLaunch(app.id)
                appRepository.updateRunningStatus(app.id, true)
                LogManager.i("Home", "✓ Launched ${app.displayName}")
            } else {
                LogManager.e("Home", "✗ Failed to launch ${app.displayName}")
                _uiState.update { it.copy(snackbarMessage = "Failed to launch ${app.displayName}") }
            }
        }
    }

    fun stopApp(app: ClonedApp) {
        LogManager.i("Home", "Stopping ${app.displayName}")
        if (appRepository.stopApp(app)) {
            viewModelScope.launch { appRepository.updateRunningStatus(app.id, false) }
            LogManager.i("Home", "✓ Stopped ${app.displayName}")
            _uiState.update { it.copy(snackbarMessage = "${app.displayName} stopped") }
        } else {
            LogManager.w("Home", "Failed to stop ${app.displayName}")
        }
    }

    fun clearAppData(app: ClonedApp) {
        LogManager.i("Home", "Clearing data for ${app.displayName}")
        viewModelScope.launch {
            val ok = appRepository.clearCloneData(app.id)
            LogManager.i("Home", if (ok) "✓ Data cleared for ${app.displayName}" else "✗ Failed to clear data")
            _uiState.update { it.copy(snackbarMessage = if (ok) "Data cleared" else "Failed to clear data") }
        }
    }

    fun cloneApp(appInfo: AppInfo) {
        LogManager.i("Clone", "Cloning ${appInfo.appName} (${appInfo.packageName})")
        viewModelScope.launch {
            val id = appRepository.cloneApp(appInfo)
            if (id > 0) {
                LogManager.i("Clone", "✓ Cloned ${appInfo.appName} → dbId=$id")
            } else {
                LogManager.e("Clone", "✗ Failed to clone ${appInfo.appName}")
            }
            _uiState.update { it.copy(snackbarMessage = if (id > 0) "${appInfo.appName} cloned" else "Failed to clone ${appInfo.appName}") }
        }
    }

    fun installGms(app: ClonedApp) {
        LogManager.i("GMS", "Installing GMS for ${app.displayName} (user=${app.userId})")
        viewModelScope.launch {
            val ok = appRepository.installGms(app.userId)
            if (ok) appRepository.updateGmsStatus(app.id, true)
            LogManager.i("GMS", if (ok) "✓ GMS installed for ${app.displayName}" else "✗ GMS install failed")
            _uiState.update { it.copy(snackbarMessage = if (ok) "GMS installed" else "GMS install failed") }
        }
    }

    fun uninstallGms(app: ClonedApp) {
        LogManager.i("GMS", "Removing GMS from ${app.displayName}")
        viewModelScope.launch {
            val ok = appRepository.uninstallGms(app.userId)
            if (ok) appRepository.updateGmsStatus(app.id, false)
            LogManager.i("GMS", if (ok) "✓ GMS removed from ${app.displayName}" else "✗ GMS removal failed")
            _uiState.update { it.copy(snackbarMessage = if (ok) "GMS removed" else "GMS removal failed") }
        }
    }

    fun showContextMenu(app: ClonedApp) { _uiState.update { it.copy(showContextMenu = true, contextMenuApp = app) } }
    fun dismissContextMenu() { _uiState.update { it.copy(showContextMenu = false, contextMenuApp = null) } }

    fun showDeleteConfirmation(app: ClonedApp) { _uiState.update { it.copy(showDeleteDialog = true, appToDelete = app, showContextMenu = false) } }
    fun dismissDeleteDialog() { _uiState.update { it.copy(showDeleteDialog = false, appToDelete = null) } }
    fun confirmDelete() {
        val app = _uiState.value.appToDelete ?: return
        LogManager.i("Home", "Deleting clone ${app.displayName} (user=${app.userId})")
        viewModelScope.launch {
            appRepository.deleteClone(app.id)
            LogManager.i("Home", "✓ Deleted ${app.displayName}")
            _uiState.update { it.copy(showDeleteDialog = false, appToDelete = null, snackbarMessage = "${app.displayName} deleted") }
        }
    }

    fun toggleBubble(enabled: Boolean) {
        LogManager.i("Bubble", if (enabled) "Bubble enabled" else "Bubble disabled")
        if (enabled) BubbleService.start(context) else BubbleService.stop(context)
        _uiState.update { it.copy(isBubbleEnabled = enabled, snackbarMessage = if (enabled) "Bubble enabled" else "Bubble disabled") }
    }

    fun createShortcut(app: ClonedApp) {
        LogManager.i("Home", "Creating shortcut for ${app.displayName}")
        viewModelScope.launch {
            val ok = appRepository.createShortcut(app)
            if (ok) appRepository.updateShortcutStatus(app.id, true)
            LogManager.i("Home", if (ok) "✓ Shortcut created for ${app.displayName}" else "✗ Shortcut failed")
            _uiState.update { it.copy(snackbarMessage = if (ok) "Shortcut created" else "Failed to create shortcut", showContextMenu = false) }
        }
    }

    fun resetDeviceInfo(app: ClonedApp) {
        LogManager.i("Identity", "Resetting identity for ${app.displayName}")
        viewModelScope.launch {
            appRepository.updateCustomName(app.id, "")
            LogManager.i("Identity", "✓ Identity reset for ${app.displayName}")
            _uiState.update { it.copy(snackbarMessage = "Identity reset for ${app.displayName}") }
        }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }

    fun killAllClones() {
        LogManager.i("Home", "Killing all clone processes")
        blackBoxEngine.killAllCloneProcesses()
        viewModelScope.launch {
            _uiState.value.clonedApps.forEach { app ->
                appRepository.updateRunningStatus(app.id, false)
            }
        }
        _uiState.update { it.copy(snackbarMessage = "All clone processes killed") }
    }
}
