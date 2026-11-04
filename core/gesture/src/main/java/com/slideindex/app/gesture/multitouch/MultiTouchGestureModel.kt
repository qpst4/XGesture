package com.slideindex.app.gesture.multitouch

import com.slideindex.app.gesture.GestureAction

/** 归一化到屏幕坐标系的一个触点（x 按屏幕高度归一化）。 */
data class TouchPointer(
    val id: Int,
    val x: Float,
    val y: Float,
)

/**
 * 多指手势类型。
 *
 * 前 8 个是几何手势（由识别器实时判定），`TAP`/`DOUBLE_TAP`/`TRIPLE_TAP`/`LONG_PRESS`
 * 是点按类，`PATH` 是录制路径（只在手势条目里出现，识别器本身不输出）。
 */
enum class MultiPointerGesture {
    PINCH,
    SPREAD,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    ROTATE_CLOCKWISE,
    ROTATE_COUNTER_CLOCKWISE,
    SWIPE_UP_LEFT,
    SWIPE_UP_RIGHT,
    SWIPE_DOWN_LEFT,
    SWIPE_DOWN_RIGHT,
    TAP,
    DOUBLE_TAP,
    TRIPLE_TAP,
    LONG_PRESS,
    /** 触摸类型（与「单击」不同）。 */
    TOUCH,
    PATH,
    ;

    companion object {
        fun fromName(raw: String?): MultiPointerGesture? =
            entries.firstOrNull { it.name == raw }

        /** 是否是点按类（不做几何/路径识别）。 */
        val MultiPointerGesture.isTapLike: Boolean
            get() = this == TAP || this == DOUBLE_TAP || this == TRIPLE_TAP || this == LONG_PRESS
    }
}

/** 手势条目：类型 + 接触点数 + 动作；`PATH` 额外带录制轨迹。 */
data class MultiTouchGestureEntry(
    val id: String,
    val kind: MultiPointerGesture,
    val fingerCount: Int,
    val action: GestureAction,
    val pathStrokes: List<PathStroke> = emptyList(),
) {
    val isPath: Boolean get() = kind == MultiPointerGesture.PATH
}

/** 手势类型的中文名（设置页/反馈提示共用）。 */
fun MultiPointerGesture.displayName(): String = when (this) {
    MultiPointerGesture.PINCH -> "捏合"
    MultiPointerGesture.SPREAD -> "张开"
    MultiPointerGesture.SWIPE_UP -> "上滑"
    MultiPointerGesture.SWIPE_DOWN -> "下滑"
    MultiPointerGesture.SWIPE_LEFT -> "左滑"
    MultiPointerGesture.SWIPE_RIGHT -> "右滑"
    MultiPointerGesture.ROTATE_CLOCKWISE -> "顺时针旋转"
    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE -> "逆时针旋转"
    MultiPointerGesture.SWIPE_UP_LEFT -> "左上滑"
    MultiPointerGesture.SWIPE_UP_RIGHT -> "右上滑"
    MultiPointerGesture.SWIPE_DOWN_LEFT -> "左下滑"
    MultiPointerGesture.SWIPE_DOWN_RIGHT -> "右下滑"
    MultiPointerGesture.TAP -> "单击"
    MultiPointerGesture.DOUBLE_TAP -> "双击"
    MultiPointerGesture.TRIPLE_TAP -> "三击"
    MultiPointerGesture.LONG_PRESS -> "长按"
    MultiPointerGesture.TOUCH -> "触屏"
    MultiPointerGesture.PATH -> "绘制手势"
}
