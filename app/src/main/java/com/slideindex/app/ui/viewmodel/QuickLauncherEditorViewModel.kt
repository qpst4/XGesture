package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
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
    /**
     * 列表页指定的面板还没出现在设置里（刚新建、写盘未完成）。
     *
     * 为 true 时编辑页只显示加载态、不渲染任何面板：否则会先渲染第一个面板，
     * 用户在那个页面上做的增删改都会写到错误的面板。
     */
    val isAwaitingPanel: Boolean = false,
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

    /**
     * 编辑页当前选中的面板。
     *
     * 列表页是「按 id 进入」的，而新建面板要等设置异步写盘后才会出现在快照里。以前这里把 id
     * 直接换算成下标、而且只换算一次：首帧读不到新面板就静默落到下标 0，之后再也不会重新解析，
     * 整个页面就停在第一个面板上（在那里做的增删改会写错面板）。
     *
     * 现在保留 id，让每次设置更新都重新解析下标——面板一出现就自动落到它上面。
     */
    private data class PanelSelection(
        val panelId: String? = null,
        val fallbackIndex: Int = 0,
    )

    private val _selection = MutableStateFlow(PanelSelection())
    private val _isGridInteractionActive = MutableStateFlow(false)
    private val _appsByPackage = MutableStateFlow<Map<String, AppInfo>>(emptyMap())

    /**
     * 刚新建、写盘可能还没回灌的面板。
     *
     * 新建后立刻进编辑页时先用它占位（不用等设置流），设置流追上后自动撤掉：
     * 既不会卡加载态，也不会在用户删掉它之后又把它“复活”。
     */
    private val _seedPanel = MutableStateFlow<QuickLauncherPanel?>(null)

    val uiState: StateFlow<QuickLauncherEditorUiState> = combine(
        this.settings,
        _selection,
        _isGridInteractionActive,
        _appsByPackage,
        _seedPanel,
    ) { currentSettings, selection, gridActive, appsMap, seedPanel ->
        val rawPanels = currentSettings.quickLauncherPanels
        val effectivePanels = QuickLauncherPanelDefaults.effectivePanels(rawPanels)
        val panels = if (seedPanel != null && effectivePanels.none { it.id == seedPanel.id }) {
            effectivePanels + seedPanel
        } else {
            effectivePanels
        }
        val targetIndex = selection.panelId
            ?.let { targetId -> panels.indexOfFirst { it.id == targetId } }
            ?: -1
        val safeIndex = if (targetIndex >= 0) {
            targetIndex
        } else {
            selection.fallbackIndex.coerceIn(0, (panels.size - 1).coerceAtLeast(0))
        }

        QuickLauncherEditorUiState(
            panels = panels,
            selectedPanelIndex = safeIndex,
            displaySettings = currentSettings.quickLauncherDisplay,
            defaultColumns = currentSettings.quickLauncherColumnsPerPage,
            defaultRows = currentSettings.quickLauncherRowsPerPage,
            appsByPackage = appsMap,
            shellCommands = currentSettings.shellCommands,
            activityShortcuts = currentSettings.activityShortcuts,
            isGridInteractionActive = gridActive,
            isLoading = false,
            isAwaitingPanel = selection.panelId != null && targetIndex < 0,
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
        // 设置流里已经出现种子面板后就不再需要它，避免之后删除它又被初值复活。
        viewModelScope.launch {
            settings.collect { current ->
                val seed = _seedPanel.value ?: return@collect
                if (current.quickLauncherPanels.any { it.id == seed.id }) {
                    _seedPanel.value = null
                }
            }
        }
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
        }
    }

    fun selectPanel(index: Int) {
        // 页内显式选择（复制/删除后换面板）优先于列表页传进来的 id。
        _selection.value = PanelSelection(panelId = null, fallbackIndex = index)
    }

    /**
     * 列表页按 id 进入：只记下目标 id，选中下标由 [uiState] 在每次设置更新时重新解析。
     *
     * 不能在这里就换算成下标——新建面板的写盘是异步的，首帧还没出现在设置里。
     */
    fun setTargetPanel(panelId: String?) {
        if (panelId.isNullOrBlank() || _selection.value.panelId == panelId) return
        _selection.value = PanelSelection(panelId = panelId, fallbackIndex = 0)
    }

    /** 新建后立刻进编辑页：先把刚建的面板作为初值占位，不必等写盘回灌。 */
    fun seedPanel(panel: QuickLauncherPanel) {
        _seedPanel.value = panel
        setTargetPanel(panel.id)
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
