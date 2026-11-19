package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCarouselClosePlanTest {

    @Test
    fun effectiveMode_recentSourceAlwaysForceStops() {
        // 最近任务模式不暴露下拉，即使旧配置里存了「划掉」，实际也只强停。
        for (configured in TaskSwitcherCloseMode.entries) {
            assertEquals(
                TaskSwitcherCloseMode.FORCE_STOP,
                AppCarouselClosePlan.effectiveMode(AppCarouselListSource.RECENT_APPS, configured),
            )
        }
    }

    @Test
    fun effectiveMode_backgroundSourceUsesConfiguredValue() {
        for (configured in TaskSwitcherCloseMode.entries) {
            assertEquals(
                configured,
                AppCarouselClosePlan.effectiveMode(
                    AppCarouselListSource.BACKGROUND_TASKS,
                    configured,
                ),
            )
        }
    }

    @Test
    fun modeActions_matchTaskSwitcherSemantics() {
        assertTrue(AppCarouselClosePlan.shouldForceStop(TaskSwitcherCloseMode.FORCE_STOP))
        assertFalse(AppCarouselClosePlan.shouldDismissTask(TaskSwitcherCloseMode.FORCE_STOP))

        assertFalse(AppCarouselClosePlan.shouldForceStop(TaskSwitcherCloseMode.DISMISS_RECENTS))
        assertTrue(AppCarouselClosePlan.shouldDismissTask(TaskSwitcherCloseMode.DISMISS_RECENTS))

        assertTrue(AppCarouselClosePlan.shouldForceStop(TaskSwitcherCloseMode.BOTH))
        assertTrue(AppCarouselClosePlan.shouldDismissTask(TaskSwitcherCloseMode.BOTH))
    }
}
