package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatBallSlideshowIntervalTest {
    @Test
    fun optionsMatchFvOrder() {
        assertEquals(
            listOf(10, 60, 180, 300, 600, 1800, 3600, 43200, 86400, 604800),
            FloatBallSettings.SLIDESHOW_INTERVAL_OPTIONS_SECONDS,
        )
        assertEquals(10, FloatBallSettings.DEFAULT_SLIDESHOW_INTERVAL_SECONDS)
    }

    @Test
    fun legacyAndArbitraryValuesNormalizeToNearestOption() {
        assertEquals(10, FloatBallSettings.normalizeSlideshowIntervalSeconds(3))
        assertEquals(60, FloatBallSettings.normalizeSlideshowIntervalSeconds(45))
        assertEquals(10, FloatBallSettings.normalizeSlideshowIntervalSeconds(0))
        assertEquals(604800, FloatBallSettings.normalizeSlideshowIntervalSeconds(999999))
    }
}
