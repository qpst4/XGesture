package com.slideindex.app.clipboard

import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 内容块的类型。
 *
 * ⚠️ **[UNKNOWN] 是"防御性"的一档，不是给本版本用的**：闪念的 `index.json` 是**整份反序列化**的
 * （`StashRepository.readFromDisk`），一旦有一个不认识的字面量抛异常，整份表就读不出来 ——
 * 而仓储的规矩是"读盘失败就拒绝一切写入"（见 `indexUnreadable`），
 * 于是一台**新版本**设备（或另一个 App）写进来的音频块，会让**旧版本**彻底写不进数据。
 * 所以未知取值一律落到 [UNKNOWN]，渲染侧显示"不支持的内容"，而不是崩。
 */
@Serializable
enum class ClipboardBlockKind {
    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,

    /** 语音块：`fileName` 指向音频目录里的文件，`durationMs` 是时长。 */
    @SerialName("audio")
    AUDIO,

    /** 本版本不认识的块（见类注释）。 */
    @SerialName("unknown")
    UNKNOWN,
}

/**
 * [ClipboardBlockKind] 的**宽容**序列化器：未知字面量 → [ClipboardBlockKind.UNKNOWN]。
 *
 * 用**属性级**注解挂在 [ClipboardContentBlock.kind] 上（而不是给枚举本身换序列化器）：
 * 这样枚举自己的生成序列化器仍然是标准那一套，只有"块里的 kind 字段"走这条宽容路径，
 * 改动面最小。
 */
object ClipboardBlockKindSerializer : KSerializer<ClipboardBlockKind> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "com.slideindex.app.clipboard.ClipboardBlockKind",
        PrimitiveKind.STRING,
    )

    override fun serialize(encoder: Encoder, value: ClipboardBlockKind) {
        encoder.encodeString(wireNameOf(value))
    }

    override fun deserialize(decoder: Decoder): ClipboardBlockKind = kindOfWireName(decoder.decodeString())

    private fun wireNameOf(kind: ClipboardBlockKind): String = when (kind) {
        ClipboardBlockKind.TEXT -> "text"
        ClipboardBlockKind.IMAGE -> "image"
        ClipboardBlockKind.AUDIO -> "audio"
        ClipboardBlockKind.UNKNOWN -> "unknown"
    }

    private fun kindOfWireName(raw: String): ClipboardBlockKind = when (raw) {
        "text" -> ClipboardBlockKind.TEXT
        "image" -> ClipboardBlockKind.IMAGE
        "audio" -> ClipboardBlockKind.AUDIO
        else -> ClipboardBlockKind.UNKNOWN
    }
}

/**
 * 一个内容块：文字 / 图片 / 语音。
 *
 * 向后兼容：新增字段一律给默认值（[durationMs] 默认 0 → 旧 JSON 读出来是 0:00 而不是崩）。
 * 未知 `kind` 由 [ClipboardBlockKindSerializer] 兜成 [ClipboardBlockKind.UNKNOWN]。
 */
@Serializable
data class ClipboardContentBlock(
    @Serializable(with = ClipboardBlockKindSerializer::class)
    val kind: ClipboardBlockKind,
    val text: String = "",
    val fileName: String = "",
    /** 仅 [ClipboardBlockKind.AUDIO] 使用：录音时长（毫秒）。 */
    val durationMs: Long = 0L,
) {
    companion object {
        fun text(value: String): ClipboardContentBlock =
            ClipboardContentBlock(kind = ClipboardBlockKind.TEXT, text = value)

        fun image(fileName: String): ClipboardContentBlock =
            ClipboardContentBlock(kind = ClipboardBlockKind.IMAGE, fileName = fileName)

        fun audio(fileName: String, durationMs: Long): ClipboardContentBlock = ClipboardContentBlock(
            kind = ClipboardBlockKind.AUDIO,
            fileName = fileName,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }
}

/**
 * 「这个块本版本不认识」的统一文案（渲染与导出共用一处，别各写一份）。
 *
 * 为什么写死而不是走字符串资源：**它同时被数据层用**（`ClipboardHtmlParser.buildHtmlFromBlocks`
 * 与剪贴板纯文本），而数据层没有 `Context`。
 */
const val ClipboardUnsupportedBlockText: String = "[不支持的内容]"

/** 毫秒 → `0:12` / `1:05`（**固定 Locale.US**：阿拉伯语环境下用默认 Locale 会输出阿拉伯数字）。 */
fun formatAudioDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

/** 语音块在**纯文本**语境（导出 / 分享 / 剪贴板纯文本 / HTML 兜底）里的可读表示。 */
fun ClipboardContentBlock.audioText(): String = "[语音 ${formatAudioDuration(durationMs)}]"

fun ClipboardEntry.resolvedContentBlocks(): List<ClipboardContentBlock> {
    if (contentBlocks.isNotEmpty()) return contentBlocks
    return ClipboardBlockParser.buildBlocks(
        text = text,
        htmlText = htmlText,
        imageFileNames = resolvedImageFileNames(),
        imageSources = ClipboardImageStore.collectImageSourcesForEntry(this)
    )
}

fun ClipboardEntry.hasRichPinContent(): Boolean =
    resolvedContentBlocks().any {
        when (it.kind) {
            ClipboardBlockKind.TEXT -> it.text.isNotBlank()
            ClipboardBlockKind.IMAGE -> it.fileName.isNotBlank()
            ClipboardBlockKind.AUDIO -> it.fileName.isNotBlank()
            // 未知块不敢当内容（钉屏要真能画出东西才行）。
            ClipboardBlockKind.UNKNOWN -> false
        }
    }

fun ClipboardEntry.isPureImageEntry(): Boolean {
    if (!hasImageContent()) return false
    val imageSources = ClipboardImageStore.collectImageSourcesForEntry(this)
    val blocks = ClipboardImageLabel.blocksForClipboardWrite(
        blocks = resolvedContentBlocks(),
        imageSources = imageSources,
        uri = uri
    )
    if (blocks.isNotEmpty()) return blocks.all { it.kind == ClipboardBlockKind.IMAGE }
    val bodyText = text.trim()
    return bodyText.isEmpty() ||
        ClipboardImageLabel.isMetadataText(bodyText, imageSources, uri)
}

fun ClipboardEntry.shouldOfferExpand(): Boolean {
    val blocks = resolvedContentBlocks()
    if (blocks.size > 1) return true
    blocks.singleOrNull()?.let { block ->
        return when (block.kind) {
            ClipboardBlockKind.TEXT -> block.text.length > 120
            ClipboardBlockKind.IMAGE -> true
            ClipboardBlockKind.AUDIO -> true
            // 未知块也要能展开：否则用户只会看到一行占位、连点都点不开。
            ClipboardBlockKind.UNKNOWN -> true
        }
    }
    if (isPureImageEntry()) return true
    return hasImageContent() && text.trim().isNotEmpty() && text.trim() != uri
}
