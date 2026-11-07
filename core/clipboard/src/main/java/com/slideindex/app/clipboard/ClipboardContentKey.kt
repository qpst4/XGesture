package com.slideindex.app.clipboard

object ClipboardContentKey {

    fun forPayload(payload: ClipboardPayload): String {
        val sources = ClipboardImageStore.collectImageSources(payload)
        val structural = structuralKeyFromPayload(payload)
        if (structural != null) return structural
        return forFields(
            type = payload.type,
            text = payload.text,
            uri = payload.uri,
            intentUri = payload.intentUri,
            htmlText = payload.htmlText,
            mimeType = payload.mimeType,
            imageUris = payload.resolvedImageUris()
        )
    }

    fun forEntry(entry: ClipboardEntry): String {
        val blocks = entry.resolvedContentBlocks()
        val sources = ClipboardImageStore.collectImageSourcesForEntry(entry)
        if (blocks.isNotEmpty()) return structuralKey(blocks, entry.uri, sources)
        return forFields(
            type = entry.type,
            text = entry.text,
            uri = entry.uri,
            intentUri = entry.intentUri,
            htmlText = entry.htmlText,
            mimeType = entry.mimeType,
            imageUris = entry.resolvedImageFileNames().ifEmpty {
                entry.uri?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            }
        )
    }

    private fun structuralKeyFromPayload(payload: ClipboardPayload): String? {
        val sources = ClipboardImageStore.collectImageSources(payload)
        val imageCount = maxOf(sources.size, payload.resolvedImageUris().size)
        val dummyFiles = List(imageCount) { index ->
            payload.resolvedImageFileNames().getOrNull(index)
                ?: payload.resolvedImageUris().getOrNull(index)
                ?: sources.getOrNull(index)
                ?: "img_$index"
        }
        val blocks = ClipboardBlockParser.buildBlocks(
            text = payload.text,
            htmlText = payload.htmlText,
            imageFileNames = dummyFiles,
            imageSources = sources
        )
        if (blocks.isEmpty()) return null
        return structuralKey(blocks, payload.uri, sources)
    }

    private fun structuralKey(
        blocks: List<ClipboardContentBlock>,
        uri: String? = null,
        sources: List<String> = emptyList()
    ): String {
        val imageIdentity = uri
            ?: sources.firstOrNull { it.isNotBlank() }
            ?: blocks.firstOrNull { it.kind == ClipboardBlockKind.IMAGE }?.fileName
            ?: ""
        var imageIndex = 0
        return blocks.joinToString("|") { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> "t:${block.text.trim()}"
                ClipboardBlockKind.IMAGE -> if (imageIdentity.isNotBlank()) "i:$imageIdentity" else "i:${imageIndex++}"
                // 音频 / 未知块不会从剪贴板来（它们只由本仓库的闪念产出），但枚举多了一档就得给答案。
                ClipboardBlockKind.AUDIO -> "a:${block.fileName}"
                ClipboardBlockKind.UNKNOWN -> "u:${block.fileName}"
            }
        }
    }

    private fun forFields(
        type: ClipboardEntryType,
        text: String,
        uri: String?,
        intentUri: String?,
        htmlText: String?,
        mimeType: String?,
        imageUris: List<String>
    ): String {
        val normalizedText = textForKey(text)
        val imageIdentity = stableImageIdentity(uri, htmlText, mimeType, imageUris)
        return buildString {
            append(
                when (type) {
                    ClipboardEntryType.TEXT -> "t"
                    ClipboardEntryType.URI -> "u"
                    ClipboardEntryType.INTENT -> "i"
                    ClipboardEntryType.HTML -> "h"
                }
            )
            append(':')
            append(normalizedText)
            imageIdentity?.let { append("|img:").append(it) }
            intentUri?.let { append("|i:").append(it) }
            uri?.takeIf { isStableUri(it) && imageIdentity == null }?.let { append("|u:").append(it) }
            if (type == ClipboardEntryType.HTML && imageIdentity == null && !htmlText.isNullOrBlank()) {
                append("|h:").append(htmlText.hashCode())
            }
        }
    }

    private fun textForKey(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return ""
        }
        return trimmed
    }

    private fun stableImageIdentity(
        uri: String?,
        htmlText: String?,
        mimeType: String?,
        imageUris: List<String>
    ): String? {
        val htmlSources = htmlText?.let { ClipboardHtmlParser.imageSources(it) }.orEmpty()
        val allSources = (imageUris + htmlSources).distinct()
        if (allSources.isNotEmpty()) {
            return allSources.joinToString("|") { normalizeImageIdentity(it) }
        }
        uri?.takeIf { isStableUri(it) }?.let { return it }
        if (mimeType?.startsWith("image/") == true && !htmlText.isNullOrBlank()) {
            return "html-image:${htmlText.hashCode()}"
        }
        return null
    }

    private fun normalizeImageIdentity(src: String): String {
        val normalized = ClipboardHtmlParser.normalizeImageSrc(src)
        return when {
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> normalized
            normalized.startsWith("data:image/", ignoreCase = true) ->
                "data:${normalized.length}:${normalized.hashCode()}"
            else -> normalized
        }
    }

    private fun isStableUri(uri: String): Boolean =
        uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true)
}
