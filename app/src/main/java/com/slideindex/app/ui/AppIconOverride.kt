package com.slideindex.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.slideindex.app.iconpack.IconPackDrawableLoader
import com.slideindex.app.iconpack.IconPackRepository
import com.slideindex.app.iconpack.IconThemeStore

/**
 * 「应用图标」全局覆盖的**运行时入口**。
 *
 * 解析顺序（三层）：
 * 1. **例外**：图标库里给这个应用单独设的图标（`GlobalIconStore.appIcon`，落盘 PNG / 文字 / 字体）；
 * 2. **主题**：设置里选中的图标包（`IconThemeStore.pack`），按 appfilter 匹配后**实时**从包里取 drawable；
 * 3. 都没有 → 返回 null，调用方走系统图标。
 *
 * 与 [ActionIconBitmap] 的分工：
 * - 动作图标的渲染入口天然收敛（所有面板最后都调 `ActionIconBitmap`），所以接一处就全覆盖；
 * - 应用图标没有这种单点：索引/任务切换器走 `AppRepository`，面板走各自的 LRU，
 *   有的直接 `pm.getApplicationIcon`。所以这里提供一个统一入口，让各渲染点
 *   「先问这里，拿不到再走原逻辑」。
 *
 * `AppRepository` 也通过 `AppIconOverrideProvider` 端口转到这里，
 * 走它的路径（索引/圆环/蜂窝/全息/轮盘）即使忘了显式调用也能生效。
 *
 * ## 作用范围（写死，不要扩大）
 *
 * 只作用于**应用启动图标**：桌面、面板、索引、最近任务里代表这个应用的那张图。
 * 以下明确**不**走这里，必要时各走各的体系：
 * - 通知栏图标（系统提供）
 * - 搜索引擎自己的图标配置
 * - 应用内 Activity 图标、快捷方式的专属图标（只有回退到宿主应用图标时才用）
 *
 * 新增取「应用启动图标」的代码**必须走这里**，不要直接调 `PackageManager.getApplicationIcon`。
 *
 * ## 线程与缓存
 *
 * - [bitmap] / [drawable] **会解码**，只能在 IO 线程调用；
 * - [peek] / [peekDrawable] **只读缓存**，主线程安全；
 * - 缓存 key 带 `GlobalIconStore.revision` + `IconThemeStore.revision`，改完图标/主题立刻失效；
 * - 主题层的 drawable 不落盘（否则换包、包更新都不跟随），所以**面板打开前建议先 [preload]**。
 */
object AppIconOverride {

    private const val MAX_CACHE_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun clear() {
        cache.evictAll()
    }

    // ---------------------------------------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------------------------------------

    /**
     * 只读缓存版本：**主线程安全**，不会解码、不会解析。
     *
     * 给「每帧绘制」的 Canvas 路径用；未命中时返回 null，由调用方决定先画占位还是回退原图。
     */
    fun peek(
        context: Context,
        packageName: String,
        sizePx: Int,
        withPlate: Boolean = false,
    ): Bitmap? {
        if (packageName.isBlank() || sizePx <= 0) return null
        if (GlobalIconStore.appIcon(context, packageName) != null) {
            return cache.get(overrideCacheKey(packageName, sizePx, withPlate))
        }
        val pack = IconThemeStore.pack(context) ?: return null
        return cache.get(themeCacheKey(pack, packageName, sizePx))
    }

    /**
     * @return 覆盖/主题后的图标；null 表示这个应用既没例外、也没被当前主题收录。
     *
     * **会解码 PNG 或从图标包取 drawable，只能在 IO 线程调用**。
     */
    fun bitmap(
        context: Context,
        packageName: String,
        sizePx: Int,
        withPlate: Boolean = false,
    ): Bitmap? {
        if (packageName.isBlank() || sizePx <= 0) return null

        val overrideKey = GlobalIconStore.appIcon(context, packageName)
        if (overrideKey != null) {
            return renderOverride(context, packageName, overrideKey, sizePx, withPlate)
        }
        return themeBitmap(context, packageName, sizePx)
    }

    /** Drawable 版本：给只接受 Drawable 的老渲染路径（蜂窝、全息、快捷方式等）。 */
    fun drawable(context: Context, packageName: String, sizePx: Int): Drawable? =
        bitmap(context, packageName, sizePx)?.let { BitmapDrawable(context.resources, it) }

    /** 只读缓存的 Drawable 版本，主线程安全。 */
    fun peekDrawable(context: Context, packageName: String, sizePx: Int): Drawable? =
        peek(context, packageName, sizePx)?.let { BitmapDrawable(context.resources, it) }

    /**
     * 预热：把一批应用的图标先解到缓存里，避免面板首帧在主线程解码。
     *
     * 必须在 IO 线程调用。已经命中缓存的不重复解码。
     */
    fun preload(context: Context, packageNames: Collection<String>, sizePx: Int) {
        if (sizePx <= 0) return
        for (packageName in packageNames) {
            if (packageName.isBlank()) continue
            if (peek(context, packageName, sizePx) != null) continue
            runCatching { bitmap(context, packageName, sizePx) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------------------------------------

    private fun renderOverride(
        context: Context,
        packageName: String,
        key: String,
        sizePx: Int,
        withPlate: Boolean,
    ): Bitmap? {
        val cacheKey = overrideCacheKey(packageName, sizePx, withPlate)
        cache.get(cacheKey)?.let { return it }
        val dark = IconPlateModeState.isDark(context)
        val bitmap = ActionIconBitmap.loadByKey(
            context = context,
            key = key,
            sizePx = sizePx,
            tintArgb = if (dark) Color.WHITE else Color.BLACK,
            withPlate = withPlate,
            darkPlate = dark,
        ) ?: return null
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun themeBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? {
        val pack = IconThemeStore.pack(context) ?: return null
        val cacheKey = themeCacheKey(pack, packageName, sizePx)
        cache.get(cacheKey)?.let { return it }

        val filter = IconPackRepository.filter(context, pack) ?: return null
        val activity = IconPackRepository.launchActivityName(context, packageName)
        val drawableName = filter.resolve(packageName, activity) ?: return null
        val bitmap = IconPackDrawableLoader.load(context, pack, drawableName, sizePx) ?: return null
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun overrideCacheKey(packageName: String, sizePx: Int, withPlate: Boolean): String =
        "o|$packageName|$sizePx|$withPlate|${GlobalIconStore.revision}"

    private fun themeCacheKey(pack: String, packageName: String, sizePx: Int): String =
        "t|$pack|$packageName|$sizePx|${IconThemeStore.revision}|${GlobalIconStore.revision}"
}
