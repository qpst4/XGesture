package com.slideindex.app.gesture

internal class GestureSessionContinuousPick {
    var taskSwitcher = false
    var quickLauncher = false
    var shell = false
    var honeycomb = false

    /** 圆环启动器（上游由 AppSwitcher 改名而来）。 */
    var ringLauncher = false
    var appCarouselSwitcher = false
    var fingertipRing = false

    /** 浮动导航：本次手势是否已显示并进入跟手选择态。 */
    var floatNav = false

    /** 快捷轮盘（本次新增）。 */
    var quickWheel = false

    fun taskSwitcherActive(): Boolean = taskSwitcher

    fun quickLauncherActive(): Boolean = quickLauncher

    fun shellActive(): Boolean = shell

    fun honeycombActive(): Boolean = honeycomb

    fun ringLauncherActive(): Boolean = ringLauncher

    fun appCarouselSwitcherActive(): Boolean = appCarouselSwitcher

    fun fingertipRingActive(): Boolean = fingertipRing

    fun quickWheelActive(): Boolean = quickWheel

    fun clearQuickLauncher() {
        quickLauncher = false
    }

    fun clearShell() {
        shell = false
    }

    fun clearHoneycomb() {
        honeycomb = false
    }

    fun clearRingLauncher() {
        ringLauncher = false
    }

    fun clearAppCarouselSwitcher() {
        appCarouselSwitcher = false
    }

    fun clearFingertipRing() {
        fingertipRing = false
    }

    fun floatNavActive(): Boolean = floatNav

    fun clearFloatNav() {
        floatNav = false
    }

    fun clearQuickWheel() {
        quickWheel = false
    }

    fun reset() {
        taskSwitcher = false
        quickLauncher = false
        shell = false
        honeycomb = false
        ringLauncher = false
        appCarouselSwitcher = false
        fingertipRing = false
        floatNav = false
        quickWheel = false
    }
}
