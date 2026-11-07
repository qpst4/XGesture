package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable

object FloatBallImageLoader {
    fun loadBitmap(context: Context, uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        val uri = FloatBallStyleAssetStore.resolveReadableUri(context, uriString) ?: return null
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull()
    }

    /** Decode once per URI; caller owns animation start/stop for animated drawables. */
    fun loadDrawable(context: Context, uriString: String): Drawable? {
        if (uriString.isBlank()) return null
        val uri = FloatBallStyleAssetStore.resolveReadableUri(context, uriString) ?: return null
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeDrawable(source)
        }.getOrNull()
    }
}