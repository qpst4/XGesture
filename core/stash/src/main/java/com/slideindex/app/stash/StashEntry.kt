package com.slideindex.app.stash

import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.clipboard.audioText
import kotlinx.serialization.Serializable

@Serializable
enum class StashEntryType {
    TEXT,
    IMAGE,
    RICH,
}

@Serializable
data class StashEntry(
    val id: String,
    val type: StashEntryType,
    val text: String? = null,
    val imageFileName: String? = null,
    val contentBlocks: List<ClipboardContentBlock> = emptyList(),
    val htmlText: String? = null,
    val createdAtEpochMs: Long,
    val starred: Boolean = false,
    /** 钉图在屏幕上的显示宽高（逻辑像素），用于从暂存夹恢复时保持原尺寸。 */
    val pinDisplayWidthPx: Int? = null,
    val pinDisplayHeightPx: Int? = null
)

sealed class StashRichPart {
    data class Text(val text: String) : StashRichPart()
    data class Image(val bitmap: android.graphics.Bitmap) : StashRichPart()

    /**
     * 一段语音（§0.16.21）。
     *
     * [sourcePath] 的两种来源，与 `StashRichPart.Image` 的"新图 / 已有图"是同一个套路：
     * - **刚录完的**：cache 里的临时文件**绝对路径**（`cacheDir/stash_composer_audio/<uuid>.m4a`）——
     *   仓储会把它**复制**进闪念自己的音频目录（绝不覆盖同名文件）；
     * - **条目里已有的**（编辑条回填）：音频目录里的**文件名**（或该目录下的绝对路径）——
     *   仓储直接复用，**不复制、不重新编码**（音频没法像图片那样重编码，重编码等于丢掉刚才那段）。
     *
     * 仓储认不出这个路径是什么（文件不存在）时**跳过该块**，不会让整次保存失败。
     */
    data class Audio(val sourcePath: String, val durationMs: Long) : StashRichPart()
}

fun StashEntry.resolvedContentBlocks(): List<ClipboardContentBlock> {
    if (contentBlocks.isNotEmpty()) return contentBlocks
    return when (type) {
        StashEntryType.TEXT -> {
            val body = text?.trim().orEmpty()
            if (body.isEmpty()) emptyList() else listOf(ClipboardContentBlock.text(body))
        }
        StashEntryType.IMAGE -> {
            val fileName = imageFileName?.takeIf { it.isNotBlank() } ?: return emptyList()
            listOf(ClipboardContentBlock.image(fileName))
        }
        StashEntryType.RICH -> emptyList()
    }
}

fun StashEntry.allImageFileNames(): List<String> {
    val fromBlocks = contentBlocks
        .filter { it.kind == ClipboardBlockKind.IMAGE }
        .map { it.fileName }
        .filter { it.isNotBlank() }
    if (fromBlocks.isNotEmpty()) return fromBlocks.distinct()
    return listOfNotNull(imageFileName?.takeIf { it.isNotBlank() })
}

/**
 * 这条条目引用的**语音文件名**（按块顺序、去重）。
 *
 * 与 [allImageFileNames] 完全对称：`contentBlocks` 里没有语音块时就是空表
 * （老条目里 `text` / `imageFileName` 两个字段都没有"音频"这一档，所以没有兜底路径）。
 *
 * 用途只有两个：① 孤儿清理时"哪些音频文件还有人引用"；② 删条目 / 截断列表时删文件。
 */
fun StashEntry.allAudioFileNames(): List<String> =
    contentBlocks
        .filter { it.kind == ClipboardBlockKind.AUDIO }
        .map { it.fileName }
        .filter { it.isNotBlank() }
        .distinct()

fun StashEntry.combinedText(): String =
    resolvedContentBlocks()
        .filter { it.kind == ClipboardBlockKind.TEXT }
        .joinToString("\n\n") { it.text.trim() }
        .trim()
        .ifBlank { text?.trim().orEmpty() }

/**
 * **导出 / 分享用的纯文本**（§0.16.21）：语音块给 `[语音 0:12]`、未知块给占位、图片跳过。
 *
 * 为什么不让调用方继续用 [combinedText]：那是"正文"的语义（也是搜索的语料），
 * 把"语音 0:12"混进去会让 `/搜索 语音/` 命中所有带录音的条目 —— 那是噪声。
 * 而"分享出去"这件事必须**表达得出语音块存在**，否则一条只有录音的闪念分享出去是空的。
 */
fun StashEntry.exportText(): String =
    resolvedContentBlocks()
        .mapNotNull { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> block.text.trim().takeIf { it.isNotEmpty() }
                ClipboardBlockKind.AUDIO -> block.audioText()
                ClipboardBlockKind.UNKNOWN -> com.slideindex.app.clipboard.ClipboardUnsupportedBlockText
                // 图片在纯文本语境里没有表示（调用方走"分享图片"那条路兜底）。
                ClipboardBlockKind.IMAGE -> null
            }
        }
        .joinToString("\n\n")
        .trim()

/**
 * 关键词命中判断。
 *
 * [tagNames] 是该条目当前绑定的标签名（来自 `StashMetaRepository`，**不在** `StashEntry` 里，
 * 所以由调用方传进来）。默认空列表 —— 既有调用点不传也能编过。
 */
fun StashEntry.matchesQuery(query: String, tagNames: List<String> = emptyList()): Boolean {
    val lower = query.lowercase()
    if (tagNames.any { it.lowercase().contains(lower) }) return true
    return combinedText().contains(lower, ignoreCase = true) ||
        htmlText?.contains(lower, ignoreCase = true) == true ||
        imageFileName?.contains(lower, ignoreCase = true) == true
}

fun StashEntry.shouldOfferExpand(): Boolean {
    return when (type) {
        StashEntryType.TEXT -> (text?.length ?: 0) > 120
        StashEntryType.IMAGE -> !imageFileName.isNullOrBlank()
        StashEntryType.RICH -> {
            val blocks = resolvedContentBlocks()
            if (blocks.size > 1) return true
            val onlyText = blocks.singleOrNull()?.kind == ClipboardBlockKind.TEXT
            if (onlyText == true && blocks.first().text.length > 120) return true
            // §0.16.21：只有一段语音的条目也要能展开（否则卡片上只有一行占位，连录音都够不着）。
            // 未知块同理：展开是用户唯一能"看见这里有什么"的出口。
            if (allAudioFileNames().isNotEmpty()) return true
            if (blocks.any { it.kind == ClipboardBlockKind.UNKNOWN }) return true
            allImageFileNames().isNotEmpty() && combinedText().isNotBlank()
        }
    }
}
