package com.slideindex.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.isShellActivityShortcut
import com.slideindex.app.launcher.showsShellActivityShortcutBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.ShellCommandBadgeOverlay
import com.slideindex.app.overlay.ShortcutBadgeOverlay
import com.slideindex.app.overlay.corner.CornerSlotIconBitmap
import com.slideindex.app.shell.ShellCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 动作行尾/列表项的目标图标。
 *
 * 与 [gestureActionIcon] 的区别：绑定应用时画真实应用图标（分身带黄点），绑定快捷方式时
 * 画宿主应用图标 + 快捷方式/命令角标，绑定 shell 命令时优先画命令自己的图标。
 *
 * 设置页的晃动、悬浮球、翻转静音和晃动菜单浮层共用这一套，避免同一个动作在不同页面
 * 显示成不同样子。
 */
@Composable
fun GestureActionTargetIcon(
    action: GestureAction,
    activityShortcuts: List<ActivityShortcut> = emptyList(),
    shellCommands: List<ShellCommand> = emptyList(),
    appRepository: AppRepository? = null,
    size: Dp = 18.dp,
    contentDescription: String? = null,
) {
    val needsBitmap = action is GestureAction.LaunchApp ||
        action is GestureAction.LaunchShortcut ||
        action is GestureAction.ExecuteShellCommand
    if (!needsBitmap) {
        Icon(
            imageVector = gestureActionIcon(action, outlined = true),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
        )
        return
    }

    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(12)
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        action,
        activityShortcuts,
        shellCommands,
        appRepository,
        sizePx,
    ) {
        value = withContext(Dispatchers.IO) {
            CornerSlotIconBitmap.get(
                context = context,
                action = action,
                sizePx = sizePx,
                tintArgb = android.graphics.Color.WHITE,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
                appRepository = appRepository,
            )
        }
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                imageVector = gestureActionIcon(action, outlined = true),
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
            )
        }
        when {
            action is GestureAction.LaunchShortcut -> {
                when {
                    action.showsShellActivityShortcutBadge(activityShortcuts) ->
                        ShellCommandBadgeOverlay(iconSize = size)
                    !action.isShellActivityShortcut(activityShortcuts) ->
                        ShortcutBadgeOverlay(iconSize = size)
                }
            }
            action is GestureAction.ExecuteShellCommand &&
                action.showsShellCommandBadge(shellCommands) ->
                ShellCommandBadgeOverlay(iconSize = size)
        }
    }
}
