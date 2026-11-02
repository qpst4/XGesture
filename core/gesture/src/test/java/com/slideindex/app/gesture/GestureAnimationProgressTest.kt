package com.slideindex.app.gesture

import com.slideindex.app.overlay.animation.GestureAnimationPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureAnimationProgressTest {

    @Test
    fun leftEdge_inwardSwipe_progressGrowsWithFingerDelta() {
        assertEquals(0f, GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Left,
            originX = 12f,
            originY = 400f,
            fingerX = 0f,
            fingerY = 400f,
            swipeDirection = null,
        ), 0.01f)
        assertTrue(
            GestureAnimationProgress.progress(
                position = GestureAnimationPosition.Left,
                originX = 12f,
                originY = 400f,
                fingerX = 48f,
                fingerY = 420f,
                swipeDirection = SwipeDirection.IN,
            ) > 1f,
        )
    }

    /**
     * 底边：内滑进度只看相对位移（手指向上滑时 fingerY 为负），
     * 与「按下点离屏幕底边多远」（originY 绝对坐标）无关。
     *
     * 回归点：早前算成 `originY - fingerY`，底部恒等于「屏幕高度 + 拖动距离」，
     * 第一帧就 ≥ maxLength —— 胶囊/圆泡直接满尺寸出现，完全没有生长动画。
     */
    @Test
    fun bottomEdge_inwardSwipe_progressUsesRelativeFingerYOnly() {
        val pressedNearEdge = GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Bottom,
            originX = 540f,
            originY = 2320f,
            fingerX = 540f,
            fingerY = -80f,
            swipeDirection = null,
        )
        val pressedFarFromEdge = GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Bottom,
            originX = 540f,
            originY = 200f,
            fingerX = 540f,
            fingerY = -80f,
            swipeDirection = null,
        )
        assertEquals(80f, pressedNearEdge, 0.01f)
        assertEquals(pressedNearEdge, pressedFarFromEdge, 0.01f)
    }

    /**
     * 顶边：同样只看相对位移。回归点：早前算成 `fingerY - originY`，
     * 手指没滑过「按下点到顶边的距离」之前进度一直是负数（被上层夹成常量），
     * 表现就是提示图形卡住不动。
     */
    @Test
    fun topEdge_inwardSwipe_progressUsesRelativeFingerYOnly() {
        assertEquals(0f, GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Top,
            originX = 300f,
            originY = 75f,
            fingerX = 300f,
            fingerY = 0f,
            swipeDirection = null,
        ), 0.01f)
        assertEquals(40f, GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Top,
            originX = 300f,
            originY = 75f,
            fingerX = 300f,
            fingerY = 40f,
            swipeDirection = SwipeDirection.IN,
        ), 0.01f)
        assertEquals(40f, GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Top,
            originX = 300f,
            originY = 1500f,
            fingerX = 300f,
            fingerY = 40f,
            swipeDirection = SwipeDirection.IN,
        ), 0.01f)
    }

    @Test
    fun topEdge_horizontalAlongEdge_usesAlongAxis() {
        val progress = GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Top,
            originX = 300f,
            originY = 0f,
            fingerX = 360f,
            fingerY = 0f,
            swipeDirection = SwipeDirection.DOWN,
        )
        assertEquals(60f, progress, 0.01f)
        assertTrue(GestureAnimationProgress.isHorizontalAlongEdge(
            GestureAnimationPosition.Top,
            SwipeDirection.DOWN,
        ))
    }

    @Test
    fun bottomEdge_horizontalAlongEdge_usesAlongAxis() {
        val progress = GestureAnimationProgress.progress(
            position = GestureAnimationPosition.Bottom,
            originX = 900f,
            originY = 2320f,
            fingerX = 820f,
            fingerY = 0f,
            swipeDirection = SwipeDirection.UP,
        )
        assertEquals(80f, progress, 0.01f)
        assertFalse(GestureAnimationProgress.isHorizontalAlongEdge(
            GestureAnimationPosition.Bottom,
            SwipeDirection.IN,
        ))
    }
}
