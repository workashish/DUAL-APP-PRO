package com.utility.toolbox.ui.screens.gsflicense

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

data class GsfLicenseUiState(val app: ClonedApp? = null, val currentGsfId: String? = null, val isLoading: Boolean = true)

@HiltViewModel
class GsfLicenseViewModel @Inject constructor(private val appRepository: AppRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GsfLicenseUiState())
    val uiState: StateFlow<GsfLicenseUiState> = _uiState.asStateFlow()

    fun loadApp(appId: Long) {
        viewModelScope.launch {
            val app = appRepository.getClonedApp(appId)
            _uiState.update { it.copy(app = app, currentGsfId = app?.gsfId, isLoading = false) }
        }
    }

    fun resetLicense() {
        val app = _uiState.value.app ?: return
        viewModelScope.launch {
            appRepository.resetGsfLicense(app.id)
            val updated = appRepository.getClonedApp(app.id)
            _uiState.update { it.copy(app = updated, currentGsfId = updated?.gsfId) }
        }
    }

    fun setCustomLicense(gsfId: String) {
        val app = _uiState.value.app ?: return
        viewModelScope.launch {
            appRepository.setCustomGsfLicense(app.id, gsfId)
            val updated = appRepository.getClonedApp(app.id)
            _uiState.update { it.copy(app = updated, currentGsfId = updated?.gsfId) }
        }
    }
}
