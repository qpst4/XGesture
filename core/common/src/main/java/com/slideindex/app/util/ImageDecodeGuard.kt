package com.slideindex.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 用户图片进入内存/绘制层之前的统一限幅。
 *
 * 背景：Android 的 `RecordingCanvas` 会拒绝把超过 100MB 的位图记进显示列表
 * （`Canvas: trying to draw too large(...) bitmap`），而且 Compose 把大图缩小显示
 * 并不会改变源位图大小。唯一可靠的做法是**解码时就采样**。
 *
 * 这里不处理业务语义，只提供三个尺寸：
 * - [MAX_DISPLAY_SIDE_PX]：钉图/预览等普通显示；
 * - [MAX_ICON_SIDE_PX]：图标预览/落盘；
 * - [MAX_EDITOR_SIDE_PX]：图片编辑器源图（编辑需要比显示更大的尺寸）。
 */
object ImageDecodeGuard {

    const val MAX_DISPLAY_SIDE_PX = 2048
    const val MAX_ICON_SIDE_PX = 256
    const val MAX_EDITOR_SIDE_PX = 4096

    /** 单张位图给显示层的上限；系统硬限制是 100MB，这里留出余量。 */
    private const val MAX_DRAW_BYTES = 64L * 1024L * 1024L

    fun sampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        if (width <= 0 || height <= 0 || maxSidePx <= 0) return 1
        var sample = 1
        while (width / sample > maxSidePx || height / sample > maxSidePx) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    fun decodeUri(
        context: Context,
        uri: Uri,
        maxSidePx: Int,
        mutable: Boolean = false,
    ): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        decodeSource(source, maxSidePx, mutable)
    }.getOrNull()

    fun decodeFile(
        path: String,
        maxSidePx: Int,
        mutable: Boolean = false,
    ): Bitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            decodeSource(ImageDecoder.createSource(file), maxSidePx, mutable)
        }.getOrNull()
    }

    /**
     * 已经解出来的位图如果仍然超过显示预算，就返回一张缩小后的副本。
     *
     * 只用于兜底（例如图片来自别的模块、不能改解码参数时）；正常路径应直接用
     * [decodeUri] / [decodeFile] 采样，避免先分配一张 196MB 的原图。
     */
    fun fitForDisplay(bitmap: Bitmap, maxSidePx: Int = MAX_DISPLAY_SIDE_PX): Bitmap? {
        if (bitmap.isRecycled) return null
        val maxDim = max(bitmap.width, bitmap.height)
        val overSide = maxDim > maxSidePx
        val overBytes = bitmap.byteCount > MAX_DRAW_BYTES
        if (!overSide && !overBytes) return bitmap

        val sideScale = maxSidePx.toFloat() / maxDim.coerceAtLeast(1)
        val pixelCount = bitmap.width.toLong() * bitmap.height.toLong()
        val byteScale = if (pixelCount > 0) {
            sqrt(MAX_DRAW_BYTES.toDouble() / (pixelCount * 4.0)).toFloat()
        } else {
            1f
        }
        val scale = minOf(sideScale, byteScale, 1f)
        val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(bitmap, targetW, targetH, true) }.getOrNull()
    }

    private fun decodeSource(
        source: ImageDecoder.Source,
        maxSidePx: Int,
        mutable: Boolean,
    ): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = mutable
        val maxDim = max(info.size.width, info.size.height)
        if (maxDim > maxSidePx) {
            decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height, maxSidePx))
        }
    }
}
