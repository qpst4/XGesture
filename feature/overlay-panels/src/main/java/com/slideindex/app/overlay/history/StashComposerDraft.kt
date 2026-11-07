package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * 「记一条」的草稿（§0.16.14，§0.16.16 起正文改成块序列）。
 *
 * ⚠️ 块类型 [DraftBlock] 本身**不在这里**（§0.16.17 挪到了同包的 `DraftBlock.kt`）：
 * 它现在有**两个**使用方 —— 本对象与就地编辑条的 [EditSessionDraft]；
 * 收尾规则与两条镜像的重算也一并挪过去了（[syncDraftMirrors] / [normalizeDraftBlocks]），
 * 免得两个草稿各写一份、慢慢走偏。本文件只剩"弹窗这一份草稿"自己的东西。
 *
 * 为什么是**进程级单例**而不是 `remember { mutableStateOf(...) }`：
 * 面板是一个 overlay 窗，切前台 App / 拉起系统相册时系统会把整个窗摘掉
 * （§0.16.13 的 `onViewDetached`），下次打开是**全新的 ComposeView + 全新的组合** ——
 * `remember` 的草稿这时全没了。用户实际感受就是"选完图回来，正文/标签/已选图全丢"。
 *
 * 放在单例里之后：窗没了，草稿还在；面板重建后 `HistoryPanelScreen` 把这几条重新接回同一份
 * `MutableState`（`by StashComposerDraft.xxx`）—— ⚠️ **只有** [blocks] 例外：它是
 * `SnapshotStateList`（`StateObject`，不是 `State`），**不能** `by`，那边是 `val composerBlocks = StashComposerDraft.blocks`
 * 直接引用（它本身 snapshot-aware，读写照样被追踪、照样触发重组）。
 *
 * ⚠️ 明说的取舍（§0.16.16 起有变化，别再按老印象理解）："活得比组合长"是为了扛
 * **系统把窗摘掉**（切前台 App / 拉相册），不是为了"关掉之后还留着"。所以：
 * - 窗被系统摘掉 → 草稿原样还在（这正是本单例存在的理由）；
 * - 用户**主动关掉**弹窗（点 FAB 收起）→ 正文块 + 标签 + 提醒 + 临时图**一起清空**
 *   （见 `HistoryPanelScreen` 的 `onOpenChange`）。老实现是"清标签/提醒/图、**留正文**"，
 *   而现在正文里有图块、图块的 cache 文件又已经被删掉了 —— 只留文字、丢掉图块，
 *   等于把用户"图在正文第 3 块"的编辑状态悄悄改掉；整份丢掉反而可预期。
 *
 * ---
 * ## §0.16.16：正文为什么是"块"而不是"一段字符串 + 一串图"
 *
 * 老模型（本次改掉）是 `text: String` 加 `imagePaths: List<String>` 两条并列的草稿：
 * 图只能排在一行缩略图里"另立门户"，存下时还得靠**光标位置**把正文切成两半再把图塞进去
 * （§0.16.15 那套 `head + images + tail`）—— 用户看到的输入框里**没有图**，
 * 也永远表达不出"图 A、文字、图 B"这种交错顺序。
 *
 * 新模型把正文本身变成 `List<DraftBlock>`：文字块与图片块**同一列、按顺序**，
 * 插入点就是光标、删除就是退格，存下时**照着这个顺序**落 `StashRichPart`。
 *
 * ### 镜像还是废弃？—— 选了**保留镜像**（`text` / `imagePaths` 只读、由 [updateBlocks] 同步）
 *
 * - [text] 和 [imagePaths] **不再由 UI 写**，它们是 `blocks` 的投影，只有 [updateBlocks] 会改它们；
 * - 为什么留：这两个名字在 `HistoryPanelScreen` 之外也还有读者（"空内容"判断之外的旁路、
 *   以及任何按 `String` 拿正文的地方），一次性全改成按块读会把改动面推到本次允许改的三个文件之外，
 *   而本次的目标是"图片进正文"，不是"一次性清掉老 API"。镜像同步的代价是
 *   **每处改块都必须走 [updateBlocks]** —— 这是本文件唯一的写入口，别处不要直接 `blocks.value = ...`。
 * - 语义（老的 `text` 是"整段正文"，现在没有整段了）：
 *   [text] = 所有**非空**文字块按顺序用 `\n` 连起来（正好等于 `StashCoordinator.addRich` 里
 *   通知/提醒用的 `joinToString("\n")`），[imagePaths] = 所有图片块的路径、按顺序。
 *
 * ### 空块不变式
 *
 * [updateBlocks] 每次收尾都会保证"**至少有一个文字块**"（见
 * [normalizeComposerBlocks]）—— UI 侧完全不做这个兜底：光标要有个可放的地方、
 * 「＋图片」也得有个块可切，否则"全是图"的草稿上用户会发现自己打不了字。
 */
object StashComposerDraft {
    /**
     * 正文的**唯一真相**：有序块序列（文字 / 图片交错）。
     *
     * 用 `SnapshotStateList` 而不是 `List`：UI 里每块一个 `BasicTextField`，
     * 只认领自己那一块的变化就够了 —— 换成整份 `List` 会让"打字"重组**所有**块
     * （每块还各自持着一个 `TextFieldValue`），块一多就白掉帧。
     */
    val blocks: SnapshotStateList<DraftBlock> = listOf(newEmptyTextBlock()).toMutableStateList()

    /**
     * 正文的**只读投影**（所有非空文字块用 `\n` 连接），由 [updateBlocks] 同步。
     *
     * ⚠️ 不要在 UI 里写它：写 [blocks]（经 [updateBlocks]）才是改正文的唯一方式。
     */
    val text = mutableStateOf("")

    /** 快速选中的标签（存下时落到新条目）。 */
    val tags = mutableStateOf<Set<String>>(emptySet())

    /** 预设的提醒时间（null = 没设）。 */
    val reminderAtMs = mutableStateOf<Long?>(null)

    /**
     * 已选图片的**只读投影**（所有图片块的路径，按正文顺序），由 [updateBlocks] 同步。
     *
     * ⚠️ 弹窗这边的块**只装 cache 临时文件绝对路径**（新选的图还没落盘），
     * 所以这个投影就是"待保存的临时图路径"，可以直接拿去解码/删除。
     * （编辑条的 [EditSessionDraft.imagePaths] 语义不同：那里混着暂存夹文件名。）
     *
     * ⚠️ 不要在 UI 里写它：写 [blocks]（经 [updateBlocks]）才是改正文的唯一方式。
     */
    val imagePaths = mutableStateOf<List<String>>(emptyList())

    /**
     * 已选语音的**只读投影**（所有语音块的路径，按正文顺序），由 [updateBlocks] 同步（§0.16.21）。
     *
     * 与 [imagePaths] 同一套：弹窗里的语音块只装 **cache 临时文件绝对路径**
     * （刚录完、还没落盘），所以这个投影就是"待保存的临时音频路径"，可以直接拿去删除。
     */
    val audioPaths = mutableStateOf<List<String>>(emptyList())

    /**
     * 一个新的**空**文字块（清空草稿时用；id 走 [newDraftBlockId] 全局单调，不会和现有块撞）。
     *
     * ⚠️ 它**不**经 [updateBlocks]：拿到的是"还没插进去的块"，插进去那一步才走 [updateBlocks]。
     */
    fun newEmptyTextBlock(): DraftBlock.Text = newEmptyDraftTextBlock()

    /**
     * 改块序列的**唯一入口**：写完顺序收尾（补齐空块、刷新镜像）。
     *
     * 为什么要一个入口：镜像（[text] / [imagePaths]）必须和块**永远一致** ——
     * 散着改的话，只要漏掉一处同步，"空内容判断"和"存下时的顺序"就会各说各话。
     *
     * 收尾与镜像重算在 [syncDraftMirrors]（与 [EditSessionDraft] 共用同一份实现，
     * 免得两个草稿的"什么算正文"慢慢走偏）。
     */
    fun updateBlocks(transform: (List<DraftBlock>) -> List<DraftBlock>) {
        val next = syncDraftMirrors(transform(blocks.toList()), text, imagePaths, audioPaths)
        blocks.clear()
        blocks.addAll(next)
    }
}
