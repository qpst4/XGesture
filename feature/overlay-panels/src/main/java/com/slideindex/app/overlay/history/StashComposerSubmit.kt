package com.slideindex.app.overlay.history

import android.content.Context
import android.util.Log
import com.slideindex.app.service.decodeStashImageFile
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashRichPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 「记一条」的**落库**这一段：把块序列（文字 / 图片 / 语音交错）落成一条闪念，
 * 并把标签、提醒、撤销、cache 临时文件收尾一并做完。
 *
 * ## 为什么要有这个文件（而不是各窗口各写一份）
 *
 * 现在有**两个**地方会记一条新闪念：
 * 1. 面板里的加号弹窗（`HistoryPanelScreen` 的 `submitComposer*`）；
 * 2. 长按贴边指示条弹出的**独立编辑窗**（[HistoryComposerWindow]）。
 *
 * 两条路必须产生**完全一样**的结果 —— 尤其这里有几个"错一次就很难发现"的点：
 * - 块顺序就是正文顺序（`addRich` 的 `parts` 一一对应落盘块）；
 * - **只要有一张图解不出来就整次不存**（§0.16.18），不能"跳过坏图把剩下的存下"；
 * - 空文字块**跳过**（它是"图片上下还能点到光标"的落点，不是内容）；
 * - 存下之后 cache 里那几份临时图 / 临时录音要删掉，否则每记一条就漏一份垃圾。
 *
 * 各写一份的话，上面每一条都可能只在一个窗口里被改对 —— 用户看到的就是
 * "面板里记的带图能存、把手记的图没了"这种没法解释的差异。
 *
 * ## 与面板那份的关系
 *
 * 面板**暂时仍走它自己那段**（`HistoryPanelScreen.submitComposerBlocks`）：那段是现网跑通的路径，
 * 本次不动它，避免为了"纯"而把已验证的行为改出回归。所以本文件里的**具体写法**（解码、
 * 判空、跳过坏图）与面板那份**必须保持一致** —— 改动其中一处时，另一处跟着改。
 */
object StashComposerSubmit {

    private const val TAG = "StashComposerSubmit"

    /**
     * 本对象自己的协程作用域。
     *
     * 为什么不用 `StashCoordinator` 的：那个是 `private`。也不用窗口 / 组合的作用域 ——
     * 写盘、打标签、排闹钟都不该因为"用户把窗关了"而被取消（条目已经落库，元数据却写一半
     * 是最糟的结果）。本对象是进程级单例，这个作用域也就活到进程结束。
     */
    private val scope = CoroutineScope(Dispatchers.Main)

    /** 解码图片的长边上限（与 `StashComposerImageTrampolineActivity` 同一口径）。 */
    private const val IMAGE_MAX_DIMENSION = 2048

    /**
     * 把草稿存成一条新闪念。
     *
     * ⚠️ **同步**返回（不 suspend）：调用方（overlay 里的 Compose 回调）按普通函数调即可，
     * 解码与写盘都在本函数内部切到 [Dispatchers.IO]。
     *
     * @param blocks 正文块序列（一份快照即可，本函数只读）。
     * @param tags 提交时选中的标签名。
     * @param reminderAtMs 预设的提醒时间（null = 没设）。
     * @param onEmpty 没内容可存（全是空块）。
     * @param onFailed 存不下（有图解不出来 / 数据层返回失败）。
     * @param onSaved 存成功了。参数 = 新条目 id、给通知用的正文摘要。
     */
    fun submit(
        context: Context,
        blocks: List<DraftBlock>,
        tags: Set<String>,
        reminderAtMs: Long?,
        onEmpty: () -> Unit = {},
        onFailed: () -> Unit = {},
        onSaved: (entryId: String, summary: String) -> Unit,
    ) {
        val appContext = context.applicationContext
        val snapshot = blocks.toList()
        scope.launch {
            // 解码离开主线程：一张长边 2048 的图解码不便宜（与面板那份同一条理由）。
            var failedImages = 0
            val parts = withContext(Dispatchers.IO) {
                buildList<StashRichPart> {
                    snapshot.forEach { block ->
                        when (block) {
                            is DraftBlock.Text -> {
                                val text = block.value.trim()
                                if (text.isNotEmpty()) add(StashRichPart.Text(text))
                            }

                            is DraftBlock.Image -> {
                                val bitmap = decodeStashImageFile(block.path, IMAGE_MAX_DIMENSION)
                                if (bitmap == null) failedImages++ else add(StashRichPart.Image(bitmap))
                            }

                            is DraftBlock.Audio -> add(StashRichPart.Audio(block.path, block.durationMs))
                        }
                    }
                }
            }
            // §0.16.18：有一张图解不出来 → **整次不存**（文字也不存），让用户删掉坏图再存。
            // 不做"跳过坏图存剩下的"：那会让用户以为存上了，下次打开才发现图没了。
            if (failedImages > 0) {
                Log.w(TAG, "提交失败：$failedImages 张图解不出来，整次不存")
                onFailed()
                return@launch
            }
            if (parts.isEmpty()) {
                onEmpty()
                return@launch
            }
            val summary = snapshot.filterIsInstance<DraftBlock.Text>()
                .map { it.value.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            val runner = com.slideindex.app.overlay.panels.PanelRuntimeHooks.addStashRich
            if (runner == null) {
                onFailed()
            } else runner(
                parts,
                null,
                null,
                { success -> if (!success) onFailed() },
                { newEntryId ->
                    onSaved(newEntryId, summary)
                    commitMetadata(appContext, newEntryId, tags, reminderAtMs, summary)
                    deleteTempFiles(snapshot)
                },
            )
        }
    }

    /**
     * 标签与提醒要**拿到新条目 id 之后**才能落盘（它们是按 id 存的元数据 + 一个闹钟）。
     *
     * 这两件事与面板里那份（`HistoryPanelScreen.onComposerSaved`）语义完全一致：
     * 有选标签才写（避免无谓的元数据写入）、有设提醒才排闹钟。
     */
    private fun commitMetadata(
        context: Context,
        entryId: String,
        tags: Set<String>,
        reminderAtMs: Long?,
        summary: String,
    ) {
        val metaRepo = StashAccess.metaRepository
        if (tags.isNotEmpty()) {
            val names = tags.toList()
            scope.launch { metaRepo?.setTags(entryId, names) }
        }
        if (reminderAtMs != null) {
            scope.launch {
                metaRepo?.setReminder(entryId, reminderAtMs)
                com.slideindex.app.stash.StashReminderScheduling.schedule
                    ?.invoke(context, entryId, reminderAtMs, summary)
            }
        }
    }

    /** 删掉 cache 里那几份还没落盘的临时文件（图 / 录音）。失败只打日志，不影响已存下的条目。 */
    private fun deleteTempFiles(snapshot: List<DraftBlock>) {
        snapshot.filterIsInstance<DraftBlock.Image>()
            .forEach { image -> runCatching { File(image.path).delete() } }
        snapshot.filterIsInstance<DraftBlock.Audio>()
            .forEach { audio -> runCatching { File(audio.path).delete() } }
    }
}
