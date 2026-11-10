package com.slideindex.app.ui



import androidx.compose.foundation.layout.size

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Edit

import androidx.compose.material.icons.outlined.Tune

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

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

    /** 进入「识别与定位」子页（滑动距离 / 长按触发时长 / 启动器位置） */
    onOpenTuning: () -> Unit,

    /** 进入「自定义手势」列表（FV 里这一行叫 `v_setting_custom_gesture`） */
    onOpenCustomGesture: () -> Unit

) {

    val tuningSectionTitle = stringResource(R.string.float_ball_gesture_tuning_section)
    val tuningEntryTitle = stringResource(R.string.float_ball_gesture_tuning_title)
    // FV「自定义手势」入口的文案同样必须在 Lazy 作用域外解析（见下面对 settingsLazySmallTitle 的调用）。
    val customGestureSectionTitle = stringResource(R.string.fv_custom_gesture_section)
    val customGestureEntryTitle = stringResource(R.string.fv_custom_gesture_title)
    val customGestureEntrySummary = stringResource(R.string.fv_custom_gesture_summary)
    // 分组小标题必须在 Lazy 作用域外解析（settingsLazySmallTitle 的 title 是普通 String）。
    val gestureGroupTitles = FloatBallGestureGroup.displayOrder.map { group ->
        group to floatBallGestureGroupTitle(group)
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_gesture_settings_title),
        pageHint = stringResource(R.string.float_ball_gesture_settings_summary),
        onBack = onBack
    ) {

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

        // 与 FV 一致：自定义手势入口（v_setting_custom_gesture）紧跟在 8 个方向手势之后。
        // 手势本身在列表页里增删改（FV 的 FooSettingCustomGuesture），这里只做一个导航行。
        settingsLazySmallTitle(
            key = "section-custom-gesture",
            title = customGestureSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "float-ball-custom-gesture",
            items = listOf(
                settingsCardScopeItem("custom-gesture-entry") {
                    SettingNavigationRow(
                        icon = { label ->
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        title = customGestureEntryTitle,
                        subtitle = customGestureEntrySummary,
                        enabled = true,
                        onClick = onOpenCustomGesture,
                    )
                }
            )
        )

        // 滑动距离 / 长按触发时长 / 启动器位置属于低频参数，收进「识别与定位」子页；
        // 入口放在页面最底下，这样 8 个方向手势能直接顶到页面最上面。副标题带当前值摘要，
        // 不进入子页也能确认参数现状（排查“滑不动”时不用盲找）。
        settingsLazySmallTitle(
            key = "section-tuning",
            title = tuningSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "float-ball-gesture-tuning",
            items = listOf(
                settingsCardScopeItem("tuning-entry") {
                    SettingNavigationRow(
                        icon = { label ->
                            Icon(
                                Icons.Outlined.Tune,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        title = tuningEntryTitle,
                        subtitle = floatBallGestureTuningSummary(settings),
                        enabled = true,
                        onClick = onOpenTuning,
                    )
                }
            )
        )

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

                activityShortcuts = settings.activityShortcuts,

                shellCommands = settings.shellCommands,

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


