package com.slideindex.app.gesture.multitouch

import android.view.Surface

/**
 * 把「触摸面板自然坐标系」里识别出的手势方向，换算成「当前屏幕方向」的方向。
 *
 * 为什么需要：识别器在 shell 进程里直接读 evdev，拿到的坐标是**面板自然方向**
 * （手机通常是竖屏），它不知道屏幕现在转到了哪个方向。横屏时用户做的「向右滑」，
 * 在面板坐标里可能是「向下滑」；直接按识别结果匹配动作条目就会绑错方向。
 *
 * 旋转约定与 [mapTouchAnchorToScreen] 相同：`Display.getRotation()` 是画面旋转角，
 * 和设备物理旋转相反。以 [Surface.ROTATION_90]（设备逆时针转、自然顶边在左）为例：
 * 面板的「右」对应屏幕的「上」。
 *
 * 只处理**方向**，不处理位置：
 * - 八方向滑动按 90° 的整数倍旋转；
 * - 捏合 / 张开 / 顺逆旋转 / 点按 / 触摸与方向无关，原样返回；
 * - 绘制手势（PATH）的模板与实时轨迹都在同一套面板坐标里采集，不做换算。
 */
fun MultiPointerGesture.forDisplayRotation(rotation: Int): MultiPointerGesture {
    val vector = DIRECTION_VECTORS[this] ?: return this
    val (x, y) = when (rotation) {
        Surface.ROTATION_90 -> vector.second to -vector.first
        Surface.ROTATION_180 -> -vector.first to -vector.second
        Surface.ROTATION_270 -> -vector.second to vector.first
        else -> vector
    }
    return VECTOR_DIRECTIONS[x to y] ?: this
}

/** 面板坐标里的方向向量：x 向右、y 向下（与 evdev 归一化坐标一致）。 */
private val DIRECTION_VECTORS: Map<MultiPointerGesture, Pair<Int, Int>> = mapOf(
    MultiPointerGesture.SWIPE_RIGHT to (1 to 0),
    MultiPointerGesture.SWIPE_LEFT to (-1 to 0),
    MultiPointerGesture.SWIPE_DOWN to (0 to 1),
    MultiPointerGesture.SWIPE_UP to (0 to -1),
    MultiPointerGesture.SWIPE_DOWN_RIGHT to (1 to 1),
    MultiPointerGesture.SWIPE_DOWN_LEFT to (-1 to 1),
    MultiPointerGesture.SWIPE_UP_RIGHT to (1 to -1),
    MultiPointerGesture.SWIPE_UP_LEFT to (-1 to -1),
)

private val VECTOR_DIRECTIONS: Map<Pair<Int, Int>, MultiPointerGesture> =
    DIRECTION_VECTORS.entries.associate { (gesture, vector) -> vector to gesture }
