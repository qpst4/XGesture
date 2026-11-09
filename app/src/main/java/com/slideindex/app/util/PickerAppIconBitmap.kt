package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PickerAppIconBitmap {
    // 挑应用网格里的图标按 58dp 渲染，3x 屏是 174px，这里取 176px 留余量。
    // 单条约 124KB（176² × 4B），统一按字节淘汰，上限 16MB。
    private const val ICON_SIZE_PX = 176
    private const val MAX_CACHE_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            runCatching { value.asAndroidBitmap().allocationByteCount }
                .getOrDefault(ICON_SIZE_PX * ICON_SIZE_PX * 4)
    }

    /**
     * 缓存 key 带上图标修订号：在图标库里改完应用图标，这里不会再吐旧图。
     *
     * [com.slideindex.app.ui.GlobalIconStore.revision] 是进程级状态，同步读没有副作用。
     */
    private fun cacheKey(packageName: String): String =
        "$packageName@${com.slideindex.app.ui.GlobalIconStore.revision}"

    fun peek(packageName: String): ImageBitmap? = cache.get(cacheKey(packageName))

    fun peekActivity(packageName: String, className: String): ImageBitmap? =
        cache.get(activityCacheKey(packageName, className))

    fun putActivityIcon(packageName: String, className: String, drawable: Drawable) {
        val key = activityCacheKey(packageName, className)
        if (cache.get(key) != null) return
        cache.put(key, drawable.toSafeImageBitmap(ICON_SIZE_PX))
    }

    suspend fun load(context: Context, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            cache.get(cacheKey(packageName))?.let { return@withContext it }
            // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）。
            // 冰箱 / 宏 / 搜索面板的结果卡片都走这里，所以在这一层接一次就全覆盖。
            com.slideindex.app.ui.AppIconOverride
                .bitmap(context, packageName, ICON_SIZE_PX)
                ?.let { override ->
                    val imageBitmap = override.asImageBitmap()
                    cache.put(cacheKey(packageName), imageBitmap)
                    return@withContext imageBitmap
                }
            val pm = context.applicationContext.packageManager
            val drawable = try {
                pm.getApplicationIcon(packageName)
            } catch (_: Exception) {
                return@withContext null
            }
            val bitmap = drawable.toSafeImageBitmap(ICON_SIZE_PX)
            cache.put(cacheKey(packageName), bitmap)
            bitmap
        }

    /** 优先 Activity 图标，失败回退应用图标。 */
    suspend fun loadActivity(context: Context, packageName: String, className: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = activityCacheKey(packageName, className)
            cache.get(key)?.let { return@withContext it }
            val pm = context.applicationContext.packageManager
            val component = ComponentName(packageName, className)
            val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            val drawable = runCatching { pm.getActivityIcon(component) }.getOrNull()
                ?: runCatching { pm.getActivityInfo(component, flags).loadIcon(pm) }.getOrNull()
                ?: runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
                ?: return@withContext null
            val bitmap = drawable.toSafeImageBitmap(ICON_SIZE_PX)
            cache.put(key, bitmap)
            bitmap
        }

    fun clear() {
        cache.evictAll()
    }

    private fun activityCacheKey(packageName: String, className: String): String =
        "$packageName/$className"
}
