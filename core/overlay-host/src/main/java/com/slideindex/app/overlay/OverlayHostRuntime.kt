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
     * 无障碍服务的 `Class`（`:app` 的 `SlideIndexAccessibilityService::class.java`）。
     *
     * 权限工具要用它拼 `ComponentName` 去查已启用服务列表；模块不认识服务类，未接入时按"未启用"处理。
     */
    @Volatile
    var accessibilityServiceClass: Class<*>? = null

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

    /**
     * 浮层内容的主题包装（`:app` 注入 `OverlayAwareModuleTheme`）。
     *
     * 用接口而不是可组合函数类型：`@Composable` 的 SAM 转换在接口上更稳，
     * 而且实现方可以写成匿名对象。未注入时直接渲染内容（无主题包装）。
     */
    @Volatile
    var themeWrapper: OverlayThemeWrapper? = null

    /** 诊断快照（无障碍连接 / 浮层就绪 / 前台包名）；未接入时返回 null。 */
    @Volatile
    var diagnosticsInfo: (() -> DiagnosticsInfo?)? = null

    /** LSPosed 模块回执状态文本；未接入时返回 null。 */
    @Volatile
    var moduleBridgeText: ((Context) -> String)? = null
}

/** 见 [OverlayHostRuntime.diagnosticsInfo]。 */
data class DiagnosticsInfo(
    val serviceConnected: Boolean,
    val overlayReady: Boolean,
    val foregroundPackage: String?,
)

/** 见 [OverlayHostRuntime.themeWrapper]。 */
fun interface OverlayThemeWrapper {
    @androidx.compose.runtime.Composable
    fun wrap(content: @androidx.compose.runtime.Composable () -> Unit)
}
