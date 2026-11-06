package com.slideindex.app.shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeSensitivityScaleTest {

    @Test
    fun effectiveThreshold_higherUiMeansLowerThreshold() {
        // 区间重标定：UI 1 = 旧滑条 5 档的阈值，UI 20 不变。
        assertEquals(16.2f, ShakeSensitivityScale.effectiveThreshold(1f), 0.001f)
        assertEquals(2f, ShakeSensitivityScale.effectiveThreshold(20f), 0.001f)
        assertTrue(ShakeSensitivityScale.effectiveThreshold(14f) > ShakeSensitivityScale.effectiveThreshold(18f))
    }

    @Test
    fun effectiveThreshold_staysWithinReachableGyroRange() {
        for (ui in 1..20) {
            val threshold = ShakeSensitivityScale.effectiveThreshold(ui.toFloat())
            assertTrue("ui=$ui threshold=$threshold", threshold in 2f..16.2f)
        }
    }

    @Test
    fun migrateUiFromV2_keepsUiMonotonicAndInRange() {
        // 阈值区间整体下移后不可能再"保持原阈值"，只保证旧值映射后仍然单调、落在 UI 范围内。
        var previous = 0f
        for (legacy in 1..10) {
            val migrated = ShakeSensitivityScale.migrateUiFromV2(legacy.toFloat())
            assertTrue("legacy=$legacy migrated=$migrated", migrated in 1f..20f)
            assertTrue("legacy=$legacy migrated=$migrated", migrated >= previous)
            previous = migrated
        }
    }

    @Test
    fun clampUi_limitsToOneThroughTwenty() {
        assertEquals(1f, ShakeSensitivityScale.clampUi(0.2f))
        assertEquals(20f, ShakeSensitivityScale.clampUi(99f))
        assertEquals(14f, ShakeSensitivityScale.clampUi(14f))
    }
}
