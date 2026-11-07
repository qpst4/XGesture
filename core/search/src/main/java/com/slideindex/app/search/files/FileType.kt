package com.slideindex.app.search.files

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 */

/** Categories of file types that can be filtered in search results. */
enum class FileType {
    DOCUMENTS,
    PICTURES,
    VIDEOS,
    AUDIO,
    APKS,
    OTHER,
    ;

    companion object {
        val ALL: Set<FileType> = entries.toSet()

        fun fromNames(names: Set<String>?): Set<FileType> {
            if (names.isNullOrEmpty()) return ALL
            val parsed = names.mapNotNull { name -> entries.firstOrNull { it.name == name } }.toSet()
            return parsed.ifEmpty { ALL }
        }
    }
}

/** Utility functions for categorizing files by MIME type / extension. */
object FileTypeUtils {
    private const val IMAGE_PREFIX = "image/"
    private const val VIDEO_PREFIX = "video/"
    private const val AUDIO_PREFIX = "audio/"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val BINARY_MIME_TYPE = "application/octet-stream"

    private val DOCUMENT_PREFIXES = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.ms-word",
        "application/vnd.openxmlformats-officedocument.wordprocessingml",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml",
        "application/vnd.oasis.opendocument",
        "application/rtf",
        "application/x-rtf",
        "text/",
    )

    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
        "rtf", "txt", "md", "csv", "json", "xml", "yaml", "yml", "html", "htm", "log", "epub",
    )
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "avif",
        "tiff", "tif", "ico", "raw", "dng", "cr2", "nef", "arw",
    )
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpeg", "mpg", "ts",
    )
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "aac", "m4a", "flac", "ogg", "oga", "wma", "opus", "amr", "aiff", "mid", "midi",
    )

    fun getFileType(file: DeviceFileEntry): FileType {
        if (file.isDirectory) return FileType.OTHER
        val typeFromMime = getFileType(file.mimeType)
        val normalizedMime = file.mimeType?.lowercase()
        val shouldTrustMime = normalizedMime != null && normalizedMime != BINARY_MIME_TYPE
        if (typeFromMime != FileType.OTHER && shouldTrustMime) {
            return typeFromMime
        }
        return getFileTypeFromName(file.displayName) ?: typeFromMime
    }

    fun isApkFile(file: DeviceFileEntry): Boolean {
        if (file.isDirectory) return false
        if (file.mimeType?.lowercase() == APK_MIME_TYPE) return true
        return file.displayName.lowercase().endsWith(".apk")
    }

    fun isImage(file: DeviceFileEntry): Boolean =
        !file.isDirectory && getFileType(file) == FileType.PICTURES

    fun isPdf(file: DeviceFileEntry): Boolean {
        if (file.isDirectory) return false
        if (file.mimeType?.lowercase() == "application/pdf") return true
        return file.displayName.lowercase().endsWith(".pdf")
    }

    private fun getFileType(mimeType: String?): FileType {
        if (mimeType == null) return FileType.OTHER
        val normalizedMime = mimeType.lowercase()
        return when {
            normalizedMime.startsWith(IMAGE_PREFIX) -> FileType.PICTURES
            normalizedMime.startsWith(VIDEO_PREFIX) -> FileType.VIDEOS
            normalizedMime.startsWith(AUDIO_PREFIX) -> FileType.AUDIO
            normalizedMime == APK_MIME_TYPE -> FileType.APKS
            DOCUMENT_PREFIXES.any { normalizedMime.startsWith(it) } -> FileType.DOCUMENTS
            else -> FileType.OTHER
        }
    }

    private fun getFileTypeFromName(fileName: String): FileType? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: return null
        return when {
            extension == "apk" -> FileType.APKS
            extension in IMAGE_EXTENSIONS -> FileType.PICTURES
            extension in VIDEO_EXTENSIONS -> FileType.VIDEOS
            extension in AUDIO_EXTENSIONS -> FileType.AUDIO
            extension in DOCUMENT_EXTENSIONS -> FileType.DOCUMENTS
            else -> null
        }
    }
}
