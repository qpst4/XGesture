package com.slideindex.app.search.files

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 *
 * In-memory filters applied after MediaStore name search.
 */
data class FileSearchFilterOptions(
    val enabledFileTypes: Set<FileType> = FileType.ALL,
    val showFolders: Boolean = false,
    val showSystemFiles: Boolean = false,
    val folderWhitelistPatterns: Set<String> = emptySet(),
    val folderBlacklistPatterns: Set<String> = emptySet(),
)

object FileSearchFilter {
    fun filterCandidates(
        fullList: List<DeviceFileEntry>,
        options: FileSearchFilterOptions,
    ): List<DeviceFileEntry> {
        val pathMatcher = FolderPathPatternMatcher.createPathMatcher(
            whitelistPatterns = options.folderWhitelistPatterns,
            blacklistPatterns = options.folderBlacklistPatterns,
        )
        return fullList.filter { file ->
            val isSystem = FileClassifier.isSystemFolder(file) || FileClassifier.isSystemFile(file)
            if (file.isDirectory) {
                if (!options.showFolders) return@filter false
            } else {
                val fileType = FileTypeUtils.getFileType(file)
                if (fileType !in options.enabledFileTypes) return@filter false
                if (fileType == FileType.OTHER && isSystem) return@filter false
                if (FileTypeUtils.isApkFile(file) && FileType.APKS !in options.enabledFileTypes) {
                    return@filter false
                }
            }
            if (isSystem && !options.showSystemFiles) return@filter false
            if (file.displayName.startsWith(".") && !options.showSystemFiles) return@filter false
            if (!options.showSystemFiles && FileClassifier.isInTrashFolder(file)) return@filter false
            pathMatcher(file)
        }
    }
}
