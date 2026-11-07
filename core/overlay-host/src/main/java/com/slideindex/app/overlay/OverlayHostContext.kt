package com.slideindex.app.overlay

import android.content.Context

/**
 * 无障碍宿主 context（即 `SlideIndexAccessibilityService.overlayHostContext()`）。
 *
 * 这是 `:app` 的 `OverlayDependencyAccess.overlayHostContext()` 的模块内替代：
 * 面板只要宿主 context，不该为此依赖 DI 门面。未接入无障碍服务时返回 null。
 */
fun overlayHostContext(): Context? = OverlayHostRuntime.accessibilityHostContext?.invoke()
