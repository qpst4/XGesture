package com.slideindex.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 小组件面板里应用图标的缓存 + 后台加载。
 *
 * 面板卡片原先在主线程直接调 [android.content.pm.PackageManager.getApplicationIcon]，而部分 OEM
 * （如 Flyme）会在该调用里做主题化重绘：逐像素 `Bitmap.getPixel` 裁边，单个图标就能吃掉几百毫秒，
 * 面板首帧因此被卡住（实测单帧 979ms / 1011ms）。统一改到后台线程并缓存结果。
 */
internal object WidgetAppIconCache {
  private const val ICON_PX = 132
  private const val MAX_ENTRIES = 32

  private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {}
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mainHandler = Handler(Looper.getMainLooper())

  fun clear() {
    cache.evictAll()
  }

  fun peek(packageName: String): Bitmap? =
    if (packageName.isBlank()) null else cache.get(packageName)

  /**
   * 已缓存则同步回调，否则后台加载后回调。回调始终在主线程，
   * 且 [onLoaded] 由调用方自行校验视图是否仍然有效。
   */
  fun load(context: Context, packageName: String, onLoaded: (Bitmap) -> Unit) {
    if (packageName.isBlank()) return
    peek(packageName)?.let { cached ->
      onLoaded(cached)
      return
    }
    val appContext = context.applicationContext
    scope.launch {
      // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）；覆盖图不写 LruCache，
      // 否则改完图标后这个按包名缓存的旧图会一直返回。
      com.slideindex.app.ui.AppIconOverride.bitmap(appContext, packageName, ICON_PX)?.let { override ->
        mainHandler.post { onLoaded(override) }
        return@launch
      }
      val bitmap = runCatching {
        appContext.packageManager.getApplicationIcon(packageName)
          .toBitmap(width = ICON_PX, height = ICON_PX)
      }.getOrNull() ?: return@launch
      cache.put(packageName, bitmap)
      mainHandler.post { onLoaded(bitmap) }
    }
  }
}
