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

    /**
     * 浮层 Compose 用的透明主题（`:app` 的 `R.style.Theme_SlideIndex_Transparent`）。
     *
     * 资源 id 不能跨模块引用，由 Application 启动时注入；为 0 时退回系统透明主题。
     */
    @Volatile
    var overlayThemeResId: Int = 0

    /**
     * 给浮层 context 套上应用内语言（`:app` 的 `AppLocaleApplier.wrapOverlayContext`）。
     *
     * 模块不认识语言设置，未注入时原样返回。
     */
    @Volatile
    var wrapOverlayLocale: ((Context) -> Context)? = null

    /**
     * 全局模糊开关的流（`:app` 从设置仓库取）。
     *
     * 模块里的 `rememberOverlayBlurEnabled()` 用它；未接入时按"开启"处理，
     * 与设置项默认值一致，避免启动瞬间闪一下实色面板。
     */
    @Volatile
    var overlayBlurEnabledFlow: ((Context) -> kotlinx.coroutines.flow.Flow<Boolean>?)? = null
}
