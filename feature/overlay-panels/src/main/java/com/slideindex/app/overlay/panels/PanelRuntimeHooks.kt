package com.slideindex.app.overlay.panels

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.slideindex.app.settings.AppSettings

/**
 * 面板模块（`:feature:overlay-panels`）与 `:app` 的接线点。
 *
 * 面板需要"执行特权动作 / 读最近任务 / 起小窗"这些 app 核心能力，但模块不能依赖 `:app`。
 * 这里用回调把它们反转：`SlideIndexApp` 启动时注入，未注入时全部降级为"不可用"。
 */
object PanelRuntimeHooks {

    /** 无障碍服务实例（`performGlobalAction` 等要用）。 */
    @Volatile
    var accessibilityService: (() -> AccessibilityService?)? = null

    /** 最近任务标识（`pkg/activity` 形式），拿不到时返回 null。 */
    @Volatile
    var recentTaskIdentifiers: (() -> List<String>?)? = null

    /** 强停应用；返回是否成功。 */
    @Volatile
    var forceStopPackage: ((String) -> Boolean)? = null

    /** 从最近任务列表里移除这些包。 */
    @Volatile
    var removeRecentPackages: ((Collection<String>) -> Unit)? = null

    /** 以小窗启动应用；`onSessionEnd` 在小窗结束时回调。 */
    @Volatile
    var launchFreeWindow: ((Context, String, AppSettings, (() -> Unit)?) -> Unit)? = null

    /** 应用图标覆盖（用户自定义图标）；返回 null 时用系统图标。 */
    @Volatile
    var iconOverrideBitmap: ((Context, String, Int) -> android.graphics.Bitmap?)? = null

    /** 同上，但返回 Drawable（全息启动器的球面图标用）。 */
    @Volatile
    var iconOverrideDrawable: ((Context, String, Int) -> android.graphics.drawable.Drawable?)? = null

    /** 应用仓库（`:feature:apps`）；面板读应用名/图标用。 */
    @Volatile
    var appRepository: ((Context) -> com.slideindex.app.data.AppRepository?)? = null

    /** 分身应用显示名前缀（如「微信 · 分身 2」）。 */
    @Volatile
    var cloneAppLabel: ((Context, Int, String) -> String)? = null

    /**
     * 取"模糊后的壁纸"给面板做背景；返回缓存命中时的位图，未命中时异步回调。
     *
     * 参数：无障碍服务、应用 context、模糊半径 dp、回调。
     */
    @Volatile
    var captureBlurredWallpaper:
        ((android.accessibilityservice.AccessibilityService, Context, Int, WallpaperBlurCallback) -> android.graphics.Bitmap?)? =
        null
}

/** 见 [PanelRuntimeHooks.captureBlurredWallpaper]。 */
fun interface WallpaperBlurCallback {
    fun onReady(bitmap: android.graphics.Bitmap?)
}
