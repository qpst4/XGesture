package com.slideindex.app.settings

/**
 * 「从贴边指示条打开收纳面板时，先显示哪个页签」。
 *
 * 指示条那条竖条**既能点开、也能横向拉出**，两条路都按这个设置走 —— 用户提的需求原文就是
 * "从指示条打开面板显示的 tab 是闪念还是剪贴板"。长按指示条（就地记一条）不受它影响。
 */
enum class StashPanelHandleOpenTab {
    /** 记住上次停留的页签（进程内记住；App 进程重启后回到 [STASH]）。 */
    REMEMBER_LAST,

    /** 总是「闪念」。 */
    STASH,

    /** 总是「剪贴板」。 */
    CLIPBOARD,
}
