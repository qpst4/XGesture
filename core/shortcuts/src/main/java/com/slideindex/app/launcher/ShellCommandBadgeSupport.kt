package com.slideindex.app.launcher

import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutShellSupport
import com.slideindex.app.activity.findForLaunchShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver

fun GestureAction.showsShellCommandBadge(shellCommands: List<ShellCommand>): Boolean {
    if (this !is GestureAction.ExecuteShellCommand) return false
    return ShellCommandIconResolver.findForCommandLine(command, shellCommands) != null
}

fun GestureAction.LaunchShortcut.isShellActivityShortcut(
    activityShortcuts: List<ActivityShortcut>
): Boolean {
    activityShortcuts.findForLaunchShortcut(payloadKey)?.let { shortcut ->
        if (ActivityShortcutShellSupport.isShellShortcut(shortcut)) return true
    }
    val decoded = GestureShortcutPayload.decode(payloadKey) ?: return false
    return when (decoded) {
        is GestureShortcutPayload.Decoded.IntentShortcut ->
            ActivityShortcutShellSupport.isShellUri(decoded.intentUri)
        is GestureShortcutPayload.Decoded.IntentsShortcut ->
            decoded.intentUris.any { ActivityShortcutShellSupport.isShellUri(it) }
        else -> false
    }
}

/** Shell 应用内直达（cebianshell）：一律显示 Shell 角标。 */
fun GestureAction.LaunchShortcut.showsShellActivityShortcutBadge(
    activityShortcuts: List<ActivityShortcut>
): Boolean = isShellActivityShortcut(activityShortcuts)

fun QuickLauncherItem.showsShellCommandBadge(shellCommands: List<ShellCommand>): Boolean {
    if (type != QuickLauncherItemType.ACTION) return false
    val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return false
    return action.showsShellCommandBadge(shellCommands)
}
