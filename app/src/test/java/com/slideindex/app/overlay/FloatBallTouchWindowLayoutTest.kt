package com.slideindex.app.overlay

import android.view.WindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `FloatBallTouchWindowLayout`（A3 拆出的触摸窗口类）里可独立验证的判断。
 *
 * 全屏判定决定了拖动中是否还要再扩窗；判错会导致重复扩窗或漏扩。
 */
@RunWith(RobolectricTestRunner::class)
class FloatBallTouchWindowLayoutTest {

    @Test
    fun `match parent on both axes counts as fullscreen`() {
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        assertTrue(FloatBallTouchWindowLayout.isTouchHostFullscreen(params))
    }

    @Test
    fun `fixed size window is not fullscreen`() {
        val params = WindowManager.LayoutParams().apply {
            width = 144
            height = 144
        }

        assertFalse(FloatBallTouchWindowLayout.isTouchHostFullscreen(params))
    }

    @Test
    fun `single axis match parent is not fullscreen`() {
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = 2400
        }

        assertFalse(FloatBallTouchWindowLayout.isTouchHostFullscreen(params))
    }
}
