package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 拖拽未被 IM 接受时的文件分享兜底。
 *
 * 非图片文件没法像图片一样走「写剪贴板 + 无障碍粘贴」，
 * 这里改用系统分享 Intent，把 MediaStore / FileProvider URI 授权给前台宿主或分享面板。
 */
object ClipboardDragShareFallback {

    fun hasShareableContent(clipData: ClipData): Boolean =
        (0 until clipData.itemCount).any { index ->
            val item = clipData.getItemAt(index)
            item.uri != null || !item.text.isNullOrBlank() || !item.htmlText.isNullOrBlank()
        }

    fun shareToForegroundHost(context: Context, clipData: ClipData): Boolean {
        val hostPackage = ClipboardForeground.resolvePackage?.invoke(context) ?: return false
        val uris = (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }
        val plainText = clipData.firstPlainText()
        val htmlText = clipData.firstHtmlText()
        if (uris.isEmpty() && plainText.isNullOrBlank() && htmlText.isNullOrBlank()) return false
        val intent = buildSendIntent(context, clipData, uris, plainText, htmlText)
        uris.forEach { uri ->
            runCatching {
                context.grantUriPermission(
                    hostPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        val targeted = Intent(intent).setPackage(hostPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = runCatching {
            context.startActivity(targeted)
            true
        }.getOrDefault(false)
        if (started) return true
        val chooser = Intent.createChooser(intent, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun buildSendIntent(
        context: Context,
        clipData: ClipData,
        uris: List<Uri>,
        plainText: String?,
        htmlText: String?,
    ): Intent = when {
        !htmlText.isNullOrBlank() -> Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(
                Intent.EXTRA_TEXT,
                plainText.orEmpty().ifBlank { ClipboardHtmlParser.plainTextFromHtml(htmlText) },
            )
            putExtra(Intent.EXTRA_HTML_TEXT, htmlText)
            uris.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it) }
            this.clipData = clipData
        }
        uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
            type = chooseMime(context, clipData, uris)
            putExtra(Intent.EXTRA_STREAM, uris.first())
            this.clipData = clipData
        }
        uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = chooseMime(context, clipData, uris)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            this.clipData = clipData
        }
        else -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, plainText.orEmpty())
            this.clipData = clipData
        }
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun ClipData.firstPlainText(): String? =
        (0 until itemCount).firstNotNullOfOrNull { index ->
            getItemAt(index).text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }

    private fun ClipData.firstHtmlText(): String? =
        (0 until itemCount).firstNotNullOfOrNull { index ->
            getItemAt(index).htmlText?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }

    private fun chooseMime(context: Context, clipData: ClipData, uris: List<Uri>): String {
        val fromFirstReadable = uris.firstNotNullOfOrNull { uri ->
            runCatching { context.contentResolver.getType(uri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && !it.endsWith("/*") && it != "*/*" }
        }
        if (fromFirstReadable != null) return fromFirstReadable
        val declared = (0 until clipData.description.mimeTypeCount)
            .map { clipData.description.getMimeType(it) }
        return declared.firstOrNull { it.isNotBlank() && !it.endsWith("/*") && it != "*/*" }
            ?: declared.firstOrNull { it.isNotBlank() }
            ?: "*/*"
    }
}
