package com.slideindex.app.overlay

import android.content.Intent
import com.slideindex.app.util.ShortcutKind

/** 任务切换器右键菜单项的类别。 */
enum class TaskSwitcherMenuItemType {
    SHORTCUT,
    FREE_WINDOW,
    APP_INFO,
    FORCE_STOP,
}

/**
 * 任务切换器菜单项。
 *
 * 从 `:app` 的 `TaskSwitcherContextMenu.kt` 拆出来下沉到 `:core:common`：
 * 它只是数据，被 `activity` / 面板 / 菜单构建共用，不该跟着绘制代码留在 `:app`。
 */
data class TaskSwitcherMenuItem(
    val label: String,
    val type: TaskSwitcherMenuItemType,
    val shortcutId: String? = null,
    val shortcutIntent: Intent? = null,
    /** Intent URI strings from manifest shortcuts.xml (SideGesture-style). */
    val intentUris: List<String>? = null,
    val useShellLaunch: Boolean = false,
    val kind: ShortcutKind? = null,
    val targetComponent: String? = null,
    /** True when this entry came from ShortcutService and must refresh by package+id. */
    val isServiceShortcut: Boolean = false,
)
