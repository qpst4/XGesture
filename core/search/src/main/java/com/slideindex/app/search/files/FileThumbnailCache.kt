package com.slideindex.app.search.files

import androidx.compose.ui.graphics.ImageBitmap

object FileThumbnailCache {
    private const val MAX_ENTRIES = 64
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(uri: String): ImageBitmap? = cache[uri]

    @Synchronized
    fun put(uri: String, bitmap: ImageBitmap) {
        cache[uri] = bitmap
    }
}
