package com.slideindex.app.clipboard

/**
 * 纯图片条目里常见的「标签文字」：截图文件名、IMG_xxx、或图片 content URI 本身。
 * 这些不应作为正文写入系统剪贴板。
 */
object ClipboardImageLabel {

    fun isMetadataText(
        text: String,
        imageSources: List<String> = emptyList(),
        uri: String? = null
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // 1. Is a URI / URL / path
        if (ClipboardHtmlParser.isImageSrc(trimmed)) return true
        val normalizedUri = uri?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedUri != null && (trimmed == normalizedUri || ClipboardHtmlParser.normalizeImageSrc(trimmed) == ClipboardHtmlParser.normalizeImageSrc(normalizedUri))) {
            return true
        }

        // 2. Matches any known imageSources
        val normalizedText = ClipboardHtmlParser.normalizeImageSrc(trimmed)
        if (imageSources.any { source ->
                val normalizedSource = ClipboardHtmlParser.normalizeImageSrc(source.trim())
                normalizedSource == normalizedText || source.trim() == trimmed
            }
        ) {
            return true
        }

        // 3. Ends with an image file extension (whether it has a slash path prefix or not)
        val fileName = trimmed.substringAfterLast('/')
        if (looksLikeImageFileName(fileName) || looksLikeImageFileName(trimmed)) {
            return true
        }

        return false
    }

    fun blocksForClipboardWrite(
        blocks: List<ClipboardContentBlock>,
        imageSources: List<String>,
        uri: String?
    ): List<ClipboardContentBlock> {
        if (blocks.isEmpty()) return blocks
        return blocks.filterNot { block ->
            block.kind == ClipboardBlockKind.TEXT &&
                isMetadataText(block.text, imageSources, uri)
        }
    }

    fun stripMetadataText(
        text: String,
        imageSources: List<String>,
        uri: String?
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return if (isMetadataText(trimmed, imageSources, uri)) "" else trimmed
    }

    private fun looksLikeImageFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".bmp")
    }
}
