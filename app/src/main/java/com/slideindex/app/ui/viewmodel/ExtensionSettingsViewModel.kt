package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.core.net.toUri
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.LauncherShortcutsApplier
import com.slideindex.app.launcher.resolveFolderItems
import com.slideindex.app.launcher.withItemsAtFolder
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.settings.FloatingPointerTrailType
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.overlay.FloatBallStyleAssetStore
import com.slideindex.app.settings.FloatBallStyleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.feedback.UserMessageBus
import com.slideindex.app.util.queryIntentActivitiesCompat
import com.slideindex.app.widget.WidgetPanelPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuickLauncherFolderDraft(
    val panelId: String,
    val name: String = "",
    val items: List<QuickLauncherItem> = emptyList(),
)

@HiltViewModel
class ExtensionSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val _folderDraft = MutableStateFlow<QuickLauncherFolderDraft?>(null)
    val folderDraft: StateFlow<QuickLauncherFolderDraft?> = _folderDraft.asStateFlow()

    /**
     * 全息启动器的旧「隐藏应用」迁移：把黑名单并进选中列表并切到「排除选中应用」。
     *
     * 由设置页进入时触发一次（幂等）。旧数据清空后这条路径自然变成空操作，
     * 所以不需要额外的「已迁移」标记键。
     */
    fun migrateHolographicHiddenApps() = launchSettingsWrite {
        runCatching {
            val installed = withContext(Dispatchers.IO) {
                val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                appContext.packageManager
                    .queryIntentActivitiesCompat(mainIntent, android.content.pm.PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
            }
            settingsRepository.migrateHolographicHiddenAppsOnce(installed)
        }.map { }
    }

    fun initFolderDraft(panelId: String) {
        val current = _folderDraft.value
        if (current == null || current.panelId != panelId) {
            _folderDraft.value = QuickLauncherFolderDraft(panelId)
        }
    }

    fun updateFolderDraftName(name: String) {
        _folderDraft.value = _folderDraft.value?.copy(name = name)
    }

    fun toggleFolderDraftItem(item: QuickLauncherItem, added: Boolean) {
        val draft = _folderDraft.value ?: return
        val items = if (added) {
            draft.items.filterNot { it.type == item.type && it.payload == item.payload }
        } else {
            draft.items + item
        }
        _folderDraft.value = draft.copy(items = items)
    }

    fun addFolderDraftItem(item: QuickLauncherItem) {
        val draft = _folderDraft.value ?: return
        if (draft.items.none { it.type == item.type && it.payload == item.payload }) {
            _folderDraft.value = draft.copy(items = draft.items + item)
        }
    }

    fun clearFolderDraft() {
        _folderDraft.value = null
    }

    fun commitFolderDraft(name: String) {
        val draft = _folderDraft.value ?: return
        addQuickLauncherPanelItem(
            draft.panelId,
            QuickLauncherItem.folder(name, draft.items),
        )
        _folderDraft.value = null
    }

    fun setQuickLauncherPanels(panels: List<QuickLauncherPanel>) = launchSettingsWrite {
        settingsRepository.setQuickLauncherPanels(
            QuickLauncherPanelDefaults.effectivePanels(panels),
        )
    }

    fun setQuickLauncherDisplaySettings(settings: com.slideindex.app.settings.QuickLauncherDisplaySettings) =
        launchSettingsWrite {
            settingsRepository.setQuickLauncherDisplaySettings(settings)
        }

    fun setQuickLauncherItems(items: List<QuickLauncherItem>) = launchSettingsWrite {
        settingsRepository.setQuickLauncherItems(items)
    }

    fun setHoneycombLauncherItems(items: List<QuickLauncherItem>) = launchSettingsWrite {
        settingsRepository.setHoneycombLauncherItems(items)
    }

    fun setQuickWheels(wheels: List<com.slideindex.app.settings.QuickWheel>) = launchSettingsWrite {
        settingsRepository.setQuickWheels(wheels)
    }

    fun setHoneycombDisplaySettings(settings: com.slideindex.app.settings.HoneycombDisplaySettings) =
        launchSettingsWrite {
            settingsRepository.setHoneycombDisplaySettings(settings)
        }

    fun setHolographicLauncherTimeoutSeconds(value: Int) = launchSettingsWrite {
        settingsRepository.setHolographicLauncherTimeoutSeconds(value)
    }

    fun setHolographicRotationSensitivity(value: Float) = launchSettingsWrite {
        settingsRepository.setHolographicRotationSensitivity(value)
    }

    fun setHolographicHapticLevel(value: Int) = launchSettingsWrite {
        settingsRepository.setHolographicHapticLevel(value)
    }

    fun setHolographicBackgroundStyle(value: Int) = launchSettingsWrite {
        settingsRepository.setHolographicBackgroundStyle(value)
    }

    fun setHolographicBlurDp(value: Int) = launchSettingsWrite {
        settingsRepository.setHolographicBlurDp(value)
    }

    fun setHolographicDimPercent(value: Int) = launchSettingsWrite {
        settingsRepository.setHolographicDimPercent(value)
    }

    fun addHolographicHiddenApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addHolographicHiddenApp(packageName)
    }

    fun removeHolographicHiddenApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeHolographicHiddenApp(packageName)
    }

    fun addHolographicLauncherApp(packageName: String) = launchSettingsWrite {
        settingsRepository.addHolographicLauncherApp(packageName)
    }

    fun setHolographicAppMode(mode: com.slideindex.app.settings.HolographicAppMode) =
        launchSettingsWrite {
            settingsRepository.setHolographicAppMode(mode)
        }

    fun setHolographicLauncherApps(packageNames: List<String>) = launchSettingsWrite {
        settingsRepository.setHolographicLauncherApps(packageNames)
    }

    fun removeHolographicLauncherApp(packageName: String) = launchSettingsWrite {
        settingsRepository.removeHolographicLauncherApp(packageName)
    }

    /**
     * 增删面板条目。[folderIndex] 指向面板内的文件夹时改动写进该文件夹的子项，
     * 为负（默认）时写面板根列表。
     */
    fun toggleQuickLauncherPanelItem(
        panelId: String,
        item: QuickLauncherItem,
        added: Boolean,
        folderIndex: Int = -1,
    ) = launchSettingsWrite {
        val currentPanels = QuickLauncherPanelDefaults.effectivePanels(settingsRepository.readSnapshot().quickLauncherPanels)
        val updated = currentPanels.map { panel ->
            if (panel.id == panelId) {
                val targetItems = panel.items.resolveFolderItems(folderIndex)
                val nextItems = if (added) {
                    when (item.type) {
                        QuickLauncherItemType.APP -> targetItems.filterNot { it.type == QuickLauncherItemType.APP && it.payload == item.payload }
                        QuickLauncherItemType.SHORTCUT -> {
                            val key = QuickLauncherItemCodec.shortcutItemKey(item)
                            targetItems.filterNot { it.type == QuickLauncherItemType.SHORTCUT && QuickLauncherItemCodec.shortcutItemKey(it) == key }
                        }
                        QuickLauncherItemType.ACTION -> {
                            val actionKey = QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(QuickLauncherItemCodec::actionKey)
                            targetItems.filterNot { it.type == QuickLauncherItemType.ACTION && QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) == actionKey }
                        }
                        QuickLauncherItemType.WIDGET -> targetItems.filterNot { it.type == QuickLauncherItemType.WIDGET && it.payload == item.payload }
                        QuickLauncherItemType.FOLDER -> targetItems.filterNot { it.type == QuickLauncherItemType.FOLDER && it.payload == item.payload && it.label == item.label }
                    }
                } else {
                    targetItems + item
                }
                panel.copy(items = panel.items.withItemsAtFolder(folderIndex, nextItems))
            } else {
                panel
            }
        }
        settingsRepository.setQuickLauncherPanels(QuickLauncherPanelDefaults.effectivePanels(updated))
    }

    fun addQuickLauncherPanelItem(panelId: String, item: QuickLauncherItem, folderIndex: Int = -1) =
        toggleQuickLauncherPanelItem(panelId, item, added = false, folderIndex = folderIndex)

    fun toggleHoneycombItem(item: QuickLauncherItem, added: Boolean) = launchSettingsWrite {
        val current = settingsRepository.readSnapshot().honeycombLauncher.honeycombRuntimeItems()
        val next = if (added) {
            when (item.type) {
                QuickLauncherItemType.APP -> current.filterNot { it.type == QuickLauncherItemType.APP && it.payload == item.payload }
                QuickLauncherItemType.SHORTCUT -> {
                    val key = QuickLauncherItemCodec.shortcutItemKey(item) ?: return@launchSettingsWrite Result.success(Unit)
                    current.filterNot { it.type == QuickLauncherItemType.SHORTCUT && QuickLauncherItemCodec.shortcutItemKey(it) == key }
                }
                QuickLauncherItemType.ACTION -> {
                    val actionKey = QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(QuickLauncherItemCodec::actionKey) ?: return@launchSettingsWrite Result.success(Unit)
                    current.filterNot { it.type == QuickLauncherItemType.ACTION && QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) == actionKey }
                }
                else -> return@launchSettingsWrite Result.success(Unit)
            }
        } else {
            current + item
        }
        settingsRepository.setHoneycombLauncherItems(next.honeycombRuntimeItems())
    }

    fun addHoneycombItem(item: QuickLauncherItem) =
        toggleHoneycombItem(item, added = false)

    fun setQuickLauncherColumnsPerPage(value: Int) = launchSettingsWrite {
        settingsRepository.setQuickLauncherColumnsPerPage(value)
    }

    fun setQuickLauncherRowsPerPage(value: Int) = launchSettingsWrite {
        settingsRepository.setQuickLauncherRowsPerPage(value)
    }

    fun setShellCommands(items: List<ShellCommand>) = launchSettingsWrite {
        settingsRepository.setShellCommands(items)
    }

    fun setActivityShortcuts(items: List<com.slideindex.app.activity.ActivityShortcut>) = launchSettingsWrite {
        settingsRepository.setActivityShortcuts(items)
    }

    fun setLauncherShortcutMenuOrder(ids: List<String>) = launchSettingsWrite {
        val disabled = settingsRepository.readSnapshot().launcherShortcutMenuDisabled
        settingsRepository.setLauncherShortcutMenuOrder(ids).also { result ->
            if (result.isSuccess) {
                LauncherShortcutsApplier.sync(appContext, ids, disabled)
            }
        }
    }

    fun setLauncherShortcutMenuDisabled(ids: Set<String>) = launchSettingsWrite {
        val order = settingsRepository.readSnapshot().launcherShortcutMenuOrder
        settingsRepository.setLauncherShortcutMenuDisabled(ids).also { result ->
            if (result.isSuccess) {
                LauncherShortcutsApplier.sync(appContext, order, ids)
            }
        }
    }

    fun setWidgetPanelPages(pages: List<WidgetPanelPage>) = launchSettingsWrite {
        settingsRepository.setWidgetPanelPages(pages)
    }

    fun setAppCarouselSwitcherSettings(settings: com.slideindex.app.settings.AppCarouselSwitcherSettings) =
        launchSettingsWrite {
            settingsRepository.setAppCarouselSwitcherSettings(settings)
        }

    fun setWidgetPanelBlurEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setWidgetPanelBlurEnabled(enabled)
    }

    fun setWidgetPanelBlurRadiusDp(radiusDp: Int) = launchSettingsWrite {
        settingsRepository.setWidgetPanelBlurRadiusDp(radiusDp)
    }

    fun setWidgetPanelWidthFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setWidgetPanelWidthFraction(fraction)
    }

    fun setFloatingPointerSensitivityFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerSensitivityFraction(fraction)
    }

    fun setFloatingPointerPointerDiameterPx(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerPointerDiameterPx(size)
    }

    fun setFloatingPointerRingThicknessPx(thickness: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRingThicknessPx(thickness)
    }

    fun setFloatingPointerDotDiameterPx(diameter: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDotDiameterPx(diameter)
    }

    fun setFloatingPointerRingColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRingColor(color)
    }

    fun setFloatingPointerFillColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerFillColor(color)
    }

    fun setFloatingPointerDotColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDotColor(color)
    }

    fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickVisualFeedbackEnabled(enabled)
    }

    fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickHapticEnabled(enabled)
    }

    fun setFloatingPointerRippleColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleColor(color)
    }

    fun setFloatingPointerRippleSizeDp(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleSizeDp(size)
    }

    fun setFloatingPointerRippleDurationMs(duration: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleDurationMs(duration)
    }

    fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailType(type)
    }

    fun setFloatingPointerTrailDurationMs(duration: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailDurationMs(duration)
    }

    fun setFloatingPointerTrailColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailColor(color)
    }

    fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideWhenJoystickReleased(enabled)
    }

    fun setFloatingPointerDesignId(designId: String) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDesignId(designId)
    }

    fun resetFloatingPointerVisualDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerVisualDefaults()
    }

    fun setFloatingPointerJoystickDiameterPx(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickDiameterPx(size)
    }

    fun setFloatingPointerJoystickInnerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickInnerColor(color)
    }

    fun setFloatingPointerJoystickOuterColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickOuterColor(color)
    }

    fun setFloatingPointerJoystickGradientRadiusFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickGradientRadiusFraction(fraction)
    }

    fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideOnOutsideClick(enabled)
    }

    fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideOnQuickSwipe(enabled)
    }

    fun setFloatingPointerHideWhenIdle(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideWhenIdle(enabled)
    }

    fun setFloatingPointerReleaseClickAndDismiss(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerReleaseClickAndDismiss(enabled)
    }

    fun setFloatingPointerHoverEnterSelect(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHoverEnterSelect(enabled)
    }

    fun setFloatingPointerClickDistanceThresholdDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickDistanceThresholdDp(value)
    }

    fun setFloatingPointerIdleHideDelayMs(delayMs: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerIdleHideDelayMs(delayMs)
    }

    fun setFloatingPointerJoystickLongPressAction(action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickLongPressAction(action)
    }

    fun resetFloatingPointerJoystickVisualDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickVisualDefaults()
    }

    fun resetFloatingPointerJoystickBehaviorDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickBehaviorDefaults()
    }

    fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialAlwaysVisible(enabled)
    }

    fun setFloatingPointerRadialLongPressMs(ms: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialLongPressMs(ms)
    }

    fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialSlotAction(index, action)
    }

    fun setFloatingPointerRadialOuterDiameterPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialOuterDiameterPx(value)
    }

    fun setFloatingPointerRadialInnerDiameterPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialInnerDiameterPx(value)
    }

    fun setFloatingPointerRadialOuterColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialOuterColor(color)
    }

    fun setFloatingPointerRadialInnerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialInnerColor(color)
    }

    fun setFloatingPointerRadialDividerThicknessPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialDividerThicknessPx(value)
    }

    fun setFloatingPointerRadialDividerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialDividerColor(color)
    }

    fun setFloatingPointerRadialIconSizeFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialIconSizeFraction(value)
    }

    fun setFloatingPointerRadialIconColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialIconColor(color)
    }

    fun resetFloatingPointerRadialDesignDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerRadialDesignDefaults()
    }

    fun setFloatingPointerEdgeThresholdDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeThresholdDp(value)
    }

    fun setFloatingPointerEdgePreviewSensitivity(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewSensitivity(value)
    }

    fun setFloatingPointerEdgePreviewGlowSize(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewGlowSize(value)
    }

    fun setFloatingPointerEdgePreviewShowIcon(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewShowIcon(enabled)
    }

    fun setFloatingPointerEdgeVisualColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeVisualColor(color)
    }

    fun setFloatingPointerEdgeBarEnabled(side: FloatingPointerEdgeSide, enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeBarEnabled(side, enabled)
    }

    fun setFloatingPointerEdgeBarSlotAction(
        side: FloatingPointerEdgeSide,
        slotIndex: Int,
        action: GestureAction,
    ) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeBarSlotAction(side, slotIndex, action)
    }

    fun addFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide) = launchSettingsWrite {
        settingsRepository.addFloatingPointerEdgeBarSlot(side)
    }

    fun removeFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide, slotIndex: Int) = launchSettingsWrite {
        settingsRepository.removeFloatingPointerEdgeBarSlot(side, slotIndex)
    }

    fun resetFloatingPointerEdgeDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerEdgeDefaults()
    }

    fun setFloatBallEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallEnabled(enabled)
    }

    fun setFloatBallSizeDp(sizeDp: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallSizeDp = sizeDp.coerceIn(36f, 96f)))
        },
        block = { settingsRepository.setFloatBallSizeDp(sizeDp) },
    )

    fun setFloatBallPickCrossArmDp(armDp: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickCrossArmDp(armDp)
    }

    fun setFloatBallOpacity(opacity: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallOpacity = opacity.coerceIn(0f, 1f)))
        },
        block = { settingsRepository.setFloatBallOpacity(opacity) },
    )

    fun setFloatBallHideWhenIdle(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallHideWhenIdle = enabled))
        },
        block = { settingsRepository.setFloatBallHideWhenIdle(enabled) },
    )

    fun setFloatBallIdleFreezeGif(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallIdleFreezeGif = enabled))
        },
        block = { settingsRepository.setFloatBallIdleFreezeGif(enabled) },
    )

    fun setFloatBallIdleOpacity(opacity: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallIdleOpacity = opacity.coerceIn(0f, 1f)))
        },
        block = { settingsRepository.setFloatBallIdleOpacity(opacity) },
    )

    fun setFloatBallIdleDelayMs(delayMs: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallIdleDelayMs = delayMs.coerceIn(0, 10_000)))
        },
        block = { settingsRepository.setFloatBallIdleDelayMs(delayMs) },
    )

    fun setDefaultImageViewerPackage(packageName: String?) = launchSettingsWrite {
        settingsRepository.setDefaultImageViewerPackage(packageName)
    }

    fun setImageEditorDelayDeleteEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setImageEditorDelayDeleteEnabled(enabled)
    }

    fun setFloatBallOcrFallbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallOcrFallbackEnabled(enabled)
    }

    fun setFloatBallPointerSpeedFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSpeedFraction(fraction)
    }

    fun setFloatBallPointerSpeedVerticalFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSpeedVerticalFraction(fraction)
    }

    fun setFloatBallPickAccelZoneFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickAccelZoneFraction(fraction)
    }

    fun setFloatBallPositionMode(mode: FloatBallPositionMode) = launchSettingsWrite {
        settingsRepository.setFloatBallPositionMode(mode)
    }

    fun setFloatBallPositionYFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPositionYFraction(fraction)
    }

    fun setFloatBallVisibleFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallVisibleFraction(fraction)
    }

    fun setFloatBallLineHeightFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineHeightFraction(value)
    }

    fun setFloatBallLineWidthDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineWidthDp(value)
    }

    fun setFloatBallLineOpacity(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineOpacity(value)
    }

    fun setFloatBallGestureAction(type: FloatBallGestureType, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatBallGestureAction(type, action)
    }

    fun setFloatBallStyleType(type: FloatBallStyleType) = launchSettingsWrite {
        settingsRepository.setFloatBallStyleType(type)
    }

    fun setFloatBallCustomImageUri(uri: String) = launchSettingsWrite {
        val imported = withContext(Dispatchers.IO) {
            val stored = FloatBallStyleAssetStore.importCustomImage(appContext, uri.toUri()) ?: uri
            // 宽高比必须在**导入后的本地副本**上读：原始 content:// 可能只可读一次或已失效。
            stored to FloatBallStyleAssetStore.readAspect(appContext, stored.toUri())
        }
        settingsRepository.setFloatBallCustomImageUri(imported.first)
        // 换图必须成对写宽高比：读不出来就清成 0，避免沿用上一张图的比例。
        settingsRepository.setFloatBallCustomImageAspect(imported.second ?: 0f)
    }

    fun setFloatBallCustomImageNoCrop(enabled: Boolean) = launchSettingsWrite {
        // 打开开关的瞬间就要有比例可用：老数据可能只有 URI 没有宽高比（进程启动时的迁移未必跑过），
        // 这里补一次，避免用户看到"开了跟没开一样"（方形窗口 + 透明边）。
        if (enabled) backfillCustomImageAspectIfMissing()
        settingsRepository.setFloatBallCustomImageNoCrop(enabled)
    }

    /** 只在宽高比缺失时读一次文件头；已有比例直接跳过，不产生多余写入。 */
    private suspend fun backfillCustomImageAspectIfMissing() {
        val current = overlaySettings.value
        if (current.floatBallCustomImageAspect > 0f) return
        val uri = current.floatBallCustomImageUri
        if (uri.isBlank()) return
        val aspect = withContext(Dispatchers.IO) {
            FloatBallStyleAssetStore.readAspect(appContext, uri.toUri())
        }
        if (aspect != null) settingsRepository.setFloatBallCustomImageAspect(aspect)
    }

    fun setFloatBallSlideshowUris(uris: List<String>) = launchSettingsWrite {
        val stored = withContext(Dispatchers.IO) {
            FloatBallStyleAssetStore.importSlideshow(appContext, uris.map { it.toUri() })
        }.ifEmpty { uris }
        settingsRepository.setFloatBallSlideshowUris(stored)
    }

    fun setFloatBallGifUri(uri: String) = launchSettingsWrite {
        val stored = withContext(Dispatchers.IO) {
            FloatBallStyleAssetStore.importGif(appContext, uri.toUri())
        } ?: uri
        settingsRepository.setFloatBallGifUri(stored)
    }

    fun setFloatBallPointerSlopDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSlopDp(value)
    }

    fun setFloatBallDownSwipeShortPercent(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallDownSwipeShortPercent(value)
    }

    fun setFloatBallLongPressMs(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatBallLongPressMs(value)
    }

    fun setFloatBallSideSwipeShortPercent(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallSideSwipeShortPercent(value)
    }

    fun setFloatBallUpSwipeShortPercent(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallUpSwipeShortPercent(value)
    }

    fun setFloatBallOverlayAnchorAtTouchDown(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallOverlayAnchorAtTouchDown(enabled)
    }

    fun setFloatBallGestureHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallGestureHapticEnabled(enabled)
    }

    fun setFloatBallBackGestureHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallBackGestureHapticEnabled(enabled)
    }

    fun setFloatBallLongPressAutoSelectRingSlot(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallLongPressAutoSelectRingSlot(enabled)
    }

    fun setFloatBallPickOffsetDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickOffsetDp(value)
    }

    fun setFloatBallPickTextSizeSp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickTextSizeSp(value)
    }

    fun setFloatBallPickBottomTransitionFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickBottomTransitionFraction(value)
    }

    fun setFloatBallPickTextFirstPanel(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallPickTextFirstPanel(enabled)
    }

    fun setFloatBallPickPanelStyle(style: com.slideindex.app.settings.PickResultPanelStyle) = launchSettingsWrite {
        settingsRepository.setFloatBallPickPanelStyle(style)
    }

    fun setFloatBallPickPanelPlacement(
        placement: com.slideindex.app.settings.PickResultPanelPlacement,
    ) = launchSettingsWrite {
        settingsRepository.setFloatBallPickPanelPlacement(placement)
    }

    fun setFloatBallPickSearchGridDefaultState(state: com.slideindex.app.settings.PickResultSearchGridDefaultState) = launchSettingsWrite {
        settingsRepository.setFloatBallPickSearchGridDefaultState(state)
    }

    fun setFloatBallPickSearchGridLastExpanded(expanded: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallPickSearchGridLastExpanded(expanded)
    }

    fun setFloatBallPickTextModeDefault(state: com.slideindex.app.settings.PickResultTextModeDefault) =
        launchSettingsWrite {
            settingsRepository.setFloatBallPickTextModeDefault(state)
        }

    fun setFloatBallPickDefaultSearchEngineId(id: String?) = launchSettingsWrite {
        settingsRepository.setFloatBallPickDefaultSearchEngineId(id)
    }

    fun setFloatBallPickAutoSelectAll(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallPickAutoSelectAll(enabled)
    }

    fun setFloatBallPickCopyDismissPanel(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallPickCopyDismissPanel(enabled)
    }

    fun setFloatBallPickCopyButtonPosition(position: com.slideindex.app.settings.PickResultCopyButtonPosition) =
        launchSettingsWrite {
            settingsRepository.setFloatBallPickCopyButtonPosition(position)
        }

    fun setFloatBallPickImageToolbarPosition(position: com.slideindex.app.settings.PickResultImageToolbarPosition) =
        launchSettingsWrite {
            settingsRepository.setFloatBallPickImageToolbarPosition(position)
        }

    fun setFloatBallPickHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallPickHapticEnabled(enabled)
    }

    fun setFloatBallDragPickEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallDragPickEnabled(enabled)
    }

    fun setFloatBallDragPasteEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallDragPasteEnabled(enabled)
    }

    fun setFloatBallPickPanelEnterAnimationMs(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatBallPickPanelEnterAnimationMs(value)
    }

    fun setFloatBallPickPanelExitAnimationMs(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatBallPickPanelExitAnimationMs(value)
    }

    fun setShareImageOcrHistoryEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShareImageOcrHistoryEnabled(enabled)
    }

    fun setShareSheetEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShareSheetEnabled(enabled)
    }

    fun setFloatBallHoverPauseDelayMs(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatBallHoverPauseDelayMs(value)
    }

    fun setFloatBallRegionalCancelSlopDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallRegionalCancelSlopDp(value)
    }

    fun setFvRingLauncherSettings(
        axis: com.slideindex.app.settings.FvRingLauncherAxis,
        settings: com.slideindex.app.settings.FvRingLauncherSettings,
    ) = launchSettingsWrite {
        settingsRepository.setFvRingLauncherSettings(axis, settings)
    }

    fun setFvRingLauncherLinkAppearanceAxes(
        enabled: Boolean,
        activeAxis: com.slideindex.app.settings.FvRingLauncherAxis = com.slideindex.app.settings.FvRingLauncherAxis.VERTICAL,
        mergeDirection: com.slideindex.app.settings.FvRingLauncherAxisMergeDirection? = null,
    ) = launchSettingsWrite {
        settingsRepository.setFvRingLauncherLinkAppearanceAxes(enabled, activeAxis, mergeDirection)
    }

    fun setFvRingLauncherLinkSlotAxes(
        enabled: Boolean,
        activeAxis: com.slideindex.app.settings.FvRingLauncherAxis = com.slideindex.app.settings.FvRingLauncherAxis.VERTICAL,
        mergeDirection: com.slideindex.app.settings.FvRingLauncherAxisMergeDirection? = null,
    ) = launchSettingsWrite {
        settingsRepository.setFvRingLauncherLinkSlotAxes(enabled, activeAxis, mergeDirection)
    }

    fun setDragDropFlowEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setDragDropFlowEnabled(enabled)
    }

    fun setDragDropFlowPosition(position: com.slideindex.app.settings.DragDropFlowPosition) = launchSettingsWrite {
        settingsRepository.setDragDropFlowPosition(position)
    }

    fun setDragDropFlowHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setDragDropFlowHapticEnabled(enabled)
    }
}
