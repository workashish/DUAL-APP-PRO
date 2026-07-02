package com.utility.toolbox.ui.screens.iconfake

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

data class IconFakeUiState(
    val app: ClonedApp? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class IconFakeViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IconFakeUiState())
    val uiState: StateFlow<IconFakeUiState> = _uiState.asStateFlow()

    fun loadApp(appId: Long) {
        viewModelScope.launch {
            val app = appRepository.getClonedApp(appId)
            _uiState.update { it.copy(app = app, isLoading = false) }
        }
    }

    fun setFakeIcon(iconPath: String) {
        val appId = _uiState.value.app?.id ?: return
        viewModelScope.launch {
            appRepository.setFakeIcon(appId, iconPath)
        }
    }
}
