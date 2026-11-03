package com.slideindex.app.message

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReturnChipPolicyTest {

    private val chat = "com.tencent.mobileqq"
    private val source = "com.xingin.xhs"

    @Test
    fun showsWhenTargetIsInForeground() {
        assertTrue(
            shouldShowReturnChip(
                sourcePackage = source,
                targetPackage = chat,
                foreground = ReturnChipForeground.TARGET,
                screenLocked = false,
            )
        )
    }

    @Test
    fun hidesWhenSourceIsMissing() {
        assertFalse(
            shouldShowReturnChip(
                sourcePackage = null,
                targetPackage = chat,
                foreground = ReturnChipForeground.TARGET,
                screenLocked = false,
            )
        )
        assertFalse(
            shouldShowReturnChip(
                sourcePackage = "  ",
                targetPackage = chat,
                foreground = ReturnChipForeground.TARGET,
                screenLocked = false,
            )
        )
    }

    @Test
    fun hidesWhenSourceEqualsTarget() {
        assertFalse(
            shouldShowReturnChip(
                sourcePackage = chat,
                targetPackage = chat,
                foreground = ReturnChipForeground.TARGET,
                screenLocked = false,
            )
        )
    }

    @Test
    fun hidesWhileScreenLocked() {
        assertFalse(
            shouldShowReturnChip(
                sourcePackage = source,
                targetPackage = chat,
                foreground = ReturnChipForeground.TARGET,
                screenLocked = true,
            )
        )
    }

    @Test
    fun hidesWhenTargetIsNotInForeground() {
        assertFalse(
            shouldShowReturnChip(
                sourcePackage = source,
                targetPackage = chat,
                foreground = ReturnChipForeground.OTHER,
                screenLocked = false,
            )
        )
    }

    @Test
    fun allowsWhenForegroundIsUnknown() {
        // 无障碍未连接 / 拿不到前台时放行，由调用方用兜底延时决定何时显示。
        assertTrue(
            shouldShowReturnChip(
                sourcePackage = source,
                targetPackage = chat,
                foreground = ReturnChipForeground.UNKNOWN,
                screenLocked = false,
            )
        )
    }
}
