package com.slideindex.app.launcher

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「运行宏」角标的判定：只有"运行宏"动作才显示，别的动作 / 应用 / 快捷方式都不显示。
 *
 * 背景：宏格子显示的是目标 App 的图标，不标角标会和「打开这个 App」混淆；
 * 角标只在 `ACTION` 且 payload 能解析成 [GestureAction.RunMacro] 时为真。
 */
class QuickLauncherBadgeTest {

    @Test
    fun runMacroAction_showsMacroBadge() {
        val item = QuickLauncherItem.action(GestureAction.RunMacro("macro-1"))
        assertTrue(item.showsMacroBadge())
    }

    @Test
    fun otherActions_doNotShowMacroBadge() {
        assertFalse(QuickLauncherItem.action(GestureAction.LaunchApp("com.tencent.mm")).showsMacroBadge())
        assertFalse(QuickLauncherItem.action(GestureAction.Back).showsMacroBadge())
    }

    @Test
    fun appAndShortcutItems_doNotShowMacroBadge() {
        assertFalse(QuickLauncherItem.app("com.tencent.mm").showsMacroBadge())
        assertFalse(QuickLauncherItem.shortcut("com.tencent.mm/.MainActivity").showsMacroBadge())
    }

    @Test
    fun malformedActionPayload_doesNotCrashNorShow() {
        val broken = QuickLauncherItem(QuickLauncherItemType.ACTION, "garbage")
        assertFalse(broken.showsMacroBadge())
    }
}
