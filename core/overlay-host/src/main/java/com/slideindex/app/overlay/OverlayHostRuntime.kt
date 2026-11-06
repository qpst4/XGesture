package com.slideindex.app.overlay

import android.content.Context

/**
 * `:app` 与 `:core:overlay-host` 之间的最小接线点。
 *
 * 这个模块只认识 `WindowManager` / `View`，不认识无障碍服务类（在 `:app`）也不认识权限工具，
 * 所以这两件事由 Application 启动时注入。没注入时按最保守的方式处理：无障碍 overlay 不可用，
 * 窗口类型退回 `TYPE_APPLICATION_OVERLAY`（不会崩，只是少一层类型优先级）。
 */
object OverlayHostRuntime {

    /**
     * 无障碍宿主 context（即 `SlideIndexAccessibilityService.instance`）。
     *
     * 传 lambda 而不是值：服务可能重连，取值要在调用那一刻发生。
     */
    @Volatile
    var accessibilityHostContext: (() -> Context?)? = null

    /** 本 App 的无障碍服务当前是否已启用（决定能否用 `TYPE_ACCESSIBILITY_OVERLAY`）。 */
    @Volatile
    var isAccessibilityServiceEnabled: ((Context) -> Boolean)? = null
}
