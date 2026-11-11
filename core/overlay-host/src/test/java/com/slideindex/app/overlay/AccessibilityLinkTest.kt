package com.slideindex.app.overlay

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 弱证据（动作被系统拒绝）与强证据（建窗失败）的区分。
 *
 * 回归背景：`performGlobalAction` 返回 false 曾经被直接当成“链路已断”，看门狗随即拆掉
 * 整批浮层；而 false 的大多数原因是“这个动作在旧机型上不存在 / 在视频或锁屏里被拒”，
 * 与连接无关。拆窗后重绑需要 WRITE_SECURE_SETTINGS，普通用户只能手动开关一次。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AccessibilityLinkTest {

    @After
    fun tearDown() {
        AccessibilityLink.resetForTest()
    }

    @Test
    fun `weak signals need three consecutive failures`() {
        AccessibilityLink.resetForTest()

        AccessibilityLink.noteSuspect("performGlobalAction(Back)")
        AccessibilityLink.noteSuspect("performGlobalAction(Back)")
        assertTrue("两次弱证据不得判成掉线", AccessibilityLink.isUsable())

        AccessibilityLink.noteSuspect("performGlobalAction(Back)")
        assertFalse("连续三次弱证据才升级为掉线", AccessibilityLink.isUsable())
        assertEquals(1, AccessibilityLink.brokenCount())
    }

    @Test
    fun `successful call resets weak counter`() {
        AccessibilityLink.resetForTest()

        AccessibilityLink.noteSuspect("performGlobalAction(Back)")
        AccessibilityLink.noteSuspect("performGlobalAction(Back)")
        AccessibilityLink.noteAlive("performGlobalAction(Back)")
        AccessibilityLink.noteSuspect("performGlobalAction(Back)")

        assertTrue("成功调用之后弱证据要从零重新累计", AccessibilityLink.isUsable())
    }

    @Test
    fun `strong evidence breaks the link immediately`() {
        AccessibilityLink.resetForTest()

        // 建窗失败（token null / not valid）是强证据：直接把链路标成不可用。
        AccessibilityLink.noteBroken("window-add:edge", "token null is not valid")

        assertFalse(AccessibilityLink.isUsable())
        assertEquals("window-add:edge (token null is not valid)", AccessibilityLink.brokenReason())
    }
}
