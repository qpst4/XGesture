package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.resolvedContentBlocks
import java.io.File

/**
 * 就地编辑条（`.editbar`）的草稿：**"正在编辑哪一条 + 这一条改到一半的东西"**。
 *
 * 与 [StashComposerDraft] 同一个理由（§0.16.14）：面板是 overlay 窗，切前台 App / 拉起
 * 系统相册时系统会把整个窗摘掉（§0.16.13 的 `onViewDetached`），下次打开是**全新的
 * ComposeView + 全新的组合** —— `remember` 的东西这时全丢。用户感受就是
 * "选完图（或切出去再回来）编辑条里改了一半的正文、刚勾的标签全没了"。
 *
 * 为什么草稿要**按 entryId** 存，而不是像加号弹窗那样一份就够：
 * 加号弹窗只有"一条还没存下的新条目"这唯一目标；编辑条是"对某一条已有条目"的编辑，
 * 用户可能这条改一半、又去开另一条。所以这里记住 [entryId]，换条目时旧草稿整体作废
 * （见 [begin] 的两条规则）。
 *
 * ⚠️ 与 [StashComposerDraft] 一样是**进程级**单例：它同时也意味着
 * "退出面板 / 按返回键收起编辑条之后草稿仍留着"（见 [HistoryPanelScreen] 的 KDoc 选择说明）。
 *
 * **写入是实时的，不是"保存时才写一次"**：块编辑器每次打字 / 插块 / 删块都回调
 * `onBlocksChange`，每次勾/取消标签都会回调 `onTagsChange`，`HistoryPanelScreen`
 * 把它们立刻落到这里的 [blocks] / [tags]。所以"打了一半的正文"也是活的：窗被系统摘掉、
 * 组合重建之后，只要再点这条的编辑条，块序列与标签都能原样喂回去。
 *
 * ---
 * ## §0.16.17：正文从"一段字符串 + 一串待追加图"改成**块序列**
 *
 * 老模型是 `text: String?` 加 `imagePaths: List<String>`（只装"**本次新选**的图"）：
 * 编辑条里看到的是一段纯文字 + 框外一排小图，**条目里已有的图片块根本不显示** ——
 * 用户原话"已经存过图的闪念再次编辑发现文本框中不显示图片了"。
 *
 * 新模型与弹窗完全一致：正文就是 [blocks]（`DraftBlock.Text` / `DraftBlock.Image` 交错），
 * 打开编辑条时由 [seedFromEntry] 把条目现有的 `resolvedContentBlocks()` **按原顺序**铺进来
 * （图片块存的是暂存夹里的**文件名**，显示前拼成路径），保存时按同一顺序写回。
 *
 * ⚠️ 旧字段 [text] / [imagePaths] 的处理选了**保留只读镜像**（与 [StashComposerDraft] 同一套）：
 * [text] 与 [imagePaths] 由 [updateBlocks] 同步，方便"正文串"式的读点继续工作；
 * 它们是投影，**不要**再直接写。
 */
object EditSessionDraft {
    /**
     * 当前草稿属于哪一条条目；null = 没有草稿。
     *
     * 它同时是"要不要把草稿喂回编辑条"的总开关：只有 `entryId` 与本次打开的条目一致时，
     * [blocks] / [tags] 才是这一条的草稿（否则是上一条的残留，必须忽略）。
     */
    val entryId = mutableStateOf<String?>(null)

    /**
     * 正文草稿：**有序块序列**（§0.16.17）。这是正文的唯一真相。
     *
     * 与 `StashComposerDraft.blocks` 一样是 `SnapshotStateList`：每块一个输入框，
     * 只认领自己那一块的变化就够了（不用整份 `List` 让"打字"重组所有块）。
     * 同理**不能** `by`（它不是 `State`），调用方直接引用这个列表。
     */
    val blocks: SnapshotStateList<DraftBlock> = listOf(newEmptyDraftTextBlock()).toMutableStateList()

    /**
     * 正文的**只读投影**（所有非空文字块用 `\n` 连接），由 [updateBlocks] 同步。
     *
     * ⚠️ 不要在 UI 里写它 —— 写 [blocks]（经 [updateBlocks]）才是改正文的唯一方式。
     */
    val text = mutableStateOf("")

    /**
     * **本次新选**的图片路径（trampoline 落在 cache 里的临时文件绝对路径），由 [updateBlocks] 同步。
     *
     * ⚠️ 与老语义的差别（别按老印象读）：现在它只包含 **cache 临时文件**（`File(path).exists()`
     * 且不在暂存夹目录里的那种），**不再**等于"正文里所有图片"。正文里的图片请读 [blocks]。
     * 留它是因为"关闭/换条目时删掉 cache 临时图"这一处按它走最直接。
     */
    val imagePaths = mutableStateOf<List<String>>(emptyList())

    /**
     * 正文里的语音块路径（§0.16.21），由 [updateBlocks] 同步。
     *
     * 与 [imagePaths] 同一套：里面混着 **cache 临时文件绝对路径**（刚录完的）与
     * 音频目录里的**文件名**（条目里已有的）。区分方式不靠猜 —— 见 [discardCurrent]
     * 里那条"只删绝对路径"的判据。
     */
    val audioPaths = mutableStateOf<List<String>>(emptyList())

    /**
     * 标签草稿；每次勾选/取消都会更新（见类注释）。
     *
     * `null` = 用户没动过标签（用条目当前绑定的标签）。
     */
    val tags = mutableStateOf<Set<String>?>(null)

    /**
     * 开始编辑 [entryId]。
     *
     * 两条规则（都在解决"用户实际感受"而不是"代码对称"）：
     * 1. **同一个 entryId 重复调用不清空** —— 这条最重要：窗被摘掉后重开、或者用户收起编辑条
     *    再点同一条的编辑，都会再调一次 [begin]；这时必须把上一份草稿原样留着，
     *    否则"窗被摘掉再回来草稿还在"这件事根本不成立。
     * 2. **换了 entryId 就整体作废上一份**，并且把上一份遗留的 cache 临时图**删掉**：
     *    那些图是"给上一条待追加/新插的"，留着既没人认领（草稿已经作废），又白占 cache。
     */
    fun begin(entryId: String) {
        if (this.entryId.value == entryId) return
        discardCurrent()
        this.entryId.value = entryId
    }

    /**
     * 用条目**现有的块**回填草稿（§0.16.17 的"打开编辑条就能看到已有图片"）。
     *
     * 调用时机：`HistoryPanelScreen` 在目标条目上还没有"属于这一条"的草稿时。
     * 一旦草稿已经在（[entryId] 相同），这里**什么都不做** —— 用户改到一半的那份更重要，
     * 用条目原值盖回去等于把改动吃掉（这正是 [begin] 规则 1 想保的东西）。
     *
     * 顺序就是 `resolvedContentBlocks()` 的顺序（= 落盘顺序 = 卡片渲染顺序），
     * 文字块与图片块交错着铺，不做任何"文字合并、图片提前"的重排 —— 一旦重排，
     * 用户一保存就会把条目里的块顺序改掉，那是数据损坏而不是显示问题。
     *
     * 图片块存 `ClipboardContentBlock.fileName`（暂存夹里的**文件名**），
     * 显示时由 `HistoryPanelScreen` 传进来的 `resolveImagePath` 拼成绝对路径
     * （见 [DraftBlockEditorSurface] 的那个参数）；写回时仓储按同名文件直接复用，
     * **不会**重新落盘成新文件。
     */
    fun seedFromEntry(entry: StashEntry) {
        if (entryId.value != entry.id) return
        // 已经有块了（用户改过）：保持不动。空块序列只在"刚开始编辑这条"时出现。
        if (blocks.any { it is DraftBlock.Image } ||
            blocks.any { it is DraftBlock.Audio } ||
            blocks.any { it is DraftBlock.Text && it.value.isNotEmpty() }
        ) {
            return
        }
        val seeded = entry.resolvedContentBlocks().mapNotNull { block ->
            when (block.kind) {
                // ⚠️ 用 `isNotBlank()` 而不是 `isNotEmpty()`：纯空白的文字块本来就是"没内容"，
                // 铺进来只会让用户看到一个莫名其妙的空行（而且存下时它照样会被丢掉）。
                ClipboardBlockKind.TEXT -> block.text.takeIf { it.isNotBlank() }
                    ?.let { DraftBlock.Text(id = newDraftBlockId(), value = it) }

                ClipboardBlockKind.IMAGE -> block.fileName.takeIf { it.isNotBlank() }
                    ?.let { DraftBlock.Image(id = newDraftBlockId(), path = it) }

                // §0.16.21：语音块**存文件名**（与图片同一套），播/存时由调用方拼成路径；
                // 时长跟着块走（不重算）。
                ClipboardBlockKind.AUDIO -> block.fileName.takeIf { it.isNotBlank() }
                    ?.let {
                        DraftBlock.Audio(
                            id = newDraftBlockId(),
                            path = it,
                            durationMs = block.durationMs,
                        )
                    }

                // 未知块**不铺进编辑器**：本版本连它是什么都不知道，铺进来再存回去
                // 就等于把"不支持的内容"降级成"空"（数据损坏）。它在卡片上是只读的占位。
                ClipboardBlockKind.UNKNOWN -> null
            }
        }
        replaceBlocksInternal(seeded)
    }

    /**
     * 改块序列的**唯一入口**：收尾（至少一个文字块）+ 刷新两条镜像。
     *
     * 收尾与镜像重算在 [syncDraftMirrors]（与 [StashComposerDraft] 共用同一份实现）。
     */
    fun updateBlocks(transform: (List<DraftBlock>) -> List<DraftBlock>) {
        replaceBlocksInternal(transform(blocks.toList()))
    }

    /**
     * 把**某一个块**的图片路径换成 [newPath]（§0.16.22：内置编辑器保存回来后"换掉那一张图"）。
     *
     * 为什么按**块 id** 找而不是"第几张图"：同一张图完全可能在正文里出现两次，而用户点的是
     * **那一块**上的 ✎；块顺序也会因为插字 / 删块随时变化，用序号一定会改错地方。
     *
     * 与 `updateBlocks { … }` 同一套落库步骤（收尾 + 刷新 [imagePaths] 镜像），
     * 所以新路径立刻反映到"哪些是待落盘的临时图"那条投影上。
     *
     * @return true = 真的换上了（false = 那个块已经不在了 —— 用户编辑期间把块删了、
     *   或者编辑条已经关掉换了别的条目；调用方据此决定那份结果文件是留还是删）。
     */
    fun replaceImageBlockPath(blockId: String, newPath: String): Boolean {
        if (newPath.isBlank()) return false
        var replaced = false
        updateBlocks { list ->
            list.map { block ->
                if (block is DraftBlock.Image && block.id == blockId) {
                    replaced = true
                    block.copy(path = newPath)
                } else {
                    block
                }
            }
        }
        return replaced
    }

    /** 清掉草稿（保存成功、或主动放弃这一条）。cache 临时图一并删除。 */
    fun clear() {
        discardCurrent()
    }

    /** [updateBlocks] / [seedFromEntry] 共用的落库步骤。 */
    private fun replaceBlocksInternal(next: List<DraftBlock>) {
        val normalized = syncDraftMirrors(next, text, imagePaths, audioPaths)
        blocks.clear()
        blocks.addAll(normalized)
    }

    /** 丢弃当前草稿的全部内容（含 cache 临时图 / 临时音频），但不改变谁在编辑 —— [begin] 内部也用它。 */
    private fun discardCurrent() {
        // 先删文件再清空列表：反过来的话路径就找不回来了（cache 里会留垃圾）。
        // ⚠️ 只删 **cache 临时文件**：正文里的图片/语音块存的是闪念目录里的**文件名**，
        // 拿它去 `File(...).delete()` 会删掉用户的原图/原录音 —— 这是最危险的一步，判据必须可靠。
        // 判据 = **绝对路径**：trampoline 回来的是绝对路径（`/data/.../cache/xxx.jpg`），
        // 闪念目录里的块存的是纯文件名（`<uuid>.png` / `<uuid>.m4a`），永远不是绝对路径。
        imagePaths.value.filter { File(it).isAbsolute }.forEach { path ->
            runCatching { File(path).delete() }
        }
        audioPaths.value.filter { File(it).isAbsolute }.forEach { path ->
            runCatching { File(path).delete() }
        }
        entryId.value = null
        text.value = ""
        tags.value = null
        imagePaths.value = emptyList()
        audioPaths.value = emptyList()
        blocks.clear()
        blocks.add(newEmptyDraftTextBlock())
    }
}
