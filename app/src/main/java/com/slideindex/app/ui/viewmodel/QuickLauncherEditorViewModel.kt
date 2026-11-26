package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherDefaults
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.QuickLauncherPanelMutator
import com.slideindex.app.settings.QuickLauncherDisplaySettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class QuickLauncherEditorUiState(
    val panels: List<QuickLauncherPanel> = emptyList(),
    val selectedPanelIndex: Int = 0,
    val displaySettings: QuickLauncherDisplaySettings = QuickLauncherDisplaySettings(),
    val defaultColumns: Int = 3,
    val defaultRows: Int = 4,
    val appsByPackage: Map<String, AppInfo> = emptyMap(),
    /**
     * 预览网格解析 Shell 命令 / 应用内直达图标要用到这两个列表；
     * 缺了会把动作退化成通用矢量图标。
     */
    val shellCommands: List<ShellCommand> = emptyList(),
    val activityShortcuts: List<ActivityShortcut> = emptyList(),
    val isGridInteractionActive: Boolean = false,
    val isLoading: Boolean = false,
) {
    val currentPanel: QuickLauncherPanel
        get() = panels.getOrElse(selectedPanelIndex) {
            QuickLauncherPanelDefaults.defaultPanel()
        }

    val currentPanelItems: List<QuickLauncherItem>
        get() = currentPanel.items
}

@HiltViewModel
class QuickLauncherEditorViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    private val appRepository: AppRepository,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {

    private val _selectedPanelIndex = MutableStateFlow(0)
    private val _isGridInteractionActive = MutableStateFlow(false)
    private val _appsByPackage = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    private var defaultsSeeded = false

    val uiState: StateFlow<QuickLauncherEditorUiState> = combine(
        this.settings,
        _selectedPanelIndex,
        _isGridInteractionActive,
        _appsByPackage,
    ) { currentSettings, selectedIndex, gridActive, appsMap ->
        val rawPanels = currentSettings.quickLauncherPanels
        val effectivePanels = QuickLauncherPanelDefaults.effectivePanels(rawPanels)
        val safeIndex = selectedIndex.coerceIn(0, (effectivePanels.size - 1).coerceAtLeast(0))

        QuickLauncherEditorUiState(
            panels = effectivePanels,
            selectedPanelIndex = safeIndex,
            displaySettings = currentSettings.quickLauncherDisplay,
            defaultColumns = currentSettings.quickLauncherColumnsPerPage,
            defaultRows = currentSettings.quickLauncherRowsPerPage,
            appsByPackage = appsMap,
            shellCommands = currentSettings.shellCommands,
            activityShortcuts = currentSettings.activityShortcuts,
            isGridInteractionActive = gridActive,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuickLauncherEditorUiState(
            panels = QuickLauncherPanelDefaults.effectivePanels(emptyList()),
            isLoading = true,
        ),
    )

    init {
        loadAppsAndIcons()
    }

    private fun loadAppsAndIcons() {
        viewModelScope.launch {
            val cached = appRepository.getCachedApps()
            if (cached.isNotEmpty()) {
                _appsByPackage.value = cached.associateBy { it.packageName }
            }
            val loaded = withContext(Dispatchers.IO) {
                appRepository.loadApps(force = false)
            }
            _appsByPackage.value = loaded.associateBy { it.packageName }
            seedDefaultsIfNeeded(loaded)
        }
    }

    private fun seedDefaultsIfNeeded(apps: List<AppInfo>) {
        if (defaultsSeeded || apps.isEmpty()) return
        val currentPanels = uiState.value.panels
        if (currentPanels.size == 1 && currentPanels.first().items.isEmpty()) {
            val onlyPanel = currentPanels.first()
            val effective = QuickLauncherDefaults.effectiveItems(emptyList(), apps)
            if (effective.isNotEmpty()) {
                val updated = QuickLauncherPanelMutator.updatePanelItems(currentPanels, onlyPanel.id, effective)
                setPanels(updated)
            }
        }
        defaultsSeeded = true
    }

    fun selectPanel(index: Int) {
        _selectedPanelIndex.value = index
    }

    /** 从列表页按 panelId 进入：找到对应下标再选中。 */
    fun selectPanelById(panelId: String) {
        val panels = uiState.value.panels
        val index = panels.indexOfFirst { it.id == panelId }
        if (index >= 0) selectPanel(index)
    }

    fun setGridInteractionActive(active: Boolean) {
        _isGridInteractionActive.value = active
    }

    fun setPanels(panels: List<QuickLauncherPanel>) {
        launchSettingsWrite {
            settingsRepository.setQuickLauncherPanels(
                QuickLauncherPanelDefaults.effectivePanels(panels),
            )
        }
    }

    fun setDisplaySettings(display: QuickLauncherDisplaySettings) {
        launchSettingsWrite {
            settingsRepository.setQuickLauncherDisplaySettings(display)
        }
    }

    fun updateCurrentPanelItems(items: List<QuickLauncherItem>) {
        val state = uiState.value
        val currentPanelId = state.currentPanel.id
        val updated = QuickLauncherPanelMutator.updatePanelItems(state.panels, currentPanelId, items)
        setPanels(updated)
    }
}
