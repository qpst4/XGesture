package com.slideindex.app.overlay.filebrowser

import android.net.Uri
import androidx.annotation.StringRes
import com.slideindex.app.overlay.panels.R

/**
 * FV 式分类的虚拟位置。
 *
 * 这些不是文件系统路径，不能交给 [FileBrowserPaths.normalize]；Tab、返回栈和偏好里
 * 直接保存 `media://...`，由 Controller/UI 识别为“媒体模式”。
 */
enum class FileBrowserMediaCategory(
    val path: String,
    @StringRes val labelRes: Int,
) {
    PICTURES("media://pictures", R.string.file_browser_category_pictures),
    VIDEOS("media://videos", R.string.file_browser_category_videos),
    AUDIO("media://audio", R.string.file_browser_category_music),
    DOCUMENTS("media://documents", R.string.file_browser_category_documents),
    ;

    companion object {
        fun forPath(path: String): FileBrowserMediaCategory? =
            entries.firstOrNull { it.path == path }

        fun isMediaPath(path: String): Boolean = path.startsWith(MEDIA_PREFIX)

        private const val MEDIA_PREFIX = "media://"
    }
}

/** 偏好/Tab 中保存的路径可能是文件路径，也可能是 media://，统一在这里收敛。 */
object FileBrowserMedia {
    fun normalizeLocation(path: String, internalRoot: String): String =
        if (FileBrowserMediaCategory.isMediaPath(path)) {
            FileBrowserMediaCategory.forPath(path)?.path
                ?: FileBrowserPaths.normalize(internalRoot, internalRoot)
        } else {
            FileBrowserPaths.normalize(path, internalRoot)
        }
}

data class FileBrowserMediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    val relativePath: String?,
)

data class FileBrowserMediaPage(
    val items: List<FileBrowserMediaItem>,
    val hasMore: Boolean,
    val totalCount: Int?,
    val totalBytes: Long?,
)
