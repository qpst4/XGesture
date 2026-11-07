package com.slideindex.app.overlay.filebrowser

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class FileEntryKind {
    DIRECTORY,
    FILE,
    SYMLINK,
    OTHER,
}

/** 目录项。shell 与直读两条路径都归一成这一个模型。 */
data class FileBrowserEntry(
    val name: String,
    val path: String,
    val kind: FileEntryKind,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    val permissions: String? = null,
) {
    val isDirectory: Boolean get() = kind == FileEntryKind.DIRECTORY
}

enum class FileSortOrder {
    NAME_ASC,
    NAME_DESC,
    TIME_DESC,
    TIME_ASC,
    SIZE_DESC,
    SIZE_ASC,
}

/**
 * 目录内的过滤与排序（纯逻辑，便于单测）。
 *
 * 约束：
 * - 目录永远排在文件前面，不随升降序翻转；
 * - 搜索只匹配文件名，不递归；
 * - 隐藏文件按“名称以 . 开头”判定，由调用方决定是否显示。
 */
object FileBrowserSort {
    fun apply(
        entries: List<FileBrowserEntry>,
        order: FileSortOrder,
        showHidden: Boolean,
        query: String,
    ): List<FileBrowserEntry> {
        val needle = query.trim()
        val filtered = entries.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .toList()

        val byName = compareBy<FileBrowserEntry> { it.name.lowercase(Locale.ROOT) }
        val comparator = when (order) {
            FileSortOrder.NAME_ASC -> byName
            FileSortOrder.NAME_DESC -> byName.reversed()
            FileSortOrder.TIME_DESC ->
                compareByDescending<FileBrowserEntry> { it.modifiedEpochSeconds }.then(byName)
            FileSortOrder.TIME_ASC ->
                compareBy<FileBrowserEntry> { it.modifiedEpochSeconds }.then(byName)
            FileSortOrder.SIZE_DESC ->
                compareByDescending<FileBrowserEntry> { it.sizeBytes }.then(byName)
            FileSortOrder.SIZE_ASC ->
                compareBy<FileBrowserEntry> { it.sizeBytes }.then(byName)
        }

        return filtered.sortedWith(
            compareBy<FileBrowserEntry> {
                if (it.kind == FileEntryKind.DIRECTORY) 0 else 1
            }.then(comparator),
        )
    }
}

object FileBrowserFormat {
    private val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())

    /** 例：0 B / 1023 B / 1.0 KB / 12.4 MB / 1.0 GB。 */
    fun size(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
        return String.format(Locale.ROOT, pattern, value, units[index])
    }

    /** epoch 秒 → 本地时间；0/负数显示占位符。DateTimeFormatter 线程安全，可逐行调用。 */
    fun date(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return "—"
        return dateFormatter.format(Instant.ofEpochSecond(epochSeconds))
    }
}

/** 仅用于选图标，不影响访问逻辑。 */
enum class FileKind {
    FOLDER,
    SYMLINK,
    IMAGE,
    VIDEO,
    AUDIO,
    APK,
    ARCHIVE,
    PDF,
    TEXT,
    OTHER,
    ;

    companion object {
        private val IMAGE_EXT = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "avif", "svg",
        )
        private val VIDEO_EXT = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpeg", "mpg", "ts",
        )
        private val AUDIO_EXT = setOf(
            "mp3", "wav", "aac", "m4a", "flac", "ogg", "oga", "wma", "opus", "amr", "mid", "midi",
        )
        private val ARCHIVE_EXT = setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apks", "xapk",
        )
        private val TEXT_EXT = setOf(
            "txt", "md", "csv", "json", "xml", "yaml", "yml", "html", "htm", "log",
            "kt", "java", "js", "ts", "py", "sh", "conf", "ini", "properties",
        )

        fun from(name: String, kind: FileEntryKind): FileKind = when (kind) {
            FileEntryKind.DIRECTORY -> FOLDER
            FileEntryKind.SYMLINK -> SYMLINK
            FileEntryKind.OTHER -> OTHER
            FileEntryKind.FILE -> fromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT))
        }

        private fun fromExtension(extension: String): FileKind = when {
            extension.isEmpty() -> OTHER
            extension == "apk" -> APK
            extension in IMAGE_EXT -> IMAGE
            extension in VIDEO_EXT -> VIDEO
            extension in AUDIO_EXT -> AUDIO
            extension in ARCHIVE_EXT -> ARCHIVE
            extension == "pdf" -> PDF
            extension in TEXT_EXT -> TEXT
            else -> OTHER
        }
    }
}
