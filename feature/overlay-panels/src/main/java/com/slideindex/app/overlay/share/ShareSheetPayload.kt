package com.slideindex.app.overlay.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/**
 * 「分享出来的是什么」：内置分享面板要按内容形态决定两件事 ——
 * 列哪一组目标（文本 / 图片 / 文件的系统分享目标集合本来就不一样，见 [ShareSheetKind]），
 * 以及点中目标后发哪条 `ACTION_SEND`。
 *
 * 目标枚举不在这里做：统一交给 [ShareSheetTargetCache]（带缓存与失效）。
 */
sealed interface ShareSheetPayload {

    /** 本次分享属于哪一种目标集合。 */
    val kind: ShareSheetKind

    /** 不带 component 的原始分享 Intent；点中目标后再补 `component` 直发。 */
    fun intent(): Intent

    data class Text(val text: String) : ShareSheetPayload {
        override val kind: ShareSheetKind = ShareSheetKind.TEXT

        override fun intent(): Intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    data class Image(
        val uri: Uri,
        val mimeType: String = "image/*",
    ) : ShareSheetPayload {
        override val kind: ShareSheetKind = ShareSheetKind.IMAGE

        override fun intent(): Intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // 部分目标 App 只认 clipData 里带的 URI 授权（旧 `getShareIntent` 同样这么做）。
            clipData = ClipData.newRawUri("share", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    data class Files(
        val uris: List<Uri>,
        val mimeType: String = "*/*",
    ) : ShareSheetPayload {
        override val kind: ShareSheetKind = ShareSheetKind.FILE

        override fun intent(): Intent =
            if (uris.size <= 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.firstOrNull())
                    uris.firstOrNull()?.let { clipData = ClipData.newRawUri("share", it) }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    buildClipData()?.takeIf { it.itemCount > 0 }?.let { clipData = it }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

        private fun buildClipData(): ClipData? {
            if (uris.isEmpty()) return null
            val first = ClipData.newRawUri("share", uris.first())
            uris.drop(1).forEach { first.addItem(ClipData.Item(it)) }
            return first
        }
    }
}
