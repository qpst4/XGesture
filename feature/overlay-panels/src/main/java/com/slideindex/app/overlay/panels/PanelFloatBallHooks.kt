package com.slideindex.app.overlay.panels

import com.slideindex.app.settings.AppSettings

/**
 * 悬浮球 chrome 的接线点（`:app` 的 `FloatBallOverlay` 静态方法）。
 *
 * 环状启动器 / 快捷轮盘这类面板需要"临时藏起悬浮球、结束后恢复"，
 * 但悬浮球本体在 `:app`，所以由启动器注入。
 */
object PanelFloatBallHooks {

    /** 进入自由移动模式（拖动悬浮球位置）。 */
    @Volatile
    var enterFreeMoveMode: (() -> Unit)? = null

    /** 环状启动器打开时收起悬浮球 chrome。 */
    @Volatile
    var hideChromeForRingLauncher: (() -> Unit)? = null

    /** 环状启动器关闭后恢复 chrome。 */
    @Volatile
    var restoreChromeAfterRingLauncher: (() -> Unit)? = null

    /** 启动器面板活跃期间抑制触摸宿主。 */
    @Volatile
    var suppressTouchHostsForActiveLauncherOverlay: (() -> Unit)? = null

    /** 关闭暂存浮窗（语音录制开始/结束时会用到）。 */
    var dismissStashPanel: (() -> Unit)? = null

    /** 浮球是否正在显示。 */
    var isShowing: (() -> Boolean)? = null

    /** 面板打开后把浮球 chrome 抬到面板之上（可带延迟）。 */
    var scheduleChromeAbovePanels: ((Long) -> Unit)? = null

    /** 浮层布局度量：density 与屏幕宽度。 */
    @Volatile
    var overlayLayoutMetrics: ((AppSettings) -> Pair<Float, Float>)? = null
}
