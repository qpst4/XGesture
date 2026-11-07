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
}
