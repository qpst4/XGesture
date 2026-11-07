package com.slideindex.app.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 暂存编辑器里的图片解码（降采样口径与图片中转 Activity 一致）。
 *
 * 原先定义在 `StashComposerImageTrampolineActivity.kt`；面板与 `:app` 共用，故下沉到这里。
 */
fun decodeStashImageFile(path: String, maxDimension: Int = 2048): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
        sample *= 2
    }
    BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}.getOrNull()
