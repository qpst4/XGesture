package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「退回起手位置 = 撤销」的判定必须只看**相对位移**。
 *
 * 顶部/底部触钮的触摸窗口贴在屏幕边上但本身有厚度（顶部实测 181px），
 * 手指按下点距屏幕顶边就已经有 ~150px。若把「起手点的绝对深度」
 * 混进相对位移的比较里，整段前 (起手深度 + 12dp) 的滑动都会被当成撤销、
 * 反复清零本段状态；上层 [com.slideindex.app.gesture.GestureSessionThresholdTracker]
 * 每清零一次就会把「越短滑阈值」的震动重新播一遍 —— 表现就是滑动过程中一直震。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], instrumentedPackages = ["com.slideindex.app.gesture"])
class SwipePathSegmentResetTest {

    private val topStrip = RectF(0f, 0f, 1080f, 181f)

    private fun topRecognizer(startY: Float = 150f): SwipePathRecognizer =
        SwipePathRecognizer(PanelSide.TOP, density = 2.75f).apply {
            applyDistances(shortDp = 60f, longDp = 120f)
            applyAngles(GestureAngles())
            onTouchDown(800f, startY, topStrip)
            applyCompoundGestureGate(SwipePathRecognizer.ClassifyOptions.DEFAULT)
        }

    @Test
    fun topSwipe_inwardBeyondCancelSlop_isNotTreatedAsUndo() {
        val recognizer = topRecognizer()
        // 起手点距顶边 150px、手指在窗口内按下后向内滑 100px：
        // 这已经是明确的内滑，绝不能算「退回起手位置」。
        recognizer.onTouchMove(800f, 250f)

        assertEquals(false, recognizer.consumeSegmentReset())
    }

    @Test
    fun topSwipe_firstFewPixelsStillCountAsUndo() {
        val recognizer = topRecognizer()
        // 只在起手位置附近抖动（< 12dp）时才允许清零，撤销语义不能丢。
        recognizer.onTouchMove(800f, 152f)

        assertEquals(true, recognizer.consumeSegmentReset())
    }

    @Test
    fun topSwipe_backToEdge_isTreatedAsUndo() {
        val recognizer = topRecognizer(startY = 150f)
        recognizer.onTouchMove(800f, 300f)
        recognizer.consumeSegmentReset()
        // 手指滑回屏幕顶边（相对位移为负）同样应清零，折返判定依赖它。
        recognizer.onTouchMove(800f, 0f)

        assertEquals(true, recognizer.consumeSegmentReset())
    }
}
