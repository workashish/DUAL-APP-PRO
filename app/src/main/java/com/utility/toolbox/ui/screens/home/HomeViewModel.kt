package com.utility.toolbox.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        viewModelScope.launch {
            val success = appRepository.launchApp(app)
            if (success) {
                appRepository.updateLastLaunch(app.id)
                appRepository.updateRunningStatus(app.id, true)
            } else _uiState.update { it.copy(snackbarMessage = "Failed to launch ${app.displayName}") }
        }
    }

    fun stopApp(app: ClonedApp) {
        if (appRepository.stopApp(app)) {
            viewModelScope.launch { appRepository.updateRunningStatus(app.id, false) }
            _uiState.update { it.copy(snackbarMessage = "${app.displayName} stopped") }
        }
    }

    fun clearAppData(app: ClonedApp) {
        viewModelScope.launch {
            val ok = appRepository.clearCloneData(app.id)
            _uiState.update { it.copy(snackbarMessage = if (ok) "Data cleared" else "Failed to clear data") }
        }
    }

    fun cloneApp(appInfo: AppInfo) {
        viewModelScope.launch {
            val id = appRepository.cloneApp(appInfo)
            _uiState.update { it.copy(snackbarMessage = if (id > 0) "${appInfo.appName} cloned" else "Failed to clone ${appInfo.appName}") }
        }
    }

    fun installGms(app: ClonedApp) {
        viewModelScope.launch {
            val ok = appRepository.installGms(app.userId)
            if (ok) appRepository.updateGmsStatus(app.id, true)
            _uiState.update { it.copy(snackbarMessage = if (ok) "GMS installed" else "GMS install failed") }
        }
    }

    fun uninstallGms(app: ClonedApp) {
        viewModelScope.launch {
            val ok = appRepository.uninstallGms(app.userId)
            if (ok) appRepository.updateGmsStatus(app.id, false)
            _uiState.update { it.copy(snackbarMessage = if (ok) "GMS removed" else "GMS removal failed") }
        }
    }

    fun showContextMenu(app: ClonedApp) { _uiState.update { it.copy(showContextMenu = true, contextMenuApp = app) } }
    fun dismissContextMenu() { _uiState.update { it.copy(showContextMenu = false, contextMenuApp = null) } }

    fun showDeleteConfirmation(app: ClonedApp) { _uiState.update { it.copy(showDeleteDialog = true, appToDelete = app, showContextMenu = false) } }
    fun dismissDeleteDialog() { _uiState.update { it.copy(showDeleteDialog = false, appToDelete = null) } }
    fun confirmDelete() {
        val app = _uiState.value.appToDelete ?: return
        viewModelScope.launch {
            appRepository.deleteClone(app.id)
            _uiState.update { it.copy(showDeleteDialog = false, appToDelete = null, snackbarMessage = "${app.displayName} deleted") }
        }
    }

    fun toggleBubble(enabled: Boolean) {
        if (enabled) BubbleService.start(context) else BubbleService.stop(context)
        _uiState.update { it.copy(isBubbleEnabled = enabled, snackbarMessage = if (enabled) "Bubble enabled" else "Bubble disabled") }
    }

    fun createShortcut(app: ClonedApp) {
        viewModelScope.launch {
            val ok = appRepository.createShortcut(app)
            if (ok) appRepository.updateShortcutStatus(app.id, true)
            _uiState.update { it.copy(snackbarMessage = if (ok) "Shortcut created" else "Failed to create shortcut", showContextMenu = false) }
        }
    }

    fun resetDeviceInfo(app: ClonedApp) {
        viewModelScope.launch {
            appRepository.updateCustomName(app.id, "")
            _uiState.update { it.copy(snackbarMessage = "Identity reset for ${app.displayName}") }
        }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }
}
