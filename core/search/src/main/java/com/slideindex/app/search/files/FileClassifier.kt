package com.slideindex.app.search.files

import java.util.Locale

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 */

/** System / trash classification for file search filtering. */
object FileClassifier {
    private val SYSTEM_EXCLUDED_EXTENSIONS = setOf(
        "tmp", "temp", "cache", "log", "bak", "backup", "old", "orig",
        "swp", "swo", "part", "crdownload", "download", "tmpfile",
    )

    fun isSystemFile(file: DeviceFileEntry): Boolean {
        val name = file.displayName
        if (name.startsWith(".")) return true
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.getDefault())
            .takeIf { it.isNotBlank() }
            ?: return false
        if (extension.startsWith("crypt")) {
            return extension == "crypt" || extension.drop(5).all { it.isDigit() }
        }
        return extension in SYSTEM_EXCLUDED_EXTENSIONS
    }

    fun isSystemFolder(file: DeviceFileEntry): Boolean {
        if (!file.isDirectory) return false
        return file.displayName.lowercase(Locale.getDefault()).startsWith("com.")
    }

    fun isInTrashFolder(file: DeviceFileEntry): Boolean {
        if (file.displayName.equals(".Trash", ignoreCase = true)) return true
        val relativePath = file.relativePath ?: return false
        return relativePath
            .split('/')
            .asSequence()
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.getDefault()) }
            .any { segment -> segment == ".trash" || segment.startsWith(".trash-") }
    }
}
