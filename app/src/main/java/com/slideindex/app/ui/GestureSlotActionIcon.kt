package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.CloneBadgeOverlay
import com.slideindex.app.overlay.MacroBadgeOverlay
import com.slideindex.app.overlay.ShellCommandBadgeOverlay
import com.slideindex.app.overlay.actionIconBadges
import com.slideindex.app.overlay.corner.CornerSlotIconBitmap
import com.slideindex.app.settings.AppSettings

@Composable
fun GestureSlotActionIcon(
    action: GestureAction,
    settings: AppSettings,
    contentDescription: String?,
) {
    // 全局图标覆盖优先（扩展 → 图标库）：动作槽位/悬浮球/FV/角手势的图标都走这里
    val context = androidx.compose.ui.platform.LocalContext.current
    val revision = GlobalIconStore.revision
    val overrideKey = remember(action, revision) {
        GlobalIconStore.actionIcon(context, action.type.name)
            ?: (action as? GestureAction.LaunchApp)?.let {
                GlobalIconStore.appIcon(context, it.packageName)
            }
    }
    val badges = action.actionIconBadges(
        context = context,
        activityShortcuts = settings.activityShortcuts,
        shellCommands = settings.shellCommands,
    )
    if (overrideKey != null) {
        Box(modifier = Modifier.size(24.dp)) {
            ActionIconView(
                action = action,
                iconKey = overrideKey,
                sizeDp = 24,
                showBackground = false,
            )
            if (badges.isClone) {
                CloneBadgeOverlay(iconSize = 24.dp, circular = false)
            }
        }
        return
    }
    when (action) {
        is GestureAction.LaunchApp -> {
            Box(modifier = Modifier.size(40.dp)) {
                Md3PickerPackageLeading(
                    packageName = action.packageName,
                    contentDescription = contentDescription,
                )
                if (badges.isClone) {
                    CloneBadgeOverlay(iconSize = 40.dp, circular = false)
                }
            }
        }
        is GestureAction.LaunchShortcut -> {
            Md3PickerLaunchShortcutLeading(
                action = action,
                activityShortcuts = settings.activityShortcuts,
            )
        }
        is GestureAction.ExecuteShellCommand -> {
            GestureSlotShellCommandIcon(
                action = action,
                settings = settings,
                contentDescription = contentDescription,
            )
        }
        is GestureAction.RunMacro -> {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = gestureActionIcon(action),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                )
                if (badges.showMacro) {
                    MacroBadgeOverlay(iconSize = 40.dp)
                }
                if (badges.isClone) {
                    CloneBadgeOverlay(iconSize = 40.dp, circular = false)
                }
            }
        }
        else -> {
            Icon(
                imageVector = gestureActionIcon(action),
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun GestureSlotShellCommandIcon(
    action: GestureAction.ExecuteShellCommand,
    settings: AppSettings,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSize = 28.dp
    val iconPx = with(density) { iconSize.roundToPx() }.coerceAtLeast(12)
    val bitmap by produceState<android.graphics.Bitmap?>(null, action, settings.shellCommands) {
        value = CornerSlotIconBitmap.get(
            context = context,
            action = action,
            sizePx = iconPx,
            tintArgb = android.graphics.Color.WHITE,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
        )
    }
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        } else {
            Icon(
                imageVector = gestureActionIcon(action),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
        if (action.showsShellCommandBadge(settings.shellCommands)) {
            ShellCommandBadgeOverlay(iconSize = iconSize)
        }
    }
}
