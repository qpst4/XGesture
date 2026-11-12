package com.slideindex.app.service

import android.content.Context
import android.graphics.Bitmap
import com.slideindex.app.util.ImageDecodeGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ShareImageOcrHistoryEntry(
    val id: String,
    val createdAtEpochMs: Long,
    val ocrText: String,
    val thumbnailFileName: String?,
    val tiled: Boolean
)

@Singleton
class ShareImageOcrHistoryRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val historyDir = File(appContext.filesDir, HISTORY_DIR_NAME).apply { mkdirs() }
    private val thumbnailDir = File(historyDir, THUMBNAIL_DIR_NAME).apply { mkdirs() }
    private val indexFile = File(historyDir, INDEX_FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<ShareImageOcrHistoryEntry>>(emptyList())
    val entries: StateFlow<List<ShareImageOcrHistoryEntry>> = _entries.asStateFlow()

    init {
        _entries.value = readFromDiskSync()
    }

    suspend fun append(
        ocrText: String,
        thumbnail: Bitmap?,
        tiled: Boolean
    ): ShareImageOcrHistoryEntry {
        mutex.withLock {
            val id = "${System.currentTimeMillis()}-${ocrText.hashCode()}"
            val thumbnailFileName = thumbnail?.let { bitmap ->
                saveThumbnail(id, bitmap)
            }
            val entry = ShareImageOcrHistoryEntry(
                id = id,
                createdAtEpochMs = System.currentTimeMillis(),
                ocrText = ocrText,
                thumbnailFileName = thumbnailFileName,
                tiled = tiled
            )
            val next = (listOf(entry) + readFromDisk()).take(MAX_ENTRIES)
            pruneRemovedThumbnails(previous = _entries.value, next = next)
            writeToDisk(next)
            _entries.value = next
            return entry
        }
    }

    suspend fun clear() {
        mutex.withLock {
            thumbnailDir.listFiles()?.forEach { it.delete() }
            writeToDisk(emptyList())
            _entries.value = emptyList()
        }
    }

    suspend fun reloadFromDisk() {
        mutex.withLock {
            _entries.value = readFromDiskSync()
        }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            val current = readFromDisk()
            val removed = current.find { it.id == id }
            removed?.thumbnailFileName?.let { name ->
                File(thumbnailDir, name).delete()
            }
            val next = current.filterNot { it.id == id }
            writeToDisk(next)
            _entries.value = next
        }
    }

    fun loadThumbnail(entry: ShareImageOcrHistoryEntry): Bitmap? {
        val name = entry.thumbnailFileName ?: return null
        val file = File(thumbnailDir, name)
        if (!file.exists()) return null
        return runCatching {
            ImageDecodeGuard.decodeFile(file.absolutePath, ImageDecodeGuard.MAX_DISPLAY_SIDE_PX)
        }.getOrNull()
    }

    private fun saveThumbnail(id: String, bitmap: Bitmap): String {
        val fileName = "$id.png"
        val file = File(thumbnailDir, fileName)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, output)
        }
        return fileName
    }

    private fun pruneRemovedThumbnails(
        previous: List<ShareImageOcrHistoryEntry>,
        next: List<ShareImageOcrHistoryEntry>
    ) {
        val retained = next.mapNotNull { it.thumbnailFileName }.toSet()
        previous.mapNotNull { it.thumbnailFileName }
            .filterNot { it in retained }
            .forEach { name -> File(thumbnailDir, name).delete() }
    }

    private fun readFromDiskSync(): List<ShareImageOcrHistoryEntry> = runCatching {
        if (!indexFile.exists()) return emptyList()
        json.decodeFromString<List<ShareImageOcrHistoryEntry>>(indexFile.readText())
    }.getOrDefault(emptyList())

    private suspend fun readFromDisk(): List<ShareImageOcrHistoryEntry> = withContext(Dispatchers.IO) {
        readFromDiskSync()
    }

    private suspend fun writeToDisk(entries: List<ShareImageOcrHistoryEntry>) = withContext(Dispatchers.IO) {
        indexFile.writeText(json.encodeToString(entries))
    }

    companion object {
        private const val HISTORY_DIR_NAME = "share_image_ocr_history"
        private const val THUMBNAIL_DIR_NAME = "thumbnails"
        private const val INDEX_FILE_NAME = "entries.json"
        private const val MAX_ENTRIES = 20
    }
}
