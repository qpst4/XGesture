package com.slideindex.app.otp

import android.content.Context
import com.slideindex.app.common.repositoryRunCatching
import com.slideindex.app.otp.OtpRecordCategory
import com.slideindex.app.otp.OtpRecordLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtpRecordsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val recordsFile = File(appContext.filesDir, RECORDS_FILE_NAME)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _records = MutableStateFlow<List<OtpRecord>>(emptyList())
    val records: StateFlow<List<OtpRecord>> = _records.asStateFlow()

    @Volatile
    private var limits: Map<OtpRecordCategory, Int> = DEFAULT_LIMITS

    /**
     * 写操作统一入口：进程内 mutex + **跨进程文件锁**；写完广播通知其它进程重载。
     * 各写方法内部是"锁内重新读盘再计算"，套上这层即可跨进程安全。
     */
    private suspend fun <T> withCrossProcessWrite(block: suspend () -> T): T =
        mutex.withLock {
            com.slideindex.app.util.CrossProcessStore.withFileLock(recordsFile) {
                val result = block()
                com.slideindex.app.util.CrossProcessStore.notifyChanged(appContext, recordsFile)
                result
            }
        }

    private suspend fun reloadFromDiskForExternalChange() {
        refreshFromDisk()
    }

    /**
     * 强制读一次盘（顺带把"过期的填充中"结掉）。
     *
     * 为什么要手动刷：记录状态是跨进程写的，进程之间只靠一条写盘广播互相通知重新读盘 ——
     * 广播发给了动态注册的接收者，对方没活着 / 还没注册就**直接丢了且不会补发**，
     * 于是内存快照会停在旧状态（真机现象：文件里早就是"无障碍填充"了，记录页还显示"填充中"，
     * 等到下一次别的进程写文件才突然变过来）。界面打开/回到前台时调这个方法就不会再骗人。
     *
     * 顺带做超时兜底：抓码后 [PENDING_STALE_TIMEOUT_MS] 还没等到任何填充结果 = 请求丢了
     * （跨进程广播丢、浮层进程被杀等），直接落成失败，而不是永远挂在"填充中"。
     */
    suspend fun refreshFromDisk() {
        mutex.withLock {
            com.slideindex.app.util.CrossProcessStore.withFileLock(recordsFile) {
                val loaded = trimByCategory(readFromDisk())
                val healed = reconcileStalePending(loaded)
                if (healed != loaded) {
                    runCatching { writeToDisk(healed) }
                    com.slideindex.app.util.CrossProcessStore.notifyChanged(appContext, recordsFile)
                }
                _records.value = healed
            }
        }
    }

    /** 把"抓码后一直没等到填充结果"的记录结掉，见 [PENDING_STALE_TIMEOUT_MS]。 */
    private fun reconcileStalePending(
        items: List<OtpRecord>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<OtpRecord> = items.map { record ->
        if (record.autoFillStatus == OtpRecordFillStatus.PENDING &&
            nowMs - record.timestampMs >= PENDING_STALE_TIMEOUT_MS
        ) {
            record.copy(
                autoFillStatus = OtpRecordFillStatus.FAILED,
                autoFillReason = OtpAutoFillReasons.NO_RESULT,
            )
        } else {
            record
        }
    }

    /**
     * 由 App 侧设置同步器推入每类记录条数上限；0 表示该类不记录。
     *
     * 仓库位于 `:feature:otp`，不依赖 `:feature:settings`，因此上限通过推入方式下发。
     */
    fun setCategoryLimits(codeLimit: Int, plainSmsLimit: Int, appNotifyLimit: Int) {
        limits = mapOf(
            OtpRecordCategory.CODE to OtpRecordLimits.normalize(codeLimit),
            OtpRecordCategory.PLAIN_SMS to OtpRecordLimits.normalize(plainSmsLimit),
            OtpRecordCategory.APP_NOTIFY to OtpRecordLimits.normalize(appNotifyLimit),
            OtpRecordCategory.TEST to OtpRecordLimits.normalize(codeLimit),
        )
    }

    init {
        com.slideindex.app.util.CrossProcessStore.registerListener(appContext) { changed ->
            if (changed.absolutePath == recordsFile.absolutePath) {
                scope.launch { reloadFromDiskForExternalChange() }
            }
        }
        scope.launch {
            withCrossProcessWrite {
                val loaded = readFromDisk()
                val trimmed = trimByCategory(loaded)
                val healed = reconcileStalePending(trimmed)
                if (healed != loaded) {
                    runCatching { writeToDisk(healed) }
                }
                _records.value = healed
            }
        }
    }

    fun record(
        code: String,
        packageName: String,
        title: String,
        text: String,
        timestampMs: Long = System.currentTimeMillis(),
        ruleName: String? = null,
        isTest: Boolean = false,
        autoFillStatus: OtpRecordFillStatus = OtpRecordFillStatus.NONE,
        category: OtpRecordCategory = if (isTest) OtpRecordCategory.TEST else OtpRecordCategory.CODE,
        simSlot: Int = -1,
    ) {
        scope.launch {
            recordSuspend(
                code = code,
                packageName = packageName,
                title = title,
                text = text,
                timestampMs = timestampMs,
                ruleName = ruleName,
                isTest = isTest,
                autoFillStatus = autoFillStatus,
                category = category,
                simSlot = simSlot,
            )
        }
    }

    suspend fun recordSuspend(
        code: String,
        packageName: String,
        title: String,
        text: String,
        timestampMs: Long = System.currentTimeMillis(),
        ruleName: String? = null,
        isTest: Boolean = false,
        autoFillStatus: OtpRecordFillStatus = OtpRecordFillStatus.NONE,
        category: OtpRecordCategory = if (isTest) OtpRecordCategory.TEST else OtpRecordCategory.CODE,
        simSlot: Int = -1,
    ): Result<String?> = withCrossProcessWrite {
        repositoryRunCatching {
            val limit = limits[category] ?: OtpRecordLimits.DEFAULT
            if (limit <= 0) return@repositoryRunCatching null
            val current = readFromDisk()
            // Notification and LSPosed SMS channels can report the same code within
            // a few hundred milliseconds. De-duplicate across those two categories,
            // while keeping PLAIN_SMS (no code) isolated by its original text.
            val duplicate = current.firstOrNull { existing ->
                val sameCode = existing.code == code &&
                    (timestampMs - existing.timestampMs) in 0..DEDUPE_WINDOW_MS
                if (category == OtpRecordCategory.PLAIN_SMS) {
                    sameCode && existing.category == OtpRecordCategory.PLAIN_SMS &&
                        existing.text == text
                } else {
                    sameCode && existing.category != OtpRecordCategory.PLAIN_SMS
                }
            }
            if (duplicate != null) return@repositoryRunCatching duplicate.id

            val entry = OtpRecord(
                code = code,
                packageName = packageName,
                title = title,
                text = text,
                timestampMs = timestampMs,
                ruleName = ruleName,
                isTest = isTest,
                autoFillStatus = autoFillStatus,
                category = category,
                simSlot = simSlot,
            )
            val trimmed = trimByCategory(listOf(entry) + current)
            writeToDisk(trimmed)
            _records.value = trimmed
            entry.id
        }
    }

    suspend fun updateAutoFillOutcome(
        id: String,
        success: Boolean,
        strategy: String,
        reason: String = "",
    ): Result<Unit> = withCrossProcessWrite {
        repositoryRunCatching {
            val status = OtpRecordFillStatus.fromFillResult(success, strategy)
            val fillReason = if (!success) reason.takeIf { it.isNotBlank() } else null
            val next = readFromDisk().map { record ->
                if (record.id == id) {
                    record.copy(autoFillStatus = status, autoFillReason = fillReason)
                } else {
                    record
                }
            }
            writeToDisk(next)
            _records.value = next
        }
    }

    suspend fun delete(id: String): Result<Unit> = withCrossProcessWrite {
        repositoryRunCatching {
            val next = readFromDisk().filterNot { it.id == id }
            writeToDisk(next)
            _records.value = next
        }
    }

    suspend fun exportRawJson(): String? = withCrossProcessWrite {
        if (!recordsFile.exists()) return@withCrossProcessWrite null
        withContext(Dispatchers.IO) { recordsFile.readText() }
    }

    suspend fun importRawJson(json: String): Result<Unit> = withCrossProcessWrite {
        repositoryRunCatching {
            val decoded = OtpRecordCodec.decode(json)
            val trimmed = decoded.take(MAX_RECORDS)
            writeToDisk(trimmed)
            _records.value = trimmed
        }
    }

    suspend fun clearAll(): Result<Unit> = withCrossProcessWrite {
        repositoryRunCatching {
            writeToDisk(emptyList())
            _records.value = emptyList()
        }
    }

    private suspend fun readFromDisk(): List<OtpRecord> = withContext(Dispatchers.IO) {
        if (!recordsFile.exists()) return@withContext emptyList()
        runCatching {
            OtpRecordCodec.decode(recordsFile.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun writeToDisk(items: List<OtpRecord>) = withContext(Dispatchers.IO) {
        recordsFile.writeText(OtpRecordCodec.encode(items))
    }

    /** 按分类各自保留最新 [limits] 条，再套一层总上限。 */
    private fun trimByCategory(items: List<OtpRecord>): List<OtpRecord> {
        val counts = mutableMapOf<OtpRecordCategory, Int>()
        val kept = items.filter { item ->
            val limit = limits[item.category] ?: OtpRecordLimits.DEFAULT
            if (limit <= 0) return@filter false
            val seen = counts[item.category] ?: 0
            if (seen >= limit) return@filter false
            counts[item.category] = seen + 1
            true
        }
        return kept.take(MAX_RECORDS)
    }

    companion object {
        private const val RECORDS_FILE_NAME = "otp_records.json"
        const val MAX_RECORDS = 200
        const val DEDUPE_WINDOW_MS = 60_000L

        /**
         * 抓码后等这么久还是没有填充结果，就判"请求丢了"。
         *
         * 正常链路的上限大约是：提取延迟（≤5s，实际 200ms）+ 注入回执 2s + 无障碍填一次（1～2s），
         * 所以 20s 已经是宽裕的两倍余量；真结果晚到时 [updateAutoFillOutcome] 还会按 id 覆盖回正确状态。
         */
        const val PENDING_STALE_TIMEOUT_MS = 20_000L

        private val DEFAULT_LIMITS = mapOf(
            OtpRecordCategory.CODE to OtpRecordLimits.DEFAULT,
            OtpRecordCategory.PLAIN_SMS to OtpRecordLimits.DEFAULT,
            OtpRecordCategory.APP_NOTIFY to OtpRecordLimits.DEFAULT,
            OtpRecordCategory.TEST to OtpRecordLimits.DEFAULT,
        )
    }
}
