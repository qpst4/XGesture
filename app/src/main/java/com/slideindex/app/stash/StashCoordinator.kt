package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.ScreenPinManager
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.history.HistorySaveSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object StashCoordinator {
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * 记下条目来源（卡片上那枚小 chip）。
     *
     * 来源是**可选**的展示信息，所以失败只吞掉 —— 不能让"存进闪念"这个主流程跟着失败，
     * 也不能因为元数据写不进去就让用户以为没存上。传 null 的调用点 = 纯闪念，不显示 chip。
     */
    private suspend fun rememberSource(entryId: String?, source: String?) {
        if (entryId == null || source == null) return
        runCatching { StashAccess.metaRepository?.setSource(entryId, source) }
    }

    /**
     * 通知把手侧「刚存下一条」：把手脉冲（设计稿 `.pip.pulse`），之后 peek 预览也用它。
     *
     * 把手窗与面板窗是两个 window，只能靠同进程静态量传事件（见 `HistorySaveSignal`）。
     */
    private fun notifySaved(text: String) {
        HistorySaveSignal.notifySaved(text)
    }

    fun addText(
        text: String,
        source: String? = null,
        onSaved: (String) -> Unit = {},
        onDone: (Boolean) -> Unit = {},
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        scope.launch {
            val entry = repo.addText(text)
            rememberSource(entry?.id, source)
            if (entry != null) {
                notifySaved(text)
                onSaved(entry.id)
            }
            onDone(entry != null)
        }
    }

    fun addImage(
        bitmap: Bitmap,
        pinDisplayWidthPx: Int? = null,
        pinDisplayHeightPx: Int? = null,
        source: String? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        if (copy == null) {
            onDone(false)
            return
        }
        scope.launch {
            val entry = repo.addImage(
                bitmap = copy,
                pinDisplayWidthPx = pinDisplayWidthPx,
                pinDisplayHeightPx = pinDisplayHeightPx
            )
            rememberSource(entry?.id, source)
            if (entry != null) notifySaved("")
            onDone(entry != null)
        }
    }

    fun addRich(
        parts: List<StashRichPart>,
        htmlText: String? = null,
        source: String? = null,
        onDone: (Boolean) -> Unit = {},
        /** 拿到新条目 id 时回调（加号弹窗的多图条目要用它挂标签 / 提醒 / 撤销）。 */
        onSaved: (String) -> Unit = {},
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copied = parts.mapNotNull { part ->
            when (part) {
                is StashRichPart.Text -> part
                is StashRichPart.Image -> {
                    val copy = part.bitmap.copy(part.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        ?: return@mapNotNull null
                    StashRichPart.Image(copy)
                }
                // 音频不需要"先拷一份"（不像 Bitmap 会被调用方回收）：它只是一个路径字符串，
                // 真正的复制/落盘在仓储里做（见 `StashRepository.persistAudio`）。
                is StashRichPart.Audio -> part
            }
        }
        if (copied.isEmpty()) {
            onDone(false)
            return
        }
        scope.launch {
            val entry = repo.addRich(copied, htmlText)
            rememberSource(entry?.id, source)
            if (entry != null) {
                notifySaved(copied.filterIsInstance<StashRichPart.Text>().joinToString("\n") { it.text })
                onSaved(entry.id)
            }
            onDone(entry != null)
        }
    }

    /**
     * 给已有条目追加图片（§0.16.14）：就地编辑条里"再补几张图"。
     *
     * 与 [addRich] 同一套写法：先 copy 一份 bitmap（调用方手上的图可能马上被回收/复用），
     * 再切回主线程之外交给仓储；仓储返回 false（条目没了 / 落盘失败，文件已回滚）时不发保存脉冲。
     */
    fun appendImages(entryId: String, bitmaps: List<Bitmap>, onDone: (Boolean) -> Unit = {}) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copied = bitmaps.mapNotNull { bitmap ->
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }
        if (copied.isEmpty()) {
            onDone(false)
            return
        }
        scope.launch {
            val ok = repo.appendImages(entryId, copied)
            if (ok) notifySaved("")
            onDone(ok)
        }
    }

    /**
     * **整体替换**某条目的块序列（§0.16.17）：就地编辑条保存时按块顺序写回。
     *
     * 与 [appendImages] 同一套写法：先 copy 一份 bitmap（调用方手上的图可能马上被回收/复用），
     * 再交给仓储（它负责"要么全成、要么不改"与文件名不覆盖）；仓储返回 false
     * （条目没了 / 图片落盘失败 / 整表写不进去，文件已回滚）时不发保存脉冲。
     *
     * 为什么不让调用方直接用仓储：协调层才是"通知把手侧刚存下一条"的唯一入口
     * （`HistorySaveSignal`，见 [notifySaved]）——编辑条保存也要让把手脉冲一下。
     */
    fun replaceBlocks(entryId: String, parts: List<StashRichPart>, onDone: (Boolean) -> Unit = {}) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copied = parts.mapNotNull { part ->
            when (part) {
                is StashRichPart.Text -> part
                is StashRichPart.Image -> {
                    val copy = part.bitmap.copy(part.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        ?: return@mapNotNull null
                    StashRichPart.Image(copy)
                }
                is StashRichPart.Audio -> part
            }
        }
        if (copied.none { it is StashRichPart.Image } &&
            copied.none { it is StashRichPart.Audio } &&
            copied.none { it is StashRichPart.Text && it.text.isNotBlank() }
        ) {
            onDone(false)
            return
        }
        scope.launch {
            val ok = repo.replaceBlocks(entryId, copied)
            if (ok) {
                notifySaved(copied.filterIsInstance<StashRichPart.Text>().joinToString("\n") { it.text })
            }
            onDone(ok)
        }
    }

    fun pinImageFromStash(context: Context, entry: StashEntry, bitmap: Bitmap) {
        ScreenPinManager.pinFromStashImage(
            context = context,
            bitmap = bitmap,
            displayWidthPx = entry.pinDisplayWidthPx,
            displayHeightPx = entry.pinDisplayHeightPx
        )
    }

    fun pinRichFromStash(context: Context, entry: StashEntry) {
        ScreenPinManager.pinStashRich(context, entry)
    }

    fun copyStashEntry(context: Context, entry: StashEntry): Boolean {
        val repo = StashAccess.repository
        return when (entry.type) {
            StashEntryType.TEXT -> {
                val text = entry.text.orEmpty()
                if (text.isBlank()) return false
                FloatBallTextPick.copyText(context, text)
                true
            }
            StashEntryType.IMAGE -> {
                val bitmap = repo?.loadImage(entry) ?: return false
                FloatBallTextPick.copyImage(context, bitmap)
                true
            }
            StashEntryType.RICH -> {
                val blocks = entry.resolvedContentBlocks()
                if (blocks.isEmpty()) return false
                ClipboardWriter.writeBlocks(
                    context = context,
                    blocks = blocks,
                    htmlText = entry.htmlText,
                    resolveDataUri = { fileName -> repo?.dataUriForFile(fileName) },
                    resolveContentUri = { fileName -> repo?.uriForFile(fileName) },
                    resolveDimensions = { fileName -> repo?.imageDimensions(fileName) }
                )
            }
        }
    }

    fun openStashPanel(context: Context) {
        FloatBallStashPanel.show(context)
    }

    fun openClipboardPanel(context: Context) {
        FloatBallStashPanel.show(
            context = context,
            initialTab = StashPanelInitialTab.Clipboard
        )
    }

    fun pinTextToScreen(context: Context, text: String) {
        ScreenPinManager.pinText(context, text)
    }

    fun pinImageToScreen(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null
    ) {
        ScreenPinManager.pinImage(context, bitmap, screenRect, layoutMeta)
    }

    fun pinRichFromClipboard(context: Context, entry: ClipboardEntry) {
        ScreenPinManager.pinClipboardEntry(context, entry)
    }

    /**
     * 跟手拉出：把手横向拖过阈值时调用，返回"是否真的开始跟手"。
     *
     * [initialTab] = 拉出来先显示哪一页（与"点击指示条"共用一份解析，见 `HistoryFloatService`）。
     *
     * false 的情况：面板本来就开着、或侧栏窗没挂上（无障碍服务没开）——
     * 此时把手的手势要退回原来的"拖过阈值就打开"逻辑。
     */
    fun beginHandleReveal(context: Context, initialTab: StashPanelInitialTab): Boolean =
        FloatBallStashPanel.beginDragReveal(context, initialTab)

    /** 跟手拉出：松手（[commit] = 过半就归位，否则弹回）。 */
    fun endHandleReveal(commit: Boolean) {
        FloatBallStashPanel.endDragReveal(commit)
    }

    fun addFromClipboard(context: Context, entry: ClipboardEntry, onDone: (Boolean) -> Unit = {}) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        scope.launch {
            val created = withContext(Dispatchers.IO) {
                val blocks = entry.resolvedContentBlocks().filter { block ->
                    when (block.kind) {
                        ClipboardBlockKind.TEXT -> block.text.isNotBlank()
                        ClipboardBlockKind.IMAGE -> block.fileName.isNotBlank()
                        // 剪贴板里不会有语音块；这里给答案只是为了让枚举穷尽（真来了就当没有）。
                        ClipboardBlockKind.AUDIO -> false
                        ClipboardBlockKind.UNKNOWN -> false
                    }
                }
                when {
                    blocks.size > 1 -> {
                        val parts = blocks.mapNotNull { block ->
                            when (block.kind) {
                                ClipboardBlockKind.TEXT -> StashRichPart.Text(block.text)
                                ClipboardBlockKind.IMAGE -> {
                                    val bitmap = ClipboardImageStore.loadBitmap(context, block.fileName)
                                        ?: return@mapNotNull null
                                    StashRichPart.Image(bitmap)
                                }
                                ClipboardBlockKind.AUDIO,
                                ClipboardBlockKind.UNKNOWN,
                                -> null
                            }
                        }
                        parts.takeIf { it.isNotEmpty() }?.let { repo.addRich(it, entry.htmlText) }
                    }
                    blocks.size == 1 -> {
                        val only = blocks.first()
                        when (only.kind) {
                            ClipboardBlockKind.TEXT -> repo.addText(only.text)
                            ClipboardBlockKind.IMAGE -> {
                                val bitmap = ClipboardImageStore.loadBitmap(context, only.fileName)
                                bitmap?.let { repo.addImage(it) }
                            }
                            ClipboardBlockKind.AUDIO,
                            ClipboardBlockKind.UNKNOWN,
                            -> null
                        }
                    }
                    else -> {
                        val text = entry.text.trim()
                        when {
                            text.isNotEmpty() -> repo.addText(text)
                            entry.hasImageContent() -> {
                                val bitmap = ClipboardImageStore.loadEntryThumbnail(context, entry)
                                bitmap?.let { repo.addImage(it) }
                            }
                            else -> null
                        }
                    }
                }
            }
            rememberSource(created?.id, StashMetaRepository.SOURCE_CLIPBOARD)
            if (created != null) notifySaved(created.text.orEmpty())
            onDone(created != null)
        }
    }
}
