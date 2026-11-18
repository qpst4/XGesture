package com.slideindex.app.multitouch

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
}
