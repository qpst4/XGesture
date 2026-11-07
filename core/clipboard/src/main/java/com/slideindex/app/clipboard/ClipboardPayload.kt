package com.slideindex.app.clipboard



data class ClipboardPayload(

    val type: ClipboardEntryType,

    val text: String,

    val uri: String? = null,

    val intentUri: String? = null,

    val htmlText: String? = null,

    val mimeType: String? = null,

    val imageFileName: String? = null,

    val imageFileNames: List<String> = emptyList(),

    val imageUris: List<String> = emptyList()

) {

    fun contentKey(): String = ClipboardContentKey.forPayload(this)



    fun resolvedImageUris(): List<String> {

        val fromList = imageUris.filter { it.isNotBlank() }.distinct()

        if (fromList.isNotEmpty()) return fromList

        return uri?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

    }



    fun resolvedImageFileNames(): List<String> {

        val fromList = imageFileNames.filter { it.isNotBlank() }

        if (fromList.isNotEmpty()) return fromList

        return imageFileName?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

    }



    fun hasImageContent(): Boolean =

        resolvedImageFileNames().isNotEmpty() ||

            resolvedImageUris().isNotEmpty() ||

            (mimeType?.startsWith("image/") == true && !uri.isNullOrBlank()) ||

            (!htmlText.isNullOrBlank() && ClipboardHtmlParser.imageSources(htmlText).isNotEmpty())



    fun toEntry(id: String, createdAtEpochMs: Long): ClipboardEntry {

        val fileNames = resolvedImageFileNames()

        return ClipboardEntry(

            id = id,

            type = type,

            text = text,

            uri = uri,

            intentUri = intentUri,

            htmlText = htmlText,

            mimeType = mimeType,

            imageFileName = fileNames.firstOrNull() ?: imageFileName,

            imageFileNames = fileNames,

            createdAtEpochMs = createdAtEpochMs

        )

    }

}



fun ClipboardEntry.resolvedImageFileNames(): List<String> {

    val fromList = imageFileNames.filter { it.isNotBlank() }

    if (fromList.isNotEmpty()) return fromList

    return imageFileName?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

}



fun ClipboardEntry.hasImageContent(): Boolean =

    resolvedImageFileNames().isNotEmpty() ||

        (type == ClipboardEntryType.URI && mimeType?.startsWith("image/") == true) ||

        (type == ClipboardEntryType.HTML && !htmlText.isNullOrBlank() &&

            ClipboardHtmlParser.imageSources(htmlText).isNotEmpty())



fun ClipboardEntry.displayTypeLabelKey(): ClipboardEntryType = when {

    hasImageContent() && text.isNotBlank() -> ClipboardEntryType.HTML

    else -> type

}

/** 本地 `content://` / `file://` 条目代表文件本身，不能按文本/富文本粘贴。 */
fun ClipboardEntry.isLocalFileUriEntry(): Boolean {
    if (type != ClipboardEntryType.URI) return false
    val raw = uri?.trim().orEmpty()
    return raw.startsWith("content://", ignoreCase = true) ||
        raw.startsWith("file://", ignoreCase = true)
}

