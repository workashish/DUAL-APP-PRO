package com.utility.toolbox.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.service.BlackBoxEngine
import com.utility.toolbox.service.BubbleService
import com.utility.toolbox.service.LogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val totalStorageUsed: Long = 0L,
    val isBubbleEnabled: Boolean = false,
    val clonedApps: List<ClonedApp> = emptyList(),
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val blackBoxEngine: BlackBoxEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appRepository.getAllClonedApps().collect { apps ->
                val totalSize = apps.sumOf { it.appSize }
                _uiState.update { it.copy(clonedApps = apps, totalStorageUsed = totalSize, isBubbleEnabled = BubbleService.isRunning(context)) }
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) { _uiState.update { it.copy(isDarkMode = enabled) } }
    fun toggleNotifications(enabled: Boolean) { _uiState.update { it.copy(notificationsEnabled = enabled) } }

    fun toggleBubble(enabled: Boolean) {
        if (enabled) BubbleService.start(context) else BubbleService.stop(context)
        _uiState.update { it.copy(isBubbleEnabled = enabled) }
    }

    fun requestOverlayPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return false
        }
        return true
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }

    fun killAllClones() {
        LogManager.i("Settings", "Killing all clone processes")
        blackBoxEngine.killAllCloneProcesses()
        viewModelScope.launch {
            _uiState.value.clonedApps.forEach { app ->
                appRepository.updateRunningStatus(app.id, false)
            }
        }
        _uiState.update { it.copy(snackbarMessage = "All clone processes killed") }
    }
}
