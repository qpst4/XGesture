package com.slideindex.app.clipboardfloat

import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class ClipboardFloatListController(
    private val repository: com.slideindex.app.clipboardfloat.ClipboardFloatHistorySource?,
    private val scope: CoroutineScope
) {
    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = _entries.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ClipboardEntry>>(emptyList())

    val filteredEntries: StateFlow<List<ClipboardEntry>> = combine(
        _entries,
        _searchResults,
        _searchQuery
    ) { entries, searched, query ->
        if (query.trim().isEmpty()) entries else searched
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        scope.launch {
            _searchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { query -> runSearch(query) }
        }
        scope.launch {
            repository?.revision?.collect {
                if (it == 0L) return@collect
                if (_searchQuery.value.isNotBlank()) {
                    runSearch(_searchQuery.value)
                } else {
                    reload()
                }
            }
        }
        reload()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun reload() {
        if (repository == null) return
        loadJob?.cancel()
        loadJob = scope.launch {
            val showLoading = _entries.value.isEmpty() && _searchQuery.value.isBlank()
            if (showLoading) _loading.value = true
            try {
                val page = withContext(Dispatchers.IO) {
                    repository.loadPage(limit = ClipboardFloatWindowMetrics.PAGE_SIZE)
                }
                _entries.value = page
            } finally {
                if (showLoading) _loading.value = false
            }
        }
    }

    private fun runSearch(query: String) {
        val repo = repository ?: return
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            _loading.value = true
            try {
                _searchResults.value = withContext(Dispatchers.IO) {
                    repo.search(trimmed)
                }
            } finally {
                _loading.value = false
            }
        }
    }
}
