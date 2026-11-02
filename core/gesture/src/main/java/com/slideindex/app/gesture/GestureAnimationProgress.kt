package com.slideindex.app.gesture

import com.slideindex.app.overlay.animation.GestureAnimationPosition
import kotlin.math.abs

/** Pure helpers for gesture-hint animation progress (unit-testable). */
object GestureAnimationProgress {
    /**
     * 提示图形（胶囊/圆泡）的进度。
     *
     * ⚠️ 坐标口径是**混合**的，别再把两者相减：
     * - 内滑（垂直于边）方向：`fingerX`（左右边）/ `fingerY`（上下边）是**相对按下点的位移**
     *   （GestureAnimationState.onDragStart 里 `snapTo(0f)` 起步后逐帧累加），起点为 0；
     * - 沿边方向：`fingerX`（上下边）/ `fingerY`（左右边）是**绝对屏幕坐标**，
     *   所以沿边位移要 `abs(fingerX - originX)`。
     *
     * 早前上下边把相对位移和绝对 origin 相减（`fingerY - originY` / `originY - fingerY`），
     * 底部恒等于「屏幕高度 + 拖动距离」→ 第一帧就顶满 maxLength（圆泡/胶囊直接满尺寸出现），
     * 顶部前「按下点到屏幕边的距离」全是负数 → 进度被夹成常量、完全没有生长动画。
     */
    fun progress(
        position: GestureAnimationPosition,
        originX: Float,
        originY: Float,
        fingerX: Float,
        fingerY: Float,
        swipeDirection: SwipeDirection?,
    ): Float {
        val along = abs(fingerX - originX)
        return when (position) {
            GestureAnimationPosition.Left -> fingerX
            GestureAnimationPosition.Right -> -fingerX
            GestureAnimationPosition.Bottom -> {
                val inward = -fingerY
                if (isHorizontalAlongEdge(position, swipeDirection)) {
                    along
                } else {
                    maxOf(inward, along)
                }
            }
            GestureAnimationPosition.Top -> {
                val inward = fingerY
                if (isHorizontalAlongEdge(position, swipeDirection)) {
                    along
                } else {
                    maxOf(inward, along)
                }
            }
        }.coerceAtLeast(0f)
    }

    fun isHorizontalAlongEdge(
        position: GestureAnimationPosition,
        swipeDirection: SwipeDirection?,
    ): Boolean {
        if (position != GestureAnimationPosition.Top && position != GestureAnimationPosition.Bottom) {
            return false
        }
        return when (swipeDirection) {
            SwipeDirection.UP, SwipeDirection.DOWN -> true
            SwipeDirection.UP_RIGHT, SwipeDirection.DOWN_RIGHT, SwipeDirection.IN, null -> false
        }
    }
}
