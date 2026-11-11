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

    /**
     * 浮层布局的**密度提示**：只给密度，屏宽/屏高由调用方按当前方向自己算。
     *
     * 为什么只给密度：1.64.x 这里回传的是 `(density, screenWidth)` 一整对，调用方一兜底常量
     * 就把假宽度（1080f）和假密度（1f）一起带了进来，圆环整圈按 1dp=1px 画（只有正常的 36%）。
     * 只留一个 `Float?`，宽度在类型上就塞不进来了。
     *
     * 返回 null 表示「没有更好的参考值」（悬浮球窗没建起来），调用方按真实屏幕度量算即可。
     */
    @Volatile
    var layoutDensityHint: ((AppSettings) -> Float?)? = null
}
