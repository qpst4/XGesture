package com.slideindex.app.overlay.history

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryFilter
import com.slideindex.app.clipboard.ClipboardHistoryQuery
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.stash.StashMetaStore
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.stash.StashTag
import com.slideindex.app.stash.matchesQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview

enum class HistoryPanelTab {
    Stash,
    Clipboard,
}

@OptIn(FlowPreview::class)
class HistoryPanelViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val stashRepository: StashRepository?,
    private val clipboardRepository: ClipboardHistoryQuery?,
    private val metaRepository: StashMetaRepository? = null,
) : ViewModel() {

    val stashSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_STASH_SEARCH, "")

    val clipboardSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_CLIPBOARD_SEARCH, "")

    val selectedTab: StateFlow<HistoryPanelTab> =
        savedStateHandle.getStateFlow(KEY_SELECTED_TAB, HistoryPanelTab.Stash)

    /**
     * 标签筛选的**选中集合**（多选；空集 = 「全部」，与老的单选 `null` 同义）。
     *
     * ⚠️ 真身**不在** ViewModel 里，而在进程级单例 [StashTagFilterState]（与草稿
     * [StashComposerDraft] 同一套做法）：面板是 overlay 窗，关掉 / 被系统摘掉之后下次打开是
     * **全新的 ViewModelStore + 全新的 ViewModel** —— 状态放在这里会跟着没（用户感受就是
     * "关掉再打开，我选的标签没了"）。这里只是把它桥成 Flow，喂给 [filteredStashEntries]。
     */
    val selectedTags: StateFlow<Set<String>> =
        snapshotFlow { StashTagFilterState.selectedTags.value }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                StashTagFilterState.selectedTags.value,
            )

    /** 多标签匹配方式：true = 同时含全部（AND，默认），false = 含任一（OR）。同样活在单例里。 */
    val tagMatchAll: StateFlow<Boolean> =
        snapshotFlow { StashTagFilterState.matchAll.value }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                StashTagFilterState.matchAll.value,
            )

    /** 「收藏」筛选：只看已加星标（内置标签语义，与标签筛选 AND 叠加）。同样活在单例里。 */
    val onlyStarred: StateFlow<Boolean> =
        snapshotFlow { StashTagFilterState.onlyStarred.value }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                StashTagFilterState.onlyStarred.value,
            )

    /** 元数据（标签定义 / 标签绑定 / 完成态 / 追加内容 / 来源）。见 `StashMetaRepository`。 */
    private val metaStore: StateFlow<StashMetaStore> =
        metaRepository?.store ?: MutableStateFlow(StashMetaStore())

    /** 卡片渲染要读的元数据（完成态 / 标签 / 追加 / 来源），与筛选共用同一份。 */
    val stashMeta: StateFlow<StashMetaStore> get() = metaStore

    /** 供筛选行渲染的标签定义，按 order 排序。 */
    val availableTags: StateFlow<List<StashTag>> = metaStore
        .map { store -> store.tags.sortedBy { it.order } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stashEntries: StateFlow<List<StashEntry>> = stashRepository?.entries
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())

    private val _debouncedStashSearchQuery = MutableStateFlow("")

    /**
     * 三个筛选条件（标签集合 + 同时/任一 + 收藏）打包。
     *
     * 为什么要打包：`combine` 的带类型重载只到 **5 个源**，而下面要同时观察
     * entries / 搜索词 / 三个筛选条件 / meta = 6 个，所以把这三个先合成一个。
     */
    private val stashFilterCriteria = combine(
        selectedTags,
        tagMatchAll,
        onlyStarred,
    ) { tags, matchAll, starredOnly ->
        Triple(tags, matchAll, starredOnly)
    }

    val filteredStashEntries: StateFlow<List<StashEntry>> = combine(
        stashEntries,
        _debouncedStashSearchQuery,
        stashFilterCriteria,
        metaStore,
    ) { entries, query, criteria, meta ->
        val (tags, matchAll, starredOnly) = criteria
        // 「收藏」与自建标签筛选统一遵循 matchAll（同时=AND，任一=OR）
        val tagAndStarMatch: (StashEntry) -> Boolean = { entry ->
            when {
                !starredOnly && tags.isEmpty() -> true
                matchAll -> {
                    val starOk = !starredOnly || entry.starred
                    val tagOk = tags.isEmpty() || entry.matchesTagFilter(tags, matchAll = true, meta)
                    starOk && tagOk
                }
                else -> {
                    val starMatch = starredOnly && entry.starred
                    val tagMatch = tags.isNotEmpty() && entry.matchesTagFilter(tags, matchAll = false, meta)
                    starMatch || tagMatch
                }
            }
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            entries.filter { tagAndStarMatch(it) }
        } else {
            // 搜索 **∧** 筛选条件（两者都生效，搜索作为严格交集）
            entries.filter {
                tagAndStarMatch(it) && it.matchesQuery(trimmed, meta.tagsOf(it.id))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 剪贴板固定筛选（`ClipboardHistoryFilter`），SavedStateHandle 持久化。 */
    val clipboardFilter: StateFlow<ClipboardHistoryFilter> =
        savedStateHandle.getStateFlow(KEY_CLIPBOARD_FILTER, ClipboardHistoryFilter.All)

    private val _clipboardFilterCount = MutableStateFlow(clipboardRepository?.entryCount?.value ?: 0)

    /**
     * 当前筛选下的**完整**条数（SQL `COUNT`）。
     *
     * 面板头部的数字用它，**不是** [filteredClipboardEntries]`.size` —— 后者只是"已经加载的那几页"，
     * 用户往下滑会变大（§0.16.4 待办 1 要修的就是这个）。
     */
    val clipboardFilterCount: StateFlow<Int> = _clipboardFilterCount.asStateFlow()

    private val _clipboardPagedEntries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private val _clipboardSearchResults = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private val _clipboardListLoading = MutableStateFlow(false)
    private var clipboardReachedEnd = false
    private var clipboardLoadJob: Job? = null
    private var clipboardActivateJob: Job? = null
    private var clipboardCountJob: Job? = null
    private var clipboardPagesInitialized = false
    /** 当前 `_clipboardPagedEntries` 是按哪个筛选加载的（筛选切换时要整批重来）。 */
    private var clipboardPagesFilter = ClipboardHistoryFilter.All

    val clipboardListLoading: StateFlow<Boolean> = _clipboardListLoading.asStateFlow()

    /**
     * 列表里真正要渲染的东西。
     *
     * 搜索时**忽略固定筛选**（与闪念页签"搜索时忽略标签筛选"同款，见 `filteredStashEntries`）：
     * 搜索结果本来就整批拿回来（`SEARCH_RESULT_LIMIT`），再叠一层筛选只会让人搜不到自己刚复制的东西。
     * 没在搜索时：分页本身就是按 [clipboardFilter] 从 SQLite 取的，不需要在这里再过滤一遍。
     */
    val filteredClipboardEntries: StateFlow<List<ClipboardEntry>> = combine(
        _clipboardPagedEntries,
        _clipboardSearchResults,
        clipboardSearchQuery,
    ) { paged, searched, query ->
        if (query.trim().isEmpty()) paged else searched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 头部条数：搜索时 = 搜索命中条数（整批已加载，不随滚动变），否则 = 筛选后的库总数。 */
    val clipboardViewCount: StateFlow<Int> = combine(
        clipboardFilterCount,
        filteredClipboardEntries,
        clipboardSearchQuery,
    ) { total, visible, query ->
        if (query.trim().isEmpty()) total else visible.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val expandedEntryIds: StateFlow<Set<String>> =
        savedStateHandle.getStateFlow(KEY_EXPANDED_IDS, emptySet())

    val selectedImageIndices: StateFlow<Map<String, Int>> =
        savedStateHandle.getStateFlow(KEY_IMAGE_INDICES, emptyMap())

    init {
        // 元数据孤儿清理：条目会因为删除 / `MAX_ENTRIES = 200` 裁剪而消失，元数据得跟着收敛。
        // **只在拿到非空列表时做** —— 空列表可能只是还没加载，照它清会把用户元数据抹光。
        viewModelScope.launch {
            val ids = stashRepository?.entries?.value?.map { it.id }.orEmpty()
            if (ids.isNotEmpty()) {
                runCatching { metaRepository?.pruneOrphans(ids) }
            }
        }
        viewModelScope.launch {
            stashSearchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { _debouncedStashSearchQuery.value = it }
        }
        viewModelScope.launch {
            clipboardSearchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { query ->
                    runSearch(query)
                }
        }
        viewModelScope.launch {
            clipboardRepository?.revision?.collect { revision ->
                if (revision == 0L) return@collect
                refreshClipboardFilterCount()
                if (clipboardSearchQuery.value.isNotBlank()) {
                    runSearch(clipboardSearchQuery.value)
                    return@collect
                }
                syncPagedListAfterRevision()
            }
        }
        // 筛选切换：条数立刻按新筛选重算，分页整批作废重来（游标与"到底了没有"都是按旧筛选算的）。
        viewModelScope.launch {
            clipboardFilter.collect { filter ->
                refreshClipboardFilterCount()
                if (clipboardPagesFilter == filter) return@collect
                clipboardPagesFilter = filter
                clipboardReachedEnd = false
                clipboardPagesInitialized = false
                _clipboardPagedEntries.value = emptyList()
                // 正在跑的加载是**按旧筛选**取的，必须丢掉：否则它回来会把旧筛选的那批写进
                // `_clipboardPagedEntries`，列表和条数就对不上了。
                clipboardLoadJob?.cancel()
                _clipboardListLoading.value = false
                refreshClipboardPages(showInitialLoading = true)
            }
        }
    }

    /** 重新算"当前筛选下的完整条数"（SQL COUNT，走 IO）。 */
    private fun refreshClipboardFilterCount() {
        val repo = clipboardRepository
        val filter = clipboardFilter.value
        clipboardCountJob?.cancel()
        clipboardCountJob = viewModelScope.launch {
            if (repo == null) {
                _clipboardFilterCount.value = 0
                return@launch
            }
            _clipboardFilterCount.value = repo.count(filter)
        }
    }

    fun setStashSearchQuery(query: String) {
        savedStateHandle[KEY_STASH_SEARCH] = query
    }

    fun setClipboardSearchQuery(query: String) {
        savedStateHandle[KEY_CLIPBOARD_SEARCH] = query
    }

    fun setSelectedTab(tab: HistoryPanelTab) {
        savedStateHandle[KEY_SELECTED_TAB] = tab
    }

    /** 点标签 = 切换选中（可多选）；再点已选中的 = 取消。状态落在进程级单例 [StashTagFilterState]。 */
    fun toggleTagFilter(tag: String) {
        StashTagFilterState.toggleTag(tag)
    }

    /** 清空选中（回到「全部」）。「全部」胶囊 / 「清除」胶囊 / 存下新条目后的收尾都走它。 */
    fun clearTagFilter() {
        StashTagFilterState.clear()
    }

    /** 「全部 / 任一」开关：true = 同时含全部（AND，默认），false = 含任一（OR）。 */
    fun setTagMatchAll(matchAll: Boolean) {
        StashTagFilterState.setMatchAll(matchAll)
    }

    /** 点「收藏」= 切换"只看已星标"。 */
    fun toggleStarredFilter() {
        StashTagFilterState.toggleStarred()
    }

    fun setClipboardFilter(filter: ClipboardHistoryFilter) {
        savedStateHandle[KEY_CLIPBOARD_FILTER] = filter
    }

    fun toggleExpanded(entryId: String) {
        val current = expandedEntryIds.value
        savedStateHandle[KEY_EXPANDED_IDS] = if (entryId in current) {
            current - entryId
        } else {
            current + entryId
        }
    }

    fun setSelectedImageIndex(entryId: String, index: Int) {
        savedStateHandle[KEY_IMAGE_INDICES] = selectedImageIndices.value + (entryId to index)
    }

    fun onClipboardTabActivated(context: android.content.Context) {
        clipboardActivateJob?.cancel()
        clipboardActivateJob = viewModelScope.launch {
            delay(CLIPBOARD_TAB_ACTIVATE_DELAY_MS)
            // 打开剪贴板面板就是「我要看最新的」：主动补读一次，且不受截图保护 / 自己写剪贴板的 skip 影响。
            clipboardRepository?.catchUpLatest(
                triggerContext = context,
                skipWhenListening = false,
            )
            // 条数也重算一次：监听进程（另一个进程）写库时我们这边收不到 revision。
            refreshClipboardFilterCount()
            if (!clipboardPagesInitialized || _clipboardPagedEntries.value.isEmpty()) {
                refreshClipboardPages(showInitialLoading = _clipboardPagedEntries.value.isEmpty())
            }
        }
    }

    fun ensureClipboardPagesLoaded() {
        if (_clipboardListLoading.value) return
        if (!clipboardPagesInitialized || _clipboardPagedEntries.value.isEmpty()) {
            refreshClipboardPages(showInitialLoading = _clipboardPagedEntries.value.isEmpty())
        }
    }

    fun refreshClipboardPages(showInitialLoading: Boolean = false) {
        if (_clipboardListLoading.value) return
        clipboardLoadJob?.cancel()
        clipboardLoadJob = viewModelScope.launch {
            if (showInitialLoading) {
                _clipboardListLoading.value = true
            }
            try {
                val loaded = loadClipboardPageBatch(more = false)
                _clipboardPagedEntries.value = loaded
                clipboardPagesInitialized = true
            } finally {
                _clipboardListLoading.value = false
            }
        }
    }

    fun loadMoreClipboard() {
        if (_clipboardListLoading.value || clipboardReachedEnd) return
        if (clipboardSearchQuery.value.isNotBlank()) return
        clipboardLoadJob?.cancel()
        clipboardLoadJob = viewModelScope.launch {
            _clipboardListLoading.value = true
            try {
                loadClipboardPageBatch(more = true)
            } finally {
                _clipboardListLoading.value = false
            }
        }
    }

    private suspend fun runSearch(query: String) {
        val repo = clipboardRepository ?: return
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _clipboardSearchResults.value = emptyList()
            return
        }
        _clipboardListLoading.value = true
        try {
            _clipboardSearchResults.value = withContext(Dispatchers.IO) {
                repo.search(trimmed)
            }
        } finally {
            _clipboardListLoading.value = false
        }
    }

    private suspend fun syncPagedListAfterRevision() {
        val repo = clipboardRepository ?: return
        val current = _clipboardPagedEntries.value
        val freshTop = withContext(Dispatchers.IO) {
            repo.loadPage(
                createdBeforeMs = null,
                limit = HistoryFloatPagination.PAGE_SIZE,
                filter = clipboardPagesFilter,
            )
        }.entries
        if (freshTop.isEmpty()) {
            _clipboardPagedEntries.value = emptyList()
            clipboardPagesInitialized = true
            clipboardReachedEnd = true
            return
        }
        val lastCreated = freshTop.lastOrNull()?.createdAtEpochMs
        val preservedTail = if (lastCreated != null && current.size > freshTop.size) {
            current.filter { it.createdAtEpochMs < lastCreated }
        } else {
            emptyList()
        }
        _clipboardPagedEntries.value = freshTop + preservedTail
        clipboardPagesInitialized = true
        clipboardReachedEnd = freshTop.size < HistoryFloatPagination.PAGE_SIZE && preservedTail.isEmpty()
    }

    private suspend fun loadClipboardPageBatch(more: Boolean): List<ClipboardEntry> {
        val repo = clipboardRepository ?: return emptyList()
        if (!more) {
            clipboardReachedEnd = false
        } else if (clipboardReachedEnd) {
            return _clipboardPagedEntries.value
        }
        val current = if (more) _clipboardPagedEntries.value.toMutableList() else mutableListOf()
        var batchCount = 0
        var cursor: Long? = if (more) current.lastOrNull()?.createdAtEpochMs else null
        while (!clipboardReachedEnd) {
            val page = withContext(Dispatchers.IO) {
                repo.loadPage(
                    createdBeforeMs = cursor,
                    limit = HistoryFloatPagination.PAGE_SIZE,
                    filter = clipboardPagesFilter,
                )
            }
            if (page.entries.isEmpty()) {
                clipboardReachedEnd = true
                break
            }
            val existingIds = current.mapTo(mutableSetOf()) { it.id }
            val itemsToAdd = if (current.isEmpty()) {
                page.entries.distinctBy { it.id }
            } else {
                page.entries.filter { existingIds.add(it.id) }
            }
            if (itemsToAdd.isNotEmpty()) {
                current.addAll(itemsToAdd)
                batchCount += itemsToAdd.size
            }
            clipboardReachedEnd = !page.hasMore
            if (batchCount >= HistoryFloatPagination.PAGE_SIZE) break
            if (itemsToAdd.isEmpty()) {
                clipboardReachedEnd = true
                break
            }
            cursor = current.lastOrNull()?.createdAtEpochMs
        }
        if (more) {
            _clipboardPagedEntries.value = current
        }
        clipboardPagesInitialized = current.isNotEmpty() || clipboardReachedEnd
        return current
    }

    companion object {
        private const val KEY_STASH_SEARCH = "stash_search"
        private const val KEY_CLIPBOARD_SEARCH = "clipboard_search"
        private const val KEY_EXPANDED_IDS = "expanded_entry_ids"
        private const val KEY_IMAGE_INDICES = "selected_image_indices"
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val KEY_CLIPBOARD_FILTER = "clipboard_filter"
        /** 侧栏入场动画 + chrome z-order 抬升后再刷新剪贴板，避免与 WM/DB 并发。 */
        private const val CLIPBOARD_TAB_ACTIVATE_DELAY_MS = 450L
    }
}

/**
 * 标签筛选谓词（多选）。
 *
 * - [selected] 为空 = 没筛标签 → **恒真**（一条不过滤，行为与改动前的"「全部」"完全相同）；
 * - [matchAll] = true → AND：条目必须**同时含全部**选中标签（默认，需求 2）；
 * - [matchAll] = false → OR：含**任一**选中标签即可（需求 3 的「任一」）。
 *
 * 只选一个标签时 AND / OR 退化成同一个谓词 —— 这正是"单选时代的行为不用重学"的根据。
 */
private fun StashEntry.matchesTagFilter(
    selected: Set<String>,
    matchAll: Boolean,
    meta: StashMetaStore,
): Boolean {
    if (selected.isEmpty()) return true
    val names = meta.tagsOf(id)
    return if (matchAll) selected.all { it in names } else selected.any { it in names }
}
