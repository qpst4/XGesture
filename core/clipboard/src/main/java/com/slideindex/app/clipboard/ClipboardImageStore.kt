package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ClipboardImageStore {
    private const val DIR_NAME = "clipboard"
    private const val IMAGE_DIR_NAME = "images"

    fun imageDir(context: Context): File =
        File(context.filesDir, "$DIR_NAME/$IMAGE_DIR_NAME").apply { mkdirs() }

    fun imageFile(context: Context, fileName: String): File =
        File(imageDir(context), fileName)

    fun fileNameForIndex(entryId: String, index: Int, total: Int): String =
        if (total == 1) "$entryId.png" else "${entryId}_$index.png"

    fun persistImageBytes(context: Context, entryId: String, bytes: ByteArray, index: Int, total: Int): String? {
        if (bytes.isEmpty()) return null
        val fileName = fileNameForIndex(entryId, index, total)
        val file = imageFile(context, fileName)
        return runCatching {
            file.writeBytes(bytes)
            fileName
        }.getOrNull()
    }

    fun persistFromUri(context: Context, entryId: String, uriString: String, index: Int, total: Int): String? {
        return runCatching {
            val uri = uriString.toUri()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isNotEmpty()) {
                    return@runCatching persistImageBytes(context, entryId, bytes, index, total)
                }
            }
            null
        }.getOrNull()
    }

    fun persistFromSource(context: Context, entryId: String, index: Int, total: Int, src: String): String? {
        val normalized = ClipboardHtmlParser.normalizeImageSrc(src.trim())
        if (normalized.isEmpty()) return null

        ClipboardHtmlParser.decodeDataUriImage(normalized)?.let { bytes ->
            return persistImageBytes(context, entryId, bytes, index, total)
        }

        return when {
            normalized.startsWith("content://", ignoreCase = true) ||
                normalized.startsWith("file://", ignoreCase = true) -> {
                persistFromUri(context, entryId, normalized, index, total)
            }
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> {
                downloadAndPersist(context, entryId, normalized, index, total)
            }
            else -> null
        }
    }

    fun collectImageSources(payload: ClipboardPayload): List<String> =
        collectImageSources(
            htmlText = payload.htmlText,
            imageUris = payload.resolvedImageUris()
        )

    fun collectImageSourcesForEntry(entry: ClipboardEntry): List<String> =
        collectImageSources(
            htmlText = entry.htmlText,
            imageUris = entry.resolvedImageFileNames().ifEmpty {
                entry.uri?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }
        )

    private fun collectImageSources(
        htmlText: String?,
        imageUris: List<String>
    ): List<String> {
        val result = linkedSetOf<String>()
        htmlText
            ?.let { ClipboardHtmlParser.imageSources(it) }
            .orEmpty()
            .map { ClipboardHtmlParser.normalizeImageSrc(it.trim()) }
            .filter { it.isNotEmpty() }
            .filterNot { isUnsafeHtmlEmbeddedContentUri(it) }
            .forEach { result += it }
        imageUris
            .map { ClipboardHtmlParser.normalizeImageSrc(it.trim()) }
            .filter { it.isNotEmpty() }
            .forEach { uri ->
                if (!result.contains(uri)) result += uri
            }
        return result.toList()
    }

    fun persistAllFromPayload(context: Context, entryId: String, payload: ClipboardPayload): List<String> {
        val sources = collectImageSources(payload)
        if (sources.isEmpty()) return emptyList()
        val total = sources.size
        val fileNames = mutableListOf<String>()
        sources.forEachIndexed { index, src ->
            persistFromSource(context, entryId, index, total, src)?.let { fileNames += it }
        }
        return fileNames
    }

    fun persistFromPayload(context: Context, entryId: String, payload: ClipboardPayload): String? =
        persistAllFromPayload(context, entryId, payload).firstOrNull()

    fun loadBitmap(context: Context, fileName: String?): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun imageDimensions(context: Context, fileName: String?): Pair<Int, Int>? {
        if (fileName.isNullOrBlank()) return null
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth to bounds.outHeight
        } else {
            null
        }
    }

    fun loadBitmapScaled(context: Context, fileName: String?, maxSidePx: Int): Bitmap? {
        if (fileName.isNullOrBlank()) return null
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSidePx)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * 剪贴板卡片预览：按卡片宽度解码，保证 [ContentScale.Crop] 不放大模糊；
     * 超长截图只保留顶部可见区域，避免整图解码 OOM。
     */
    fun loadThumbnailForCard(
        context: Context,
        fileName: String?,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? {
        if (fileName.isNullOrBlank() || targetWidthPx <= 0 || maxVisibleHeightPx <= 0) return null
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        return decodeThumbnailForCardFromFile(file, targetWidthPx, maxVisibleHeightPx)
    }

    /**
     * 剪贴板卡片的 **URI 版**解码（§0.16.20）。
     *
     * ⚠️ 老实现是 `BitmapFactory.decodeStream(stream)` —— **全尺寸解码**再缩放：
     * "屏幕截图"那种 1080×2400 的 URI 会先吃 10MB，超大图直接 OOM。
     * 现在先 `inJustDecodeBounds` 读尺寸、按与文件版**同一套预算**算 `inSampleSize`，
     * 再解码（流要开两次：`InputStream` 不能倒回）。
     */
    fun loadUriThumbnailForCard(
        context: Context,
        uriString: String,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? {
        if (uriString.isBlank() || targetWidthPx <= 0 || maxVisibleHeightPx <= 0) return null
        val uri = uriString.toUri()
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            // 与 `decodeThumbnailForCardFromFile` 完全同一套采样算式（两处必须一致，
            // 否则"文件图"和"URI 图"在同一个卡片里会一个清晰一个糊）。
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > targetWidthPx * 2) {
                sampleSize *= 2
            }
            val budgetSidePx = maxOf(targetWidthPx, maxVisibleHeightPx)
            val maxPixels = minOf(
                (budgetSidePx.toLong() * 2L) * (budgetSidePx.toLong() * 2L),
                CARD_IMAGE_MAX_SOURCE_PIXELS,
            )
            while (
                (bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight / sampleSize) > maxPixels
            ) {
                sampleSize *= 2
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                scaleAndCropThumbnailForCard(
                    BitmapFactory.decodeStream(stream, null, options) ?: return@use null,
                    targetWidthPx,
                    maxVisibleHeightPx
                )
            }
        }.getOrNull()
    }

    fun loadEntryThumbnail(context: Context, entry: ClipboardEntry): Bitmap? =
        loadEntryThumbnailsForPreview(context, entry).firstOrNull()

    fun loadEntryThumbnailsForPreview(
        context: Context,
        entry: ClipboardEntry,
        maxSidePx: Int = PREVIEW_MAX_SIDE_PX
    ): List<Bitmap> = ClipboardThumbnailCache.loadEntryThumbnailsForPreview(context, entry, maxSidePx)

    fun loadUriBitmapScaled(context: Context, uriString: String, maxSidePx: Int): Bitmap? {
        if (uriString.isBlank()) return null
        return runCatching {
            context.contentResolver.openInputStream(uriString.toUri())?.use { stream ->
                decodeScaledFromBytes(stream.readBytes(), maxSidePx)
            }
        }.getOrNull()
    }

    private fun decodeScaledFromBytes(bytes: ByteArray, maxSidePx: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSidePx)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun loadEntryThumbnails(context: Context, entry: ClipboardEntry): List<Bitmap> {
        val fileNames = entry.resolvedImageFileNames()
        if (fileNames.isNotEmpty()) {
            return fileNames.mapNotNull { loadBitmap(context, it) }
        }
        if (!entry.hasImageContent() || entry.uri.isNullOrBlank()) return emptyList()
        return runCatching {
            context.contentResolver.openInputStream(entry.uri.toUri())?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()?.let { listOf(it) } ?: emptyList()
    }

    fun delete(context: Context, fileName: String) {
        if (fileName.isBlank()) return
        imageFile(context, fileName).delete()
    }

    fun deleteEntryImages(context: Context, entry: ClipboardEntry) {
        entry.resolvedImageFileNames().forEach { delete(context, it) }
    }

    fun uriForFile(context: Context, fileName: String): Uri? {
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrNull()
    }

    fun localUrisForEntry(context: Context, entry: ClipboardEntry): List<Uri> =
        entry.resolvedImageFileNames().mapNotNull { uriForFile(context, it) }

    /** 写入系统剪贴板时用 data URI，便于 Word 等应用粘贴内嵌图片。 */
    fun dataUriForFile(context: Context, fileName: String): String? {
        val file = imageFile(context, fileName)
        if (!file.exists()) return null
        return runCatching {
            bytesToDataUri(file.readBytes(), mimeTypeForFileName(fileName))
        }.getOrNull()
    }

    fun dataUriForLocalUri(context: Context, uri: Uri): String? {
        if (!isOwnClipboardImageUri(context, uri)) return null
        val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        return dataUriForFile(context, fileName)
    }

    private fun isOwnClipboardImageUri(context: Context, uri: Uri): Boolean {
        if (!uri.scheme.equals("content", ignoreCase = true)) return false
        val authority = uri.authority ?: return false
        if (authority != "${context.packageName}.fileprovider") return false
        return uri.path.orEmpty().contains("/clipboard_images/", ignoreCase = true)
    }

    private fun mimeTypeForFileName(fileName: String): String = when {
        fileName.endsWith(".jpg", ignoreCase = true) ||
            fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
        fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
        fileName.endsWith(".bmp", ignoreCase = true) -> "image/bmp"
        else -> "image/png"
    }

    private fun bytesToDataUri(bytes: ByteArray, mimeType: String): String? {
        if (bytes.isEmpty()) return null
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$encoded"
    }

    private fun isUnsafeHtmlEmbeddedContentUri(src: String): Boolean {
        if (!src.startsWith("content://", ignoreCase = true)) return false
        val uri = src.toUri()
        val path = uri.path.orEmpty()
        // file_paths.xml 暴露为 clipboard_images/，URI 形如 …/clipboard_images/<file>
        if (path.contains("/clipboard_images/", ignoreCase = true)) return false
        if (path.contains("/$DIR_NAME/", ignoreCase = true)) return false
        val authority = uri.authority ?: return true
        return authority.contains("fileprovider", ignoreCase = true)
    }

    private fun downloadAndPersist(
        context: Context,
        entryId: String,
        urlString: String,
        index: Int,
        total: Int
    ): String? {
        return runCatching {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.inputStream.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) return null
                persistImageBytes(context, entryId, bytes, index, total)
            }
        }.getOrNull()
    }

    fun persistFromBitmap(context: Context, entryId: String, bitmap: Bitmap): String? {
        val fileName = "$entryId.png"
        val file = imageFile(context, fileName)
        return runCatching {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            fileName
        }.getOrNull()
    }

    private fun decodeThumbnailForCardFromFile(
        file: File,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetWidthPx * 2) {
            sampleSize *= 2
        }
        // ⚠️ §0.16.20：像素预算改用"**较大边**"，与 `StashRepository.loadThumbnailByFileNameForCard`
        // 保持**同一套算法**（两个 tab 的缩略图必须一起变清晰，不能只修一个）。
        //
        // 老写法 `targetWidthPx * maxVisibleHeightPx * 2` 对**竖长截图**（1080×2400）太紧：
        // 预算只有约 47 万像素 → 采样被推到 2 → 解出 540px 宽（比目标 680px 还小）→ 放大变糊。
        // 新预算 = `(2 × max(目标宽, 可见高))²`，上限 [CARD_IMAGE_MAX_SOURCE_PIXELS]。
        val budgetSidePx = maxOf(targetWidthPx, maxVisibleHeightPx)
        val maxPixels = minOf(
            (budgetSidePx.toLong() * 2L) * (budgetSidePx.toLong() * 2L),
            CARD_IMAGE_MAX_SOURCE_PIXELS,
        )
        while (
            (bounds.outWidth.toLong() / sampleSize) * (bounds.outHeight / sampleSize) > maxPixels
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return scaleAndCropThumbnailForCard(decoded, targetWidthPx, maxVisibleHeightPx)
    }

    private fun scaleAndCropThumbnailForCard(
        source: Bitmap,
        targetWidthPx: Int,
        maxVisibleHeightPx: Int
    ): Bitmap {
        var bitmap = source
        if (bitmap.width != targetWidthPx) {
            val scaledHeight = (
                bitmap.height.toFloat() * targetWidthPx / bitmap.width.coerceAtLeast(1)
                ).toInt().coerceAtLeast(1)
            val scaled = bitmap.scale(targetWidthPx, scaledHeight)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }
        if (bitmap.height > maxVisibleHeightPx) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, maxVisibleHeightPx)
            if (cropped !== bitmap) {
                bitmap.recycle()
                bitmap = cropped
            }
        }
        return bitmap
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var sampleSize = 1
        var longest = maxOf(width, height)
        while (longest / sampleSize > maxSidePx) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private const val PREVIEW_MAX_SIDE_PX = 384

    /**
     * 卡片缩略图解码的**源图像素上限**（§0.16.20）。
     *
     * 与 `StashRepository.HistoryCardImageMaxSourcePixels` **同一个值**（两处注释互相点名）：
     * 1200 万像素 ≈ 48MB（ARGB_8888），防止超大图/全景图把内存吃光；
     * 进 `ClipboardThumbnailCache`（`LruCache`，1/8 堆）的仍是缩放+裁切后的小图。
     */
    private const val CARD_IMAGE_MAX_SOURCE_PIXELS = 12_000_000L
}
