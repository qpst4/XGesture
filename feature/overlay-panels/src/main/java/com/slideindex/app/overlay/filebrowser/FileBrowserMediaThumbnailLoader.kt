package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MediaStore 内容的缩略图缓存；图片/视频都走 ContentResolver.loadThumbnail。 */
object FileBrowserMediaThumbnailLoader {
    private const val MAX_EDGE_PX = 256
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 10L).toInt().coerceAtLeast(4 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        cache.get(key)?.let { return@withContext it }
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    uri,
                    Size(MAX_EDGE_PX, MAX_EDGE_PX),
                    null,
                )
            } else {
                null
            }
        }.getOrNull()
        bitmap?.let { cache.put(key, it) }
        bitmap
    }
}
