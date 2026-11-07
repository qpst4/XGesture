package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * 块编辑器里的一个**块**（§0.16.16）：要么一段文字、要么一张图。
 *
 * 为什么需要它：本仓库解析到的 foundation（1.13.0-alpha03）里
 * `BasicTextField(state = TextFieldState)` **没有** `inlineContent` 参数，
 * `TextFieldState` 上也**没有** `appendInlineContent`（那个只存在于 `AnnotatedString.Builder`，
 * 且只被 `BasicText` 消费）。也就是说"文字流里嵌一张图"这条路在本版本里走不通 ——
 * 想让图片**真的出现在正文里**、和文字按顺序排，只能把正文拆成**有序的块**。
 *
 * ⚠️ 块顺序 = 落盘 `contentBlocks` 的顺序（卡片展开时也按这个顺序画），
 * 所以"块序列"是唯一真相，**不要**再在旁边维护一份"整段正文 + 一串图"。
 *
 * ---
 * ## 为什么单独一个文件、而不是塞在 [StashComposerDraft] 里
 *
 * 它现在有**两个**使用方：「记一条」弹窗（[StashComposerDraft]）与就地编辑条
 * （[EditSessionDraft]）。放在任何一边都会逼另一边去 import "对方那个草稿对象"才有类型可用，
 * 那是假的依赖关系。放这里 = **只有一个真源**（不允许各自复制一份 `DraftBlock`）。
 *
 * ## `Image.path` 的两种含义（一个字段、两种来源）
 *
 * - **新选的图**（trampoline / 相册回来）：`path` 是 cache 里的**临时文件绝对路径**；
 * - **条目里已有的图**（编辑条回填）：`path` 是暂存夹图片目录下的**文件名**
 *   （`ClipboardContentBlock.fileName`，落盘时的格式就是它）。
 *
 * 区分方式**不靠猜**：写回时先 decode、再交给仓储的"整体替换块序列"接口，由它决定
 * "这张是新的（要落盘、不能覆盖同名文件）还是老的（文件名照用）"—— 见
 * `StashRepository.replaceBlocks`。UI 侧只多一件事：**显示**前要把文件名拼成可读路径
 * （`StashRepository.imageFilePath`）。
 */
sealed interface DraftBlock {
    /** 稳定 id：Compose 的 `key`、每块的 `FocusRequester` 表、焦点衔接都要用它。 */
    val id: String

    /** 一段文字（可以是空串 —— 空块正是"图片下面还能点到光标"的落点）。 */
    data class Text(override val id: String, val value: String) : DraftBlock

    /** 一张图，见接口 KDoc 里"`path` 的两种含义"。 */
    data class Image(override val id: String, val path: String) : DraftBlock

    /**
     * 一段语音（§0.16.21）。
     *
     * [path] 与 [Image.path] 完全同一套"两种含义"：
     * - **刚录完的**：`cacheDir/stash_composer_audio/<uuid>.m4a` 的**绝对路径**；
     * - **条目里已有的**：闪念音频目录里的**文件名**（`ClipboardContentBlock.fileName`）。
     *
     * 显示/播放前要把它拼成可读路径 —— 分流规则与图片共用一个入口
     * （`HistoryPanelScreen` 的 `resolveEditBlockImagePath` 那套"集合成员判断"）。
     *
     * [durationMs] 在草稿里就带着（录音时用墙钟量出来的）：保存时直接写进块的 `durationMs`，
     * 不用等落盘后再去读文件（那样又得引入 `MediaMetadataRetriever`）。
     */
    data class Audio(override val id: String, val path: String, val durationMs: Long) : DraftBlock
}

/**
 * 块序列的 id 发号器。
 *
 * 为什么是**进程级**计数器：id 只在本进程内有意义（Compose 的 `key`、焦点表），
 * 重启后从头来过无所谓；两个草稿（弹窗 / 编辑条）共用一条序列也不会撞。
 */
private var blockIdSeq = 1

/** 取一个新的块 id（`c1` / `c2` …，人肉读日志时比 UUID 好认）。 */
fun newDraftBlockId(): String = "c${blockIdSeq++}"

/**
 * 一个新的**空**文字块（还没插进任何草稿）。
 *
 * 光标处切块、删图之后补位都要它；插进去那一步才走草稿自己的 `updateBlocks`。
 */
fun newEmptyDraftTextBlock(): DraftBlock.Text =
    DraftBlock.Text(id = newDraftBlockId(), value = "")

/**
 * 块序列的收尾规则 + 两条"只读镜像"的重算。**两个草稿共用这一份**，别各写一遍。
 *
 * 收尾保证两件事：**至少有一个文字块**、且**最后一个块一定是文字块**（见 [normalizeDraftBlocks]）。
 *
 * 镜像用 `MutableState` 装（[textMirror] / [imagePathsMirror] / [audioPathsMirror]）：
 * 它们在组合里被读，必须各自是一个可观察对象；`SnapshotStateList` 装不了"派生字符串"。
 *
 * @param blocks 收尾之后的块序列（调用方负责写回自己的 `SnapshotStateList`）。
 */
fun syncDraftMirrors(
    blocks: List<DraftBlock>,
    textMirror: androidx.compose.runtime.MutableState<String>,
    imagePathsMirror: androidx.compose.runtime.MutableState<List<String>>,
    /**
     * 语音块路径的只读投影（§0.16.21）。
     *
     * 加它而不是让调用方自己从 `blocks` 现算：关窗/换条目时"要删掉哪些 cache 临时音频"
     * 与图片是同一时刻、同一批路径 —— 分两处算迟早会出现"块清了、文件还在 cache 里"。
     */
    audioPathsMirror: androidx.compose.runtime.MutableState<List<String>>,
): List<DraftBlock> {
    val next = normalizeDraftBlocks(blocks)
    textMirror.value = next.filterIsInstance<DraftBlock.Text>()
        .map { it.value }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    imagePathsMirror.value = next.filterIsInstance<DraftBlock.Image>().map { it.path }
    audioPathsMirror.value = next.filterIsInstance<DraftBlock.Audio>().map { it.path }
    return next
}

/**
 * 收尾（§0.16.16 的"至少一个文字块" + §0.16.23 的"最后一个块必须是文字块"）：
 * 1. 一张 `DraftBlock` 都没有 → 补一个空文字块；
 * 2. 最后一个块是**图片 / 语音**（整宽独占一行的媒体块）→ 在它后面补一个空文字块。
 *
 * 第 2 条是用户实测 bug 的根因修复：「图片在正文最底部时，光标到不了图片下面」。
 * 媒体块整宽独占一行，"图片右边"**不可能**有光标（本仓库的 `BasicTextField` 没有内联元素，
 * 做不到图文混排）；能落光标的只有**块与块之间**，所以媒体块后面必须永远留一个空文字块
 * —— 否则正文末尾是图时，用户点图下面的空白没有任何块可接光标。
 *
 * 为什么**不**去合并相邻空块：用户手上有两个挨着的空块是完全合法的中途状态
 * （删掉中间一张图就会留下它），硬合并会把光标/焦点从用户刚站住的那个块上挪走。
 * 真正的合并交给"存下"那一步（空文字块本来就会被跳过）。
 *
 * ⚠️ 幂等：末尾已经是文字块时**原样返回同一个 list 实例**，不重新发 id
 * （每次重组都发新 id 会让 Compose 的 `key(block.id)` 把整块重建、输入框丢焦点）。
 */
fun normalizeDraftBlocks(blocks: List<DraftBlock>): List<DraftBlock> {
    if (blocks.isEmpty()) return listOf(newEmptyDraftTextBlock())
    if (blocks.last() is DraftBlock.Text) return blocks
    return blocks + newEmptyDraftTextBlock()
}

/** 一份块序列的**只读投影**里"文字"那一半的语义（非空文字块用 `\n` 连接）。 */
fun draftTextOf(blocks: List<DraftBlock>): String =
    blocks.filterIsInstance<DraftBlock.Text>()
        .map { it.value }
        .filter { it.isNotBlank() }
        .joinToString("\n")

/** 供 [SnapshotStateList] 之外的调用方（保存前的快照）一次性拿到两条投影。 */
fun draftImagePathsOf(blocks: List<DraftBlock>): List<String> =
    blocks.filterIsInstance<DraftBlock.Image>().map { it.path }

/** 语音块路径的投影（关窗/换条目时"要删掉哪些 cache 临时音频"用）。 */
fun draftAudioPathsOf(blocks: List<DraftBlock>): List<String> =
    blocks.filterIsInstance<DraftBlock.Audio>().map { it.path }

/** 空的只读镜像（给"没有草稿"的调用方当默认值用，省得到处 `mutableStateOf(emptyList())`）。 */
fun emptyDraftImagePathsMirror(): androidx.compose.runtime.MutableState<List<String>> =
    mutableStateOf(emptyList())
