package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slideindex.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileBrowserUiState(
    val path: String,
    val entries: List<FileBrowserEntry> = emptyList(),
    val query: String = "",
    val showHidden: Boolean = false,
    val sort: FileSortOrder = FileSortOrder.NAME_ASC,
    val loading: Boolean = false,
    val error: FileBrowserError? = null,
    val details: FileBrowserEntry? = null,
    val access: FileBrowserAccessInfo? = null,
    val limited: Boolean = false,
    val truncated: Boolean = false,
    val directorySizeBytes: Long? = null,
    val directorySizeLoading: Boolean = false,
    val directorySizeComputed: Boolean = false,
    val searchResults: List<FileBrowserEntry>? = null,
    val searchQuery: String? = null,
    val searchLoading: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val clipboard: FileClipboard? = null,
    val operationRunning: Boolean = false,
    val operationProgress: Float = 0f,
    val operationMessage: String? = null,
    val pendingPaste: FileClipboard? = null,
    val pendingConflicts: Int = 0,
    val lastTrash: List<TrashedItem> = emptyList(),
    val tabs: List<FileBrowserTab> = emptyList(),
    val activeTabId: Long = 0L,
    val mediaItems: List<FileBrowserMediaItem> = emptyList(),
    val mediaLoading: Boolean = false,
    val mediaHasMore: Boolean = false,
    val mediaTotalCount: Int? = null,
    val mediaTotalBytes: Long? = null,
    val mediaSelectedIds: Set<Long> = emptySet(),
    val mediaPreview: FileBrowserMediaItem? = null,
)

val FileBrowserUiState.mediaCategory: FileBrowserMediaCategory?
    get() = FileBrowserMediaCategory.forPath(path)

data class FileClipboard(
    val mode: FileTransferMode,
    val paths: List<String>,
)

data class FileBrowserTab(
    val id: Long,
    val path: String,
    val backStack: List<String> = emptyList(),
    val query: String = "",
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/**
 * 文件浏览器状态机。
 *
 * 所有状态写入都发生在其 [scope]（Main.immediate），目录读取在 IO；切换目录时取消旧任务，
 * 避免“先点 A 再点 B，A 的结果回来覆盖 B”。Session 跨窗口保留，所以关闭面板不会重置路径。
 */
internal class FileBrowserController(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val dataSource = FileBrowserDataSource(context)
    private val appContext = context.applicationContext
    private var loadJob: Job? = null
    private var sizeJob: Job? = null
    private var searchJob: Job? = null
    private var operationJob: Job? = null
    private var mediaJob: Job? = null
    private var nextTabId: Long = 1L
    private val scrollPositions = mutableMapOf<Pair<Long, String>, Pair<Int, Int>>()
    private var currentScrollProvider: (() -> Pair<Int, Int>)? = null

    val internalRoot: String get() = dataSource.internalRoot

    var state by mutableStateOf(
        FileBrowserUiState(path = dataSource.initialPath()),
    )
        private set

    init {
        FileBrowserPreferences.attach(context) { prefs ->
            scope.launch {
                if (state.path != dataSource.initialPath()) return@launch
                if (prefs.tabs.isNotEmpty() && state.tabs.size <= 1) {
                    val restored = prefs.tabs.mapIndexed { index, path ->
                        FileBrowserTab(
                            id = index.toLong(),
                            path = FileBrowserMedia.normalizeLocation(path, internalRoot),
                        )
                    }.toMutableList()
                    if (restored.size == 1 && restored.first().path == "/") {
                        restored[0] = restored.first().copy(path = preferredStart(null))
                    }
                    val activeIndex = prefs.activeTabIndex.coerceIn(0, restored.lastIndex)
                    val active = restored[activeIndex]
                    state = state.copy(
                        tabs = restored,
                        activeTabId = active.id,
                        path = active.path,
                        showHidden = prefs.showHidden,
                        sort = prefs.sortOrderOrDefault(),
                    )
                    reload()
                    return@launch
                }
                val last = prefs.lastPath ?: return@launch
                val target = preferredStart(last)
                if (target == state.path) return@launch
                state = state.copy(
                    path = target,
                    showHidden = prefs.showHidden,
                    sort = prefs.sortOrderOrDefault(),
                    tabs = state.tabs.map {
                        if (it.id == state.activeTabId) it.copy(path = target) else it
                    },
                )
                reload()
            }
        }
        val prefs = FileBrowserPreferences.current
        val initialPath = preferredStart(prefs.lastPath)
        state = FileBrowserUiState(
            path = initialPath,
            showHidden = prefs.showHidden,
            sort = prefs.sortOrderOrDefault(),
            tabs = listOf(FileBrowserTab(id = 0L, path = initialPath)),
            activeTabId = 0L,
        )
    }

    /** 面板重新可见：保留 Tab，清掉当前搜索词/详情，然后刷新。 */
    /**
     * 启动目录：保留上次位置，但系统根目录不当作默认入口，回落到内部存储。
     * 根目录仍可手动进入，只是下次打开不再一上来就是 `/`。
     */
    private fun preferredStart(lastPath: String?): String {
        val fallback = FileBrowserMedia.normalizeLocation(
            dataSource.initialPath(),
            internalRoot,
        )
        val raw = lastPath?.takeIf { it.isNotBlank() } ?: return fallback
        val normalized = FileBrowserMedia.normalizeLocation(raw, internalRoot)
        return if (normalized == "/") fallback else normalized
    }

    fun onShow() {
        val prefs = FileBrowserPreferences.current
        val target = if (state.path == dataSource.initialPath() && !prefs.lastPath.isNullOrBlank()) {
            preferredStart(prefs.lastPath)
        } else {
            state.path
        }
        val mediaMode = FileBrowserMediaCategory.isMediaPath(target)
        state = state.copy(
            path = target,
            query = "",
            details = null,
            showHidden = prefs.showHidden,
            sort = prefs.sortOrderOrDefault(),
            loading = !mediaMode,
            error = null,
            directorySizeBytes = null,
            directorySizeLoading = false,
            directorySizeComputed = false,
            mediaItems = emptyList(),
            mediaLoading = mediaMode,
            mediaHasMore = mediaMode,
            mediaTotalCount = null,
            mediaTotalBytes = null,
            mediaSelectedIds = emptySet(),
            mediaPreview = null,
            tabs = state.tabs.map {
                if (it.id == state.activeTabId) it.copy(path = target, query = "") else it
            },
        )
        if (mediaMode) {
            loadMedia(reset = true)
        } else {
            reload(resolveAccess = true)
        }
        rememberTabs()
    }

    fun bindScrollProvider(provider: () -> Pair<Int, Int>) {
        currentScrollProvider = provider
    }

    private fun saveCurrentScroll() {
        val position = currentScrollProvider?.invoke() ?: return
        scrollPositions[state.activeTabId to state.path] = position
        Log.d("FileBrowserScroll", "save path=${state.path} pos=$position")
    }

    fun navigateTo(path: String) {
        navigateTo(path, pushHistory = true)
    }

    private fun navigateTo(path: String, pushHistory: Boolean) {
        if (FileBrowserMediaCategory.isMediaPath(path)) {
            val category = FileBrowserMediaCategory.forPath(path) ?: return
            navigateToMedia(category, pushHistory)
            return
        }
        val normalized = FileBrowserPaths.normalize(path, internalRoot)
        if (normalized == state.path && state.error == null) return
        mediaJob?.cancel()
        mediaJob = null
        saveCurrentScroll()
        searchJob?.cancel()
        searchJob = null
        val currentTab = state.tabs.firstOrNull { it.id == state.activeTabId }
        val history = currentTab?.backStack.orEmpty().let { stack ->
            if (pushHistory && state.path != normalized) stack + state.path else stack
        }
        val tabs = state.tabs.map {
            if (it.id == state.activeTabId) {
                it.copy(path = normalized, query = "", backStack = history)
            } else {
                it
            }
        }
        state = state.copy(
            path = normalized,
            entries = emptyList(),
            query = "",
            details = null,
            loading = true,
            error = null,
            limited = false,
            truncated = false,
            directorySizeBytes = null,
            directorySizeLoading = false,
            directorySizeComputed = false,
            searchResults = null,
            searchQuery = null,
            searchLoading = false,
            selectionMode = false,
            selectedPaths = emptySet(),
            mediaItems = emptyList(),
            mediaLoading = false,
            mediaHasMore = false,
            mediaTotalCount = null,
            mediaTotalBytes = null,
            mediaSelectedIds = emptySet(),
            mediaPreview = null,
            tabs = tabs,
        )
        FileBrowserPreferences.rememberLastPath(normalized)
        rememberTabs()
        reload(resolveAccess = false)
    }

    /** 进入 FV 式媒体分类；不是文件系统目录。 */
    private fun navigateToMedia(
        category: FileBrowserMediaCategory,
        pushHistory: Boolean,
    ) {
        val normalized = category.path
        if (normalized == state.path &&
            state.error == null &&
            (state.mediaItems.isNotEmpty() || state.mediaLoading)
        ) {
            return
        }
        loadJob?.cancel()
        loadJob = null
        sizeJob?.cancel()
        sizeJob = null
        searchJob?.cancel()
        searchJob = null
        val currentTab = state.tabs.firstOrNull { it.id == state.activeTabId }
        val history = currentTab?.backStack.orEmpty().let { stack ->
            if (pushHistory && state.path != normalized) stack + state.path else stack
        }
        val tabs = state.tabs.map {
            if (it.id == state.activeTabId) {
                it.copy(path = normalized, query = "", backStack = history)
            } else {
                it
            }
        }
        state = state.copy(
            path = normalized,
            entries = emptyList(),
            query = "",
            details = null,
            loading = false,
            error = null,
            limited = false,
            truncated = false,
            directorySizeBytes = null,
            directorySizeLoading = false,
            directorySizeComputed = false,
            searchResults = null,
            searchQuery = null,
            searchLoading = false,
            selectionMode = false,
            selectedPaths = emptySet(),
            mediaItems = emptyList(),
            mediaLoading = true,
            mediaHasMore = true,
            mediaTotalCount = null,
            mediaTotalBytes = null,
            mediaSelectedIds = emptySet(),
            mediaPreview = null,
            tabs = tabs,
        )
        FileBrowserPreferences.rememberLastPath(normalized)
        rememberTabs()
        loadMedia(reset = true)
    }

    fun goParent() {
        goParent(pushHistory = true)
    }

    private fun goParent(pushHistory: Boolean) {
        if (FileBrowserMediaCategory.isMediaPath(state.path)) {
            navigateTo(internalRoot, pushHistory = pushHistory)
            return
        }
        FileBrowserPaths.parent(state.path, internalRoot)?.let { parent ->
            navigateTo(parent, pushHistory = pushHistory)
        }
    }

    fun goBack() {
        saveCurrentScroll()
        val tab = state.tabs.firstOrNull { it.id == state.activeTabId }
        val target = tab?.backStack?.lastOrNull()
        if (target == null) {
            goParent(pushHistory = false)
            return
        }
        val tabs = state.tabs.map {
            if (it.id == state.activeTabId) it.copy(backStack = it.backStack.dropLast(1)) else it
        }
        state = state.copy(tabs = tabs)
        navigateTo(target, pushHistory = false)
    }

    fun selectTab(id: Long) {
        if (id == state.activeTabId) return
        val saved = saveActiveTab()
        val target = saved.firstOrNull { it.id == id } ?: return
        val mediaMode = FileBrowserMediaCategory.isMediaPath(target.path)
        state = state.copy(
            tabs = saved,
            activeTabId = id,
            path = target.path,
            query = target.query,
            entries = emptyList(),
            details = null,
            searchResults = null,
            searchQuery = null,
            selectionMode = false,
            selectedPaths = emptySet(),
            loading = !mediaMode,
            error = null,
            mediaItems = emptyList(),
            mediaLoading = mediaMode,
            mediaHasMore = mediaMode,
            mediaTotalCount = null,
            mediaTotalBytes = null,
            mediaSelectedIds = emptySet(),
            mediaPreview = null,
        )
        FileBrowserPreferences.rememberLastPath(target.path)
        rememberTabs()
        reload(resolveAccess = true)
    }

    fun newTab() {
        val tabs = saveActiveTab()
        val id = nextTabId++
        state = state.copy(
            tabs = tabs + FileBrowserTab(id = id, path = state.path),
            activeTabId = id,
            query = "",
            selectionMode = false,
            selectedPaths = emptySet(),
            details = null,
            mediaSelectedIds = emptySet(),
            mediaPreview = null,
        )
        rememberTabs()
    }

    fun closeTab(id: Long) {
        if (state.tabs.size <= 1) return
        val tabs = saveActiveTab()
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val remaining = tabs.filterNot { it.id == id }
        if (id != state.activeTabId) {
            state = state.copy(tabs = remaining)
            rememberTabs()
            return
        }
        val next = remaining[(index - 1).coerceAtLeast(0)]
        val mediaMode = FileBrowserMediaCategory.isMediaPath(next.path)
        state = state.copy(
            tabs = remaining,
            activeTabId = next.id,
            path = next.path,
            query = next.query,
            entries = emptyList(),
            details = null,
            loading = !mediaMode,
            error = null,
            mediaItems = emptyList(),
            mediaLoading = mediaMode,
            mediaHasMore = mediaMode,
            mediaTotalCount = null,
            mediaTotalBytes = null,
            mediaSelectedIds = emptySet(),
            mediaPreview = null,
        )
        rememberTabs()
        reload(resolveAccess = true)
    }

    private fun saveActiveTab(): List<FileBrowserTab> {
        val scroll = scrollPositions[state.activeTabId to state.path]
        return state.tabs.map {
            if (it.id == state.activeTabId) {
                it.copy(
                    path = state.path,
                    query = state.query,
                    scrollIndex = scroll?.first ?: it.scrollIndex,
                    scrollOffset = scroll?.second ?: it.scrollOffset,
                )
            } else {
                it
            }
        }
    }

    private fun rememberTabs() {
        val index = state.tabs.indexOfFirst { it.id == state.activeTabId }
        FileBrowserPreferences.rememberTabs(
            paths = state.tabs.map { it.path },
            activeIndex = index.coerceAtLeast(0),
        )
    }

    fun saveScroll(index: Int, offset: Int) {
        saveScrollAt(state.path, index, offset)
    }

    fun saveScrollAt(path: String, index: Int, offset: Int) {
        scrollPositions[state.activeTabId to path] = index to offset
    }

    fun scrollForActiveTab(): Pair<Int, Int> {
        val tab = state.tabs.firstOrNull { it.id == state.activeTabId }
        val cached = scrollPositions[state.activeTabId to state.path]
        Log.d("FileBrowserScroll", "read path=${state.path} cached=$cached tab=${tab?.path}")
        return cached
            ?: if (tab?.path == state.path) {
                (tab.scrollIndex) to (tab.scrollOffset)
            } else {
                0 to 0
            }
    }

    fun refresh() {
        reload(resolveAccess = true)
    }

    /** 授权返回后调用：重新探测访问能力并重载当前目录。 */
    fun refreshAccessAndReload() {
        reload(resolveAccess = true)
    }

    fun setQuery(query: String) {
        state = state.copy(
            query = query,
            tabs = state.tabs.map {
                if (it.id == state.activeTabId) it.copy(query = query) else it
            },
        )
    }

    fun setShowHidden(show: Boolean) {
        state = state.copy(showHidden = show)
        FileBrowserPreferences.setShowHidden(show)
    }

    fun setSort(order: FileSortOrder) {
        state = state.copy(sort = order)
        FileBrowserPreferences.setSort(order)
    }

    fun showDetails(entry: FileBrowserEntry) {
        state = state.copy(details = entry)
    }

    fun hideDetails() {
        state = state.copy(details = null)
    }

    fun toggleMediaSelection(item: FileBrowserMediaItem) {
        val selected = if (item.id in state.mediaSelectedIds) {
            state.mediaSelectedIds - item.id
        } else {
            state.mediaSelectedIds + item.id
        }
        state = state.copy(
            mediaSelectedIds = selected,
            mediaPreview = null,
        )
    }

    fun clearMediaSelection() {
        if (state.mediaSelectedIds.isEmpty()) return
        state = state.copy(mediaSelectedIds = emptySet())
    }

    fun showMediaPreview(item: FileBrowserMediaItem) {
        state = state.copy(mediaPreview = item, mediaSelectedIds = emptySet())
    }

    fun hideMediaPreview() {
        if (state.mediaPreview == null) return
        state = state.copy(mediaPreview = null)
    }

    fun enterSelection(entry: FileBrowserEntry) {
        if (state.selectionMode && entry.path in state.selectedPaths) return
        val selected = if (state.selectionMode) {
            state.selectedPaths + entry.path
        } else {
            setOf(entry.path)
        }
        state = state.copy(
            selectionMode = true,
            selectedPaths = selected,
            details = null,
        )
    }

    fun toggleSelection(path: String) {
        val selected = if (path in state.selectedPaths) {
            state.selectedPaths - path
        } else {
            state.selectedPaths + path
        }
        state = state.copy(
            selectionMode = selected.isNotEmpty(),
            selectedPaths = selected,
        )
    }

    /**
     * 滑动勾选：把 [paths] 并入当前选择（只增不减，取消仍靠点击）。
     * 与 [toggleSelection] 相反，这里不会因为“路径本来就在选择里”而把勾去掉。
     */
    fun selectRange(paths: Collection<String>) {
        if (paths.isEmpty()) return
        state = state.copy(
            selectionMode = true,
            selectedPaths = state.selectedPaths + paths,
            details = null,
        )
    }

    fun exitSelection() {
        state = state.copy(selectionMode = false, selectedPaths = emptySet())
    }

    fun copySelection(cut: Boolean) {
        if (state.selectedPaths.isEmpty()) return
        state = state.copy(
            clipboard = FileClipboard(
                mode = if (cut) FileTransferMode.MOVE else FileTransferMode.COPY,
                paths = state.selectedPaths.toList(),
            ),
            selectionMode = false,
            selectedPaths = emptySet(),
        )
    }

    fun clearClipboard() {
        state = state.copy(clipboard = null)
    }

    fun paste() {
        val clipboard = state.clipboard ?: return
        if (state.operationRunning) return
        val conflicts = FileBrowserOperations.conflicts(clipboard.paths, state.path)
        if (conflicts > 0) {
            state = state.copy(
                pendingPaste = clipboard,
                pendingConflicts = conflicts,
                selectionMode = false,
                selectedPaths = emptySet(),
            )
            return
        }
        executePaste(clipboard, FileConflictPolicy.RENAME)
    }

    fun resolvePaste(policy: FileConflictPolicy) {
        val clipboard = state.pendingPaste ?: return
        state = state.copy(pendingPaste = null, pendingConflicts = 0)
        executePaste(clipboard, policy)
    }

    fun cancelPaste() {
        state = state.copy(pendingPaste = null, pendingConflicts = 0)
    }

    private fun executePaste(clipboard: FileClipboard, policy: FileConflictPolicy) {
        val access = state.access ?: return
        if (state.operationRunning) return
        val target = state.path
        state = state.copy(
            operationRunning = true,
            operationProgress = 0f,
            operationMessage = null,
            selectionMode = false,
            selectedPaths = emptySet(),
        )
        operationJob = scope.launch {
            val report = FileBrowserOperations.transfer(
                paths = clipboard.paths,
                targetDir = target,
                mode = clipboard.mode,
                access = access,
                policy = policy,
            ) { done, total ->
                state = state.copy(
                    operationProgress = if (total == 0) 0f else done.toFloat() / total,
                )
            }
            if (!isActive) return@launch
            state = state.copy(
                operationRunning = false,
                operationProgress = 0f,
                operationMessage = when {
                    report.failures.isNotEmpty() -> appContext.getString(
                        R.string.file_browser_failed_count,
                        report.failures.size,
                    )
                    report.skipped > 0 -> appContext.getString(
                        R.string.file_browser_skipped_count,
                        report.skipped,
                    )
                    else -> null
                },
                clipboard = if (report.failures.isEmpty()) null else clipboard,
            )
            refresh()
        }
    }

    fun dropEntryInto(sourcePath: String, targetDir: String, cut: Boolean) {
        dropEntriesInto(listOf(sourcePath), targetDir, cut)
    }

    fun dropEntriesInto(sourcePaths: List<String>, targetDir: String, cut: Boolean) {
        val access = state.access ?: return
        val paths = sourcePaths.filter { it != targetDir }.distinct()
        if (state.operationRunning || paths.isEmpty()) return
        state = state.copy(
            operationRunning = true,
            operationProgress = 0f,
            operationMessage = null,
            selectionMode = false,
            selectedPaths = emptySet(),
        )
        operationJob = scope.launch {
            val report = FileBrowserOperations.transfer(
                paths = paths,
                targetDir = targetDir,
                mode = if (cut) FileTransferMode.MOVE else FileTransferMode.COPY,
                access = access,
                policy = FileConflictPolicy.RENAME,
            ) { done, total ->
                state = state.copy(
                    operationProgress = if (total == 0) 0f else done.toFloat() / total,
                )
            }
            if (!isActive) return@launch
            state = state.copy(
                operationRunning = false,
                operationProgress = 0f,
                operationMessage = if (report.failures.isEmpty()) {
                    null
                } else {
                    appContext.getString(R.string.file_browser_failed_count, report.failures.size)
                },
            )
            refresh()
        }
    }

    fun cancelOperation() {
        operationJob?.cancel()
        operationJob = null
        state = state.copy(
            operationRunning = false,
            operationProgress = 0f,
            operationMessage = appContext.getString(R.string.file_browser_cancelled),
        )
    }

    fun deleteEntry(entry: FileBrowserEntry) {
        val access = state.access ?: return
        if (state.operationRunning) return
        state = state.copy(
            operationRunning = true,
            operationProgress = 0f,
            operationMessage = null,
        )
        operationJob = scope.launch {
            val report = FileBrowserTrash.trash(appContext, listOf(entry.path), access)
            if (!isActive) return@launch
            state = state.copy(
                operationRunning = false,
                operationProgress = 0f,
                operationMessage = if (report.failures.isEmpty()) {
                    null
                } else {
                    appContext.getString(R.string.file_browser_delete_failed)
                },
                lastTrash = report.trashed,
                details = null,
            )
            refresh()
        }
    }

    fun undoDelete() {
        val items = state.lastTrash
        val access = state.access ?: return
        if (items.isEmpty() || state.operationRunning) return
        state = state.copy(operationRunning = true, operationProgress = 0f)
        operationJob = scope.launch {
            val failures = FileBrowserTrash.restore(items, access)
            if (!isActive) return@launch
            state = state.copy(
                operationRunning = false,
                operationProgress = 0f,
                operationMessage = if (failures.isEmpty()) {
                    null
                } else {
                    appContext.getString(R.string.file_browser_restore_failed, failures.size)
                },
                lastTrash = if (failures.isEmpty()) emptyList() else items,
            )
            refresh()
        }
    }

    fun clearTrashUndo() {
        state = state.copy(lastTrash = emptyList())
    }

    fun deleteSelected() {
        if (state.selectedPaths.isEmpty()) return
        val access = state.access ?: return
        if (state.operationRunning) return
        val paths = state.selectedPaths.toList()
        state = state.copy(
            operationRunning = true,
            operationProgress = 0f,
            operationMessage = null,
        )
        operationJob = scope.launch {
            val report = FileBrowserTrash.trash(appContext, paths, access)
            if (!isActive) return@launch
            state = state.copy(
                operationRunning = false,
                operationProgress = 0f,
                operationMessage = if (report.failures.isEmpty()) {
                    null
                } else {
                    appContext.getString(R.string.file_browser_failed_count, report.failures.size)
                },
                selectionMode = false,
                selectedPaths = emptySet(),
                lastTrash = report.trashed,
            )
            refresh()
        }
    }

    fun cancel() {
        loadJob?.cancel()
        loadJob = null
        sizeJob?.cancel()
        sizeJob = null
        searchJob?.cancel()
        searchJob = null
        operationJob?.cancel()
        operationJob = null
        mediaJob?.cancel()
        mediaJob = null
    }

    fun searchRecursive(query: String) {
        val needle = query.trim()
        if (needle.isEmpty()) return
        val base = state.path
        val access = state.access ?: return
        searchJob?.cancel()
        state = state.copy(
            searchResults = emptyList(),
            searchQuery = needle,
            searchLoading = true,
        )
        searchJob = scope.launch {
            val results = withContext(Dispatchers.IO) {
                FileBrowserSearch.search(base, needle, access)
            }
            if (!isActive) return@launch
            state = state.copy(searchResults = results, searchLoading = false)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        state = state.copy(searchResults = null, searchQuery = null, searchLoading = false)
    }

    fun openSearchResult(entry: FileBrowserEntry) {
        clearSearch()
        if (entry.kind == FileEntryKind.DIRECTORY || entry.kind == FileEntryKind.SYMLINK) {
            navigateTo(entry.path)
        } else {
            showDetails(entry)
        }
    }

    fun computeDirectorySize() {
        if (state.mediaCategory != null) return
        if (state.loading || state.directorySizeLoading) return
        val access = state.access ?: return
        val targetPath = state.path
        sizeJob?.cancel()
        state = state.copy(
            directorySizeBytes = null,
            directorySizeLoading = true,
            directorySizeComputed = false,
        )
        sizeJob = scope.launch {
            val size = FileBrowserDirectorySize.compute(targetPath, access)
            if (!isActive || state.path != targetPath) return@launch
            state = state.copy(
                directorySizeBytes = size,
                directorySizeLoading = false,
                directorySizeComputed = true,
            )
        }
    }

    fun loadMoreMedia() {
        if (state.mediaLoading || !state.mediaHasMore) return
        loadMedia(reset = false)
    }

    private fun loadMedia(reset: Boolean) {
        val category = state.mediaCategory ?: return
        if (!reset && (state.mediaLoading || !state.mediaHasMore)) return
        loadJob?.cancel()
        loadJob = null
        sizeJob?.cancel()
        sizeJob = null
        mediaJob?.cancel()
        val offset = if (reset) 0 else state.mediaItems.size
        if (reset) {
            state = state.copy(
                mediaItems = emptyList(),
                mediaLoading = true,
                mediaHasMore = true,
                mediaTotalCount = null,
                mediaTotalBytes = null,
                mediaSelectedIds = emptySet(),
                mediaPreview = null,
                error = null,
            )
        } else {
            state = state.copy(mediaLoading = true, error = null)
        }
        mediaJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                FileBrowserMediaSource.loadPage(
                    context = appContext,
                    category = category,
                    offset = offset,
                )
            }
            if (!isActive || state.mediaCategory != category) return@launch
            state = when (result) {
                is FileBrowserMediaResult.Success -> state.copy(
                    path = category.path,
                    mediaItems = if (reset) {
                        result.page.items
                    } else {
                        state.mediaItems + result.page.items
                    },
                    mediaLoading = false,
                    mediaHasMore = result.page.hasMore,
                    mediaTotalCount = result.page.totalCount ?: state.mediaTotalCount,
                    mediaTotalBytes = result.page.totalBytes ?: state.mediaTotalBytes,
                    error = null,
                )
                FileBrowserMediaResult.PermissionDenied -> state.copy(
                    mediaLoading = false,
                    mediaHasMore = false,
                    error = FileBrowserError.PermissionDenied,
                )
                is FileBrowserMediaResult.Failure -> state.copy(
                    mediaLoading = false,
                    mediaHasMore = false,
                    error = FileBrowserError.Unknown(result.detail),
                )
            }
        }
    }

    private fun reload(resolveAccess: Boolean = false) {
        if (FileBrowserMediaCategory.isMediaPath(state.path)) {
            loadJob?.cancel()
            loadJob = null
            loadMedia(reset = true)
            return
        }
        mediaJob?.cancel()
        mediaJob = null
        loadJob?.cancel()
        sizeJob?.cancel()
        val targetPath = state.path
        loadJob = scope.launch {
            state = state.copy(
                loading = true,
                error = null,
                directorySizeBytes = null,
                directorySizeLoading = false,
                directorySizeComputed = false,
            )
            val access = if (resolveAccess || state.access == null) {
                withContext(Dispatchers.IO) { dataSource.resolveAccess() }
            } else {
                state.access!!
            }
            if (!isActive) return@launch
            state = state.copy(access = access)

            val result = withContext(Dispatchers.IO) { dataSource.list(targetPath, access) }
            if (!isActive) return@launch
            // 读盘期间用户可能切到了媒体分类或另一个目录；旧结果不能把路径改回去。
            if (state.path != targetPath || state.mediaCategory != null) return@launch

            state = when (result) {
                is FileBrowserListingResult.Success -> state.copy(
                    path = targetPath,
                    entries = result.entries,
                    loading = false,
                    error = null,
                    limited = result.limited,
                    truncated = result.truncated,
                )
                is FileBrowserListingResult.Failure -> state.copy(
                    path = targetPath,
                    entries = emptyList(),
                    loading = false,
                    error = result.error,
                    limited = false,
                    truncated = false,
                    directorySizeBytes = null,
                    directorySizeLoading = false,
                    directorySizeComputed = false,
                )
            }
        }
    }
}
