package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import com.slideindex.app.util.ImageDecodeGuard

object FloatBallImageLoader {

    /**
     * 悬浮球图片按球的实际显示尺寸采样。
     *
     * 以前这里没有设置 target size：用户选一张 7K 壁纸做悬浮球，ImageDecoder 会解出
     * 196MB 的位图，Compose 再把它交给 RecordingCanvas 时直接抛
     * `Canvas: trying to draw too large(...)`。球最大也就几百 px，1024 足够。
     */
    private const val MAX_BALL_IMAGE_SIDE_PX = 1024

    fun loadBitmap(context: Context, uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        val uri = FloatBallStyleAssetStore.resolveReadableUri(context, uriString) ?: return null
        return ImageDecodeGuard.decodeUri(
            context = context,
            uri = uri,
            maxSidePx = MAX_BALL_IMAGE_SIDE_PX,
            mutable = false,
        )
    }

    /** Decode once per URI; caller owns animation start/stop for animated drawables. */
    fun loadDrawable(context: Context, uriString: String): Drawable? {
        if (uriString.isBlank()) return null
        val uri = FloatBallStyleAssetStore.resolveReadableUri(context, uriString) ?: return null
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val maxDim = maxOf(info.size.width, info.size.height)
                if (maxDim > MAX_BALL_IMAGE_SIDE_PX) {
                    val target = MAX_BALL_IMAGE_SIDE_PX.toFloat() / maxDim
                    val width = (info.size.width * target).toInt().coerceAtLeast(1)
                    val height = (info.size.height * target).toInt().coerceAtLeast(1)
                    decoder.setTargetSize(width, height)
                }
            }
        }.getOrNull()
    }
}
