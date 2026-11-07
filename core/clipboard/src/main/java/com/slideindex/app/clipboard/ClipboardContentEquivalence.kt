package com.slideindex.app.clipboard

object ClipboardContentEquivalence {

    fun fingerprint(payload: ClipboardPayload): String {
        if (canUsePlainTextFastPath(payload)) {
            return plainTextFingerprint(normalizedPlainText(payload.text, payload.htmlText))
        }
        val uri = payload.uri?.takeIf { it.isNotBlank() }
        val sources = ClipboardImageStore.collectImageSources(payload)
        return fingerprintBlocks(blocksFromPayload(payload), uri, sources)
    }

    fun fingerprint(entry: ClipboardEntry): String {
        if (canUsePlainTextFastPath(entry)) {
            return plainTextFingerprint(normalizedPlainText(entry.text, entry.htmlText))
        }
        val uri = entry.uri?.takeIf { it.isNotBlank() }
        val sources = ClipboardImageStore.collectImageSourcesForEntry(entry)
        return fingerprintBlocks(entry.resolvedContentBlocks(), uri, sources)
    }

    fun matches(entry: ClipboardEntry, payload: ClipboardPayload): Boolean {
        if (fingerprint(entry) == fingerprint(payload)) return true
        return looselyMatches(entry, payload)
    }

    fun looselyMatches(entry: ClipboardEntry, payload: ClipboardPayload): Boolean {
        val entryText = normalizedPlainText(entry.text, entry.htmlText)
        val payloadText = normalizedPlainText(payload.text, payload.htmlText)
        if (entryText != payloadText) return false
        return imageCountForEntry(entry) == rawImageCount(payload)
    }

    private fun blocksFromPayload(payload: ClipboardPayload): List<ClipboardContentBlock> {
        val rawHtmlSources = payload.htmlText
            ?.let { ClipboardHtmlParser.imageSources(it) }
            .orEmpty()
        val imageUris = payload.resolvedImageUris()
        val imageFileNames = payload.resolvedImageFileNames()
        val imageCount = maxOf(
            rawHtmlSources.size,
            imageUris.size,
            imageFileNames.size
        )
        val sources = when {
            rawHtmlSources.isNotEmpty() -> rawHtmlSources
            imageUris.isNotEmpty() -> imageUris
            else -> emptyList()
        }
        val dummyFiles = List(imageCount) { index ->
            imageFileNames.getOrNull(index)
                ?: imageUris.getOrNull(index)
                ?: rawHtmlSources.getOrNull(index)
                ?: "img_$index"
        }
        return ClipboardBlockParser.buildBlocks(
            text = payload.text,
            htmlText = payload.htmlText,
            imageFileNames = dummyFiles,
            imageSources = sources
        )
    }

    private fun canUsePlainTextFastPath(payload: ClipboardPayload): Boolean =
        !payload.hasImageContent() &&
            payload.htmlText.isNullOrBlank() &&
            payload.uri.isNullOrBlank() &&
            payload.intentUri.isNullOrBlank()

    private fun canUsePlainTextFastPath(entry: ClipboardEntry): Boolean =
        !entry.hasImageContent() &&
            entry.htmlText.isNullOrBlank() &&
            entry.uri.isNullOrBlank() &&
            entry.intentUri.isNullOrBlank()

    private fun plainTextFingerprint(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return "$trimmed|T|n:0"
    }

    private fun fingerprintBlocks(
        blocks: List<ClipboardContentBlock>,
        uri: String? = null,
        sources: List<String> = emptyList()
    ): String {
        if (blocks.isEmpty()) return ""
        val combinedText = blocks
            .filter { it.kind == ClipboardBlockKind.TEXT }
            .joinToString("\n") { it.text.trim() }
            .trim()
        val imageIdentity = uri
            ?: sources.firstOrNull { it.isNotBlank() }
            ?: blocks.firstOrNull { it.kind == ClipboardBlockKind.IMAGE }?.fileName
            ?: ""
        val structure = blocks.joinToString("|") { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> "T"
                ClipboardBlockKind.IMAGE -> if (imageIdentity.isNotBlank()) "I:$imageIdentity" else "I"
                // 见 `ClipboardContentKey.structuralKey` 的同类分支说明。
                ClipboardBlockKind.AUDIO -> "A"
                ClipboardBlockKind.UNKNOWN -> "U"
            }
        }
        val imageCount = blocks.count { it.kind == ClipboardBlockKind.IMAGE }
        return "$combinedText|$structure|n:$imageCount"
    }

    private fun imageCountForEntry(entry: ClipboardEntry): Int {
        val fromBlocks = entry.resolvedContentBlocks().count { it.kind == ClipboardBlockKind.IMAGE }
        val fromFiles = entry.resolvedImageFileNames().size
        val fromHtml = entry.htmlText?.let { ClipboardHtmlParser.imageSources(it).size } ?: 0
        return maxOf(fromBlocks, fromFiles, fromHtml)
    }

    private fun rawImageCount(payload: ClipboardPayload): Int {
        val fromHtml = payload.htmlText?.let { ClipboardHtmlParser.imageSources(it).size } ?: 0
        val fromUris = payload.resolvedImageUris().size
        val fromFiles = payload.resolvedImageFileNames().size
        return maxOf(fromHtml, fromUris, fromFiles)
    }

    private fun normalizedPlainText(plainText: String, htmlText: String?): String {
        val fromHtml = htmlText?.let { ClipboardHtmlParser.plainTextFromHtml(it).trim() }.orEmpty()
        return (fromHtml.ifBlank { plainText }).trim()
    }
}
