package com.slideindex.app.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.slideindex.app.clipboard.monitor.ClipboardMonitorController
import com.slideindex.app.clipboard.monitor.ClipboardMonitorProcess
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.effectiveClipboardMonitoringMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusStore
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusProbe
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Singleton
class ClipboardHistoryRepository @Inject constructor(
    @ApplicationContext appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val clipboardMonitorController: ClipboardMonitorController
) : com.slideindex.app.clipboard.ClipboardHistoryQuery {
    private val context = appContext.applicationContext
    private val storageDir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    private val indexFile = File(storageDir, INDEX_FILE_NAME)
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val store = ClipboardHistoryStore(context, json)
    private val _entryCount = MutableStateFlow(0)
    override val entryCount: StateFlow<Int> = _entryCount.asStateFlow()
    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 上次主动探测 LSPosed 模块状态的时间（节流用）。 */
    private var lastModuleProbeAtMs = 0L

    private var screenshotMonitor: ScreenshotMonitor? = null
    private var lastCapturedKey: String? = null
    private var lastCapturedFingerprint: String? = null
    private var lastCapturedAtMs: Long = 0L
    private var lastScreenshotIngestAtMs: Long = 0L
    private var outgoingWriteKey: String? = null
    private var outgoingWriteFingerprint: String? = null
    private var outgoingWriteSuppressUntilMs: Long = 0L
    private var suppressedOutgoingEntryId: String? = null
    private var skipIngestRemaining: Int = 0
    private val inFlightFingerprints = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    init {
        ClipboardAccess.repository = this
        com.slideindex.app.clipboard.ClipboardService.query = this
        migrateLegacyJsonIfNeeded()
        refreshEntryCount()
        clipboardMonitorController.onPayloadCaptured = { payload ->
            ingestPayload(payload, showOverlay = true)
        }
    }

    suspend fun addPayload(
        payload: ClipboardPayload,
        promoteExistingOnMatch: Boolean = true,
        fromPassiveRefresh: Boolean = false
    ) {
        if (payload.text.trim().isEmpty() &&
            payload.uri.isNullOrBlank() &&
            payload.intentUri.isNullOrBlank() &&
            !payload.hasImageContent()
        ) {
            return
        }
        // 超长文本不落库：先在入口拦掉，避免先写图片文件、再被 store 丢弃留下孤儿文件。
        if (ClipboardEntrySizeLimit.isTextTooLarge(payload)) return
        writeMutex.withLock {
            val contentKey = payload.contentKey()
            val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
            findMatchingEntry(payload)?.let { existing ->
                if (!promoteExistingOnMatch) return
                if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
                lastCapturedKey = contentKey
                lastCapturedFingerprint = fingerprint
                lastCapturedAtMs = System.currentTimeMillis()
                promoteExistingEntry(existing)
                return
            }
            val entryId = UUID.randomUUID().toString()
            val imageFileNames = persistPayloadImages(entryId, payload)
            val imageSources = ClipboardImageStore.collectImageSources(payload)
            val baseEntry = payload.toEntry(
                id = entryId,
                createdAtEpochMs = System.currentTimeMillis()
            ).copy(
                imageFileName = imageFileNames.firstOrNull() ?: payload.imageFileName,
                imageFileNames = imageFileNames.ifEmpty { payload.resolvedImageFileNames() }
            )
            val entry = baseEntry.copy(
                contentBlocks = ClipboardBlockParser.buildBlocks(
                    text = baseEntry.text,
                    htmlText = baseEntry.htmlText,
                    imageFileNames = baseEntry.resolvedImageFileNames(),
                    imageSources = imageSources
                )
            )
            if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
            removeMatchingEntries(payload, fingerprint)
            lastCapturedKey = contentKey
            lastCapturedFingerprint = fingerprint
            lastCapturedAtMs = System.currentTimeMillis()
            store.insert(entry)
            applyTrimToConfiguredMaxLocked()
            bumpRevisionLocked()
        }
    }

    suspend fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        addPayload(
            ClipboardPayload(
                type = ClipboardEntryType.TEXT,
                text = trimmed
            )
        )
    }

    override suspend fun delete(id: String) {
        writeMutex.withLock {
            val removed = store.queryById(id)
            removed?.let { ClipboardImageStore.deleteEntryImages(context, it) }
            store.delete(id)
            bumpRevisionLocked()
        }
    }

    suspend fun clearAll() {
        writeMutex.withLock {
            var cursor: Long? = null
            while (true) {
                val batch = store.queryPageBefore(cursor, 100)
                if (batch.isEmpty()) break
                batch.forEach { ClipboardImageStore.deleteEntryImages(context, it) }
                if (batch.size < 100) break
                cursor = batch.last().createdAtEpochMs
            }
            store.deleteAll()
            bumpRevisionLocked()
        }
    }

    suspend fun trimToConfiguredMax() {
        writeMutex.withLock {
            applyTrimToConfiguredMaxLocked()
            bumpRevisionLocked()
        }
    }

    /**
     * 兜底补读：主动读一次系统剪贴板，把最新一条补进历史。
     *
     * 用途：特权监听失效期间（Shizuku 没起来、监听服务被停、binder 掉线）复制的内容，
     * 在用户进入应用或打开剪贴板界面时补回来。
     *
     * 与「监听事件驱动的入库」相比：
     * - 不弹复制预览浮窗、不置顶已存在的内容；
     * - 无视「自己写剪贴板」的 skip 计数：这是一次主动采样，不是在消化事件流；
     * - 无视「截图保护」：用户主动打开界面的意图优先于该保护。
     *
     * @param skipWhenListening 监听正常时不重复补读（进应用、打开剪贴板浮窗用 true；
     *   剪贴板面板用 false，保持「打开面板总能刷新一次」的既有行为）。
     */
    fun catchUpLatestClipboard(
        triggerContext: Context? = null,
        skipWhenListening: Boolean = true,
    ) {
        // 只有默认进程参与补读：引擎进程与 Shizuku 用户服务等旁支进程没有剪贴板宿主。
        if (!com.slideindex.app.util.AppProcess.isMain) return
        if (skipWhenListening) {
            if (!settingsRepository.readSnapshot().clipboardBackgroundMonitoring) return
            if (clipboardMonitorController.isListening) return
        }
        val readContext = triggerContext ?: context
        val direct = ClipboardReader.read(readContext)
        if (direct != null) {
            ClipboardReadTelemetry.onCatchUp(readContext, "direct", true)
            ingestCatchUpPayload(direct)
            return
        }
        // 直接读不到通常是还没有窗口焦点（例如刚回到前台），用焦点探针补一次。
        ClipboardFocusReader.read(readContext) { payload ->
            ClipboardReadTelemetry.onCatchUp(readContext, "focus", payload != null)
            if (payload != null) ingestCatchUpPayload(payload)
        }
    }

    private fun ingestCatchUpPayload(payload: ClipboardPayload) {
        ingestPayload(
            payload = payload,
            promoteExistingOnMatch = false,
            fromPassiveRefresh = false,
            showOverlay = false,
            bypassOutgoingSkip = true,
        )
    }

    fun noteOutgoingWrite(entry: ClipboardEntry) {
        val key = entry.contentKey()
        val fingerprint = ClipboardContentEquivalence.fingerprint(entry)
        lastCapturedKey = key
        lastCapturedFingerprint = fingerprint
        lastCapturedAtMs = System.currentTimeMillis()
        outgoingWriteKey = key
        outgoingWriteFingerprint = fingerprint
        suppressedOutgoingEntryId = entry.id
        outgoingWriteSuppressUntilMs = System.currentTimeMillis() + 4_000L
        skipIngestRemaining = 3
        clipboardMonitorController.config.ignoreOwnClipboardWrite()
    }

    fun promoteById(id: String) {
        scope.launch {
            writeMutex.withLock {
                val existing = store.queryById(id) ?: return@withLock
                promoteExistingEntry(existing)
            }
        }
    }

    fun ingestPayload(
        payload: ClipboardPayload,
        promoteExistingOnMatch: Boolean = true,
        fromPassiveRefresh: Boolean = false,
        showOverlay: Boolean = false,
        bypassOutgoingSkip: Boolean = false,
    ) {
        if (!bypassOutgoingSkip && consumeOutgoingWriteSkip()) return
        if (payload.text.trim().isEmpty() &&
            payload.uri.isNullOrBlank() &&
            payload.intentUri.isNullOrBlank() &&
            !payload.hasImageContent()
        ) {
            return
        }
        val contentKey = payload.contentKey()
        val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
        val now = System.currentTimeMillis()
        if ((contentKey == lastCapturedKey || fingerprint == lastCapturedFingerprint) &&
            now - lastCapturedAtMs < SAME_CLIP_DEDUP_MS
        ) {
            maybeShowClipboardOverlay(payload, fromPassiveRefresh, showOverlay)
            if (promoteExistingOnMatch) {
                promoteExistingPayloadIfNeeded(payload, fromPassiveRefresh)
            }
            return
        }
        if (now < outgoingWriteSuppressUntilMs) {
            if (contentKey == outgoingWriteKey || fingerprint == outgoingWriteFingerprint) {
                lastCapturedKey = contentKey
                lastCapturedFingerprint = fingerprint
                lastCapturedAtMs = now
                return
            }
            suppressedOutgoingEntryId?.let { entryId ->
                val suppressedEntry = store.queryById(entryId)
                if (suppressedEntry != null && ClipboardContentEquivalence.matches(suppressedEntry, payload)) {
                    lastCapturedKey = contentKey
                    lastCapturedFingerprint = fingerprint
                    lastCapturedAtMs = now
                    return
                }
            }
        }
        if (!inFlightFingerprints.add(fingerprint)) {
            if (promoteExistingOnMatch) {
                promoteExistingPayloadIfNeeded(payload, fromPassiveRefresh)
            }
            return
        }
        lastCapturedKey = contentKey
        lastCapturedFingerprint = fingerprint
        lastCapturedAtMs = now
        maybeShowClipboardOverlay(payload, fromPassiveRefresh, showOverlay)
        scope.launch {
            try {
                addPayload(payload, promoteExistingOnMatch, fromPassiveRefresh)
            } finally {
                inFlightFingerprints.remove(fingerprint)
            }
        }
    }

    private fun maybeShowClipboardOverlay(
        payload: ClipboardPayload,
        fromPassiveRefresh: Boolean,
        showOverlay: Boolean,
    ) {
        if (!showOverlay || fromPassiveRefresh) return
        if (!settingsRepository.readSnapshot().clipboardOverlayEnabled) return
        mainHandler.post {
            com.slideindex.app.clipboardoverlay.ClipboardOverlayWindow.show(context, payload)
        }
    }

    fun ingestCapturedText(text: String) {
        ingestPayload(
            ClipboardPayload(
                type = ClipboardEntryType.TEXT,
                text = text
            )
        )
    }

    fun ingestScreenshot(uri: Uri, displayName: String?, mimeType: String? = "image/png") {
        lastScreenshotIngestAtMs = System.currentTimeMillis()
        val label = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: "screenshot"
        ingestPayload(
            ClipboardPayload(
                type = ClipboardEntryType.URI,
                text = label,
                uri = uri.toString(),
                mimeType = mimeType?.takeIf { it.isNotBlank() } ?: "image/*",
                imageUris = listOf(uri.toString())
            )
        )
    }

    fun syncClipboardMonitoringFromSettings() {
        if (!ClipboardMonitorProcess.isMonitorProcess()) {
            syncExternalMonitorService()
            return
        }
        val settings = settingsRepository.readSnapshot()
        if (!settings.clipboardBackgroundMonitoring) {
            stopClipboardListening()
            return
        }
        clipboardMonitorController.startIfNeeded(resolveClipboardMonitoringMode())
    }

    fun restartClipboardMonitoringFromSettings() {
        if (!ClipboardMonitorProcess.isMonitorProcess()) {
            syncExternalMonitorService()
            return
        }
        val settings = settingsRepository.readSnapshot()
        if (!settings.clipboardBackgroundMonitoring) {
            stopClipboardListening()
            return
        }
        clipboardMonitorController.restart(resolveClipboardMonitoringMode())
    }

    /**
     * 非监听进程：按设置启停跑在 `:clipboard-monitor` 的前台服务。
     *
     * 模式解析交给监听进程自己（见 [com.slideindex.app.clipboard.monitor.ClipboardMonitorForegroundService]），
     * 这里只负责"该起就起、该停就停"，不再依赖 overlay 进程转发。
     */
    private fun syncExternalMonitorService() {
        if (!settingsRepository.readSnapshot().clipboardBackgroundMonitoring) {
            clipboardMonitorController.stopMonitorServiceFromOutside()
            return
        }
        clipboardMonitorController.startMonitorServiceFromOutside()
    }

    /**
     * 解析当前该用哪个监听模式。
     *
     * - 用户显式选了某个模式：照用；
     * - 「跟随特权模式」：先 Shizuku / Root（非 root 用户照旧首选），后端起不来时退 LSPosed 白名单，
     *   模块也没响应才退标准公开 API。
     */
    private fun resolveClipboardMonitoringMode(): ClipboardMonitoringMode {
        val settings = settingsRepository.readSnapshot()
        // 旧字段 clipboardBackgroundMonitoringMode 只是历史遗留（老版本 UI 直接选具体模式时写的）；
        // 只有它被显式设成具体模式才尊重它，否则一律按新的 channel + capture 解析 ——
        // 否则设置页勾了 Root、监听却仍按旧字段以 Shizuku 启动（真机现象：勾选 Root / 状态显示 Shizuku）。
        val legacy = settings.clipboardBackgroundMonitoringMode
        if (legacy != ClipboardMonitoringMode.FOLLOW_PRIVILEGE) {
            return legacy.effective(settings.privilegeMode)
        }
        if (settings.clipboardMonitoringChannel !=
            com.slideindex.app.settings.ClipboardMonitoringChannel.FOLLOW_PRIVILEGE
        ) {
            return settings.effectiveClipboardMonitoringMode().effective(settings.privilegeMode)
        }
        // 「跟随特权模式」：Shizuku 可能只是**还没就绪**（监听进程刚起/binder 刚断重连），
        // 这时不该立刻降级到别的通道（用户会看到"我选的跟随特权，怎么跑在 LSPosed 上"）。
        // 先等一小会儿再判，等不到才降级。
        runCatching {
            if (settings.privilegeMode == com.slideindex.app.settings.PrivilegeMode.SHIZUKU &&
                !rikka.shizuku.Shizuku.pingBinder()
            ) {
                val deadline = android.os.SystemClock.elapsedRealtime() + SHIZUKU_READY_WAIT_MS
                while (android.os.SystemClock.elapsedRealtime() < deadline &&
                    !rikka.shizuku.Shizuku.pingBinder()
                ) {
                    Thread.sleep(SHIZUKU_READY_POLL_MS)
                }
            }
        }
        val privileged = settings.effectiveClipboardMonitoringMode().effective(settings.privilegeMode)
        if (clipboardMonitorController.isBackendAvailable(privileged)) return privileged
        if (isLsposedModuleResponsive()) return ClipboardMonitoringMode.LSPOSED
        return ClipboardMonitoringMode.STANDARD
    }

    /**
     * 模块可用性：只要曾经响应过、且状态不是「未就绪」就认为可用。
     *
     * 不再设新鲜度上限——否则重启后旧状态过期，「跟随特权模式」就不会退到 LSPosed。
     * 没有记录或明确未就绪时主动探一次（带节流），探到可用就重新同步监听。
     */
    private fun isLsposedModuleResponsive(): Boolean {
        val snapshot = ModuleBridgeStatusStore.read(context)
        if (snapshot.updatedAtMs > 0L &&
            snapshot.state != ModuleHookBridgeContract.STATUS_STATE_NOT_READY
        ) {
            return true
        }
        val now = System.currentTimeMillis()
        if (now - lastModuleProbeAtMs >= MODULE_PROBE_THROTTLE_MS) {
            lastModuleProbeAtMs = now
            ModuleBridgeStatusProbe.probe(context) { status, _ ->
                if (status != ModuleBridgeStatusProbe.Status.NotReady) {
                    syncClipboardMonitoringFromSettings()
                }
            }
        }
        return false
    }

    fun startClipboardListening() {
        restartClipboardMonitoringFromSettings()
    }

    fun stopClipboardListening() {
        clipboardMonitorController.stop()
    }

    fun startScreenshotMonitoring() {
        stopScreenshotMonitoring()
        if (!settingsRepository.readSnapshot().clipboardScreenshotMonitoring) return
        if (!ClipboardPermissionHelper.hasMediaReadPermission(context)) return
        startScreenshotMonitor()
    }

    fun stopScreenshotMonitoring() {
        screenshotMonitor?.stop()
        screenshotMonitor = null
    }

    fun startListening() {
        startClipboardListening()
        startScreenshotMonitoring()
    }

    fun stopListening() {
        stopClipboardListening()
        stopScreenshotMonitoring()
    }

    suspend fun reloadFromDisk() {
        writeMutex.withLock {
            if (indexFile.exists()) {
                val legacy = runCatching {
                    json.decodeFromString<List<ClipboardEntry>>(indexFile.readText())
                }.getOrDefault(emptyList())
                store.deleteAll()
                if (legacy.isNotEmpty()) {
                    store.migrateFromJsonIndex(trimLegacyToConfiguredMax(legacy))
                }
                indexFile.renameTo(File(storageDir, "$INDEX_FILE_NAME.migrated"))
            }
            refreshEntryCountLocked()
            bumpRevisionLocked()
        }
    }

    /** 备份用：整份剪贴板历史（文字 + 图片文件名），图片文件本身在 `files/clipboard/` 目录里。 */
    suspend fun exportRawJson(): String = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            json.encodeToString(store.queryAll())
        }
    }

    /**
     * 恢复用：整表替换并刷新计数/版本。
     *
     * @return 数据能解析并写回时 true。
     */
    suspend fun importRawJson(raw: String): Boolean = withContext(Dispatchers.IO) {
        if (raw.isBlank()) return@withContext false
        val entries = runCatching {
            json.decodeFromString<List<ClipboardEntry>>(raw)
        }.getOrNull() ?: return@withContext false
        writeMutex.withLock {
            store.replaceAll(entries)
            refreshEntryCountLocked()
            bumpRevisionLocked()
        }
        true
    }

    suspend fun peekLatestEntry(): ClipboardEntry? = withContext(Dispatchers.IO) {
        store.queryLatest()
    }

    /**
     * 某个筛选下的**完整**条数（不是"已经加载了几页"）。
     *
     * 面板搜索框右侧那个数字用它：剪贴板是分页加载的，用「已加载条数」当总数会随着下滑变大
     * （`docs/capsule-refactor-plan.md` §0.16.4 待办 1）。这里直接问 SQLite。
     */
    suspend fun countEntries(filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All): Int =
        withContext(Dispatchers.IO) {
            store.count(filter)
        }

    suspend fun loadHistoryPage(
        createdBeforeMs: Long? = null,
        limit: Int,
        filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All,
    ): ClipboardHistoryPage = withContext(Dispatchers.IO) {
        val pageSize = limit.coerceAtLeast(1)
        val total = store.count(filter)
        val slice = store.queryPageBefore(createdBeforeMs, pageSize, filter)
        ClipboardHistoryPage(
            entries = slice,
            totalCount = total,
            hasMore = slice.size == pageSize
        )
    }

    /** @deprecated 使用 keyset [loadHistoryPage]；offset 仅作兼容。 */
    suspend fun loadFloatHistoryPage(offset: Int, limit: Int): ClipboardHistoryPage {
        var cursor: Long? = null
        var skipped = 0
        val target = offset.coerceAtLeast(0)
        val pageSize = limit.coerceAtLeast(1)
        while (skipped < target) {
            val step = minOf(100, target - skipped)
            val page = loadHistoryPage(cursor, step)
            if (page.entries.isEmpty()) {
                return ClipboardHistoryPage(emptyList(), page.totalCount, hasMore = false)
            }
            skipped += page.entries.size
            cursor = page.nextCursor
            if (!page.hasMore) break
        }
        return loadHistoryPage(cursor, pageSize)
    }

    suspend fun searchHistory(query: String, limit: Int = ClipboardHistoryStore.SEARCH_RESULT_LIMIT): List<ClipboardEntry> =
        withContext(Dispatchers.IO) {
            store.search(query, limit)
        }

    // ---- ClipboardHistoryQuery（P1 只读接口）----

    override suspend fun loadPage(
        createdBeforeMs: Long?,
        limit: Int,
        filter: ClipboardHistoryFilter,
    ): ClipboardHistoryPage = loadHistoryPage(createdBeforeMs, limit, filter)

    override suspend fun count(filter: ClipboardHistoryFilter): Int = countEntries(filter)

    override suspend fun search(query: String, limit: Int): List<ClipboardEntry> =
        searchHistory(query, limit)

    override suspend fun peekLatest(): ClipboardEntry? = peekLatestEntry()
    private fun startScreenshotMonitor() {
        if (!ClipboardPermissionHelper.hasMediaReadPermission(context)) return
        screenshotMonitor = ScreenshotMonitor(context) { uri, displayName, mimeType ->
            ingestScreenshot(uri, displayName, mimeType)
        }.also { it.start() }
    }

    private fun shouldBlockDisplacingScreenshot(
        payload: ClipboardPayload,
        fromPassiveRefresh: Boolean
    ): Boolean {
        if (!fromPassiveRefresh) return false
        if (payload.hasImageContent()) return false
        if (System.currentTimeMillis() - lastScreenshotIngestAtMs >= SCREENSHOT_TOP_GUARD_MS) return false
        val top = store.queryLatest() ?: return false
        return top.hasImageContent()
    }

    private fun consumeOutgoingWriteSkip(): Boolean {
        if (skipIngestRemaining <= 0) return false
        skipIngestRemaining--
        return true
    }

    private fun findMatchingEntry(payload: ClipboardPayload): ClipboardEntry? {
        val fingerprint = ClipboardContentEquivalence.fingerprint(payload)
        if (fingerprint.isNotEmpty()) {
            store.findLatestByFingerprint(fingerprint)?.let { return it }
        }
        val contentKey = payload.contentKey()
        store.findLatestByContentKey(contentKey)?.let { return it }
        return null
    }

    private fun promoteExistingPayloadIfNeeded(
        payload: ClipboardPayload,
        fromPassiveRefresh: Boolean = false
    ) {
        if (shouldBlockDisplacingScreenshot(payload, fromPassiveRefresh)) return
        val existing = findMatchingEntry(payload) ?: return
        scope.launch {
            writeMutex.withLock {
                val match = store.queryById(existing.id) ?: return@withLock
                if (store.queryLatest()?.id == match.id) return@withLock
                promoteExistingEntry(match)
            }
        }
    }

    private fun promoteExistingEntry(existing: ClipboardEntry) {
        val fingerprint = ClipboardContentEquivalence.fingerprint(existing)
        val contentKey = existing.contentKey()
        val preservedImageNames = existing.resolvedImageFileNames().toSet()
        val matching = collectMatchingEntries(fingerprint, contentKey)
        matching
            .filter { it.id != existing.id }
            .forEach { deleteEntryImagesExceptPreserved(it, preservedImageNames) }
        deleteMatchingRows(fingerprint, contentKey)
        val promoted = existing.copy(createdAtEpochMs = System.currentTimeMillis())
        store.insert(promoted)
        applyTrimToConfiguredMaxLocked()
        bumpRevisionLocked()
    }

    private fun removeMatchingEntries(payload: ClipboardPayload, fingerprint: String) {
        val contentKey = payload.contentKey()
        collectMatchingEntries(fingerprint, contentKey).forEach { entry ->
            ClipboardImageStore.deleteEntryImages(context, entry)
        }
        deleteMatchingRows(fingerprint, contentKey)
    }

    private fun collectMatchingEntries(fingerprint: String, contentKey: String): List<ClipboardEntry> {
        val byId = linkedMapOf<String, ClipboardEntry>()
        if (fingerprint.isNotEmpty()) {
            store.queryByFingerprint(fingerprint).forEach { byId[it.id] = it }
        }
        if (contentKey.isNotEmpty()) {
            store.queryByContentKey(contentKey).forEach { byId[it.id] = it }
        }
        return byId.values.toList()
    }

    private fun deleteMatchingRows(fingerprint: String, contentKey: String) {
        if (fingerprint.isNotEmpty()) {
            store.deleteByFingerprint(fingerprint)
        }
        if (contentKey.isNotEmpty()) {
            store.deleteByContentKey(contentKey)
        }
    }

    private fun deleteEntryImagesExceptPreserved(entry: ClipboardEntry, preserved: Set<String>) {
        entry.resolvedImageFileNames()
            .filter { it !in preserved }
            .forEach { ClipboardImageStore.delete(context, it) }
    }

    private fun persistPayloadImages(entryId: String, payload: ClipboardPayload): List<String> {
        if (!payload.hasImageContent()) return emptyList()
        val existing = payload.resolvedImageFileNames().filter {
            ClipboardImageStore.imageFile(context, it).exists()
        }
        if (existing.isNotEmpty()) return existing
        return ClipboardImageStore.persistAllFromPayload(context, entryId, payload)
    }

    private fun configuredMaxEntries(): Int =
        settingsRepository.readSnapshot().clipboardHistoryMaxEntries

    private fun applyTrimToConfiguredMaxLocked() {
        val max = configuredMaxEntries()
        if (max < 0) return
        val removed = store.trimToMax(max)
        removed.forEach { entry ->
            ClipboardImageStore.deleteEntryImages(context, entry)
        }
    }

    private fun migrateLegacyJsonIfNeeded(force: Boolean = false) {
        if (!force && store.count() > 0) return
        if (!indexFile.exists()) return
        val legacy = runCatching {
            json.decodeFromString<List<ClipboardEntry>>(indexFile.readText())
        }.getOrDefault(emptyList())
        if (legacy.isEmpty()) return
        if (force) {
            store.deleteAll()
        }
        store.migrateFromJsonIndex(trimLegacyToConfiguredMax(legacy))
        indexFile.renameTo(File(storageDir, "$INDEX_FILE_NAME.migrated"))
    }


    private fun trimLegacyToConfiguredMax(entries: List<ClipboardEntry>): List<ClipboardEntry> {
        val max = configuredMaxEntries()
        if (max < 0) return entries
        if (entries.size <= max) return entries
        val kept = entries.take(max)
        entries.drop(max).forEach { entry ->
            ClipboardImageStore.deleteEntryImages(context, entry)
        }
        return kept
    }

    private fun refreshEntryCount() {
        _entryCount.value = store.count()
    }

    private fun refreshEntryCountLocked() {
        _entryCount.value = store.count()
    }

    private fun bumpRevisionLocked() {
        refreshEntryCountLocked()
        _revision.value = _revision.value + 1L
    }

    companion object {
        private const val DIR_NAME = "clipboard"
        private const val INDEX_FILE_NAME = "history.json"
        private const val SAME_CLIP_DEDUP_MS = 400L
        private const val SCREENSHOT_TOP_GUARD_MS = 10_000L
        /** 主动探测模块状态的节流间隔。 */
        private const val MODULE_PROBE_THROTTLE_MS = 30_000L
        /** 「跟随特权」时等 Shizuku 就绪的上限与轮询间隔（避免刚启动就被判定不可用而降级）。 */
        private const val SHIZUKU_READY_WAIT_MS = 3_000L
        private const val SHIZUKU_READY_POLL_MS = 200L
    }
}
