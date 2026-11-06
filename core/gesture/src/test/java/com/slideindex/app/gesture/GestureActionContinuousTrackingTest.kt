package com.slideindex.app.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionContinuousTrackingTest {

    @Test
    fun quickLauncherWithPanelId_supportsContinuousOnSwipe() {
        val action = GestureAction.QuickLauncher("panel-1")
        assertTrue(action.isContinuousTrackingKind())
        assertTrue(action.supportsContinuousTracking(GestureTriggerType.SHORT_SWIPE_IN))
        assertFalse(action.supportsContinuousTracking(GestureTriggerType.SHORT_SINGLE_TAP))
    }

    @Test
    fun quickLauncherEmptyPanelId_supportsContinuousOnSwipe() {
        val action = GestureAction.QuickLauncher()
        assertTrue(action.isContinuousTrackingKind())
        assertTrue(action.supportsContinuousTracking(GestureTriggerType.LONG_SWIPE_IN))
    }

    /**
     * 应用索引的长按槽位此前只能选「松手 / 即时」：`OpenIndex` 不在长按白名单里，
     * 持续触发被 UI 置灰。加入后长按可以「按住滑动选择、松手启动」。
     */
    @Test
    fun openIndex_supportsContinuousOnLongPress() {
        assertTrue(GestureAction.OpenIndex.isContinuousTrackingKind())
        assertTrue(
            GestureAction.OpenIndex.supportsContinuousTracking(GestureTriggerType.SHORT_LONG_PRESS)
        )
        assertTrue(
            GestureAction.OpenIndex.supportsContinuousTracking(GestureTriggerType.LONG_LONG_PRESS)
        )
        // 单击仍是点按类：长按例外不外溢。
        assertFalse(
            GestureAction.OpenIndex.supportsContinuousTracking(GestureTriggerType.SHORT_SINGLE_TAP)
        )
    }

    /** 与 UI 选择器同一口径：长按 + 索引下「持续触发」必须可选。 */
    @Test
    fun openIndex_continuousModeSelectableOnLongPress() {
        assertTrue(
            GestureTriggerMode.CONTINUOUS.supportsAction(
                GestureAction.OpenIndex,
                GestureTriggerType.SHORT_LONG_PRESS,
            )
        )
    }
}
