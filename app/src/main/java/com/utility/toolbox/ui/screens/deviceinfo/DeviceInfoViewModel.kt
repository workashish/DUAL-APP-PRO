package com.utility.toolbox.ui.screens.deviceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.domain.model.ClonedApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceInfoUiState(val app: ClonedApp? = null, val isLoading: Boolean = true, val message: String? = null)

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(private val appRepository: AppRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()
    private var currentAppId: Long = 0

    fun loadApp(appId: Long) {
        currentAppId = appId
        viewModelScope.launch {
            val app = appRepository.getClonedApp(appId)
            _uiState.update { it.copy(app = app, isLoading = false) }
        }
    }

    fun resetDeviceInfo() {
        val app = _uiState.value.app ?: return
        viewModelScope.launch {
            val ok = appRepository.resetDeviceInfo(app.id)
            if (ok) {
                val updated = appRepository.getClonedApp(app.id)
                _uiState.update { it.copy(app = updated, message = "Device identity reset — new Android ID, IMEI, serial generated") }
            } else {
                _uiState.update { it.copy(message = "Reset failed") }
            }
        }
    }

    fun resetGsf() {
        val app = _uiState.value.app ?: return
        viewModelScope.launch {
            val ok = appRepository.resetGsfLicense(app.id)
            if (ok) {
                val updated = appRepository.getClonedApp(app.id)
                _uiState.update { it.copy(app = updated, message = "GSF license reset — new Android ID and GSF ID generated") }
            } else {
                _uiState.update { it.copy(message = "Reset failed") }
            }
        }
    }

    fun clearMessage() { _uiState.update { it.copy(message = null) } }
}
