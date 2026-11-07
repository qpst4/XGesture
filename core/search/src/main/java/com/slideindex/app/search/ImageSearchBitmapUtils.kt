package com.slideindex.app.search

/*
 * Portions derived from Circle To Search (https://github.com/AKS-Labs/CircleToSearch)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.graphics.Bitmap
import androidx.core.graphics.scale

internal object ImageSearchBitmapUtils {
    fun resizeForUpload(source: Bitmap, maxLength: Int = 1280): Bitmap {
        if (source.width <= maxLength && source.height <= maxLength) return source
        val aspectRatio = source.width.toDouble() / source.height.toDouble()
        val targetWidth = if (aspectRatio >= 1) maxLength else (maxLength * aspectRatio).toInt()
        val targetHeight = if (aspectRatio < 1) maxLength else (maxLength / aspectRatio).toInt()
        return source.scale(targetWidth, targetHeight)
    }
}
