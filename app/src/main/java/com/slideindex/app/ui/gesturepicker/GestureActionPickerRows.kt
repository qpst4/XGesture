package com.slideindex.app.ui.gesturepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerAppShortcutLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.shortcutKindChip
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.pickerSegmentCount
import com.slideindex.app.ui.pickerSegmentIndex
import com.slideindex.app.util.ShortcutKind
import androidx.compose.material3.MaterialTheme

internal enum class ActionPickerTab {
    ACTIONS,
    APPS,
    SHORTCUTS,
}

@Composable
internal fun ActionPickerOpenLinkRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    subtitle: String?,
    onOpenConfig: () -> Unit,
    highlightPulse: Float = 0f,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        highlightPulse = highlightPulse,
        onClick = onOpenConfig,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            com.slideindex.app.ui.ActionIconSlot(action = action, sizeDp = 24)
        }
        },
        trailingMode = PickerTrailingMode.None,
    )
}

/**
 * 「快捷轮盘」行：点它是打开配置（先选轮盘 + 形态），但"当前就是这个动作"时
 * 仍要与普通动作行一样显示为已选中（标题主色 + 右侧打勾）。
 */
@Composable
internal fun ActionPickerQuickWheelRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    subtitle: String?,
    selected: Boolean,
    onOpenConfig: () -> Unit,
    highlightPulse: Float = 0f,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = selected,
        highlightPulse = if (selected) highlightPulse else 0f,
        onClick = onOpenConfig,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            com.slideindex.app.ui.ActionIconSlot(action = action, sizeDp = 24)
        }
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}

@Composable
internal fun ActionPickerExecuteShellCommandRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    subtitle: String?,
    onOpenConfig: () -> Unit,
    highlightPulse: Float = 0f,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        highlightPulse = highlightPulse,
        onClick = onOpenConfig,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            com.slideindex.app.ui.ActionIconSlot(action = action, sizeDp = 24)
        }
        },
        trailingMode = PickerTrailingMode.None,
    )
}

@Composable
internal fun ActionPickerSimulateKeyEventRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    subtitle: String?,
    onOpenConfig: () -> Unit,
    highlightPulse: Float = 0f,
) {
    val label = androidx.compose.ui.res.stringResource(com.slideindex.app.R.string.gesture_action_simulate_key_event)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        highlightPulse = highlightPulse,
        onClick = onOpenConfig,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            com.slideindex.app.ui.ActionIconSlot(action = action, sizeDp = 24)
        }
        },
        trailingMode = PickerTrailingMode.None,
    )
}

@Composable
internal fun ActionPickerActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    highlightPulse: Float = 0f,
) {
    val label = gestureActionLabel(action)
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = gestureActionDescription(action),
        selected = selected,
        highlightPulse = if (selected) highlightPulse else 0f,
        onClick = onClick,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            com.slideindex.app.ui.ActionIconSlot(action = action, sizeDp = 24)
        }
        },
        trailingMode = PickerTrailingMode.Radio,
    )
}


internal fun launchWindowModeTitleRes(mode: LaunchWindowMode): Int = when (mode) {
    LaunchWindowMode.FOLLOW_GLOBAL -> R.string.launch_window_mode_follow_global
    LaunchWindowMode.ALWAYS_FULLSCREEN -> R.string.launch_window_mode_fullscreen
    LaunchWindowMode.ALWAYS_FREE_WINDOW -> R.string.launch_window_mode_free_window
}

private val LaunchWindowMode.descriptionRes: Int?
    get() = when (this) {
        LaunchWindowMode.FOLLOW_GLOBAL -> R.string.launch_window_mode_follow_global_desc
        LaunchWindowMode.ALWAYS_FULLSCREEN -> null
        LaunchWindowMode.ALWAYS_FREE_WINDOW -> R.string.launch_window_mode_free_window_desc
    }

/**
 * 选择某个已绑定应用的启动形态（跟随全局 / 全屏 / 小窗）。
 *
 * 选中只改本地态，点「确定」才回调落库；取消即放弃本次选择。
 */
@Composable
internal fun GestureLaunchWindowModeDialog(
    app: AppInfo,
    initialMode: LaunchWindowMode,
    onDismiss: () -> Unit,
    onConfirm: (LaunchWindowMode) -> Unit,
) {
    var selected by remember(app.packageName, initialMode) { mutableStateOf(initialMode) }
    val options = remember { LaunchWindowMode.entries.toList() }

    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.launch_window_mode_title),
        onConfirm = { onConfirm(selected) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            options.forEachIndexed { index, mode ->
                Md3PickerListRow(
                    segmentIndex = pickerSegmentIndex(index, options.size),
                    segmentCount = pickerSegmentCount(options.size),
                    title = stringResource(launchWindowModeTitleRes(mode)),
                    subtitle = mode.descriptionRes?.let { stringResource(it) },
                    selected = selected == mode,
                    onClick = { selected = mode },
                    leadingContent = { Md3PickerAppLeading(app) },
                    trailingMode = PickerTrailingMode.Radio,
                )
            }
        }
    }
}

@Composable
internal fun ActionPickerShortcutRow(
    shortcut: TaskSwitcherMenuItem,
    packageName: String,
    segmentIndex: Int,
    segmentCount: Int,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    highlightPulse: Float = 0f,
) {
    val action = shortcutToLaunchShortcut(shortcut, packageName)
    val selected = current is GestureAction.LaunchShortcut && current.payloadKey == action.payloadKey
    val kindChip = shortcutKindChip(shortcut.kind)
    val subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() }
        ?: shortcut.shortcutId?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('(') }
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = subtitle,
        selected = selected,
        highlightPulse = if (selected) highlightPulse else 0f,
        onClick = { onSelect(action) },
        leadingContent = {
            Md3PickerAppShortcutLeading(
                packageName = packageName,
                selected = selected,
                contentDescription = shortcut.label,
            )
        },
        trailingMode = PickerTrailingMode.Icon,
        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        trailingIconDescription = shortcut.label,
        trailingIconAlignedToChoice = true,
        trailingChip = kindChip,
    )
}

internal fun shortcutToLaunchShortcut(
    shortcut: TaskSwitcherMenuItem,
    packageName: String,
): GestureAction.LaunchShortcut {
    val serviceId = shortcut.shortcutId?.takeIf { shortcut.isServiceShortcut && it.isNotBlank() }
    if (serviceId != null) {
        return GestureAction.LaunchShortcut.dynamic(packageName, serviceId, shortcut.label)
    }
    val uris = shortcut.intentUris
    if (!uris.isNullOrEmpty()) {
        return if (uris.size == 1) {
            GestureAction.LaunchShortcut.intent(uris[0], shortcut.label)
        } else {
            GestureAction.LaunchShortcut.intents(uris, shortcut.label)
        }
    }
    val component = shortcut.targetComponent?.takeIf { it.isNotBlank() }
    if (component != null) {
        return GestureAction.LaunchShortcut.component(component, shortcut.label)
    }
    val shortcutId = shortcut.shortcutId?.takeIf { it.isNotBlank() } ?: shortcut.label
    return GestureAction.LaunchShortcut.dynamic(packageName, shortcutId, shortcut.label)
}
