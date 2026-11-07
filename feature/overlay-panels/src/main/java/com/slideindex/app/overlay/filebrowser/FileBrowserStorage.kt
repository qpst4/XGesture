package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class FileBrowserStorageUsage(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat()
}

data class FileBrowserVolume(
    val label: String,
    val path: String,
    val removable: Boolean,
)

object FileBrowserStorage {
    @Suppress("DEPRECATION")
    fun internalStoragePath(): String = Environment.getExternalStorageDirectory()
        ?.absolutePath
        ?.takeIf { it.isNotBlank() }
        ?: FileBrowserPaths.DEFAULT_INTERNAL_ROOT

    fun read(path: String): FileBrowserStorageUsage? = runCatching {
        val stat = StatFs(path)
        FileBrowserStorageUsage(
            totalBytes = stat.blockCountLong * stat.blockSizeLong,
            availableBytes = stat.availableBlocksLong * stat.blockSizeLong,
        )
    }.getOrNull()

    /** 当前挂载的存储卷 + 其他用户的 /storage/emulated/<id>。 */
    fun volumes(context: Context): List<FileBrowserVolume> {
        val primary = internalStoragePath()
        val result = LinkedHashMap<String, FileBrowserVolume>()
        result[primary] = FileBrowserVolume("Internal storage", primary, removable = false)
        val manager = context.getSystemService(StorageManager::class.java)
        manager?.storageVolumes?.forEach { volume ->
            val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { volume.directory?.absolutePath }.getOrNull()
            } else {
                null
            }
            val path = directory?.takeIf { it.isNotBlank() }
                ?: if (volume.isPrimary) primary else null
            if (path == null || !File(path).isDirectory) return@forEach
            val description = runCatching { volume.getDescription(context) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: if (volume.isPrimary) "Internal storage" else path.substringAfterLast('/')
            result[path] = FileBrowserVolume(description, path, volume.isRemovable)
        }
        runCatching {
            File("/storage/emulated").listFiles()
                ?.filter { it.isDirectory && it.name.toIntOrNull() != null }
                ?.filter { it.absolutePath != primary }
                ?.forEach { dir ->
                    result.putIfAbsent(
                        dir.absolutePath,
                        FileBrowserVolume("User ${dir.name}", dir.absolutePath, removable = false),
                    )
                }
        }
        return result.values.toList()
    }
}
