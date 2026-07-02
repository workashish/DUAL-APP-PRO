package com.utility.toolbox.ui.screens.deviceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.service.BlackBoxEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceInfoUiState(
    val app: ClonedApp? = null,
    val deviceIdentity: BlackBoxEngine.DeviceIdentity? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val blackBoxEngine: BlackBoxEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    fun loadApp(appId: Long) {
        viewModelScope.launch {
            val app = appRepository.getClonedApp(appId)
            val identity = app?.let { blackBoxEngine.getDeviceIdentity(it.workspaceId) }
            _uiState.update {
                it.copy(app = app, deviceIdentity = identity, isLoading = false)
            }
        }
    }

    fun resetDeviceInfo() {
        val app = _uiState.value.app ?: return
        viewModelScope.launch {
            appRepository.resetDeviceInfo(app.id)
            _uiState.update {
                it.copy(deviceIdentity = blackBoxEngine.getDeviceIdentity(app.workspaceId))
            }
        }
    }

    fun resetGsf() {
        val app = _uiState.value.app ?: return
        viewModelScope.launch {
            appRepository.resetGsfLicense(app.id)
            _uiState.update {
                it.copy(deviceIdentity = blackBoxEngine.getDeviceIdentity(app.workspaceId))
            }
        }
    }
}
