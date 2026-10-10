package com.slideindex.app.freezer

import com.slideindex.app.freezer.FreezerIntentResolution.Decision
import com.slideindex.app.settings.FreezerAppIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class FreezerIntentResolutionTest {

    @Test
    fun `recorded pause keeps an active app paused even in freeze work mode`() {
        // 这次修复的核心：用户点开应用后状态被清成启用，手势必须还它「暂停」，而不是冻结。
        assertEquals(
            Decision.Pause,
            FreezerIntentResolution.decide(
                state = FreezerAppState.ACTIVE,
                intent = FreezerAppIntent.PAUSE,
                fallbackPause = false,
            ),
        )
    }

    @Test
    fun `recorded freeze freezes an active app even in pause work mode`() {
        assertEquals(
            Decision.Freeze,
            FreezerIntentResolution.decide(
                state = FreezerAppState.ACTIVE,
                intent = FreezerAppIntent.FROZEN,
                fallbackPause = true,
            ),
        )
    }

    @Test
    fun `app without a record follows the work mode`() {
        assertEquals(
            Decision.Pause,
            FreezerIntentResolution.decide(FreezerAppState.ACTIVE, intent = null, fallbackPause = true),
        )
        assertEquals(
            Decision.Freeze,
            FreezerIntentResolution.decide(FreezerAppState.ACTIVE, intent = null, fallbackPause = false),
        )
    }

    @Test
    fun `app already in its target state is left alone`() {
        assertEquals(
            Decision.Skip,
            FreezerIntentResolution.decide(FreezerAppState.PAUSED, FreezerAppIntent.PAUSE, fallbackPause = true),
        )
        assertEquals(
            Decision.Skip,
            FreezerIntentResolution.decide(FreezerAppState.FROZEN, FreezerAppIntent.FROZEN, fallbackPause = false),
        )
    }

    @Test
    fun `frozen app is not unfrozen just to honour a pause record`() {
        // 冻结优先于暂停：已冻结的包要变成暂停得先解冻，不值得为「还原意图」踩这个中间态。
        assertEquals(
            Decision.Skip,
            FreezerIntentResolution.decide(FreezerAppState.FROZEN, FreezerAppIntent.PAUSE, fallbackPause = true),
        )
    }

    @Test
    fun `decision maps to the intent it records`() {
        assertEquals(FreezerAppIntent.FROZEN, FreezerIntentResolution.intentFor(Decision.Freeze))
        assertEquals(FreezerAppIntent.PAUSE, FreezerIntentResolution.intentFor(Decision.Pause))
        assertEquals(null, FreezerIntentResolution.intentFor(Decision.Skip))
    }
}
