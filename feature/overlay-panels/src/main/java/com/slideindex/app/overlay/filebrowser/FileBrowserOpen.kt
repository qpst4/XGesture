package com.slideindex.app.overlay.filebrowser

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * 仅对 App 能直读的文件开放“用其他应用打开”。
 *
 * Root/Shizuku 才能读的文件不在这里处理：需要先用特权 cp 导出到 cache，属于后续的 root 导出流程。
 */
object FileBrowserOpen {
    fun canOpen(path: String): Boolean {
        val file = File(path)
        return file.isFile && file.canRead()
    }

    fun open(context: Context, path: String, chooserTitle: String): Boolean {
        val file = File(path)
        if (!file.isFile || !file.canRead()) return false
        return openFile(context, file, chooserTitle)
    }

    fun openFile(context: Context, file: File, chooserTitle: String): Boolean {
        if (!file.isFile || !file.canRead()) return false
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull() ?: return false
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, uri)
        }
        val chooser = Intent.createChooser(view, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(chooser) }.isSuccess
    }
}
