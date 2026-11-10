package com.slideindex.app.stash

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一枚标签的定义。 */
@Serializable
data class StashTag(
    val name: String,
    /** 颜色，0xAARRGGBB 的十进制值。用 Long 存，避免 JSON 里出现负数歧义的 Int。 */
    val colorArgb: Long,
    /** 排序权重，小的在前。 */
    val order: Int = 0,
)

/** 一条「追加」—— 在原记录上后来续写的一段。 */
@Serializable
data class StashAppend(
    val text: String,
    val atEpochMs: Long,
)

/**
 * 元数据文件的编解码器（**单例，放在文件顶层**）。
 *
 * - `ignoreUnknownKeys`：老版本读到新字段不能炸。
 * - `encodeDefaults = true`：与 `index.json` 相反 —— 这个文件由我们独占，字段完整落盘才好排查。
 *
 * 放在顶层还有一个好处：单测能直接引用它，测的就是线上这份配置，不会和实现跑偏。
 */
internal val StashMetaJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 所有**新**的条目元数据都放这里，**独立文件存放，绝不写进 `index.json`**。
 *
 * 为什么必须独立（`docs/capsule-refactor-p0-findings.md` §4.1）：
 * `index.json` 是 `List<StashEntry>` 的序列化结果，老版本 App 用
 * `Json { ignoreUnknownKeys = true }` 读、再**整体重写**整张表 —— 新字段会被忽略并抹掉；
 * 而且它的配置是 `encodeDefaults = false`，等于默认值的字段根本不落盘。
 *
 * 为什么合成**一个**文件而不是三个：三者都是「按 entryId 索引的条目元数据」，
 * 同生命周期、同清理时机。合起来只有一把锁、一个监听、一次重载，
 * 也不会出现「标签删了但完成态还留着孤儿」这类跨文件不一致。
 */
@Serializable
data class StashMetaStore(
    val version: Int = 1,
    val tags: List<StashTag> = emptyList(),
    /** entryId -> 该条的标签名列表。 */
    val assignments: Map<String, List<String>> = emptyMap(),
    /** entryId -> 完成时间戳 ms。**存在即已完成**，不存 false。 */
    val doneAt: Map<String, Long> = emptyMap(),
    /** entryId -> 追加内容（按时间正序）。 */
    val appends: Map<String, List<StashAppend>> = emptyMap(),
    /**
     * entryId -> 来源键（[StashMetaRepository.SOURCE_CLIPBOARD] 等）。
     *
     * 存**字符串键而不是 enum**：老版本遇到不认识的 enum 值会在解码**整个文件**时抛异常，
     * 而这个文件的读失败会连带禁用所有写入（见 `writeToDisk` 的保护）—— 于是一个新来源键
     * 就能让旧版本的标签/完成态全部写不进去。字符串键读不懂就当"没有来源"，最多少一枚 chip。
     */
    val sources: Map<String, String> = emptyMap(),
    /**
     * entryId -> 提醒触发时间戳（ms）。**存在即已设提醒**（与 [doneAt] 同款约定）。
     *
     * 同样存进这个独立文件：`index.json` 会被老版本整体重写，提醒会丢。
     */
    val reminders: Map<String, Long> = emptyMap(),
    /**
     * entryId -> 这次提醒**已经响过**的时间戳（ms）。
     *
     * 为什么需要它（§0.16.15）：[reminders] 是"待触发"的表，提醒一到点（或面板打开时
     * 被 [StashMetaRepository.clearExpiredReminders] 收尾）就从中消失了 —— 于是卡片上那行 ⏰
     * 会**凭空消失**，用户看到的是"我设的提醒不见了"，而事实是"它已经响过，只是你没处理"。
     * 这一笔把那个事实留下来：卡片据此画灰色的「已提醒」，`StashReminderPendingState`
     * 也据此判断"有没有一条已提醒但没被处理掉的条目"。
     *
     * 清理时机：用户重新设提醒（[StashMetaRepository.setReminder] 传非 null）、
     * 标完成 / 清提醒（传 null）、条目被删（[StashMetaRepository.forget] / `pruneOrphans`）。
     */
    val firedAt: Map<String, Long> = emptyMap(),
) {
    fun isDone(entryId: String): Boolean = doneAt.containsKey(entryId)

    fun tagsOf(entryId: String): List<String> = assignments[entryId].orEmpty()

    fun appendsOf(entryId: String): List<StashAppend> = appends[entryId].orEmpty()

    fun sourceOf(entryId: String): String? = sources[entryId]

    fun reminderOf(entryId: String): Long? = reminders[entryId]

    fun firedAtOf(entryId: String): Long? = firedAt[entryId]
}

@Singleton
class StashMetaRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, META_FILE_NAME)
    // 跨进程安全：进程内互斥 + 跨进程文件锁 + 写完广播。
    // per-file 锁，**不要**和 index.json 共用。
    private val mutex =
        com.slideindex.app.util.CrossProcessStore.CrossProcessMutex(appContext, storeFile)
    // 编解码器见文件顶部 StashMetaJson（单测直接用它，保证测的就是线上配置）。
    private val json = StashMetaJson

    private val _store = MutableStateFlow(StashMetaStore())
    val store: StateFlow<StashMetaStore> = _store.asStateFlow()

    init {
        com.slideindex.app.util.CrossProcessStore.registerListener(appContext, storeFile) {
            _store.value = readFromDiskSync()
        }
        val loaded = readFromDiskSync()
        if (loaded.tags.isEmpty() && !storeFile.exists()) {
            // 仅首次运行灌默认标签；之后以文件为准，用户删掉的标签不会被回灌。
            val seeded = loaded.copy(tags = DEFAULT_TAGS)
            runCatching { writeToDiskSync(seeded) }
                .onFailure { Log.w(TAG, "seed stash meta failed", it) }
            _store.value = if (storeFile.exists()) readFromDiskSync() else seeded
        } else {
            _store.value = loaded
        }
        StashAccess.metaRepository = this
    }

    /* ---------------- 标签 ---------------- */

    fun tags(): List<StashTag> = _store.value.tags.sortedBy { it.order }

    fun tagsOf(entryId: String): List<String> = _store.value.tagsOf(entryId)

    fun isTagged(entryId: String, name: String): Boolean = name in tagsOf(entryId)

    suspend fun setTags(entryId: String, names: List<String>) = mutate { current ->
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val next = current.assignments.toMutableMap()
        if (cleaned.isEmpty()) next.remove(entryId) else next[entryId] = cleaned
        current.copy(assignments = next)
    }

    suspend fun toggleTag(entryId: String, name: String) = mutate { current ->
        val now = current.assignments[entryId].orEmpty()
        val updated = if (name in now) now - name else now + name
        val next = current.assignments.toMutableMap()
        if (updated.isEmpty()) next.remove(entryId) else next[entryId] = updated
        current.copy(assignments = next)
    }

    /**
     * 新增一枚标签定义。@return 是否真的加上了（重名 / 空名 → false，UI 据此提示"已有同名标签"）。
     */
    suspend fun addTag(name: String, colorArgb: Long): Boolean =
        mutateIfChanged { StashTagEdits.add(it, name, colorArgb) }

    /** 删除一枚标签定义，同时清掉所有条目对它的绑定。「待办」拒绝删除。 */
    suspend fun removeTag(name: String): Boolean =
        mutateIfChanged { StashTagEdits.remove(it, name) }

    /**
     * 改名，**连带改写所有条目的绑定**（否则旧名会变成孤儿）；目标名已存在时按合并处理。
     *
     * 具体规则与"为什么"都在 [StashTagEdits.rename]（纯函数，有单测）。
     *
     * @return 是否真的改了。
     */
    suspend fun renameTag(oldName: String, newName: String): Boolean =
        mutateIfChanged { StashTagEdits.rename(it, oldName, newName) }

    /** 改色（「待办」也允许：颜色不参与关键字判定）。 */
    suspend fun setTagColor(name: String, colorArgb: Long): Boolean =
        mutateIfChanged { StashTagEdits.setColor(it, name, colorArgb) }

    /**
     * 把标签挪到第 [targetIndex] 位（标签排序）。
     *
     * ⚠️ **UI 还没接**（§0.16.5）：长按拖拽那套手势单独一轮做，这里先把"重排 = 原子重写
     * 0..n-1"的语义和边界用例（越界夹取、原位不算变化）钉死在单测里，UI 只负责算目标下标。
     *
     * @return 是否真的动了。
     */
    suspend fun moveTag(name: String, targetIndex: Int): Boolean =
        mutateIfChanged { StashTagEdits.move(it, name, targetIndex) }

    /* ---------------- 完成态 ---------------- */

    fun isDone(entryId: String): Boolean = _store.value.isDone(entryId)

    suspend fun setDone(entryId: String, done: Boolean) = mutate { current ->
        val next = current.doneAt.toMutableMap()
        if (done) next[entryId] = System.currentTimeMillis() else next.remove(entryId)
        current.copy(doneAt = next)
    }

    /** 带「待办」标签**且未完成**的条数 —— 把手据此变色。 */
    fun pendingTodoCount(): Int = _store.value.run {
        assignments.count { (entryId, names) ->
            TODO_TAG_NAME in names && !isDone(entryId)
        }
    }

    /* ---------------- 追加 ---------------- */

    fun appendsOf(entryId: String): List<StashAppend> = _store.value.appendsOf(entryId)

    suspend fun appendText(entryId: String, text: String) = mutate { current ->
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            current
        } else {
            val next = current.appends.toMutableMap()
            val list = next[entryId].orEmpty() + StashAppend(trimmed, System.currentTimeMillis())
            next[entryId] = list
            current.copy(appends = next)
        }
    }

    suspend fun removeAppends(entryId: String) = mutate { current ->
        if (entryId !in current.appends) current
        else current.copy(appends = current.appends - entryId)
    }

    /** 去掉最后一段追加（"追加"的撤销）。 */
    suspend fun removeLastAppend(entryId: String) = mutate { current ->
        val list = current.appends[entryId].orEmpty()
        if (list.isEmpty()) {
            current
        } else {
            val next = current.appends.toMutableMap()
            val remaining = list.dropLast(1)
            if (remaining.isEmpty()) next.remove(entryId) else next[entryId] = remaining
            current.copy(appends = next)
        }
    }

    /* ---------------- 来源 ---------------- */

    fun sourceOf(entryId: String): String? = _store.value.sourceOf(entryId)

    /**
     * 记下条目是从哪来的（剪贴板 / 取词 / 图片）。传 null 或空串等于清掉。
     *
     * 只有**非「纯闪念」**的来源才需要记：用户自己记下来的条目没有来源 chip，
     * 这正是设计稿里 `src === 'stash'` 才显示 chip 的语义。
     */
    suspend fun setSource(entryId: String, source: String?) = mutate { current ->
        val next = current.sources.toMutableMap()
        if (source.isNullOrBlank()) next.remove(entryId) else next[entryId] = source
        if (next == current.sources) current else current.copy(sources = next)
    }

    /* ---------------- 提醒 ---------------- */

    fun reminderOf(entryId: String): Long? = _store.value.reminderOf(entryId)

    /**
     * 这条提醒**响过没有**（[StashMetaStore.firedAt]）；返回响过的时间戳，没响过是 null。
     *
     * 卡片上那行灰色「已提醒」画的就是它（见 `HistoryTimelineEntryRow` 的 `reminderOverdue`）。
     */
    fun firedAtOf(entryId: String): Long? = _store.value.firedAtOf(entryId)

    // ⚠️ `isDone(entryId)` 已经在本文件"完成态"那一段定义过（唯一实现留在那边）：
    // `StashReminderPendingState` 的兜底规则就是复用它，这里不要再写一遍 —— 同名同签名
    // 会被编译器判成 "Conflicting overloads"（半成品里确实重复了一次，已删）。

    /** 当前所有待触发提醒（面板打开时用它把闹钟补排一次，见 `StashReminderScheduler`）。 */
    fun pendingReminders(): Map<String, Long> = _store.value.reminders

    /**
     * 设/清提醒。传 null 就是取消。
     *
     * ⚠️ 两个方向都要动 [StashMetaStore.firedAt]（§0.16.15）：
     * - **设**（非 null）：这是一次**新**的提醒，"上次响过"的记录必须清掉，否则卡片会在
     *   新提醒还没到点时就顶着「已提醒」；
     * - **清**（null）：用户主动取消 / 标完成，那一笔也没有意义了，一并清掉。
     *   注意 [clearExpiredReminders] 走的是另一条路（它要**留下** `firedAt`，见那边注释）。
     */
    suspend fun setReminder(entryId: String, atEpochMs: Long?) = mutate { current ->
        val next = current.reminders.toMutableMap()
        if (atEpochMs == null) next.remove(entryId) else next[entryId] = atEpochMs
        val nextFired = if (entryId in current.firedAt) current.firedAt - entryId else current.firedAt
        if (next == current.reminders && nextFired == current.firedAt) {
            current
        } else {
            current.copy(reminders = next, firedAt = nextFired)
        }
    }

    /**
     * 收尾**已经过点**的提醒：删掉它们（第 4 条：过期提醒清理），并同步清掉闹钟与提醒镜像；
     * 同时给它们记一条 [StashMetaStore.firedAt]（"响过了"—— 记的是**那条提醒该响的时刻** `atMs`，
     * 不是"对账这一刻"，理由见规则 3 与实现里的 ⚠️），卡片据此画灰色「已提醒」（§0.16.15）。
     *
     * 「稍后」写回显示（为什么需要它）：用户点了通知上的「稍后 10 分钟」时进程可能没装数据层，
     * 只能把新时间记在 [StashReminderMirror] 的 snooze override 里；于是 meta 里这一条的时间
     * **已经过点**、而真实该响的时间在未来。这种条目不能删，要把 meta 的时间**改成那个更晚的时间**
     * （即 [mergeSnoozeOverrides] 的语义），下次面板打开时才显示对、闹钟也才对。
     *
     * 所以处理规则是：
     * 1. `atMs > now`：不动（这条提醒还没到点）；
     * 2. `atMs <= now` 且 snooze override 在未来：把 meta 改成 override 的时间，**不删**
     *    （用户点过「稍后」、并且还没到那个新时间）；
     * 3. `atMs <= now` 且没有 override、或 override 也过点了：从 [StashMetaStore.reminders] 删掉
     *    （**不是**删这条条目的全部元数据），`AlarmManager.cancel` + 镜像一并清，
     *    并写一条 [StashMetaStore.firedAt] —— "删提醒"和"记下它响过"是一件事的两半：
     *    只删不记，卡片上那行 ⏰ 就会凭空消失（用户实测的抱怨）；只记不删，闹钟会一直挂着。
     *    ⚠️ 这条 `firedAt` 写的是**该条目自己的 `atMs`**（只有 `atMs` 缺失/非正数才退化成 `nowMs`）。
     *    两个理由：① 「已提醒」显示的是"这条提醒响过/到点过"的时刻，本来就是 `atMs`；
     *    ② `StashReminderPendingState` 的流光兜底判据是 `now - firedAt <= 30min`，
     *    写 `nowMs` 会让"搬走一条过期很久的提醒"反而把光点亮半小时。细节在下面实现的 ⚠️ 里。
     *
     * 幂等、可反复调用。
     *
     * 用法（调用点在 `HistoryPanelScreen` 的补排 `LaunchedEffect` 里，由主任务接线）：
     * ```
     * val overrides = StashReminderMirror.snoozeOverrides(appContext)
     * metaRepo?.mergeSnoozeOverrides(overrides)
     * metaRepo?.clearExpiredReminders()
     * // 再用"并过 override 之后"的 reminders 去 rescheduleAll(...)
     * ```
     * ⚠️ 顺序不能反：先 [mergeSnoozeOverrides] 再清，否则第 2 条规则看不到 override、
     * 会把一条其实还有效的提醒删掉（override 也会被一起清掉，救不回来）。
     *
     * @param nowMs 判定"过期"的时间基准，留参数是为了可测（默认当前时间）。
     *   ⚠️ 它**只**用来判过期和在 `atMs` 不可用时兜底，**不再**被写进 `firedAt`（那是 `atMs`）。
     * @return 真正删掉的条数（第 2 条那种"改成更晚时间"的不计入）。
     */
    suspend fun clearExpiredReminders(nowMs: Long = System.currentTimeMillis()): Int {
        // 「稍后」优先：这也是 mergeSnoozeOverrides 的同一份语义，两个方法都要认它。
        val overrides = StashReminderMirror.snoozeOverrides(appContext)
        // 先算出"删哪些 / 改哪些"，副作用（闹钟、镜像、偏好）留到写盘之后再做 ——
        // transform 里尽量只做纯计算，便于推理。
        val expiredRemovals = mutableListOf<String>()
        val overriddenToFuture = mutableListOf<Pair<String, Long>>()
        mutate { current ->
            var changed = false
            val reminders = current.reminders.toMutableMap()
            val fired = current.firedAt.toMutableMap()
            current.reminders.forEach { (entryId, atEpochMs) ->
                if (atEpochMs > nowMs) return@forEach
                val override = overrides[entryId]
                if (override != null && override > nowMs) {
                    // 用户在"稍后"里把它推到了未来，而且那个新时间还没到：保留并纠正时间。
                    reminders[entryId] = override
                    overriddenToFuture += entryId to override
                    changed = true
                } else {
                    // 没有 override，或者 override 自己也是过去时间（闹钟早已响过/被系统丢掉）→ 真过期。
                    reminders.remove(entryId)
                    // ⚠️ 这里**必须**留下"响过了"的痕迹（而不是像 setReminder(null) 那样清掉）：
                    // 卡片上那行 ⏰ 就是靠它从"未来时间"切换成灰色的「已提醒」。
                    //
                    // ⚠️ 记的是**这条提醒该响的时刻 `atEpochMs`**，而不是"对账这一刻" `nowMs`：
                    // - 语义上 `firedAt` 回答的是"这条提醒什么时候响过 / 到点过"，那本来就是 `atMs`；
                    //   卡片那行灰色「已提醒」也应当按这个时刻展示（`stashReminderRow` 把 `firedAt`
                    //   一路传给 `HistoryTimelineGutter` 画"已提醒 昨天 21:30"）。写 `nowMs` 的话，
                    //   "提醒过点三天后用户才打开面板"会把这一行显示成"已提醒 刚刚"，是假信息；
                    // - 流光那边更硬：`StashReminderPendingState` 的兜底判据是
                    //   `now - firedAt <= STALE_PENDING_GRACE_MS`（30 分钟）。写 `nowMs` 会让一条
                    //   **过期很久**的条目在被对账搬走的那一刻被当成"刚响过"→ 光又亮半小时
                    //   （刚修好"通知没了光还亮"，再来一次回光返照更糟）；写 `atMs` 则只有真的
                    //   刚响过的才亮 —— 搬走陈年过期项时，光**当场**就是灭的。
                    //
                    // 兜底：`atMs` 缺失 / 非正数理论上不会发生（这条就是从 `reminders` 里取出来的），
                    // 但真遇上坏数据时宁可退化成 `nowMs`（"它响过"这个语义仍然成立），也不能往
                    // `firedAt` 里塞 0 / 负数：那会让 `recentlyFired` 永远为假，卡片还会显示 1970 年。
                    fired[entryId] = if (atEpochMs > 0L) atEpochMs else nowMs
                    expiredRemovals += entryId
                    changed = true
                }
            }
            if (!changed) current else current.copy(reminders = reminders, firedAt = fired)
        }

        // 闹钟：过期的取消；被"稍后"纠正的按新时间重排 ——
        // 那条闹钟本来就已经被「稍后」排到 override 时间上了，这里重排是幂等的（而且能修好
        // "用户后来在面板里手动改了时间、但 override 还留着"的残留）。
        expiredRemovals.forEach { entryId ->
            StashReminderScheduling.cancel?.invoke(appContext, entryId)
            StashReminderMirror.removeSnoozeOverride(appContext, entryId)
        }
        val overriddenTexts = StashReminderMirror.all(appContext).associate { it.first to it.third }
        overriddenToFuture.forEach { (entryId, atEpochMs) ->
            // 重排时正文取自镜像（meta 不存正文）：镜像里那份就是用户设置时写进去的正文。
            StashReminderScheduling.schedule?.invoke(appContext, entryId, atEpochMs, overriddenTexts[entryId].orEmpty())
            StashReminderMirror.removeSnoozeOverride(appContext, entryId)
        }
        Log.i(
            TAG,
            "clearExpiredReminders: 删除 ${expiredRemovals.size} 条过期提醒，" +
                "按稍后时间顺延 ${overriddenToFuture.size} 条",
        )
        return expiredRemovals.size
    }

    /**
     * 把 [StashReminderMirror] 攒下的「稍后」时间**并回**提醒表。
     *
     * 为什么要有这一步：通知上的「稍后」是在广播进程里点的，那边刻意不碰数据层，
     * 只能把新时间记进 `SharedPreferences`。如果一直不并回来，面板里显示的还是老时间，
     * [StashReminderScheduler.rescheduleAll] 也会照老时间重排 —— 老时间已过点，闹钟立刻响一次。
     *
     * 规则（**只前进不后退**）：override 必须是**未来时间**、且比当前值更晚，才更新 ——
     * 前者保证不会把一个已经过期的提醒"复活"成一条立刻要响的闹钟（那种情况交给
     * [clearExpiredReminders] 删掉），后者防住"用户先手动改成一个更晚的时间、
     * 之后那个旧的 snooze 才被并回来"把提醒往前提。并成功后清掉该 override，避免下次重复并。
     *
     * 幂等。调用点（`HistoryPanelScreen` 的补排 `LaunchedEffect`，由主任务接线）：
     * ```
     * metaRepo?.mergeSnoozeOverrides(StashReminderMirror.snoozeOverrides(appContext))
     * ```
     *
     * @param nowMs 判定"override 还算不算数"的时间基准，留参数是为了可测（默认当前时间）。
     * @return 真正被更新的条数。
     */
    suspend fun mergeSnoozeOverrides(
        overrides: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): Int {
        if (overrides.isEmpty()) return 0
        val merged = mutableListOf<Pair<String, Long>>()
        mutate { current ->
            val reminders = current.reminders.toMutableMap()
            overrides.forEach { (entryId, overrideAt) ->
                // 表里没有这一条的提醒 → 忽略（用户可能已经取消/删除了）。
                val existing = reminders[entryId] ?: return@forEach
                if (overrideAt > nowMs && overrideAt > existing) {
                    reminders[entryId] = overrideAt
                    merged += entryId to overrideAt
                }
            }
            if (merged.isEmpty()) current else current.copy(reminders = reminders)
        }
        merged.forEach { (entryId, atEpochMs) ->
            // 只改镜像里的时间、保留正文：这里拿不到正文（meta 不存），而用空串覆盖
            // 会让这条提醒重启补排后的通知变成"有一条闪念到时间了"这种没有内容的兜底文案。
            StashReminderMirror.updateTime(appContext, entryId, atEpochMs)
            StashReminderMirror.removeSnoozeOverride(appContext, entryId)
        }
        Log.i(TAG, "mergeSnoozeOverrides: 并回 ${merged.size} 条稍后时间")
        return merged.size
    }

    /* ---------------- 清理 ---------------- */

    /** 条目被删除时调用：把它的标签绑定 / 完成态 / 追加内容 / 来源 / 提醒（含"已提醒"记录）一并清掉。 */
    suspend fun forget(entryId: String) = mutate { current ->
        if (entryId !in current.assignments &&
            entryId !in current.doneAt &&
            entryId !in current.appends &&
            entryId !in current.sources &&
            entryId !in current.reminders &&
            entryId !in current.firedAt
        ) {
            current
        } else {
            current.copy(
                assignments = current.assignments - entryId,
                doneAt = current.doneAt - entryId,
                appends = current.appends - entryId,
                sources = current.sources - entryId,
                reminders = current.reminders - entryId,
                firedAt = current.firedAt - entryId,
            )
        }
    }

    /**
     * 清掉所有指向已不存在条目的孤儿元数据。
     *
     * 需要用这个的原因：条目会因为 `MAX_ENTRIES = 200` 被裁掉（`StashRepository.trimToMax`），
     * 而且删除路径不止一处 —— 与其在每个删除点都记得调 `forget`，不如定期按有效 id 收敛。
     */
    suspend fun pruneOrphans(validEntryIds: Collection<String>) = mutate { current ->
        val keep = validEntryIds.toSet()
        val assignments = current.assignments.filterKeys { it in keep }
        val doneAt = current.doneAt.filterKeys { it in keep }
        val appends = current.appends.filterKeys { it in keep }
        val sources = current.sources.filterKeys { it in keep }
        val reminders = current.reminders.filterKeys { it in keep }
        val firedAt = current.firedAt.filterKeys { it in keep }
        if (assignments.size == current.assignments.size &&
            doneAt.size == current.doneAt.size &&
            appends.size == current.appends.size &&
            sources.size == current.sources.size &&
            reminders.size == current.reminders.size &&
            firedAt.size == current.firedAt.size
        ) {
            current
        } else {
            current.copy(
                assignments = assignments,
                doneAt = doneAt,
                appends = appends,
                sources = sources,
                reminders = reminders,
                firedAt = firedAt,
            )
        }
    }

    /* ---------------- 备份 ---------------- */

    /** 备份用：`stash_meta.json` 原文；文件不存在时返回空串。 */
    suspend fun exportRawJson(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (storeFile.exists()) storeFile.readText() else ""
        }
    }

    /**
     * 恢复用：整份写回并同步内存态。
     *
     * 写完之后由调用方负责重排提醒（`StashReminderScheduler.rescheduleAll`）——core 层不依赖 app。
     *
     * @return 内容能解析并落盘时 true。
     */
    suspend fun importRawJson(raw: String): Boolean {
        if (raw.isBlank()) return false
        val decoded = runCatching {
            json.decodeFromString<StashMetaStore>(raw)
        }.getOrNull() ?: return false
        mutex.withLock {
            withContext(Dispatchers.IO) { writeToDiskSync(decoded) }
            readFailed = false
            _store.value = decoded
        }
        com.slideindex.app.util.CrossProcessStore.notifyChanged(appContext, storeFile)
        return true
    }

    /* ---------------- 底层 ---------------- */

    private suspend fun mutate(transform: (StashMetaStore) -> StashMetaStore) {
        mutex.withLock {
            val next = transform(readFromDiskSync())
            writeToDisk(next)
            _store.value = next
        }
    }

    /**
     * 与 [mutate] 同一条写路径，但允许 [transform] 用 `null` 表示"没变化"：
     * 这时**不写盘、不广播**，只回一个 false 给 UI。
     */
    private suspend fun mutateIfChanged(transform: (StashMetaStore) -> StashMetaStore?): Boolean =
        mutex.withLock {
            val current = readFromDiskSync()
            val next = transform(current) ?: return@withLock false
            writeToDisk(next)
            _store.value = next
            true
        }

    private fun readFromDiskSync(): StashMetaStore {
        if (!storeFile.exists()) return StashMetaStore()
        return runCatching { json.decodeFromString<StashMetaStore>(storeFile.readText()) }
            .getOrElse { cause ->
                Log.w(TAG, "stash meta unreadable; refusing to overwrite it", cause)
                readFailed = true
                StashMetaStore()
            }
    }

    /**
     * 读不出来时**不要**写。
     *
     * 所有写路径都是「读全表 → 改 → 整表写回」，读失败返回空表会让下一次写入
     * 把用户的全部元数据覆盖成空。这里直接跳过写入；只要之后有一次成功读取，
     * 标志位就会被清掉，写入自动恢复。
     */
    private suspend fun writeToDisk(store: StashMetaStore) {
        if (readFailed) {
            Log.w(TAG, "skip meta write: last read failed")
            return
        }
        withContext(Dispatchers.IO) { writeToDiskSync(store) }
    }

    private fun writeToDiskSync(store: StashMetaStore) {
        storeFile.writeText(json.encodeToString(store))
        readFailed = false
    }

    @Volatile
    private var readFailed = false

    companion object {
        private const val TAG = "StashMetaRepository"
        private const val META_FILE_NAME = "stash_meta.json"

        /** 与设计稿一致的五枚默认标签。 */
        val DEFAULT_TAGS: List<StashTag> = listOf(
            StashTag(WORK_TAG_NAME, 0xFF5B8DF6, 0),
            StashTag(IDEA_TAG_NAME, 0xFFF0A93B, 1),
            StashTag(TODO_TAG_NAME, 0xFF57B87A, 2),
            StashTag(INSPIRATION_TAG_NAME, 0xFFC77DF0, 3),
            StashTag(DESIGN_TAG_NAME, 0xFFE86E8C, 4),
        )

        /*
         * ⚠️ 下面这五个名字是**内部关键字**：既是 `stash_meta.json` 里 `assignments` 的**存储键**
         * （`{条目 → [标签名]}`），又是语义判定（`TODO_TAG_NAME` = "这条是不是待办"）的依据。
         * **绝不能跟着界面语言变** —— 一变，老库里的绑定就对不上、完成态与把手变色也会错。
         *
         * 界面上的显示名由 `HistoryPanelUi.stashTagLabel()` 在**渲染时**按当前语言给出
         * （各语言的 `stash_tag_default_*` 资源）。所以"英文界面上标签还是中文"这件事
         * 不需要改数据，只需要改显示层。
         */

        /** 「完成」动作只出现在带这枚标签的条目上。 */
        const val TODO_TAG_NAME = "待办"

        /** 默认标签「工作」的内部名（显示名走资源，见上方说明）。 */
        const val WORK_TAG_NAME = "工作"

        /** 默认标签「想法」的内部名。 */
        const val IDEA_TAG_NAME = "想法"

        /** 默认标签「灵感」的内部名。 */
        const val INSPIRATION_TAG_NAME = "灵感"

        /** 默认标签「设计」的内部名。 */
        const val DESIGN_TAG_NAME = "设计"

        /** 来源：从剪贴板「暂存到闪念」。 */
        const val SOURCE_CLIPBOARD = "clipboard"

        /** 来源：取词面板里暂存。 */
        const val SOURCE_PICK = "pick"

        /** 来源：图片（编辑器 / 截图）存进闪念。 */
        const val SOURCE_IMAGE = "image"
    }
}
