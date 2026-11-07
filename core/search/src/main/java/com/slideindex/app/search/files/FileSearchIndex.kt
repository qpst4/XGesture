package com.slideindex.app.search.files

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.mtp.MtpConstants
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 *
 * MediaStore.Files file name search (ported from Quick Search's FileSearchRepository core).
 */
object FileSearchIndex {
    private const val COLUMN_FORMAT = "format"
    private const val MIN_QUERY_LENGTH = 2
    private const val DEFAULT_LIMIT = 8
    /** Fetch extra rows so type/path filters still yield enough results. */
    private const val CANDIDATE_FETCH_MULTIPLIER = 8
    private const val CANDIDATE_FETCH_MAX = 80
    private const val SQL_DOT = "'.'"
    private const val SQL_EMPTY = "''"
    private const val SQL_HYPHEN = "'-'"
    private const val SQL_SPACE = "' '"
    private const val SQL_UNDERSCORE = "'_'"
    private const val DATE_MODIFIED_SORT = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

    private val FILE_PROJECTION: Array<String> = buildList {
        add(MediaStore.Files.FileColumns._ID)
        add(MediaStore.Files.FileColumns.DISPLAY_NAME)
        add(MediaStore.Files.FileColumns.MIME_TYPE)
        add(MediaStore.Files.FileColumns.DATE_MODIFIED)
        add(COLUMN_FORMAT)
        add(MediaStore.MediaColumns.RELATIVE_PATH)
    }.toTypedArray()

    fun hasPermission(context: Context): Boolean = Environment.isExternalStorageManager()

    suspend fun search(
        context: Context,
        query: String,
        limit: Int = DEFAULT_LIMIT,
        filterOptions: FileSearchFilterOptions = FileSearchFilterOptions(),
    ): List<DeviceFileEntry> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH || !hasPermission(context)) return@withContext emptyList()

        val queryTokens = normalizeQueryTokens(trimmed)
        if (queryTokens.isEmpty()) return@withContext emptyList()

        val fetchLimit = (limit * CANDIDATE_FETCH_MULTIPLIER).coerceAtMost(CANDIDATE_FETCH_MAX)
        val displayNameTokenSelection = queryTokens.joinToString(" AND ") {
            "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ? ESCAPE '\\'"
        }
        val compactDisplayNameSelection = "${compactDisplayNameExpression()} LIKE ? ESCAPE '\\'"
        val selection =
            "($displayNameTokenSelection OR $compactDisplayNameSelection) AND " +
                "(format = ${MtpConstants.FORMAT_ASSOCIATION} OR " +
                "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.%')"
        val selectionArgs = (
            queryTokens.map { "%${escapeLikeQuery(it)}%" } +
                "%${escapeLikeQuery(queryTokens.joinToString(""))}%"
            ).toTypedArray()

        val uri = filesContentUri()
        val raw = ArrayList<DeviceFileEntry>(fetchLimit)
        runCatching {
            context.contentResolver.query(
                uri,
                FILE_PROJECTION,
                selection,
                selectionArgs,
                DATE_MODIFIED_SORT,
            )?.use { cursor ->
                val indices = ColumnIndices.from(cursor)
                while (cursor.moveToNext() && raw.size < fetchLimit) {
                    createEntry(cursor, uri, indices)?.let(raw::add)
                }
            }
        }
        FileSearchFilter.filterCandidates(raw, filterOptions).take(limit)
    }

    suspend fun searchFolders(
        context: Context,
        query: String,
        limit: Int = 30,
    ): List<DeviceFileEntry> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH || !hasPermission(context)) return@withContext emptyList()
        val queryTokens = normalizeQueryTokens(trimmed)
        if (queryTokens.isEmpty()) return@withContext emptyList()

        val displayNameTokenSelection = queryTokens.joinToString(" AND ") {
            "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ? ESCAPE '\\'"
        }
        val compactDisplayNameSelection = "${compactDisplayNameExpression()} LIKE ? ESCAPE '\\'"
        val selection =
            "($displayNameTokenSelection OR $compactDisplayNameSelection) AND " +
                "format = ${MtpConstants.FORMAT_ASSOCIATION}"
        val selectionArgs = (
            queryTokens.map { "%${escapeLikeQuery(it)}%" } +
                "%${escapeLikeQuery(queryTokens.joinToString(""))}%"
            ).toTypedArray()

        val uri = filesContentUri()
        val results = ArrayList<DeviceFileEntry>(limit)
        runCatching {
            context.contentResolver.query(
                uri,
                FILE_PROJECTION,
                selection,
                selectionArgs,
                DATE_MODIFIED_SORT,
            )?.use { cursor ->
                val indices = ColumnIndices.from(cursor)
                while (cursor.moveToNext() && results.size < limit) {
                    createEntry(cursor, uri, indices)?.let(results::add)
                }
            }
        }
        results
    }

    private fun filesContentUri(): Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private fun normalizeQueryTokens(query: String): List<String> =
        query.lowercase(Locale.getDefault())
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun compactDisplayNameExpression(): String {
        val lowerDisplayName = "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME})"
        val withoutSpaces = "REPLACE($lowerDisplayName, $SQL_SPACE, $SQL_EMPTY)"
        val withoutHyphens = "REPLACE($withoutSpaces, $SQL_HYPHEN, $SQL_EMPTY)"
        val withoutUnderscores = "REPLACE($withoutHyphens, $SQL_UNDERSCORE, $SQL_EMPTY)"
        return "REPLACE($withoutUnderscores, $SQL_DOT, $SQL_EMPTY)"
    }

    private fun escapeLikeQuery(query: String): String =
        buildString(query.length) {
            query.forEach { char ->
                when (char) {
                    '\\', '%', '_' -> {
                        append('\\')
                        append(char)
                    }
                    else -> append(char)
                }
            }
        }

    private data class ColumnIndices(
        val idIndex: Int,
        val nameIndex: Int,
        val mimeIndex: Int,
        val modifiedIndex: Int,
        val formatIndex: Int,
        val relativePathIndex: Int,
    ) {
        companion object {
            fun from(cursor: Cursor): ColumnIndices = ColumnIndices(
                idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID),
                nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME),
                mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE),
                modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED),
                formatIndex = cursor.getColumnIndex(COLUMN_FORMAT),
                relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH),
            )
        }
    }

    private fun createEntry(
        cursor: Cursor,
        baseUri: Uri,
        indices: ColumnIndices,
    ): DeviceFileEntry? {
        val name = cursor.getString(indices.nameIndex) ?: return null
        val mimeType = cursor.getString(indices.mimeIndex)
        val modified = if (cursor.isNull(indices.modifiedIndex)) {
            0L
        } else {
            cursor.getLong(indices.modifiedIndex)
        }
        val isDirectory = indices.formatIndex != -1 &&
            !cursor.isNull(indices.formatIndex) &&
            cursor.getInt(indices.formatIndex) == MtpConstants.FORMAT_ASSOCIATION
        val relativePath = if (indices.relativePathIndex != -1 && !cursor.isNull(indices.relativePathIndex)) {
            cursor.getString(indices.relativePathIndex)
        } else {
            null
        }
        val id = cursor.getLong(indices.idIndex)
        val fileUri = ContentUris.withAppendedId(baseUri, id)
        return DeviceFileEntry(
            uri = fileUri,
            displayName = name,
            mimeType = mimeType,
            lastModified = modified,
            isDirectory = isDirectory,
            relativePath = relativePath,
        )
    }
}
