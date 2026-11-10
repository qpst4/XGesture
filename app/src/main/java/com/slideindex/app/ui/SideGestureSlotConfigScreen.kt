package com.slideindex.app.ui



import androidx.compose.foundation.layout.size

import com.slideindex.app.ui.settings.components.MiuixNavigationRow

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.slideindex.app.R
import com.slideindex.app.gesture.requiresContinuousTriggerOnly

import com.slideindex.app.gesture.GestureAction

import com.slideindex.app.gesture.GestureTriggerMode

import com.slideindex.app.gesture.GestureTriggerType

import com.slideindex.app.overlay.PanelSide

import com.slideindex.app.settings.AppCarouselSwitcherSettings
import com.slideindex.app.settings.AppSettings

import com.slideindex.app.settings.slotAction

import com.slideindex.app.settings.defaultTriggerModeFor

import com.slideindex.app.settings.slotTriggerMode

import com.slideindex.app.settings.gestureConfigSide

import com.slideindex.app.gesture.supportsAction

import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.MiuixTabSettingsCard
import com.slideindex.app.gesture.compoundTierTriggers

import com.slideindex.app.ui.miuix.groupedCardItems

import com.slideindex.app.ui.settings.components.settingsCardScopeItem

import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

import kotlin.math.roundToInt

import top.yukonga.miuix.kmp.basic.BasicComponentDefaults



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun SideGestureSlotConfigScreen(

    side: PanelSide,

    handleId: String,

    trigger: GestureTriggerType,

    settings: AppSettings,

    onBack: () -> Unit,

    /**
     * 非 null = 正在编辑「按应用分档」里的某个档（issue #26 v2）。
     *
     * 本页里的**应用轮播切换参数**与**指环槽位配置**都不在档快照里（全局设置），
     * 档内一律隐藏 / 置灰并注明「全局设置，档内不适用」，绝不能在档里改到全局。
     */
    profileId: String? = null,

    onOpenActionPick: (GestureTriggerType) -> Unit,

    onOpenModePick: (GestureTriggerType) -> Unit,

    onOpenShellCommand: (GestureTriggerType, String) -> Unit,

    onOpenQuickLauncherPanel: (GestureTriggerType, String) -> Unit = { _, _ -> },

    onOpenFingertipRingConfig: (GestureTriggerType) -> Unit = {},

    onTriggerDoubleTapIntervalChange: (Int) -> Unit = {},

    appCarouselSettings: AppCarouselSwitcherSettings = AppCarouselSwitcherSettings(),

    onAppCarouselSettingsChange: (AppCarouselSwitcherSettings) -> Unit = {},

) {

    val configSide = settings.gestureConfigSide(side, handleId)

    // 组合手势一条路径含短滑/长滑两档，行内可切档（折返等单档路径退化为无切换）。
    val tierTriggers = remember(trigger) { trigger.compoundTierTriggers() }
    var selectedTrigger by rememberSaveable(trigger) { mutableStateOf(trigger) }
    val shortTierLabel = stringResource(R.string.side_gestures_direction_short_slot)
    val longTierLabel = stringResource(R.string.side_gestures_direction_long_slot)
    // ⚠️ DSL 里的 `settingsLazyHint` 不是 @Composable，文案要在组合期先取好。
    val profileGlobalOnlyHint = stringResource(R.string.gesture_app_profiles_global_only_hint)

    val selectedAction = settings.slotAction(configSide, selectedTrigger, handleId)

    val storedMode = settings.slotTriggerMode(configSide, selectedTrigger, handleId)
    val sideDefaultMode = settings.defaultTriggerModeFor(configSide)
    // 「默认」已不对用户暴露：未设置过时按松手触发；只能持续触发的动作固定为持续触发
    // （仅限能按住拖动的槽位；单击/双击/点按没有持续阶段，按松手兜底）。
    val selectedMode = when {
        storedMode != GestureTriggerMode.DEFAULT -> storedMode
        selectedAction.requiresContinuousTriggerOnly() &&
            trigger !in GestureTriggerType.pressTapEntries() -> GestureTriggerMode.CONTINUOUS
        else -> GestureTriggerMode.ON_RELEASE
    }

    var localAppCarouselSettings by remember(appCarouselSettings) {
        mutableStateOf(appCarouselSettings)
    }
    LaunchedEffect(appCarouselSettings) {
        localAppCarouselSettings = appCarouselSettings
    }

    val screenTitle = triggerSlotLabel(side, selectedTrigger)



    val actionSectionTitle = stringResource(R.string.slot_action_type)

    val quickLauncherSectionTitle = stringResource(R.string.quick_launcher_panel_pick_title)

    val shellCommandSectionTitle = stringResource(R.string.gesture_shell_command_config_title)

    val fingertipRingSectionTitle = stringResource(R.string.fingertip_ring_config_section)

    val appCarouselSectionTitle = stringResource(R.string.app_carousel_settings_title)

    val triggerModeSectionTitle = stringResource(R.string.slot_trigger_mode)

    val doubleTapIntervalSectionTitle = stringResource(R.string.trigger_double_tap_interval)

    val resources = LocalContext.current.resources



    SettingsScreenScaffold(

        title = screenTitle,

        pageHint = stringResource(R.string.side_gestures_desc),

        onBack = onBack,

    ) {

        if (tierTriggers.size > 1) {
            item(key = "slot-tier-tabs") {
                MiuixTabSettingsCard(
                    tabs = tierTriggers.map { tier ->
                        if (tier.isLongDistance) longTierLabel else shortTierLabel
                    },
                    selectedTabIndex = tierTriggers.indexOf(selectedTrigger).coerceAtLeast(0),
                    onTabSelected = { index -> selectedTrigger = tierTriggers[index] },
                    contentTopPadding = 0.dp,
                ) {}
            }
        }

        settingsLazySmallTitle(

            key = "slot-action-section",

            title = actionSectionTitle,

        )

        groupedCardItems(

            keyPrefix = "side-gesture-action",

            items = buildList {

                add(

                    settingsCardScopeItem("slot-action") {

                        SettingNavigationRow(

                            icon = { _ ->
                                com.slideindex.app.ui.ActionIconSlot(
                                    action = selectedAction,
                                    sizeDp = 24,
                                )
                            },

                            title = gestureActionLabel(selectedAction, settings),

                            subtitle = stringResource(R.string.slot_pick_action),

                            onClick = { onOpenActionPick(selectedTrigger) },

                        )

                    },

                )

            },

        )



        if (selectedAction is GestureAction.QuickLauncher) {

            settingsLazySmallTitle(

                key = "slot-quick-launcher-section",

                title = quickLauncherSectionTitle,

            )

            groupedCardItems(

                keyPrefix = "side-gesture-quick-launcher",

                items = buildList {

                    add(

                        settingsCardScopeItem("slot-quick-launcher") {

                            SettingNavigationRow(

                                icon = { label ->

                                    Icon(

                                        imageVector = gestureActionImageVector(selectedAction, outlined = true),

                                        contentDescription = label,

                                        modifier = Modifier.size(24.dp),

                                        tint = MaterialTheme.colorScheme.onSurface,

                                    )

                                },

                                title = quickLauncherPanelLabel(settings, selectedAction.panelId),

                                subtitle = stringResource(R.string.quick_launcher_panel_pick_title),

                                onClick = {
                                    onOpenQuickLauncherPanel(selectedTrigger, selectedAction.panelId)
                                },

                            )

                        },

                    )

                },

            )

        }



        if (selectedAction == GestureAction.AppCarouselSwitcher) {

            settingsLazySmallTitle(

                key = "slot-app-carousel-section",

                title = appCarouselSectionTitle,

            )

            if (profileId != null) {

                settingsLazyHint(
                    key = "slot-app-carousel-global-only",
                    text = profileGlobalOnlyHint,
                )

            } else {

                groupedCardItems(

                    keyPrefix = "side-gesture-app-carousel",

                    items = appCarouselSwitcherSettingsCardItems(

                        keyPrefix = "side-gesture-app-carousel",

                        local = localAppCarouselSettings,

                        onUpdate = { next ->

                            localAppCarouselSettings = next

                            onAppCarouselSettingsChange(next)

                        },

                    ),

                )

            }

        }



        if (selectedAction is GestureAction.FingertipRing) {

            settingsLazySmallTitle(

                key = "slot-fingertip-ring-section",

                title = fingertipRingSectionTitle,

            )

            groupedCardItems(

                keyPrefix = "side-gesture-fingertip-ring",

                items = buildList {

                    add(

                        settingsCardScopeItem("slot-fingertip-ring") {

                            SettingNavigationRow(

                                icon = { label ->

                                    Icon(

                                        imageVector = gestureActionImageVector(selectedAction, outlined = true),

                                        contentDescription = label,

                                        modifier = Modifier.size(24.dp),

                                        tint = MaterialTheme.colorScheme.onSurface,

                                    )

                                },

                                title = stringResource(R.string.fingertip_ring_config_title),

                                subtitle = if (profileId != null) {
                                    stringResource(R.string.gesture_app_profiles_global_only_hint)
                                } else {
                                    stringResource(R.string.fingertip_ring_config_desc)
                                },

                                enabled = profileId == null,

                                onClick = { onOpenFingertipRingConfig(selectedTrigger) },

                            )

                        },

                    )

                },

            )

        }



        if (selectedAction is GestureAction.ExecuteShellCommand) {

            settingsLazySmallTitle(

                key = "slot-shell-command-section",

                title = shellCommandSectionTitle,

            )

            groupedCardItems(

                keyPrefix = "side-gesture-shell-command",

                items = buildList {

                    add(

                        settingsCardScopeItem("slot-shell-command") {

                            SettingNavigationRow(

                                icon = { label ->

                                    Icon(

                                        imageVector = gestureActionImageVector(selectedAction, outlined = true),

                                        contentDescription = label,

                                        modifier = Modifier.size(24.dp),

                                        tint = MaterialTheme.colorScheme.onSurface,

                                    )

                                },

                                title = gestureExecuteShellCommandPreview(selectedAction.command),

                                subtitle = stringResource(R.string.gesture_shell_command_config_title),

                                onClick = {
                                    onOpenShellCommand(selectedTrigger, selectedAction.command)
                                },

                            )

                        },

                    )

                },

            )

        }



        if (selectedTrigger == GestureTriggerType.SHORT_DOUBLE_TAP) {

            settingsLazySmallTitle(

                key = "slot-double-tap-interval-section",

                title = doubleTapIntervalSectionTitle,

            )

            groupedCardItems(

                keyPrefix = "side-gesture-double-tap-interval",

                items = buildList {

                    add(

                        settingsCardScopeItem("slot-double-tap-interval") {

                            MiuixSliderRow(

                                title = doubleTapIntervalSectionTitle,

                                summary = stringResource(R.string.trigger_double_tap_interval_desc),

                                value = settings.triggerDoubleTapIntervalMs.toFloat(),

                                valueRange = 0f..800f,

                                insideMargin = BasicComponentDefaults.InsideMargin,

                                steps = 16,

                                commitOnFinish = true,

                                formatLabel = { ms ->

                                    resources.getString(

                                        R.string.trigger_double_tap_interval_value,

                                        ms.roundToInt(),

                                    )

                                },

                                onValueChange = { onTriggerDoubleTapIntervalChange(it.roundToInt()) },

                            )

                        },

                    )

                },

            )

        }



        // 单击/双击固定松手触发（见 GestureTriggerType.isTapFixedOnRelease），不提供触发模式选择。
        if (!selectedTrigger.isTapFixedOnRelease) {

            settingsLazySmallTitle(

                key = "slot-trigger-mode-section",

                title = triggerModeSectionTitle,

            )

            groupedCardItems(

                keyPrefix = "side-gesture-trigger-mode",

                items = buildList {

                    add(

                        settingsCardScopeItem("slot-trigger-mode") {

                            MiuixNavigationRow(

                                title = slotTriggerModeTitle(selectedMode, sideDefaultMode),

                                summary = slotTriggerModeSubtitle(selectedMode, sideDefaultMode),

                                onClick = { onOpenModePick(selectedTrigger) },

                            )

                        },

                    )

                },

            )

        }

    }

}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun SideGestureTriggerModePickerScreen(

    title: String,

    current: GestureTriggerMode,

    action: GestureAction,

    trigger: GestureTriggerType,

    sideDefaultMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,

    includeDefaultOption: Boolean,

    onBack: () -> Unit,

    onSelect: (GestureTriggerMode) -> Unit,

) {

    // 单击/双击/点按槽位没有按住拖动的持续阶段，不提供「持续触发」模式。
    val continuousUsable = trigger !in GestureTriggerType.pressTapEntries()

    val modes = if (includeDefaultOption) {

        GestureTriggerMode.configurableEntries

    } else {

        GestureTriggerMode.configurableEntries.filter { it != GestureTriggerMode.DEFAULT }

    }.filter { it != GestureTriggerMode.CONTINUOUS || continuousUsable }

    val modeItems = buildList {

        modes.forEach { mode ->

            add(
                settingsCardScopeItem("mode-${mode.name}") {
                    val enabled = mode == GestureTriggerMode.DEFAULT || mode.supportsAction(action, trigger)

                    val modeTitle = when (mode) {

                        GestureTriggerMode.DEFAULT -> stringResource(R.string.trigger_mode_default)

                        else -> triggerModeLabel(mode, includeDefault = false)

                    }

                    val subtitle = when (mode) {

                        GestureTriggerMode.DEFAULT -> stringResource(

                            R.string.trigger_mode_default_slot_desc,

                            triggerModeLabel(sideDefaultMode, includeDefault = false),

                        )

                        else -> triggerModeDescription(mode)

                    }

                    SettingRadioRow(

                        title = modeTitle,

                        subtitle = subtitle,

                        selected = current == mode,

                        enabled = enabled,

                        onClick = { if (enabled) onSelect(mode) },

                    )
                },
            )

        }

    }

    SettingsRadioPickerScreen(

        title = title,

        onBack = onBack,

        items = modeItems,

    )

}



@Composable

fun slotTriggerModeTitle(mode: GestureTriggerMode, sideDefaultMode: GestureTriggerMode): String =

    when (mode) {

        GestureTriggerMode.DEFAULT -> stringResource(R.string.trigger_mode_default)

        else -> triggerModeLabel(mode, includeDefault = false)

    }



@Composable

fun slotTriggerModeSubtitle(

    mode: GestureTriggerMode,

    sideDefaultMode: GestureTriggerMode,

): String = when (mode) {

    GestureTriggerMode.DEFAULT -> stringResource(

        R.string.trigger_mode_default_slot_desc,

        triggerModeLabel(sideDefaultMode, includeDefault = false),

    )

    else -> triggerModeDescription(mode)

}



@Composable

fun triggerModeLabel(mode: GestureTriggerMode, includeDefault: Boolean = true): String =

    when (mode) {

        GestureTriggerMode.DEFAULT ->

            if (includeDefault) stringResource(R.string.trigger_mode_default) else stringResource(R.string.trigger_mode_on_release)

        GestureTriggerMode.ON_RELEASE -> stringResource(R.string.trigger_mode_on_release)

        GestureTriggerMode.CONTINUOUS -> stringResource(R.string.trigger_mode_continuous)

        GestureTriggerMode.IMMEDIATE -> stringResource(R.string.trigger_mode_immediate)

    }



@Composable

fun triggerModeDescription(mode: GestureTriggerMode): String = when (mode) {

    GestureTriggerMode.DEFAULT -> stringResource(R.string.default_trigger_mode_desc)

    GestureTriggerMode.ON_RELEASE -> stringResource(R.string.trigger_mode_on_release_desc)

    GestureTriggerMode.CONTINUOUS -> stringResource(

        R.string.trigger_mode_continuous_desc,

        continuousTrackingActionsSummary(),

    )

    GestureTriggerMode.IMMEDIATE -> stringResource(R.string.trigger_mode_immediate_desc)

}



@Composable

private fun continuousTrackingActionsSummary(): String {

    val labels = mutableListOf<String>()

    for (action in GestureAction.continuousTrackingActions) {

        labels.add(gestureActionLabel(action))

    }

    return labels.joinToString("、")

}



@Composable

fun triggerLabel(side: PanelSide, trigger: GestureTriggerType): String = stringResource(

    when (trigger) {

        GestureTriggerType.SHORT_SWIPE_IN, GestureTriggerType.LONG_SWIPE_IN -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_top

        }

        GestureTriggerType.SHORT_SWIPE_UP_RIGHT, GestureTriggerType.LONG_SWIPE_UP_RIGHT -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_up_right_on_left

            PanelSide.RIGHT -> R.string.gesture_swipe_up_left_on_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_up_left_on_bottom

            PanelSide.TOP -> R.string.gesture_swipe_up_left_on_top

        }

        GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_down_right_on_left

            PanelSide.RIGHT -> R.string.gesture_swipe_down_left_on_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_down_right_on_bottom

            PanelSide.TOP -> R.string.gesture_swipe_down_right_on_top

        }

        GestureTriggerType.SHORT_SWIPE_IN_UP, GestureTriggerType.LONG_SWIPE_IN_UP -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_up_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_up_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_up_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_up_top

        }

        GestureTriggerType.SHORT_SWIPE_IN_DOWN, GestureTriggerType.LONG_SWIPE_IN_DOWN -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_down_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_down_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_down_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_down_top

        }

        GestureTriggerType.SHORT_SWIPE_IN_AND_BACK -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_and_back_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_and_back_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_and_back_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_and_back_top

        }

        GestureTriggerType.SHORT_SWIPE_UP_IN, GestureTriggerType.LONG_SWIPE_UP_IN -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_up_in_left
            PanelSide.RIGHT -> R.string.gesture_swipe_up_in_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_up_in_bottom
            PanelSide.TOP -> R.string.gesture_swipe_up_in_top
        }

        GestureTriggerType.SHORT_SWIPE_DOWN_IN, GestureTriggerType.LONG_SWIPE_DOWN_IN -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_down_in_left
            PanelSide.RIGHT -> R.string.gesture_swipe_down_in_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_down_in_bottom
            PanelSide.TOP -> R.string.gesture_swipe_down_in_top
        }

        GestureTriggerType.SHORT_SWIPE_UP_AND_BACK -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_up_and_back_left
            PanelSide.RIGHT -> R.string.gesture_swipe_up_and_back_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_up_and_back_bottom
            PanelSide.TOP -> R.string.gesture_swipe_up_and_back_top
        }

        GestureTriggerType.SHORT_SWIPE_DOWN_AND_BACK -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_down_and_back_left
            PanelSide.RIGHT -> R.string.gesture_swipe_down_and_back_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_down_and_back_bottom
            PanelSide.TOP -> R.string.gesture_swipe_down_and_back_top
        }

        GestureTriggerType.SHORT_SWIPE_UP -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_left

            else -> R.string.gesture_short_swipe_up

        }

        GestureTriggerType.SHORT_SWIPE_DOWN -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_right

            else -> R.string.gesture_short_swipe_down

        }

        GestureTriggerType.SHORT_LONG_PRESS -> R.string.gesture_short_long_press

        GestureTriggerType.SHORT_SINGLE_TAP -> R.string.gesture_short_single_tap

        GestureTriggerType.SHORT_DOUBLE_TAP -> R.string.gesture_short_double_tap

        GestureTriggerType.LONG_SWIPE_UP -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_long_swipe_left

            else -> R.string.gesture_long_swipe_up

        }

        GestureTriggerType.LONG_SWIPE_DOWN -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_long_swipe_right

            else -> R.string.gesture_long_swipe_down

        }

        GestureTriggerType.LONG_LONG_PRESS -> R.string.gesture_long_long_press

        GestureTriggerType.LONG_SINGLE_TAP -> R.string.gesture_long_single_tap

        GestureTriggerType.SHORT_SWIPE_IN_HOVER -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_in_hover_left
            PanelSide.RIGHT -> R.string.gesture_swipe_in_hover_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_in_hover_bottom
            PanelSide.TOP -> R.string.gesture_swipe_in_hover_top
        }

        GestureTriggerType.SHORT_SWIPE_UP_RIGHT_HOVER -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_up_right_hover_on_left
            PanelSide.RIGHT -> R.string.gesture_swipe_up_left_hover_on_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_up_left_hover_on_bottom
            PanelSide.TOP -> R.string.gesture_swipe_up_left_hover_on_top
        }

        GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT_HOVER -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_down_right_hover_on_left
            PanelSide.RIGHT -> R.string.gesture_swipe_down_left_hover_on_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_down_right_hover_on_bottom
            PanelSide.TOP -> R.string.gesture_swipe_down_right_hover_on_top
        }

        GestureTriggerType.SHORT_SWIPE_UP_HOVER -> when (side) {
            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_left_hover
            else -> R.string.gesture_short_swipe_up_hover
        }

        GestureTriggerType.SHORT_SWIPE_DOWN_HOVER -> when (side) {
            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_right_hover
            else -> R.string.gesture_short_swipe_down_hover
        }

    },

)



@Composable

fun triggerSlotLabel(side: PanelSide, trigger: GestureTriggerType): String {
    if (trigger.isPressOrTap || trigger.isHoverSwipe) {
        return triggerLabel(side, trigger)
    }
    val path = triggerLabel(side, trigger)
    if (trigger.isCompoundSwipe) {
        val distanceLabel = stringResource(
            if (trigger.isLongDistance) {
                R.string.side_gestures_direction_long_slot
            } else {
                R.string.side_gestures_direction_short_slot
            },
        )
        return stringResource(R.string.side_gestures_compound_slot_title, path, distanceLabel)
    }
    return path
}


