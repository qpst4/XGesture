package com.slideindex.app.multitouch

import com.slideindex.app.gesture.multitouch.TouchAnchor

/**
 * 全局触控的触发条件判定，对齐 GMD 的 `TriggerEnum.c()` / `KeyboardBehaviorEnum`。
 *
 * 只认手势**起点**：落在哪块区域、是不是压在键盘上，都在手指落下的那一刻定死。
 * 放在这里的都是纯函数，service 进程负责把真实坐标和键盘矩形喂进来，单测不需要设备。
 */
object MultiTouchTriggerFilter {

    const val AREA_ANYWHERE = 0

    /** 中：离四条边都超过边缘带宽度才算。 */
    const val AREA_MIDDLE = 1
    const val AREA_TOP = 2
    const val AREA_BOTTOM = 3

    const val KEYBOARD_BLOCKED = 0

    /** 仅在键盘上方活动：起点落在键盘矩形内不触发。 */
    const val KEYBOARD_ABOVE = 1
    const val KEYBOARD_EVERYWHERE = 2

    fun areaId(raw: String?): Int = when (raw) {
        "middle" -> AREA_MIDDLE
        "top" -> AREA_TOP
        "bottom" -> AREA_BOTTOM
        else -> AREA_ANYWHERE
    }

    fun keyboardBehaviorId(raw: String?): Int = when (raw) {
        "blocked" -> KEYBOARD_BLOCKED
        "everywhere" -> KEYBOARD_EVERYWHERE
        else -> KEYBOARD_ABOVE
    }

    /**
     * 区域判定。
     *
     * @param startU 起点横坐标（0..1，屏幕宽归一化）
     * @param startV 起点纵坐标（0..1，屏幕高归一化）
     */
    fun areaAllows(
        area: Int,
        startU: Float,
        startV: Float,
        widthPx: Int,
        heightPx: Int,
        borderPx: Int,
    ): Boolean = when (area) {
        AREA_TOP -> startV <= 0.5f
        AREA_BOTTOM -> startV >= 0.5f
        AREA_MIDDLE -> {
            // 没有屏幕尺寸（刚启动还没推 display）时不拦，保持可用。
            if (widthPx <= 0 || heightPx <= 0) {
                true
            } else {
                val x = startU * widthPx
                val y = startV * heightPx
                val border = borderPx.toFloat()
                x > border && x < widthPx - border && y > border && y < heightPx - border
            }
        }

        else -> true
    }

    /**
     * 键盘策略判定（对齐 GMD 的 `fc0.a()`）。
     *
     * 键盘可见但拿不到矩形时按「不触发」处理——这是 GMD 的保守语义，宁可漏一次，
     * 也不要在键盘上误触发。
     */
    fun keyboardAllows(
        behavior: Int,
        startU: Float,
        startV: Float,
        visible: Boolean,
        hasBounds: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        displayWidth: Int,
        displayHeight: Int,
    ): Boolean {
        if (!visible) return true
        return when (behavior) {
            KEYBOARD_BLOCKED -> false
            KEYBOARD_EVERYWHERE -> true
            else -> {
                if (!hasBounds || displayWidth <= 0 || displayHeight <= 0) {
                    false
                } else {
                    val x = startU * displayWidth
                    val y = startV * displayHeight
                    !(x >= left && x < right && y >= top && y < bottom)
                }
            }
        }
    }

    /** 键盘矩形（display 坐标），避免长参数列表。 */
    data class KeyboardRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    /**
     * 组合判定：先区域、再键盘。
     *
     * - **区域**：绘制手势（[path] 非空）按 GMD 的语义——轨迹上**任意一个采样点**落在选中区域
     *   就算命中（`sx.C()` 遍历全部轨迹点）；几何手势只看起点。
     * - **键盘**：只看起点是否压在 IME 矩形内，与触发型手势一致；没有起点时跳过键盘判定。
     */
    fun accepts(
        area: Int,
        keyboardBehavior: Int,
        start: TouchAnchor?,
        path: List<TouchAnchor>?,
        widthPx: Int,
        heightPx: Int,
        borderPx: Int,
        keyboardVisible: Boolean,
        keyboardHasBounds: Boolean,
        keyboardBounds: KeyboardRect,
        displayWidth: Int,
        displayHeight: Int,
    ): Boolean {
        val areaOk = when {
            !path.isNullOrEmpty() -> path.any {
                areaAllows(area, it.u, it.v, widthPx, heightPx, borderPx)
            }

            start != null -> areaAllows(area, start.u, start.v, widthPx, heightPx, borderPx)
            else -> true
        }
        if (!areaOk) return false
        val anchor = start ?: return true
        return keyboardAllows(
            behavior = keyboardBehavior,
            startU = anchor.u,
            startV = anchor.v,
            visible = keyboardVisible,
            hasBounds = keyboardHasBounds,
            left = keyboardBounds.left,
            top = keyboardBounds.top,
            right = keyboardBounds.right,
            bottom = keyboardBounds.bottom,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
        )
    }
}
