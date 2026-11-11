package com.slideindex.app.gesture.multitouch

/**
 * 当前屏幕的显示状态，用于把触摸屏的**面板自然坐标**换算成**屏幕坐标**。
 *
 * 为什么需要：观察器在 shell 进程里直接读 evdev，坐标永远是面板自然方向（手机通常竖屏）；
 * 屏幕转到横屏后，用户眼里的「右滑」在面板坐标里可能是「下滑」。做法对齐 GMD：
 * **在入口把每个触点旋转到屏幕坐标**，后面的识别、路径录制/匹配、锚点就都自动一致。
 *
 * 约定：`rotation` 是 `Display.getRotation()`（画面旋转角，与设备物理旋转相反），
 * 且假设「面板自然方向 == 屏幕自然方向」（绝大多数手机成立）。
 */
data class TouchDisplayTransform(
    /** `android.view.Surface.ROTATION_*`。 */
    val rotation: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
) {
    /** 拿到有效尺寸之前（例如旧版 service 不知道横屏）不要做换算。 */
    val valid: Boolean
        get() = screenWidthPx > 0 && screenHeightPx > 0

    companion object {
        /** 没有显示信息时的「不换算」状态。 */
        val IDENTITY = TouchDisplayTransform(rotation = 0, screenWidthPx = 0, screenHeightPx = 0)
    }
}

/**
 * 面板自然像素坐标 → 当前屏幕像素坐标。
 *
 * 公式与 GMD 的 `parser/f.a(Point)` 一致（W/H 为旋转后的屏幕宽高）：
 * - 90°：`(y, W - x)`
 * - 180°：`(W - x, H - y)`
 * - 270°：`(H - y, x)`
 * - 0°：原样
 *
 * 面板量程与屏幕自然尺寸不一致时（裁切、缩放），先按自然尺寸等比缩放再旋转。
 *
 * @return 屏幕像素坐标；[transform] 不可用时原样返回。
 */
fun rotatePanelPointToScreen(
    x: Float,
    y: Float,
    panelWidth: Float,
    panelHeight: Float,
    transform: TouchDisplayTransform,
): Pair<Float, Float> {
    if (!transform.valid || panelWidth <= 0f || panelHeight <= 0f) return x to y

    // 屏幕自然方向（竖屏）的宽高：rotation 为奇数时当前宽高是交换过的。
    val naturalWidth = if (transform.rotation % 2 == 0) {
        transform.screenWidthPx.toFloat()
    } else {
        transform.screenHeightPx.toFloat()
    }
    val naturalHeight = if (transform.rotation % 2 == 0) {
        transform.screenHeightPx.toFloat()
    } else {
        transform.screenWidthPx.toFloat()
    }

    // 面板 → 自然屏幕：处理量程与屏幕尺寸不一致的机型。
    val nx = x * naturalWidth / panelWidth
    val ny = y * naturalHeight / panelHeight

    return when (transform.rotation) {
        1 -> ny to (naturalWidth - nx)
        2 -> (naturalWidth - nx) to (naturalHeight - ny)
        3 -> (naturalHeight - ny) to nx
        else -> nx to ny
    }
}
