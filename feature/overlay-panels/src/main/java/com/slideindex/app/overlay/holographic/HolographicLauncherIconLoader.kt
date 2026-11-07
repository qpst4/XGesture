package com.slideindex.app.overlay.holographic

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.slideindex.app.data.AppRepository
import java.util.concurrent.Executors

/**
 * 全息启动器的图标后台补齐。
 *
 * 为什么必须后台做：`HolographicLauncherOverlayWindow.show` 跑在主线程，而魅族/小米等 OEM 的
 * `PackageManager.getApplicationIcon` 会走主题图标重建（Flyme: `FlymeThemeHelper.cropTransparentSpace`
 * 逐像素 `Bitmap.getPixel`），单次可达秒级 —— 球面要几百个图标，同步解析必然把触摸事件卡到 ANR
 * （见 `OverlayIconLoader` 里记录的那次 `Input dispatching timed out` 事故）。
 *
 * 契约与 `HoneycombIconLoader` 一致：只补齐 `icon == null` 的项，补齐后回调一次，由调用方重排球面。
 */
internal object HolographicLauncherIconLoader {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HolographicIcons").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadMissingIconsAsync(
        context: Context,
        appRepository: AppRepository,
        apps: List<HolographicLauncherApp>,
        onIconsReady: () -> Unit,
    ) {
        val missing = apps.filter { it.icon == null }
        if (missing.isEmpty()) return
        worker.execute {
            var updated = false
            for (app in missing) {
                // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）
                val drawable = com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideDrawable
                    ?.invoke(context, app.packageName, 128)
                    ?: appRepository.launchIconDrawable(app.packageName)
                    ?: continue
                app.icon = com.slideindex.app.util.CloneBadge.wrap(drawable, app.cloneUserId > 0) ?: drawable
                updated = true
            }
            if (updated) {
                mainHandler.post(onIconsReady)
            }
        }
    }
}
