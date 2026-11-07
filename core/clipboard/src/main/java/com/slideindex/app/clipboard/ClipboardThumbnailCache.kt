package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

/**
 * LRU cache for clipboard list preview thumbnails so [LazyColumn] recycle does not re-decode.
 */
object ClipboardThumbnailCache {
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024

    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun evictAll() {
        bitmapCache.evictAll()
    }

    fun loadEntryThumbnailsForPreview(
        context: Context,
        entry: ClipboardEntry,
        maxSidePx: Int
    ): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { fileName ->
                getOrLoadFile(context, fileName, maxSidePx)
            }
        }
        if (!entry.hasImageContent() || entry.uri.isNullOrBlank()) return emptyList()
        return getOrLoadUri(context, entry.uri, maxSidePx)?.let { listOf(it) } ?: emptyList()
    }

    fun loadBlockThumbnail(context: Context, fileName: String, maxSidePx: Int): Bitmap? =
        getOrLoadFile(context, fileName, maxSidePx)

    fun loadEntryThumbnailsForCard(
        context: Context,
        entry: ClipboardEntry,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { fileName ->
                getOrLoadFileForCard(context, fileName, targetWidthPx, maxVisibleHeightPx)
            }
        }
        if (!entry.hasImageContent() || entry.uri.isNullOrBlank()) return emptyList()
        return getOrLoadUriForCard(context, entry.uri, targetWidthPx, maxVisibleHeightPx)
            ?.let { listOf(it) }
            ?: emptyList()
    }

    fun loadBlockThumbnailForCard(
        context: Context,
        fileName: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? = getOrLoadFileForCard(context, fileName, targetWidthPx, maxVisibleHeightPx)

    fun evictEntry(entry: ClipboardEntry) {
        val fileNames = entry.resolvedImageFileNames()
        val uri = entry.uri?.takeIf { it.isNotBlank() }
        val keysToRemove = bitmapCache.snapshot().keys.filter { key ->
            fileNames.any { fileName -> key.startsWith("file:$fileName:") } ||
                (uri != null && key.startsWith("uri:$uri:"))
        }
        keysToRemove.forEach { bitmapCache.remove(it) }
    }

    fun clear() {
        bitmapCache.evictAll()
    }

    private fun getOrLoadFile(context: Context, fileName: String, maxSidePx: Int): Bitmap? {
        val key = fileKey(fileName, maxSidePx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadBitmapScaled(context, fileName, maxSidePx) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun getOrLoadUri(context: Context, uri: String, maxSidePx: Int): Bitmap? {
        val key = uriKey(uri, maxSidePx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadUriBitmapScaled(context, uri, maxSidePx) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun getOrLoadFileForCard(
        context: Context,
        fileName: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? {
        val key = fileCardKey(fileName, targetWidthPx, maxVisibleHeightPx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadThumbnailForCard(
            context,
            fileName,
            targetWidthPx,
            maxVisibleHeightPx
        ) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun getOrLoadUriForCard(
        context: Context,
        uri: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? {
        val key = uriCardKey(uri, targetWidthPx, maxVisibleHeightPx)
        bitmapCache.get(key)?.let { return it }
        val loaded = ClipboardImageStore.loadUriThumbnailForCard(
            context,
            uri,
            targetWidthPx,
            maxVisibleHeightPx
        ) ?: return null
        bitmapCache.put(key, loaded)
        return loaded
    }

    private fun fileKey(fileName: String, maxSidePx: Int = 0): String = "file:$fileName:$maxSidePx"

    private fun uriKey(uri: String, maxSidePx: Int = 0): String = "uri:$uri:$maxSidePx"

    private fun fileCardKey(fileName: String, targetWidthPx: Int, maxVisibleHeightPx: Int): String =
        "file:$fileName:w$targetWidthPx:h$maxVisibleHeightPx"

    private fun uriCardKey(uri: String, targetWidthPx: Int, maxVisibleHeightPx: Int): String =
        "uri:$uri:w$targetWidthPx:h$maxVisibleHeightPx"
}
