package com.slideindex.app.gesture.multitouch

import android.view.Surface

/** 屏幕原始像素坐标（左上角原点），与 `MotionEvent.rawX/rawY`、浮层窗口用的是同一套坐标。 */
data class ScreenAnchor(val x: Float, val y: Float)

/**
 * 把面板自然坐标 [TouchAnchor] 换算成**当前屏幕坐标**（原始像素）。
 *
 * 前提：触摸屏的自然方向与显示器的自然方向一致（手机、平板都是这么装的；两者不一致时
 * 原始坐标连普通点击都对不上，属于设备级校正，不在本功能处理范围内）。
 *
 * 旋转约定来自 `Display.getRotation()`：它是**画面被旋转的角度**，与设备物理旋转方向相反
 * （文档原话：设备逆时针转 90°，渲染为了补偿会顺时针转 90°，返回值就是 [Surface.ROTATION_90]）。
 * 以面板自然方向为基准（u 沿自然 X 轴向右、v 沿自然 Y 轴向下；手机通常是竖屏自然方向，
 * 即 u 沿短边、v 沿长边）：
 *
 * - [Surface.ROTATION_0]：屏幕 x = u，屏幕 y = v；
 * - [Surface.ROTATION_90]（设备逆时针转、自然顶边在**左**）：屏幕 x = v，屏幕 y = 1 - u；
 * - [Surface.ROTATION_180]：屏幕 x = 1 - u，屏幕 y = 1 - v；
 * - [Surface.ROTATION_270]（设备顺时针转、自然顶边在**右**）：屏幕 x = 1 - v，屏幕 y = u。
 *
 * 横屏时 [widthPx]/[heightPx] 已经是当前方向的屏幕尺寸，公式里直接乘即可。
 *
 * 纯函数，方便单测；真机上可用一次手指落点核对（`MultiTouchGestureCoordinator` 会打点）。
 */
fun mapTouchAnchorToScreen(
    anchor: TouchAnchor,
    widthPx: Int,
    heightPx: Int,
    rotation: Int,
): ScreenAnchor {
    val width = widthPx.coerceAtLeast(1).toFloat()
    val height = heightPx.coerceAtLeast(1).toFloat()
    val u = anchor.u.coerceIn(0f, 1f)
    val v = anchor.v.coerceIn(0f, 1f)
    val (x, y) = when (rotation) {
        Surface.ROTATION_90 -> v * width to (1f - u) * height
        Surface.ROTATION_180 -> (1f - u) * width to (1f - v) * height
        Surface.ROTATION_270 -> (1f - v) * width to u * height
        else -> u * width to v * height
    }
    return ScreenAnchor(
        x = x.coerceIn(0f, width),
        y = y.coerceIn(0f, height),
    )
}
