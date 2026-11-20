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

/** 手势的生效区域：手势起点（绘制手势为轨迹任意点）落在该区域才触发。 */
enum class MultiTouchTriggerArea(val key: String) {
    ANYWHERE("anywhere"),
    TOP("top"),
    BOTTOM("bottom"),
    ;

    companion object {
        fun fromKey(raw: String?): MultiTouchTriggerArea =
            entries.firstOrNull { it.key == raw } ?: ANYWHERE
    }
}

/** 手势条目：类型 + 接触点数 + 动作 + 生效区域；`PATH` 额外带录制轨迹。 */
data class MultiTouchGestureEntry(
    val id: String,
    val kind: MultiPointerGesture,
    val fingerCount: Int,
    val action: GestureAction,
    val pathStrokes: List<PathStroke> = emptyList(),
    val triggerArea: MultiTouchTriggerArea = MultiTouchTriggerArea.ANYWHERE,
) {
    val isPath: Boolean get() = kind == MultiPointerGesture.PATH
}
