package com.slideindex.app.clipboard

import java.util.regex.Pattern

object ClipboardBlockParser {
    private val IMG_TAG_PATTERN = Pattern.compile(
        """<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>""",
        Pattern.CASE_INSENSITIVE
    )

    fun buildBlocks(
        text: String,
        htmlText: String?,
        imageFileNames: List<String>,
        imageSources: List<String>
    ): List<ClipboardContentBlock> {
        if (!htmlText.isNullOrBlank() && imageFileNames.isNotEmpty()) {
            val parsed = parseFromHtml(htmlText, imageFileNames, imageSources)
            if (parsed.isNotEmpty()) return parsed
        }
        return buildFallbackBlocks(text, imageFileNames, imageSources)
    }

    private fun parseFromHtml(
        html: String,
        imageFileNames: List<String>,
        imageSources: List<String>
    ): List<ClipboardContentBlock> {
        val srcToFileName = buildSrcToFileNameMap(imageSources, imageFileNames)
        val blocks = mutableListOf<ClipboardContentBlock>()
        var lastEnd = 0
        val matcher = IMG_TAG_PATTERN.matcher(html)
        while (matcher.find()) {
            appendTextBlock(blocks, html.substring(lastEnd, matcher.start()))
            val src = matcher.group(1)?.trim()?.let { ClipboardHtmlParser.normalizeImageSrc(it) }
            val fileName = src?.let { resolveFileName(it, srcToFileName, imageSources, imageFileNames) }
            if (!fileName.isNullOrBlank()) {
                blocks += ClipboardContentBlock.image(fileName)
            }
            lastEnd = matcher.end()
        }
        appendTextBlock(blocks, html.substring(lastEnd))
        return mergeAdjacentTextBlocks(blocks)
    }

    private fun buildSrcToFileNameMap(
        imageSources: List<String>,
        imageFileNames: List<String>
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        imageSources.forEachIndexed { index, src ->
            val normalized = ClipboardHtmlParser.normalizeImageSrc(src.trim())
            val fileName = imageFileNames.getOrNull(index) ?: return@forEachIndexed
            if (normalized.isNotEmpty()) {
                map.putIfAbsent(normalized, fileName)
            }
        }
        return map
    }

    private fun resolveFileName(
        normalizedSrc: String,
        srcToFileName: Map<String, String>,
        imageSources: List<String>,
        imageFileNames: List<String>
    ): String? {
        srcToFileName[normalizedSrc]?.let { return it }
        val index = imageSources.indexOfFirst {
            ClipboardHtmlParser.normalizeImageSrc(it.trim()) == normalizedSrc
        }
        if (index >= 0) return imageFileNames.getOrNull(index)
        return null
    }

    private fun appendTextBlock(blocks: MutableList<ClipboardContentBlock>, htmlFragment: String) {
        val plain = ClipboardHtmlParser.plainTextFromHtml(htmlFragment).trim()
        if (plain.isEmpty()) return
        val last = blocks.lastOrNull()
        if (last?.kind == ClipboardBlockKind.TEXT) {
            blocks[blocks.lastIndex] = ClipboardContentBlock.text("${last.text}\n$plain".trim())
        } else {
            blocks += ClipboardContentBlock.text(plain)
        }
    }

    private fun mergeAdjacentTextBlocks(blocks: List<ClipboardContentBlock>): List<ClipboardContentBlock> {
        if (blocks.isEmpty()) return blocks
        val merged = mutableListOf<ClipboardContentBlock>()
        blocks.forEach { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> {
                    val last = merged.lastOrNull()
                    if (last?.kind == ClipboardBlockKind.TEXT) {
                        merged[merged.lastIndex] = ClipboardContentBlock.text(
                            "${last.text}\n${block.text}".trim()
                        )
                    } else {
                        merged += block
                    }
                }
                // 语音块（本仓库的闪念才会产出）与未知块都不是文字，原样保留顺序即可。
                ClipboardBlockKind.IMAGE,
                ClipboardBlockKind.AUDIO,
                ClipboardBlockKind.UNKNOWN,
                -> merged += block
            }
        }
        return merged
    }

    private fun buildFallbackBlocks(
        text: String,
        imageFileNames: List<String>,
        imageSources: List<String>
    ): List<ClipboardContentBlock> {
        val blocks = mutableListOf<ClipboardContentBlock>()
        val trimmed = text.trim()
        val hasImages = imageFileNames.any { it.isNotBlank() }
        if (trimmed.isNotEmpty() && (!hasImages || !ClipboardImageLabel.isMetadataText(trimmed, imageSources))) {
            blocks += ClipboardContentBlock.text(trimmed)
        }
        imageFileNames.filter { it.isNotBlank() }.forEach { blocks += ClipboardContentBlock.image(it) }
        return blocks
    }
}
