package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.slideindex.app.overlay.share.ShareSheetLauncher
import com.slideindex.app.overlay.share.ShareSheetPayload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 多选分享：直接文件用 FileProvider，Root 文件先导出到 cache。 */
internal suspend fun shareFileBrowserPaths(
    context: Context,
    paths: List<String>,
    access: FileBrowserAccessInfo?,
): Int {
    val uris = withContext(Dispatchers.IO) {
        paths.mapNotNull { path ->
            runCatching {
                val direct = File(path)
                val file = if (direct.isFile && direct.canRead()) {
                    direct
                } else if (access?.mode == AccessMode.ROOT) {
                    FileBrowserExport.exportToCache(context, path)
                } else {
                    null
                } ?: return@mapNotNull null
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }.getOrNull()
        }
    }
    if (uris.isEmpty()) return 0
    withContext(Dispatchers.Main) {
        // 内置分享面板开启时在浮层里点选目标直接发；关闭时退回系统面板（由 launcher 先收起本浮窗）。
        ShareSheetLauncher.share(
            context = context,
            payload = ShareSheetPayload.Files(uris = uris, mimeType = "*/*"),
            dismissSource = { FileBrowserOverlayWindow.dismiss() },
        )
    }
    return uris.size
}
