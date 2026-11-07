package com.slideindex.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

fun Drawable.toSafeImageBitmap(sizePx: Int = 96): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null && !bitmap.isRecycled) {
        return runCatching { bitmap.asImageBitmap() }.getOrElse {
            createBitmap(1, 1).asImageBitmap()
        }
    }
    return runCatching {
        val safeSize = sizePx.coerceIn(1, 256)
        val bitmap = createBitmap(safeSize, safeSize)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, safeSize, safeSize)
        draw(canvas)
        bitmap.asImageBitmap()
    }.getOrElse {
        createBitmap(1, 1).asImageBitmap()
    }
}
