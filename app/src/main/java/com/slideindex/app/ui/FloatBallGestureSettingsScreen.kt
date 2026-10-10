package com.slideindex.app.ui



import androidx.compose.foundation.layout.size

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.floatball.FloatBallGestureGroup

import com.slideindex.app.floatball.FloatBallGestureType

import com.slideindex.app.gesture.GestureAction

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.ui.miuix.groupedCardItems

import com.slideindex.app.ui.settings.components.SettingNavigationRow

import com.slideindex.app.ui.settings.components.SettingsCardScope

import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

import com.slideindex.app.ui.settings.components.settingsCardScopeItem

import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

import kotlin.math.roundToInt



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun FloatBallGestureSettingsScreen(

    settings: AppSettings,

    accessibilityGranted: Boolean,

    onBack: () -> Unit,

    onOpenActionPick: (FloatBallGestureType) -> Unit,

    onOpenShellCommand: (FloatBallGestureType, String) -> Unit,

    /**
     * 该手势绑了「快速启动器」时，点右侧齿轮去选要打开的面板（页面）。
     * 与侧滑触钮的同名入口一致：快速启动器可以有多页，不指定就只能打开默认页。
     */
    onOpenQuickLauncherPanel: (FloatBallGestureType, String) -> Unit,

    onDownSwipeShortPercentChange: (Float) -> Unit,

    onSideSwipeShortPercentChange: (Float) -> Unit,

    onUpSwipeShortPercentChange: (Float) -> Unit,

    onLongPressMsChange: (Int) -> Unit,

    onOverlayAnchorAtTouchDownChange: (Boolean) -> Unit

) {

    val distanceSectionTitle = stringResource(R.string.float_ball_gesture_distance_section)
    val longPressSectionTitle = stringResource(R.string.float_ball_gesture_long_press_section)
    val overlayAnchorSectionTitle = stringResource(R.string.float_ball_gesture_overlay_anchor_section)
    // 分组小标题必须在 Lazy 作用域外解析（settingsLazySmallTitle 的 title 是普通 String）。
    val gestureGroupTitles = FloatBallGestureGroup.displayOrder.map { group ->
        group to floatBallGestureGroupTitle(group)
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_gesture_settings_title),
        pageHint = stringResource(R.string.float_ball_gesture_settings_summary),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "section-distance",
            title = distanceSectionTitle
        )
        groupedCardItems(
            keyPrefix = "float-ball-gesture-distance",
            items = buildList {
                add(
                    settingsCardScopeItem("down-swipe-distance") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_gesture_down_swipe_distance),
                            value = settings.floatBallDownSwipeShortPercent,
                            valueRange = 50f..500f,
                            steps = 18,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                settings.floatBallDownSwipeShortPercent.roundToInt()
                            ),
                            onValueChange = onDownSwipeShortPercentChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("side-swipe-distance") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_gesture_side_swipe_distance),
                            value = settings.floatBallSideSwipeShortPercent,
                            valueRange = 50f..500f,
                            steps = 18,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                settings.floatBallSideSwipeShortPercent.roundToInt()
                            ),
                            onValueChange = onSideSwipeShortPercentChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("up-swipe-distance") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_gesture_up_swipe_distance),
                            value = settings.floatBallUpSwipeShortPercent,
                            valueRange = 50f..500f,
                            steps = 18,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                settings.floatBallUpSwipeShortPercent.roundToInt()
                            ),
                            onValueChange = onUpSwipeShortPercentChange
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "section-long-press",
            title = longPressSectionTitle
        )
        groupedCardItems(
            keyPrefix = "float-ball-gesture-long-press",
            items = listOf(
                settingsCardScopeItem("long-press-ms") {
                    SettingsSliderRow(
                        title = stringResource(R.string.float_ball_gesture_long_press_ms),
                        value = settings.floatBallLongPressMs.toFloat(),
                        valueRange = 200f..2000f,
                        steps = 17,
                        enabled = true,
                        label = stringResource(
                            R.string.float_ball_gesture_long_press_ms_value,
                            settings.floatBallLongPressMs
                        ),
                        onValueChange = { onLongPressMsChange(it.roundToInt()) }
                    )
                }
            )
        )

        settingsLazySmallTitle(

            key = "section-overlay-anchor",

            title = overlayAnchorSectionTitle

        )

        groupedCardItems(

            keyPrefix = "float-ball-gesture-overlay-anchor",

            items = listOf(

                settingsCardScopeItem("overlay-anchor-touch-down") {

                    SettingSwitchRow(

                        title = stringResource(R.string.float_ball_gesture_overlay_anchor_touch_down),

                        subtitle = stringResource(R.string.float_ball_gesture_overlay_anchor_touch_down_summary),

                        checked = settings.floatBallOverlayAnchorAtTouchDown,

                        enabled = true,

                        onCheckedChange = onOverlayAnchorAtTouchDownChange

                    )

                }

            )

        )

        gestureGroupTitles.forEach { (group, groupTitle) ->
            settingsLazySmallTitle(
                key = "section-actions-${group.name.lowercase()}",
                title = groupTitle
            )
            groupedCardItems(
                keyPrefix = "float-ball-gesture-actions-${group.name.lowercase()}",
                items = buildList {
                    group.types.forEach { type ->
                        val action = settings.floatBallGestureActions[type] ?: GestureAction.None
                        add(
                            settingsCardScopeItem("action-${type.name}") {
                                FloatBallGestureActionRow(
                                    type = type,
                                    settings = settings,
                                    title = floatBallGestureLabel(type),
                                    action = action,
                                    enabled = true,
                                    showSettings = action is GestureAction.LaunchApp ||
                                        action is GestureAction.LaunchShortcut ||
                                        action is GestureAction.SimulatePointerSwipe ||
                                        action is GestureAction.QuickLauncher ||
                                        action is GestureAction.ExecuteShellCommand,
                                    onClick = { onOpenActionPick(type) },
                                    onSettingsClick = when (action) {
                                        is GestureAction.ExecuteShellCommand -> {
                                            { onOpenShellCommand(type, action.command) }
                                        }
                                        is GestureAction.QuickLauncher -> {
                                            { onOpenQuickLauncherPanel(type, action.panelId) }
                                        }
                                        else -> null
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }

    }

}

/** 手势分组小标题（穷尽 when，新增分组会编译报错提醒补文案）。 */
@Composable

internal fun floatBallGestureGroupTitle(group: FloatBallGestureGroup): String = when (group) {

    FloatBallGestureGroup.DOWN_SWIPE -> stringResource(R.string.float_ball_gesture_group_down_swipe)

    FloatBallGestureGroup.UP_SWIPE -> stringResource(R.string.float_ball_gesture_group_up_swipe)

    FloatBallGestureGroup.SIDE_SWIPE -> stringResource(R.string.float_ball_gesture_group_side_swipe)

    FloatBallGestureGroup.TAP -> stringResource(R.string.float_ball_gesture_group_tap)

}



@Composable

private fun SettingsCardScope.FloatBallGestureActionRow(

    type: FloatBallGestureType,

    settings: AppSettings,

    title: String,

    action: GestureAction,

    enabled: Boolean,

    showSettings: Boolean,

    onClick: () -> Unit,

    onSettingsClick: (() -> Unit)? = null

) {

    SettingNavigationRow(

        icon = { label ->

            FloatBallGestureIcon(

                type = type,

                settings = settings,

                contentDescription = label,

                modifier = Modifier.size(22.dp)

            )

        },

        title = title,

        subtitle = gestureActionSettingSubtitle(action),

        enabled = enabled,

        onClick = onClick,

        trailingContent = {

            GestureActionSettingTrailing(

                action = action,

                enabled = enabled,

                showSettings = showSettings,

                onSettingsClick = onSettingsClick,

                onClick = onClick

            )

        }

    )

}



@Composable

fun floatBallGestureLabel(type: FloatBallGestureType): String = when (type) {

    FloatBallGestureType.SWIPE_UP_SHORT -> stringResource(R.string.float_ball_gesture_swipe_up_short)

    FloatBallGestureType.SWIPE_UP_LONG -> stringResource(R.string.float_ball_gesture_swipe_up_long)

    FloatBallGestureType.SWIPE_UP_RETURN -> stringResource(R.string.float_ball_gesture_swipe_up_return)

    FloatBallGestureType.SWIPE_UP_IN -> stringResource(R.string.float_ball_gesture_swipe_up_in)

    FloatBallGestureType.SWIPE_DOWN_SHORT -> stringResource(R.string.float_ball_gesture_swipe_down_short)

    FloatBallGestureType.SWIPE_DOWN_LONG -> stringResource(R.string.float_ball_gesture_swipe_down_long)

    FloatBallGestureType.SWIPE_DOWN_RETURN -> stringResource(R.string.float_ball_gesture_swipe_down_return)

    FloatBallGestureType.SWIPE_DOWN_IN -> stringResource(R.string.float_ball_gesture_swipe_down_in)

    FloatBallGestureType.SWIPE_SIDE_SHORT -> stringResource(R.string.float_ball_gesture_swipe_side_short)

    FloatBallGestureType.SWIPE_SIDE_LONG -> stringResource(R.string.float_ball_gesture_swipe_side_long)

    FloatBallGestureType.SWIPE_SIDE_RETURN -> stringResource(R.string.float_ball_gesture_swipe_side_return)

    FloatBallGestureType.SWIPE_IN_DOWN -> stringResource(R.string.float_ball_gesture_swipe_in_down)

    FloatBallGestureType.SWIPE_IN_UP -> stringResource(R.string.float_ball_gesture_swipe_in_up)

    FloatBallGestureType.SINGLE_TAP -> stringResource(R.string.float_ball_gesture_single_tap)

    FloatBallGestureType.DOUBLE_TAP -> stringResource(R.string.float_ball_gesture_double_tap)

    FloatBallGestureType.LONG_PRESS -> stringResource(R.string.float_ball_gesture_long_press)

}


