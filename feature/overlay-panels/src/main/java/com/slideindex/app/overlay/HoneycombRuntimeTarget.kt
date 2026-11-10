package com.slideindex.app.overlay

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.graphics.drawable.Drawable
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.showsMacroBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.launcher.showsShortcutBadge
import com.slideindex.app.shell.ShellCommand

class HoneycombRuntimeTarget(
    val item: QuickLauncherItem,
    @JvmField val label: String,
    @JvmField var icon: Drawable?,
    shellCommands: List<ShellCommand> = emptyList(),
    /** 分身条目：右上角黄点由渲染器在形状裁剪之外绘制。 */
    @JvmField val isClone: Boolean = false,
) {
    val isShortcut: Boolean = item.showsShortcutBadge()
    val isShellCommandBadge: Boolean = item.showsShellCommandBadge(shellCommands)

    /** 「运行宏」角标：宏格子用目标 App 图标，需要和「打开应用」区分。 */
    val isMacroBadge: Boolean = item.showsMacroBadge()
}
