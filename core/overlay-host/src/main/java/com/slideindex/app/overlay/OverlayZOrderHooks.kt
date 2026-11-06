package com.slideindex.app.overlay

/**
 * z-order 联动的接线点。
 *
 * `compositor`（本模块）负责把浮层分到同一个 z 带，但要抬升的浮球/指针窗口在 `:app` 里，
 * 呈现层是否存活也只有无障碍服务知道。所以这里放四个由 Application 注入的回调；
 * 未注入时全部静默跳过——最坏结果是层级不理想，不会崩。
 */
object OverlayZOrderHooks {

    /** 把悬浮球 chrome 抬到内容面板之上。 */
    @Volatile
    var scheduleChromeAbovePanels: (() -> Unit)? = null

    /** 空闲时恢复 chrome 的常规 z 序。 */
    @Volatile
    var restoreChromeZOrderIfIdle: (() -> Unit)? = null

    /** 把悬浮指针窗口抬到最前。 */
    @Volatile
    var bringFloatingPointerToFront: (() -> Unit)? = null

    /** 无障碍呈现层是否仍然需要把 chrome 抬起（未接入时按 false）。 */
    @Volatile
    var edgePresentationNeedsChromeRaise: (() -> Boolean)? = null

    /** 悬浮球当前是否可见（全屏面板创建时要决定是否把 chrome 抬上去）。 */
    @Volatile
    var isFloatBallShowing: (() -> Boolean)? = null

    /** 面板挂到 chrome 之上时通知悬浮球（它会重排自己的 z 序）。 */
    @Volatile
    var notifyPanelAttachedAboveChrome: (() -> Unit)? = null
}
