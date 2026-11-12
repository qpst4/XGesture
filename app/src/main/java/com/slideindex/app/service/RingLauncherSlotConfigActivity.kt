package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FvRingLauncherAxis
import com.slideindex.app.settings.fvRingLauncherFor
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.ui.picker.PickerApps
import com.slideindex.app.ui.ringlauncher.RingLauncherSlotConfigSheet
import com.slideindex.app.ui.ringlauncher.RingLauncherSlotIconEditorHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 圆环槽位的**整页编辑器**（Activity，不再是浮层里的 sheet）。
 *
 * 与边角轮盘的 `CornerGestureSlotPickTrampolineActivity` 同形态：编辑期间圆环先挂起
 * （[RingLauncherOverlayWindow.suspendForSlotIconEditor]），退出时恢复
 * （[RingLauncherOverlayWindow.resumeAfterSlotIconEditor]，另有前台自愈兜底）。
 *
 * 槽位内容（应用/快捷方式/动作/快捷启动器条目）与自定义图标都在本页完成：
 * 图标编辑直接内联成子页，避免再起一个 Activity 把圆环的挂起/恢复嵌套两层。
 */
@AndroidEntryPoint
class RingLauncherSlotConfigActivity : ComponentActivity() {

    @Inject lateinit var deps: AppDependencies

    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val axis = intent.getStringExtra(EXTRA_AXIS)?.let(::axisFromName)
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
        if (axis == null || slotIndex < 0) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // 圆环先让位（幂等：调用方可能已经挂起过）。
        RingLauncherOverlayWindow.suspendForSlotIconEditor()

        setContent {
            val scope = rememberCoroutineScope()
            var appSettings by remember { mutableStateOf(deps.settingsRepository.readSnapshot()) }
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var iconEditing by remember { mutableStateOf(false) }
            var sheetBack by remember { mutableStateOf<(() -> Unit)?>(null) }

            LaunchedEffect(axis, slotIndex) {
                appSettings = deps.settingsRepository.settings.first()
                apps = PickerApps.fromCached(
                    this@RingLauncherSlotConfigActivity,
                    deps.appRepository,
                    appSettings.freezerAppPackages,
                )
                // 图标编辑保存后回来，重新读一次以刷新预览。
                appSettings = deps.settingsRepository.settings.first()
            }

            BackHandler(enabled = iconEditing) { iconEditing = false }
            BackHandler(enabled = !iconEditing) {
                val back = sheetBack
                if (back != null) back() else finishPicker()
            }

            CompositionLocalProvider(LocalAppDependencies provides deps) {
                ModuleTheme(settings = appSettings) {
                    if (iconEditing) {
                        val fvSettings = appSettings.fvRingLauncherFor(axis)
                        val slotLabel = fvSettings.itemAt(slotIndex)
                            ?.let { it.label.ifBlank { it.payload } }
                            .orEmpty()
                        RingLauncherSlotIconEditorHost(
                            slotLabel = slotLabel,
                            initialOverride = fvSettings.iconOverrideAt(slotIndex),
                            onBack = { iconEditing = false },
                            onSave = { override ->
                                scope.launch {
                                    deps.settingsRepository.setFvRingLauncherSlotIconOverride(
                                        axis = axis,
                                        index = slotIndex,
                                        override = override,
                                    )
                                    RingLauncherOverlayWindow.refreshFromSettings()
                                    appSettings = deps.settingsRepository.settings.first()
                                    iconEditing = false
                                }
                            },
                        )
                    } else {
                        RingLauncherSlotConfigSheet(
                            slotIndex = slotIndex,
                            currentItem = appSettings.fvRingLauncherFor(axis).itemAt(slotIndex),
                            apps = apps,
                            activityShortcuts = appSettings.activityShortcuts,
                            shellCommands = appSettings.shellCommands,
                            quickLauncherPanels = appSettings.quickLauncherPanels,
                            onDismiss = { finishPicker() },
                            onSelectItem = { selectedItem ->
                                scope.launch {
                                    // 与浮层版一致：清空槽位时落一个空 APP 条目。
                                    val itemToSet = selectedItem
                                        ?: QuickLauncherItem(QuickLauncherItemType.APP, "", "")
                                    deps.settingsRepository.setFvRingLauncherSlot(
                                        axis = axis,
                                        index = slotIndex,
                                        item = itemToSet,
                                    )
                                    RingLauncherOverlayWindow.refreshFromSettings()
                                    finishPicker()
                                }
                            },
                            onOpenCustomIconEditor = { iconEditing = true },
                            launchCreateShortcut = { host, onResult ->
                                CreateShortcutTrampoline.launch(
                                    context = this@RingLauncherSlotConfigActivity,
                                    host = host,
                                    onPrepare = {},
                                    onResult = onResult,
                                )
                            },
                            onBackHandlerReady = { handler -> sheetBack = handler },
                            pageMode = true,
                        )
                    }
                }
            }
        }
    }

    private fun finishPicker() {
        if (finished) return
        finished = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        RingLauncherOverlayWindow.resumeAfterSlotIconEditor()
    }

    override fun onDestroy() {
        // 进程被杀/返回键被系统吞掉等异常路径：别让圆环一直停在挂起态。
        if (!finished) {
            finished = true
            RingLauncherOverlayWindow.resumeAfterSlotIconEditor()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_AXIS = "axis"
        private const val EXTRA_SLOT_INDEX = "slot_index"

        fun createIntent(
            context: Context,
            axis: FvRingLauncherAxis,
            slotIndex: Int,
        ): Intent = Intent(context, RingLauncherSlotConfigActivity::class.java).apply {
            putExtra(EXTRA_AXIS, axis.name)
            putExtra(EXTRA_SLOT_INDEX, slotIndex)
        }

        private fun axisFromName(name: String): FvRingLauncherAxis? =
            runCatching { FvRingLauncherAxis.valueOf(name) }.getOrNull()
    }
}
