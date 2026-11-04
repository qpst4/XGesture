package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelGridLogic
import com.slideindex.app.widget.WidgetPanelPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WidgetPanelUiState(
    val pages: List<WidgetPanelPage> = emptyList(),
    val selectedPageIndex: Int = 0,
    val blurEnabled: Boolean = false,
    val blurRadiusDp: Int = 16,
    val closeOnClickMode: Int = 0,
    val isGridInteractionActive: Boolean = false,
    val isLoading: Boolean = false,
) {
    val currentPage: WidgetPanelPage
        get() = pages.getOrElse(selectedPageIndex) { WidgetPanelDefaults.defaultPage }
}

@HiltViewModel
class WidgetPanelEditorViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {

    private val _selectedPageIndex = MutableStateFlow(0)
    private val _isGridInteractionActive = MutableStateFlow(false)

    val uiState: StateFlow<WidgetPanelUiState> = combine(
        this.settings,
        _selectedPageIndex,
        _isGridInteractionActive,
    ) { currentSettings, selectedIndex, gridActive ->
        val pages = WidgetPanelDefaults.effectivePages(currentSettings.widgetPanelPages)
            .map { WidgetPanelGridLogic.fitPageToGrid(it) }
        val safeIndex = selectedIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        WidgetPanelUiState(
            pages = pages,
            selectedPageIndex = safeIndex,
            blurEnabled = currentSettings.widgetPanelBlurEnabled,
            blurRadiusDp = currentSettings.widgetPanelBlurRadiusDp,
            closeOnClickMode = currentSettings.widgetPanelCloseOnClickMode,
            isGridInteractionActive = gridActive,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WidgetPanelUiState(isLoading = true),
    )

    fun selectPage(index: Int) {
        _selectedPageIndex.value = index
    }

    fun setGridInteractionActive(active: Boolean) {
        _isGridInteractionActive.value = active
    }

    fun setPages(pages: List<WidgetPanelPage>) {
        launchSettingsWrite {
            settingsRepository.setWidgetPanelPages(pages)
        }
    }

    fun setBlurEnabled(enabled: Boolean) {
        launchSettingsWrite {
            settingsRepository.setWidgetPanelBlurEnabled(enabled)
        }
    }

    fun setBlurRadius(radiusDp: Int) {
        launchSettingsWrite {
            settingsRepository.setWidgetPanelBlurRadiusDp(radiusDp)
        }
    }

    fun setCloseOnClickMode(mode: Int) {
        launchSettingsWrite {
            settingsRepository.setWidgetPanelCloseOnClickMode(mode)
        }
    }
}
