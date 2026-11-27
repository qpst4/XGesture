package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.HoneycombDisplaySettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import com.slideindex.app.util.QuickLauncherIconResolver
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

data class HoneycombLauncherUiState(
    val items: List<QuickLauncherItem> = emptyList(),
    val displaySettings: HoneycombDisplaySettings = HoneycombDisplaySettings(),
    val appsByPackage: Map<String, AppInfo> = emptyMap(),
    val activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
    val shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    val iconBitmaps: List<ImageBitmap?> = emptyList(),
    val isLayoutEditing: Boolean = false,
    val isLoading: Boolean = false,
)

@HiltViewModel
class HoneycombLauncherEditorViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    private val appRepository: AppRepository,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {

    private val _isLayoutEditing = MutableStateFlow(false)
    private val _appsByPackage = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    private val _iconBitmaps = MutableStateFlow<List<ImageBitmap?>>(emptyList())

    val uiState: StateFlow<HoneycombLauncherUiState> = combine(
        this.settings,
        _isLayoutEditing,
        _appsByPackage,
        _iconBitmaps,
    ) { currentSettings, layoutEditing, appsMap, icons ->
        HoneycombLauncherUiState(
            items = currentSettings.honeycombLauncher.honeycombRuntimeItems(),
            displaySettings = currentSettings.honeycombDisplay,
            appsByPackage = appsMap,
            activityShortcuts = currentSettings.activityShortcuts,
            shellCommands = currentSettings.shellCommands,
            iconBitmaps = icons,
            isLayoutEditing = layoutEditing,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HoneycombLauncherUiState(isLoading = true),
    )

    init {
        loadAppsAndIcons()
    }

    private fun loadAppsAndIcons() {
        viewModelScope.launch {
            val cached = appRepository.getCachedApps()
            if (cached.isNotEmpty()) {
                _appsByPackage.value = cached.associateBy { it.packageName }
                refreshIcons()
            }
            val loaded = withContext(Dispatchers.IO) {
                appRepository.loadApps(force = false)
            }
            _appsByPackage.value = loaded.associateBy { it.packageName }
            refreshIcons()
        }
    }

    fun refreshIcons() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = uiState.value
            val currentItems = state.items
            val appsMap = state.appsByPackage
            val currentSettings = settingsRepository.readSnapshot()
            val resolved = currentItems.map { item ->
                QuickLauncherIconResolver.iconBitmap(
                    item,
                    appsMap,
                    128,
                    appContext,
                    activityShortcuts = currentSettings.activityShortcuts,
                    shellCommands = currentSettings.shellCommands,
                )?.asImageBitmap()
            }
            _iconBitmaps.value = resolved
        }
    }

    fun setLayoutEditing(editing: Boolean) {
        _isLayoutEditing.value = editing
    }

    fun setItems(items: List<QuickLauncherItem>) {
        val normalized = items.honeycombRuntimeItems()
        launchSettingsWrite {
            settingsRepository.setHoneycombLauncherItems(normalized)
        }
        refreshIcons()
    }

    fun setDisplaySettings(display: HoneycombDisplaySettings) {
        launchSettingsWrite {
            settingsRepository.setHoneycombDisplaySettings(display)
        }
    }
}
