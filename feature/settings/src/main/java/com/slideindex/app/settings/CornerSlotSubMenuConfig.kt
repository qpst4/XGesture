package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction

data class CornerSlotSubMenuConfig(
    val enabled: Boolean = false,
    /**
     * 二级菜单条目。
     *
     * 反馈 #385：以前只允许放「快捷方式」；现在放宽成任意动作 ——
     * 应用（LaunchApp）、内置动作（返回/截图/…）都能进二级菜单。
     * 老数据里全是 LaunchShortcut，按 GestureAction 读不会丢。
     */
    val items: List<GestureAction> = emptyList(),
) {
    fun isActive(): Boolean = enabled && items.isNotEmpty()
}
