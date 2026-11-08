package com.slideindex.app.overlay.filebrowser

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface FileBrowserMediaResult {
    data class Success(val page: FileBrowserMediaPage) : FileBrowserMediaResult
    data object PermissionDenied : FileBrowserMediaResult
    data class Failure(val detail: String?) : FileBrowserMediaResult
}

/**
 * MediaStore 数据源：只读、分页、按修改时间倒序。
 *
 * 这是“全共享存储媒体库”，不是文件系统浏览；`Android/data`、`/data` 不会出现在这里。
 */
object FileBrowserMediaSource {
    const val PAGE_SIZE = 120

    private val BASE_PROJECTION = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.RELATIVE_PATH,
    )

    private const val DOCUMENT_SELECTION =
        "(media_type = 0 OR media_type IS NULL) AND mime_type IS NOT NULL AND (" +
            "mime_type = 'application/pdf' OR " +
            "mime_type LIKE 'text/%' OR " +
            "mime_type LIKE 'application/msword%' OR " +
            "mime_type LIKE 'application/vnd.ms-%' OR " +
            "mime_type LIKE 'application/vnd.openxmlformats-%' OR " +
            "mime_type LIKE 'application/vnd.oasis.opendocument%' OR " +
            "mime_type LIKE 'application/rtf%' OR " +
            "mime_type LIKE 'application/epub%' OR " +
            "mime_type LIKE 'application/json%' OR " +
            "mime_type LIKE 'application/xml%'" +
            ")"

    fun requiredPermissions(category: FileBrowserMediaCategory): List<String> = when {
        category == FileBrowserMediaCategory.DOCUMENTS -> emptyList()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when (category) {
            FileBrowserMediaCategory.PICTURES -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
            FileBrowserMediaCategory.VIDEOS -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
            FileBrowserMediaCategory.AUDIO -> listOf(Manifest.permission.READ_MEDIA_AUDIO)
            FileBrowserMediaCategory.DOCUMENTS -> emptyList()
        }
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPermission(
        context: Context,
        category: FileBrowserMediaCategory,
    ): Boolean {
        if (Environment.isExternalStorageManager()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
        val permissions = requiredPermissions(category)
        if (permissions.isEmpty()) return false
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun loadPage(
        context: Context,
        category: FileBrowserMediaCategory,
        offset: Int,
        limit: Int = PAGE_SIZE,
    ): FileBrowserMediaResult = withContext(Dispatchers.IO) {
        if (!hasPermission(context, category)) {
            return@withContext FileBrowserMediaResult.PermissionDenied
        }
        val collection = collectionFor(category)
        val (selection, selectionArgs) = selectionFor(category)
        val resolver = context.contentResolver
        val items = runCatching {
            queryPage(
                resolver = resolver,
                collection = collection,
                selection = selection,
                selectionArgs = selectionArgs,
                offset = offset,
                limit = limit,
            )
        }.getOrElse { error ->
            return@withContext if (error is SecurityException) {
                FileBrowserMediaResult.PermissionDenied
            } else {
                FileBrowserMediaResult.Failure(error.message)
            }
        }

        val summary = if (offset == 0) {
            runCatching {
                querySummary(resolver, collection, selection, selectionArgs)
            }.getOrNull()
        } else {
            null
        }

        FileBrowserMediaResult.Success(
            FileBrowserMediaPage(
                items = items,
                hasMore = items.size >= limit,
                totalCount = summary?.first,
                totalBytes = summary?.second,
            ),
        )
    }

    private fun collectionFor(category: FileBrowserMediaCategory) = when (category) {
        FileBrowserMediaCategory.PICTURES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        FileBrowserMediaCategory.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        FileBrowserMediaCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        FileBrowserMediaCategory.DOCUMENTS ->
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }

    private fun selectionFor(category: FileBrowserMediaCategory): Pair<String?, Array<String>?> =
        when (category) {
            FileBrowserMediaCategory.DOCUMENTS -> DOCUMENT_SELECTION to null
            else -> null to null
        }

    private fun queryPage(
        resolver: ContentResolver,
        collection: android.net.Uri,
        selection: String?,
        selectionArgs: Array<String>?,
        offset: Int,
        limit: Int,
    ): List<FileBrowserMediaItem> {
        val args = Bundle().apply {
            if (selection != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            }
            if (selectionArgs != null) {
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        val items = ArrayList<FileBrowserMediaItem>(limit)
        resolver.query(collection, BASE_PROJECTION, args, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val relativeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                val size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                val modified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex)
                items += FileBrowserMediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = name,
                    mimeType = cursor.getString(mimeIndex),
                    sizeBytes = size,
                    modifiedEpochSeconds = modified,
                    relativePath = relativeIndex.takeIf { it >= 0 }?.let(cursor::getString),
                )
            }
        }
        return items
    }

    private fun querySummary(
        resolver: ContentResolver,
        collection: android.net.Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Pair<Int?, Long?>? {
        val projection = arrayOf(
            "COUNT(*) AS item_count",
            "SUM(${MediaStore.MediaColumns.SIZE}) AS item_bytes",
        )
        val args = Bundle().apply {
            if (selection != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            }
            if (selectionArgs != null) {
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            }
        }
        resolver.query(collection, projection, args, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val count = if (cursor.isNull(0)) null else cursor.getInt(0)
            val bytes = if (cursor.isNull(1)) null else cursor.getLong(1)
            return count to bytes
        }
        return null
    }
}
