package com.slideindex.app.clipboard



import android.content.ClipData

import android.content.ClipDescription

import android.content.Context

import android.content.Intent



object ClipboardReader {

    fun read(context: Context): ClipboardPayload? {

        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return null

        val clip = clipboard.primaryClip ?: return null

        val description = clip.description

        val parts = ClipParts()

        for (index in 0 until clip.itemCount) {

            mergeItem(context, clip.getItemAt(index), description, index, parts)

        }

        enrichFromHtml(parts)

        return buildPayload(parts)

    }



    private data class ClipParts(

        var plainText: String = "",

        var htmlText: String? = null,

        val imageUris: MutableList<String> = mutableListOf(),

        var imageMimeType: String? = null,

        var fileUri: String? = null,

        var fileMimeType: String? = null,

        var intentUri: String? = null

    )



    private fun mimeTypeAt(description: ClipDescription?, index: Int): String? {
        if (description == null) return null
        val count = description.mimeTypeCount
        if (count == 0) return null
        val safeIndex = if (index in 0 until count) index else 0
        return description.getMimeType(safeIndex)
    }

    private fun mergeItem(

        context: Context,

        item: ClipData.Item,

        description: ClipDescription?,

        index: Int,

        parts: ClipParts

    ) {

        val html = item.htmlText?.trim()

        if (!html.isNullOrBlank()) {

            parts.htmlText = html

        }



        val text = item.text?.toString()?.trim()

        if (!text.isNullOrBlank()) {

            if (parts.plainText.isBlank()) {

                parts.plainText = text

            }

        }



        val uri = item.uri
        if (uri != null) {
            val mimeType = mimeTypeAt(description, index)
            if (ClipboardHtmlParser.uriLooksLikeImage(uri, mimeType)) {

                addImageUri(parts, uri.toString(), mimeType ?: "image/*")

            } else if (parts.fileUri == null) {

                parts.fileUri = uri.toString()

                parts.fileMimeType = mimeType

            }

            val coerced = item.coerceToText(context)?.toString()?.trim()

            if (parts.plainText.isBlank() && !coerced.isNullOrBlank()) {

                parts.plainText = coerced

            } else if (parts.plainText.isBlank() && !ClipboardHtmlParser.uriLooksLikeImage(uri, mimeType)) {

                parts.plainText = uri.toString()

            }

        }



        val intent = item.intent

        if (intent != null && parts.intentUri == null) {

            parts.intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)

            if (parts.plainText.isBlank()) {

                val display = item.coerceToText(context)?.toString()?.trim()

                parts.plainText = if (!display.isNullOrBlank()) display else parts.intentUri.orEmpty()

            }

        }



        if (parts.plainText.isBlank()) {

            val styled = item.coerceToStyledText(context)?.toString()?.trim()

            if (!styled.isNullOrBlank()) {

                parts.plainText = styled

            }

        }

        if (parts.plainText.isBlank()) {

            val coerced = item.coerceToText(context)?.toString()?.trim()

            if (!coerced.isNullOrBlank()) {

                parts.plainText = coerced

            }

        }

    }



    private fun addImageUri(parts: ClipParts, uri: String, mimeType: String) {
        if (parts.imageUris.contains(uri)) return

        parts.imageUris += uri

        if (parts.imageMimeType == null) {

            parts.imageMimeType = mimeType

        }

    }



    private fun enrichFromHtml(parts: ClipParts) {

        val html = parts.htmlText ?: return

        if (parts.plainText.isBlank()) {

            parts.plainText = ClipboardHtmlParser.plainTextFromHtml(html)

        }

        ClipboardHtmlParser.imageSources(html).forEach { src ->

            if (ClipboardHtmlParser.isImageSrc(src)) {

                val normalized = ClipboardHtmlParser.normalizeImageSrc(src)

                addImageUri(parts, normalized, "image/*")

            }

        }

    }



    private fun buildPayload(parts: ClipParts): ClipboardPayload? {

        val imageUris = parts.imageUris.distinct()

        val imageUri = imageUris.firstOrNull()

        val fileUri = parts.fileUri

        val plainText = parts.plainText.trim()

        val htmlText = parts.htmlText?.trim()?.takeIf { it.isNotEmpty() }

        val intentUri = parts.intentUri



        if (intentUri != null && imageUri == null && plainText.isEmpty() && fileUri == null) {

            return ClipboardPayload(

                type = ClipboardEntryType.INTENT,

                text = intentUri,

                intentUri = intentUri

            )

        }



        val hasImage = imageUris.isNotEmpty()

        val hasText = plainText.isNotEmpty()

        val hasHtml = htmlText != null

        val hasFile = !fileUri.isNullOrBlank()



        if (!hasImage && !hasText && !hasFile && intentUri == null) return null



        return when {

            hasHtml && (hasImage || hasText) -> ClipboardPayload(

                type = ClipboardEntryType.HTML,

                text = plainText.ifBlank { imageUri.orEmpty() },

                uri = imageUri ?: fileUri,

                imageUris = imageUris,

                htmlText = htmlText,

                mimeType = parts.imageMimeType ?: parts.fileMimeType

            )

            hasImage && hasText -> {
                val isMeta = ClipboardImageLabel.isMetadataText(plainText, imageUris, imageUri)
                ClipboardPayload(
                    type = if (isMeta) ClipboardEntryType.URI else ClipboardEntryType.HTML,
                    text = plainText,
                    uri = imageUri,
                    imageUris = imageUris,
                    htmlText = if (isMeta) null else ClipboardHtmlParser.buildHtml(plainText, imageUris),
                    mimeType = parts.imageMimeType
                )
            }

            hasImage -> ClipboardPayload(

                type = ClipboardEntryType.URI,

                text = plainText,

                uri = imageUri,

                imageUris = imageUris,

                mimeType = parts.imageMimeType

            )

            hasFile -> ClipboardPayload(

                type = ClipboardEntryType.URI,

                text = plainText.ifBlank { fileUri.orEmpty() },

                uri = fileUri,

                mimeType = parts.fileMimeType

            )

            hasText -> ClipboardPayload(

                type = ClipboardEntryType.TEXT,

                text = plainText

            )

            else -> ClipboardPayload(

                type = ClipboardEntryType.INTENT,

                text = intentUri.orEmpty(),

                intentUri = intentUri

            )

        }

    }

}

