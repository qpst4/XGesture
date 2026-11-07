package com.slideindex.app.overlay.quickwheel

import com.slideindex.app.gesture.QuickWheelLaunchLevelShape
import com.slideindex.app.overlay.layout.QuickWheelShape

/**
 * 展平一级 / 二级的形态覆盖；`null` = 跟随轮盘自身配置。
 *
 * 原在 app 的 `GestureQuickWheelActionScreen`；随 quickwheel 面板一起下沉。
 */
fun QuickWheelLaunchLevelShape.toWheelShapeOrNull(): QuickWheelShape? = when (this) {
    QuickWheelLaunchLevelShape.FOLLOW -> null
    QuickWheelLaunchLevelShape.CIRCLE -> QuickWheelShape.CIRCLE
    QuickWheelLaunchLevelShape.RECT -> QuickWheelShape.RECT
}
