package com.slideindex.app.stash

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 闪念提醒的**镜像**：`SharedPreferences` 里一份"哪条、什么时候、正文是什么"的副本。
 *
 * 为什么需要第二份数据（真正的表在 [StashMetaRepository] 的 `stash_meta.json` 里）：
 * 设备重启 / 应用更新 / 改时区之后 `AlarmManager` 里的闹钟会被系统清掉，要补排就得**在开机广播里**
 * 读一遍"有哪些提醒"。而那会儿进程是被广播冷启动的 —— Hilt 图还没构造、`stash_meta.json`
 * 还要过 `CrossProcessStore` 的文件锁，为一个补排动作走整条依赖链太重、也太容易翻车。
 * `SharedPreferences` 是纯文件读、无依赖注入、无协程，恰好在广播的 10 秒窗口里够用。
 *
 * 另一份职责是**记录「稍后 10 分钟」的结果**（[putSnoozeOverride]）：
 * 通知里点"稍后"时进程可能是被广播拉起来的，那时写不了数据层；但用户下次打开面板看到的
 * 必须是那个新时间。于是先把新时间记在这里，等面板打开时再并回 meta
 * （[StashMetaRepository.mergeSnoozeOverrides]）。
 *
 * 第三份职责是**通知自身的状态**（[putNotifiedAt] / [markDismissed]）：`StashReminderPendingState`
 * 要知道"这条提醒的通知还挂着吗 / 用户是不是刚划掉了它"。它同样活在偏好里，理由一样 ——
 * 通知的收发发生在没有数据层的进程里。
 *
 * ⚠️ 这里**只是镜像**，不是真相：它随时可能因为进程被杀而丢掉最后一次写；丢了的最坏后果是
 * "重启后这一次提醒没补排"或者"面板里显示的时间旧了一点"，不会损坏 `stash_meta.json`。
 * 反过来，任何写 meta 成功的地方都应该顺手镜像一份（[StashReminderScheduler] 已经这么做了）。
 *
 * ⚠️ 正文自己序列化（不用 kotlinx.serialization）：条目正文里什么都有 —— 换行、引号、
 * 控制字符（含 `\u0000`；用 `"$at\u0000$text"` 这种裸拼就是在这里翻车的）。
 * 一个小 JSON 对象 + 转义最省事，也不需要为一个字段多引一层编解码器。
 */
object StashReminderMirror {
    private const val TAG = "StashReminderMirror"

    private const val PREFS_NAME = "stash_remind_mirror"
    private const val PREFS_NAME_SNOOZE = "stash_remind_snooze_mirror"
    private const val PREFS_NAME_NOTIFIED = "stash_remind_notified_mirror"

    private const val KEY_AT = "at"
    private const val KEY_TEXT = "text"

    /**
     * 记下/刷新一条提醒。
     *
     * 用 `commit()`（同步落盘）而不是 `apply()`：调用点里既有广播接收器（进程随时可能被回收），
     * 也有"写完就 `finish()` 的 Activity"；异步落盘在这两种场景下都可能根本没跑完。
     * 单条记录很小，同步写的代价可以接受。
     *
     * @return 是否落盘成功（失败只记日志，调用方不需要处理 —— 镜像丢一份不影响主流程）。
     */
    fun put(context: Context, entryId: String, atEpochMs: Long, text: String): Boolean {
        val payload = encode(atEpochMs, text)
        val ok = prefs(context).edit().putString(entryId, payload).commit()
        if (!ok) Log.w(TAG, "put 落盘失败 entryId=$entryId")
        return ok
    }

    /** 删掉一条提醒的镜像。取消提醒 / 条目被删 / 完成时都要调，否则会在重启后"复活"闹钟。 */
    fun remove(context: Context, entryId: String) {
        prefs(context).edit().remove(entryId).commit()
    }

    /**
     * 只把时间改成 [atEpochMs]，**保留原有正文**。
     *
     * 给 [StashMetaRepository.mergeSnoozeOverrides] 用：那里只有"新时间"（meta 不存正文、
     * snooze override 也只存时间），如果用 [put] 就得传一个空正文，会把补排后的通知内容抹掉。
     * 镜像里没有这条记录时**什么都不做**（说明它本来就没有提醒）。
     */
    fun updateTime(context: Context, entryId: String, atEpochMs: Long) {
        val current = decode(prefs(context).getString(entryId, null)) ?: return
        put(context, entryId, atEpochMs, current.second)
    }

    /**
     * 当前镜像里的全部提醒。
     *
     * 解析不了的记录**直接跳过**（并留一行日志）：镜像不是真相，宁可少补排一条，
     * 也不能因为一条脏数据让整个开机广播抛异常 —— 那样所有提醒都不补排了。
     */
    fun all(context: Context): List<Triple<String, Long, String>> {
        val entries = prefs(context).all
        if (entries.isEmpty()) return emptyList()
        val result = ArrayList<Triple<String, Long, String>>(entries.size)
        entries.forEach { (entryId, raw) ->
            val decoded = decode(raw as? String)
            if (decoded == null) {
                Log.w(TAG, "镜像记录解析失败，跳过 entryId=$entryId")
            } else {
                result += Triple(entryId, decoded.first, decoded.second)
            }
        }
        return result
    }

    /**
     * 记下"用户点了稍后，新的到点时间是 this"。
     *
     * 与 [put] 的区别是**语义**：`put` 是"表里的时间就是这个"，override 是"表里的时间旧了，
     * 打开 App 时请把它改成这个"。[StashMetaRepository.clearExpiredReminders] /
     * [StashMetaRepository.mergeSnoozeOverrides] 会把 override 并回去，并清掉它。
     */
    fun putSnoozeOverride(context: Context, entryId: String, atEpochMs: Long) {
        val ok = prefsSnooze(context).edit().putLong(entryId, atEpochMs).commit()
        if (!ok) Log.w(TAG, "putSnoozeOverride 落盘失败 entryId=$entryId")
    }

    /** 当前还没并回 meta 的稍后时间（entryId -> atEpochMs）。 */
    fun snoozeOverrides(context: Context): Map<String, Long> {
        val entries = prefsSnooze(context).all
        if (entries.isEmpty()) return emptyMap()
        val result = HashMap<String, Long>(entries.size)
        entries.forEach { (entryId, raw) ->
            // `all` 的值类型由存进去的 `Long` 决定，但跨进程/坏文件时给个兜底，读不动就当没有。
            (raw as? Long)?.let { result[entryId] = it }
        }
        return result
    }

    /** 并回 meta 之后清掉；提醒被取消/完成/删除时也要清（否则残留的 override 会在下次并回时复活提醒）。 */
    fun removeSnoozeOverride(context: Context, entryId: String) {
        prefsSnooze(context).edit().remove(entryId).commit()
    }

    /* ---------------- 通知的"发出 / 被划掉"（给 `StashReminderPendingState` 用） ---------------- */

    /**
     * 提醒通知**真的提交给系统了**（[android.app.NotificationManager.notify] 没抛异常）。
     *
     * 为什么要单独记一笔：`StashReminderPendingState` 判断"有未处理的提醒"时，数据层兜底是
     * "提醒时间已过 且 条目未完成"，但**划掉通知**只清 pending、刻意不改数据层 —— 没有这个
     * 时间戳的话，下一次 refresh 会立刻用兜底规则把刚灭掉的状态重新点亮。
     *
     * 存在**独立的偏好文件**里而不是复用 [PREFS_NAME]：那个文件是 `entryId -> JSON` 的扁平表，
     * [all] 会把每个 key 都当一条提醒记录去解析，塞进非 JSON 的值只会让每次 [all] 多刷一行
     * "解析失败"日志。宁可多一个文件，也不要在同一张表里混两种语义。
     */
    fun putNotifiedAt(context: Context, entryId: String, atEpochMs: Long) {
        val ok = prefsNotified(context).edit().putLong(entryId, atEpochMs).commit()
        if (!ok) Log.w(TAG, "putNotifiedAt 落盘失败 entryId=$entryId")
    }

    /**
     * 该条提醒的通知**发出之后**是否被用户划掉过（`dismissedAt >= notifiedAt`）。
     *
     * 比较两个时间戳而不是存一个布尔量：布尔量在"这次划掉、之后又是一条新提醒"时只能靠
     * 调用方记得来清，漏清一次就会让新提醒的通知永远点不亮指示条；时间戳则在
     * 新通知重新 [putNotifiedAt] 的那一刻**自动作废**（旧时间 < 新的 notifiedAt）。
     */
    fun dismissedSinceNotified(context: Context, entryId: String): Boolean {
        val dismissedAt = prefsNotified(context).getLong(dismissedKey(entryId), 0L)
        if (dismissedAt <= 0L) return false
        val notifiedAt = prefsNotified(context).getLong(entryId, 0L)
        return dismissedAt >= notifiedAt
    }

    /** 用户划掉了这条提醒的通知（`setDeleteIntent`）。 */
    fun markDismissed(context: Context, entryId: String, atEpochMs: Long) {
        prefsNotified(context).edit().putLong(dismissedKey(entryId), atEpochMs).commit()
    }

    /**
     * 曾经**真的发出去过**通知的那些 entryId（不管后来有没有被划掉 / 完成 / 正在不在栏里）。
     *
     * 用途只有一个：`StashReminderPendingState` 要拿"我关心的这批通知 id"去和
     * `NotificationManager.getActiveNotifications()` 对（那个 API 拿不到通知的 extras，
     * 没法反查 entryId）。数据层在的时候那份清单来自 `reminders`；**数据层还没挂上时**
     * （进程刚被广播拉起来、Hilt 还在构造）就只剩这里这一份 —— 没有它，指示条在这种
     * 时序下会永远点不亮。
     *
     * 为什么"过期不清理"是安全的：调用方**只会**拿这些 id 去问"它现在还在通知栏里吗"，
     * 残留的 id 最多多比一次 `Set.contains`，不可能点亮相册里已经不在的通知。
     */
    fun notifiedEntryIds(context: Context): Set<String> =
        prefsNotified(context).all.keys.filterNot { it.startsWith(DISMISSED_KEY_PREFIX) }.toSet()

    /** 提醒被完成 / 取消 / 删除时调用：这一笔"通知状态"已经没有意义了。 */
    fun clearNotified(context: Context, entryId: String) {
        prefsNotified(context).edit().remove(entryId).remove(dismissedKey(entryId)).commit()
    }

    /** 划掉时间的 key 前缀。加前缀是为了和"通知发出的时间"同表共存又不撞名。 */
    private const val DISMISSED_KEY_PREFIX = "dismissed:"

    private fun dismissedKey(entryId: String): String = "$DISMISSED_KEY_PREFIX$entryId"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun prefsSnooze(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME_SNOOZE, Context.MODE_PRIVATE)

    private fun prefsNotified(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME_NOTIFIED, Context.MODE_PRIVATE)

    /* ---------------- JSON（只有一个 {at, text} 对象，手写够了） ---------------- */

    private fun encode(atEpochMs: Long, text: String): String =
        buildString(text.length + 24) {
            append("{\"").append(KEY_AT).append("\":").append(atEpochMs)
            append(",\"").append(KEY_TEXT).append("\":\"")
            append(escape(text))
            append("\"}")
        }

    /**
     * @return `(atEpochMs, text)`；结构不对 / 值类型不对时返回 null（见 [all] 的取舍）。
     */
    private fun decode(raw: String?): Pair<Long, String>? {
        val body = raw?.trim()?.takeIf { it.length >= 2 && it.first() == '{' && it.last() == '}' }
            ?: return null
        val at = readLong(body, KEY_AT) ?: return null
        val text = readString(body, KEY_TEXT) ?: return null
        return at to text
    }

    /** 找 `"key":<数字>`。keys 都是我们自己写的短键名，不会出现在被转义的正文里（正文里的引号一定带反斜杠）。 */
    private fun readLong(body: String, key: String): Long? {
        val marker = "\"$key\":"
        val start = body.indexOf(marker)
        if (start < 0) return null
        var i = start + marker.length
        while (i < body.length && body[i] == ' ') i++
        val begin = i
        while (i < body.length && (body[i] == '-' || body[i].isDigit())) i++
        if (i == begin) return null
        return body.substring(begin, i).toLongOrNull()
    }

    /** 找 `"key":"<字符串>"`，并把转义还原。 */
    private fun readString(body: String, key: String): String? {
        val marker = "\"$key\":\""
        val start = body.indexOf(marker)
        if (start < 0) return null
        val out = StringBuilder()
        var i = start + marker.length
        while (i < body.length) {
            when (val c = body[i]) {
                '"' -> return out.toString()
                '\\' -> {
                    i++
                    if (i >= body.length) return null
                    when (val esc = body[i]) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            // 代理对（emoji 等）是两个连续的 \uXXXX，拼起来交给 String 自己合。
                            if (i + 4 > body.lastIndex) return null
                            val hex = body.substring(i + 1, i + 5).toIntOrNull(16) ?: return null
                            out.append(hex.toChar())
                            i += 4
                        }
                        else -> out.append(esc)
                    }
                }
                else -> out.append(c)
            }
            i++
        }
        // 没有收尾引号 = 记录被截断，按解析失败处理。
        return null
    }

    /** 只转义 JSON 要求的字符；其余（中文、emoji）原样留着，省空间也好排查。 */
    private fun escape(text: String): String {
        val out = StringBuilder(text.length + 8)
        text.forEach { c ->
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                // 其余 C0 控制字符（含 \u0000）必须转义，否则落进 XML 的偏好文件会读不回来。
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        return out.toString()
    }
}
