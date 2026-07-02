package com.utility.toolbox.ui.screens.customize

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utility.toolbox.data.repository.AppRepository
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.ui.theme.WorkspaceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomizeUiState(
    val clonedApp: ClonedApp? = null,
    val customName: String = "",
    val selectedColorIndex: Int = 0,
    val isLoading: Boolean = true,
    val saved: Boolean = false
)

@HiltViewModel
class CustomizeViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomizeUiState())
    val uiState: StateFlow<CustomizeUiState> = _uiState.asStateFlow()

    fun loadApp(appId: Long) {
        viewModelScope.launch {
            val app = appRepository.getClonedApp(appId)
            val colorIndex = app?.customIconColor?.let { storedColor ->
                WorkspaceColors.indexOfFirst { it.value.toInt() == storedColor }
            } ?: 0

            _uiState.update {
                it.copy(
                    clonedApp = app,
                    customName = app?.customName ?: app?.appName ?: "",
                    selectedColorIndex = if (colorIndex >= 0) colorIndex else 0,
                    isLoading = false
                )
            }
        }
    }

    fun updateCustomName(name: String) {
        _uiState.update { it.copy(customName = name) }
    }

    fun selectColor(index: Int) {
        _uiState.update { it.copy(selectedColorIndex = index) }
    }

    fun save() {
        val app = _uiState.value.clonedApp ?: return
        val name = _uiState.value.customName
        val color = WorkspaceColors[_uiState.value.selectedColorIndex]

        viewModelScope.launch {
            appRepository.updateCustomName(app.id, name)
            // Store the actual color ARGB value as an Int
            appRepository.updateCustomIconColor(app.id, color.value.toInt())
            _uiState.update { it.copy(saved = true) }
        }
    }
}
