package com.slideindex.app.clipboard

import android.net.Uri
import android.util.Base64
import androidx.core.text.HtmlCompat
import java.util.regex.Pattern

object ClipboardHtmlParser {
    private val IMG_SRC_PATTERN = Pattern.compile(
        """<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

    fun plainTextFromHtml(html: String): String =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .trim()

    fun firstImageSrc(html: String): String? = imageSources(html).firstOrNull()

    fun imageSources(html: String): List<String> {
        val matcher = IMG_SRC_PATTERN.matcher(html)
        val results = mutableListOf<String>()
        while (matcher.find()) {
            matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { results += it }
        }
        return results
    }

    fun buildHtml(plainText: String, imageSrc: String): String =
        buildHtml(plainText, listOf(imageSrc))

    fun buildHtml(plainText: String, imageSrcs: List<String>): String {
        val escaped = escapePlainText(plainText)
        val images = imageSrcs.joinToString("") { src -> buildImgTag(src, null) }
        return "<html><body><p>$escaped</p>$images</body></html>"
    }

    fun buildHtmlFromBlocks(
        blocks: List<ClipboardContentBlock>,
        imageSrcForFile: (String) -> String?,
        imageSizeForFile: (String) -> Pair<Int, Int>? = { null }
    ): String {
        val body = buildString {
            blocks.forEach { block ->
                when (block.kind) {
                    ClipboardBlockKind.TEXT -> {
                        if (block.text.isBlank()) return@forEach
                        append("<p>").append(escapePlainText(block.text)).append("</p>")
                    }
                    ClipboardBlockKind.IMAGE -> {
                        val src = imageSrcForFile(block.fileName) ?: return@forEach
                        append(buildImgTag(src, imageSizeForFile(block.fileName)))
                    }
                    // 音频块在 HTML 里没有可嵌入的东西（我们**不**把音频塞进剪贴板），
                    // 给一段可读的纯文本占位 —— 关键是"别产出空 HTML / 坏 HTML"。
                    ClipboardBlockKind.AUDIO -> append("<p>")
                        .append(escapePlainText(block.audioText()))
                        .append("</p>")
                    ClipboardBlockKind.UNKNOWN -> append("<p>")
                        .append(escapePlainText(ClipboardUnsupportedBlockText))
                        .append("</p>")
                }
            }
        }
        return "<html><body>$body</body></html>"
    }

    /**
     * 按文档顺序替换 img 的 src，保留 width/height/style 等布局属性（Word 粘贴依赖这些）。
     */
    fun rebuildHtmlImageSources(html: String, newSources: List<String>): String {
        if (newSources.isEmpty()) return html
        val matcher = IMG_SRC_PATTERN.matcher(html)
        val result = StringBuffer()
        var sourceIndex = 0
        while (matcher.find()) {
            if (sourceIndex >= newSources.size) break
            val originalTag = matcher.group(0) ?: continue
            val newTag = replaceImgSrc(originalTag, newSources[sourceIndex++])
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(newTag))
        }
        matcher.appendTail(result)
        return result.toString()
    }

    private fun replaceImgSrc(imgTag: String, newSrc: String): String =
        IMG_SRC_REPLACE_PATTERN.matcher(imgTag).replaceFirst("""src="$newSrc"""")

    private fun buildImgTag(src: String, size: Pair<Int, Int>?): String {
        val (width, height) = size?.let { normalizeDisplaySize(it.first, it.second) }
            ?: (DEFAULT_CLIPBOARD_IMAGE_WIDTH_PX to null)
        return if (height != null && height > 0) {
            """<img src="$src" width="$width" height="$height"/>"""
        } else {
            """<img src="$src" width="$width"/>"""
        }
    }

    private fun normalizeDisplaySize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) {
            return DEFAULT_CLIPBOARD_IMAGE_WIDTH_PX to (DEFAULT_CLIPBOARD_IMAGE_WIDTH_PX * 3 / 4)
        }
        if (width <= DEFAULT_CLIPBOARD_IMAGE_WIDTH_PX) return width to height
        val scaledHeight = (height.toFloat() / width * DEFAULT_CLIPBOARD_IMAGE_WIDTH_PX).toInt()
            .coerceAtLeast(1)
        return DEFAULT_CLIPBOARD_IMAGE_WIDTH_PX to scaledHeight
    }

    private val IMG_SRC_REPLACE_PATTERN = Pattern.compile(
        """\bsrc\s*=\s*["'][^"']*["']""",
        Pattern.CASE_INSENSITIVE
    )

    private const val DEFAULT_CLIPBOARD_IMAGE_WIDTH_PX = 480

    private fun escapePlainText(plainText: String): String =
        plainText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")

    fun isImageSrc(src: String): Boolean {
        val lower = src.lowercase()
        return lower.startsWith("data:image/") ||
            lower.startsWith("content://") ||
            lower.startsWith("file://") ||
            lower.startsWith("http://") ||
            lower.startsWith("https://")
    }

    fun decodeDataUriImage(dataUri: String): ByteArray? {
        if (!dataUri.startsWith("data:", ignoreCase = true)) return null
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex < 0) return null
        val meta = dataUri.substring(5, commaIndex)
        if (!meta.contains("base64", ignoreCase = true)) return null
        val payload = dataUri.substring(commaIndex + 1)
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
    }

    fun normalizeImageSrc(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        else -> src
    }

    fun uriLooksLikeImage(uri: Uri, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/") == true) return true
        val path = uri.toString().lowercase()
        return path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".webp") ||
            path.endsWith(".gif") ||
            path.endsWith(".bmp")
    }
}
