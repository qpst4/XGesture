package com.slideindex.app.overlay

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Loads the system wallpaper and applies a stack blur (shared by search panel + honeycomb).
 * 对齐 quick-search：优先 [WallpaperManager.getDrawable]，异步友好；失败返回 null，由调用方降级。
 */
object SystemWallpaperBlurHelper {
    private const val TAG = "SystemWallpaperBlur"
    private const val DOWNSAMPLE = 6
    private const val MAX_SOURCE_DIMENSION = 2400
    private const val MAX_SOURCE_PIXELS = 4_000_000
    private val cached = AtomicReference<CacheEntry?>(null)

    data class CacheEntry(
        val wallpaperId: Int?,
        val width: Int,
        val height: Int,
        val radius: Int,
        val bitmap: Bitmap
    )

    /**
     * 读取系统壁纸需要「所有文件访问」（MANAGE_EXTERNAL_STORAGE）。
     */
    fun hasWallpaperAccessPermission(context: Context): Boolean =
        Environment.isExternalStorageManager()

    /**
     * 打开系统「所有文件访问权限」页面。
     * 若 context 不是 Activity，添加 FLAG_ACTIVITY_NEW_TASK。
     */
    fun requestWallpaperPermission(context: Context) {
        if (hasWallpaperAccessPermission(context)) return
        val isActivity = context is android.app.Activity
        fun Intent.applyContextFlags(): Intent = apply {
            if (!isActivity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val appSpecificIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri()
        ).applyContextFlags()

        val generalIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
        ).applyContextFlags()

        runCatching {
            context.startActivity(appSpecificIntent)
        }.onFailure {
            runCatching {
                context.startActivity(generalIntent)
            }
        }
    }

    fun hasCached(width: Int, height: Int, radius: Int): Bitmap? {
        val entry = cached.get() ?: return null
        if (entry.width != width || entry.height != height || entry.radius != radius) return null
        if (entry.bitmap.isRecycled) return null
        return entry.bitmap
    }

    /** 仅查内存缓存，不触发解码（供主线程首帧）。 */
    @JvmStatic
    fun peekCachedBlurred(context: Context, blurDp: Int): Bitmap? {
        val metrics = context.resources.displayMetrics
        val width = max(48, metrics.widthPixels / DOWNSAMPLE)
        val height = max(96, metrics.heightPixels / DOWNSAMPLE)
        val radius = blurRadiusForDp(blurDp)
        val wallpaperId = currentWallpaperId(context)
        val cachedBmp = hasCached(width, height, radius) ?: return null
        val entry = cached.get() ?: return null
        if (wallpaperId != null && entry.wallpaperId != null && entry.wallpaperId != wallpaperId) {
            return null
        }
        return cachedBmp
    }

    fun invalidate() {
        cached.set(null)
    }

    suspend fun loadBlurred(
        context: Context,
        blurDp: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        loadBlurredInternal(context.applicationContext, blurDp)
    }

    /** 可在后台线程调用；勿在主线程做首次解码。 */
    @JvmStatic
    fun loadBlurredSync(context: Context, blurDp: Int): Bitmap? =
        loadBlurredInternal(context.applicationContext, blurDp)

    private fun loadBlurredInternal(context: Context, blurDp: Int): Bitmap? {
        val metrics = context.resources.displayMetrics
        val width = max(48, metrics.widthPixels / DOWNSAMPLE)
        val height = max(96, metrics.heightPixels / DOWNSAMPLE)
        val radius = blurRadiusForDp(blurDp)
        val wallpaperId = currentWallpaperId(context)
        hasCached(width, height, radius)?.let { cachedBmp ->
            val entry = cached.get()
            if (entry != null &&
                (wallpaperId == null || entry.wallpaperId == null || entry.wallpaperId == wallpaperId)
            ) {
                return cachedBmp
            }
        }
        val source = loadWallpaperBitmap(context) ?: return null
        return try {
            val blurred = renderAndBlur(source, width, height, radius)
            cached.set(CacheEntry(wallpaperId, width, height, radius, blurred))
            blurred
        } catch (error: Throwable) {
            Log.w(TAG, "renderAndBlur failed", error)
            null
        } finally {
            if (!source.isRecycled) {
                source.recycle()
            }
        }
    }

    private fun currentWallpaperId(context: Context): Int? =
        runCatching {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull()

    /**
     * 仅加载系统桌面壁纸，禁止走无障碍截屏/当前页面内容。
     * 顺序对齐 quick-search：drawable → fastDrawable →（有权限时）wallpaper file。
     */
    private fun loadWallpaperBitmap(context: Context): Bitmap? {
        val wm = WallpaperManager.getInstance(context)
        try {
            wm.drawable?.let { drawableToBitmap(it) }?.let { return it }
        } catch (error: SecurityException) {
            Log.w(TAG, "WallpaperManager.drawable SecurityException (need files access?)", error)
        } catch (error: Exception) {
            Log.w(TAG, "WallpaperManager.drawable failed", error)
        }
        try {
            wm.fastDrawable?.let { drawableToBitmap(it) }?.let { return it }
        } catch (error: SecurityException) {
            Log.w(TAG, "WallpaperManager.fastDrawable SecurityException", error)
        } catch (error: Exception) {
            Log.w(TAG, "WallpaperManager.fastDrawable failed", error)
        }
        runCatching {
            wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            }
        }.onFailure { Log.w(TAG, "getWallpaperFile(FLAG_SYSTEM) failed", it) }
            .getOrNull()
            ?.takeUnless { it.isRecycled }
            ?.let { return it }
        Log.w(
            TAG,
            "Unable to decode system wallpaper bitmap (filesAccess=${hasWallpaperAccessPermission(context)})"
        )
        return null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? =
        runCatching {
            val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1920
            val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1080
            val scale = minOf(
                1f,
                MAX_SOURCE_DIMENSION.toFloat() / sourceWidth,
                MAX_SOURCE_DIMENSION.toFloat() / sourceHeight,
                sqrt(
                    MAX_SOURCE_PIXELS.toFloat() / (sourceWidth.toFloat() * sourceHeight.toFloat())
                ).coerceAtMost(1f)
            )
            val width = max(1, (sourceWidth * scale).roundToInt())
            val height = max(1, (sourceHeight * scale).roundToInt())
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        }.onFailure { Log.w(TAG, "Failed to rasterize wallpaper drawable", it) }
            .getOrNull()

    private fun renderAndBlur(source: Bitmap, width: Int, height: Int, radius: Int): Bitmap {
        val result = createBitmap(width, height)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        if (radius > 0) {
            stackBlur(result, radius, 2)
        }
        return result
    }

    private fun blurRadiusForDp(blurDp: Int): Int =
        if (blurDp <= 0) 0 else max(1, (blurDp / DOWNSAMPLE.toFloat()).roundToInt())

    private fun stackBlur(bitmap: Bitmap, radius: Int, iterations: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        val target = IntArray(source.size)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        repeat(iterations) {
            blurHorizontal(source, target, width, height, radius)
            blurVertical(target, source, width, height, radius)
        }
        bitmap.setPixels(source, 0, width, 0, 0, width, height)
    }

    private fun blurHorizontal(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val samples = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (offset in -radius..radius) {
                val color = source[row + clamp(offset, 0, width - 1)]
                a += color ushr 24
                r += (color shr 16) and 255
                g += (color shr 8) and 255
                b += color and 255
            }
            for (x in 0 until width) {
                target[row + x] = pack(a, r, g, b, samples)
                val remove = source[row + clamp(x - radius, 0, width - 1)]
                val add = source[row + clamp(x + radius + 1, 0, width - 1)]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add shr 16) and 255) - ((remove shr 16) and 255)
                g += ((add shr 8) and 255) - ((remove shr 8) and 255)
                b += (add and 255) - (remove and 255)
            }
        }
    }

    private fun blurVertical(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val samples = radius * 2 + 1
        for (x in 0 until width) {
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (offset in -radius..radius) {
                val color = source[clamp(offset, 0, height - 1) * width + x]
                a += color ushr 24
                r += (color shr 16) and 255
                g += (color shr 8) and 255
                b += color and 255
            }
            for (y in 0 until height) {
                target[y * width + x] = pack(a, r, g, b, samples)
                val remove = source[clamp(y - radius, 0, height - 1) * width + x]
                val add = source[clamp(y + radius + 1, 0, height - 1) * width + x]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add shr 16) and 255) - ((remove shr 16) and 255)
                g += ((add shr 8) and 255) - ((remove shr 8) and 255)
                b += (add and 255) - (remove and 255)
            }
        }
    }

    private fun pack(a: Long, r: Long, g: Long, b: Long, samples: Int): Int =
        ((a / samples).toInt() shl 24) or
            ((r / samples).toInt() shl 16) or
            ((g / samples).toInt() shl 8) or
            (b / samples).toInt()

    private fun clamp(value: Int, minimum: Int, maximum: Int): Int =
        maxOf(minimum, minOf(maximum, value))
}
