package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.cloneTargetUserId
import com.slideindex.app.launcher.isShellActivityShortcut
import com.slideindex.app.launcher.showsShellActivityShortcutBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.shell.ShellCommand

/**
 * 一个动作在图标右下角 / 右上角需要显示的角标。
 *
 * 功能环、设置页行图标、副标题共用这份判定，避免"环上对、设置页不对"。
 */
internal data class ActionIconBadges(
    val showShortcut: Boolean = false,
    val showShellCommand: Boolean = false,
    val showMacro: Boolean = false,
    /** 0 = 主实例；>0 = 分身 / 工作资料。 */
    val cloneUserId: Int = 0,
) {
    val isClone: Boolean get() = cloneUserId > 0
}

internal fun GestureAction.actionIconBadges(
    context: Context,
    activityShortcuts: List<ActivityShortcut>,
    shellCommands: List<ShellCommand>,
): ActionIconBadges = when (this) {
    is GestureAction.LaunchApp -> ActionIconBadges(cloneUserId = cloneUserId)

    is GestureAction.LaunchShortcut -> {
        val shellShortcut = showsShellActivityShortcutBadge(activityShortcuts)
        ActionIconBadges(
            showShortcut = !shellShortcut && !isShellActivityShortcut(activityShortcuts),
            showShellCommand = shellShortcut,
        )
    }

    is GestureAction.ExecuteShellCommand -> ActionIconBadges(
        showShellCommand = showsShellCommandBadge(shellCommands),
    )

    is GestureAction.RunMacro -> ActionIconBadges(
        showMacro = true,
        cloneUserId = cloneTargetUserId(context),
    )

    else -> ActionIconBadges()
}
