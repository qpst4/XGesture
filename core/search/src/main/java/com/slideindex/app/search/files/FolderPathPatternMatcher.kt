package com.slideindex.app.search.files

import java.util.Locale

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 *
 * Matches [DeviceFileEntry] paths using limited patterns.
 * Only the star-slash-path-slash-star form is supported (contains the inner segment).
 */
object FolderPathPatternMatcher {
    private val multiSlashRegex = "/+".toRegex()

    fun normalizePathFilterPattern(rawPattern: String): String? {
        val trimmed = rawPattern.trim()
        if (trimmed.isBlank()) return null
        val normalizedPath = trimmed
            .replace('\\', '/')
            .replace(multiSlashRegex, "/")
            .removePrefix("*/")
            .removePrefix("/")
            .removeSuffix("/*")
            .trim('/')
            .trim()
        if (normalizedPath.isBlank()) return null
        return "*/$normalizedPath/*"
    }

    fun patternDisplayPath(pattern: String): String =
        pattern.removePrefix("*/").removeSuffix("/*").trim('/')

    fun folderDisplayPath(folder: DeviceFileEntry): String =
        listOfNotNull(folder.relativePath?.trim('/'), folder.displayName.trim())
            .filter { it.isNotBlank() }
            .joinToString("/")

    fun createPathMatcher(
        whitelistPatterns: Set<String>,
        blacklistPatterns: Set<String>,
    ): (DeviceFileEntry) -> Boolean {
        val normalizedWhitelist = normalizePathPatterns(whitelistPatterns)
        val normalizedBlacklist = normalizePathPatterns(blacklistPatterns)
        return { file ->
            val candidatePath = buildCandidatePath(file)
            val matchesWhitelist = normalizedWhitelist.isEmpty() ||
                normalizedWhitelist.any { matchesPattern(candidatePath, it) }
            val matchesBlacklist = normalizedBlacklist.any { matchesPattern(candidatePath, it) }
            matchesWhitelist && !matchesBlacklist
        }
    }

    private fun normalizePathPatterns(patterns: Set<String>): Set<String> =
        patterns.asSequence().mapNotNull(::normalizePattern).toSet()

    private fun normalizePattern(rawPattern: String): String? {
        val trimmed = rawPattern.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed
            .replace('\\', '/')
            .replace(multiSlashRegex, "/")
            .removePrefix("/")
            .trim()
            .lowercase(Locale.getDefault())
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun buildCandidatePath(file: DeviceFileEntry): String {
        val relativeSegments = file.relativePath.orEmpty()
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val fileName = file.displayName.trim()
        if (fileName.isEmpty()) return ""
        return (relativeSegments + fileName).joinToString("/").lowercase(Locale.getDefault())
    }

    private fun matchesPattern(path: String, pattern: String): Boolean {
        if (pattern.startsWith("*/") && pattern.endsWith("/*") && pattern.length > 4) {
            val core = pattern.removePrefix("*/").removeSuffix("/*")
            return core.isNotBlank() && path.contains(core)
        }
        return false
    }
}
