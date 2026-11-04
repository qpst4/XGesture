package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.multitouch.MultiPointerGesture

/**
 * 手势类型 → 字符串资源。
 *
 * 文案留在 app 层：`core/gesture` 是模型模块（还没有 res 目录），把中文名放在那里既过不了
 * 「新增 Kotlin 行硬编码中文」这道门禁，也让模型层依赖了界面语言。
 */
internal fun gestureKindLabelRes(kind: MultiPointerGesture): Int = when (kind) {
    MultiPointerGesture.PINCH -> R.string.multitouch_kind_pinch
    MultiPointerGesture.SPREAD -> R.string.multitouch_kind_spread
    MultiPointerGesture.SWIPE_UP -> R.string.multitouch_kind_swipe_up
    MultiPointerGesture.SWIPE_DOWN -> R.string.multitouch_kind_swipe_down
    MultiPointerGesture.SWIPE_LEFT -> R.string.multitouch_kind_swipe_left
    MultiPointerGesture.SWIPE_RIGHT -> R.string.multitouch_kind_swipe_right
    MultiPointerGesture.ROTATE_CLOCKWISE -> R.string.multitouch_kind_rotate_clockwise
    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE ->
        R.string.multitouch_kind_rotate_counter_clockwise

    MultiPointerGesture.SWIPE_UP_LEFT -> R.string.multitouch_kind_swipe_up_left
    MultiPointerGesture.SWIPE_UP_RIGHT -> R.string.multitouch_kind_swipe_up_right
    MultiPointerGesture.SWIPE_DOWN_LEFT -> R.string.multitouch_kind_swipe_down_left
    MultiPointerGesture.SWIPE_DOWN_RIGHT -> R.string.multitouch_kind_swipe_down_right
    MultiPointerGesture.TAP -> R.string.multitouch_kind_tap
    MultiPointerGesture.DOUBLE_TAP -> R.string.multitouch_kind_double_tap
    MultiPointerGesture.TRIPLE_TAP -> R.string.multitouch_kind_triple_tap
    MultiPointerGesture.LONG_PRESS -> R.string.multitouch_kind_long_press
    MultiPointerGesture.TOUCH -> R.string.multitouch_kind_touch
    MultiPointerGesture.PATH -> R.string.multitouch_kind_path
}

/** Compose 侧读取手势类型名（与设置页同包，调用处不需要额外的 import）。 */
@Composable
internal fun MultiPointerGesture.displayName(): String = stringResource(gestureKindLabelRes(this))
