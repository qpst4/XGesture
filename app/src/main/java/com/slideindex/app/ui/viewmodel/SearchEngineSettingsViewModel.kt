package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.overlay.pickresult.invalidateSearchEngineIconCache
import com.slideindex.app.search.ShareImageTarget
import com.slideindex.app.search.ShareImageTargetResolver
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.search.SearchEngineImportResult
import com.slideindex.app.search.SearchEngineImporter
import com.slideindex.app.search.SearchEngineValidator
import com.slideindex.app.search.SearchHistoryRepository
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.settings.AggregatedImageSearchEnginePreferencesStore
import com.slideindex.app.settings.SearchPanelHistoryCapacity
import com.slideindex.app.settings.SearchPanelSectionAliasSettings
import com.slideindex.app.settings.SearchPanelEnterAction
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchGroupDefaults
import com.slideindex.app.settings.SearchGroupKind
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.SearchEngineEditorCategory
import com.slideindex.app.ui.SearchEngineEditorResult
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope

data class SearchEngineDraft(
    val engineId: String = "",
    val name: String = "",
    val aliasCode: String = "",
    val engineType: SearchEngineType = SearchEngineType.DIRECT_LINK,
    val searchLink: String = "",
    val externJumpLink: String = "",
    val externJumpPackage: String = "",
    val targetPackage: String = "",
    val targetActivity: String = "",
    val autoInputEnter: Boolean = true,
    val pendingIconUri: Uri? = null,
    val pendingIconPath: String? = null,
    val pendingTextIcon: String? = null,
    /** 「用应用图标」时跟随的包名；非空表示图标实时跟随该应用（含图标库覆盖）。 */
    val pendingIconSourcePackage: String? = null,
)

data class SearchEngineImportPreviewState(
    val uri: Uri,
    val sourceLabel: String,
    val importedCount: Int,
    val skippedCount: Int,
    val mergedEngines: List<SearchEngineConfig>,
)

data class SearchGroupDraft(
    val groupId: String = "",
    val kind: SearchGroupKind = SearchGroupKind.TEXT,
    val name: String = "",
    val enabled: Boolean = true,
    val memberIds: List<String> = emptyList(),
    val intervalMillis: Int = SearchGroupDefaults.DEFAULT_INTERVAL_MILLIS,
    val iconType: SearchIconType = SearchIconType.OTHER,
    val iconPath: String? = null,
    val textIcon: String? = null,
    val iconSourcePackage: String? = null,
) {
    val isNew: Boolean get() = groupId.isBlank()
}

@HiltViewModel
class SearchEngineSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val clipboardHistoryRepository: ClipboardHistoryRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val _editorDraft = MutableStateFlow<SearchEngineDraft?>(null)
    val editorDraft: StateFlow<SearchEngineDraft?> = _editorDraft.asStateFlow()

    /**
     * 当前草稿所属的分类。
     *
     * 文字引擎编辑器与图片分享编辑器共用同一个 Activity 级 ViewModel 实例，而「新增」时
     * 两边传进来的 engineId 都是空串 —— 只按 engineId 判重，就会把「图片新增」留下的草稿
     * 当成「文字新增」的同一份草稿复用，于是文字编辑器按图片分享布局渲染（只有
     * 「选择接收图片分享的应用」，没有链接/跳转/分享 tab）。
     */
    private var draftCategory: SearchEngineEditorCategory? = null

    fun initDraft(initialEngine: SearchEngineConfig?, category: SearchEngineEditorCategory) {
        val currentDraft = _editorDraft.value
        if (currentDraft != null && draftCategory == category) {
            val targetId = initialEngine?.id.orEmpty()
            if (currentDraft.engineId == targetId) return
            // settings 还没把新引擎推下来时，不要用 null 覆盖已有 draft；
            // 等 initialEngine 到达后，这里会因 id 不同而重建。
            if (initialEngine == null) return
        }
        val isLegacyExternalLink = initialEngine?.engineType == SearchEngineType.EXTERN_JUMP_LINK
        val normalizedEngineType = when {
            category == SearchEngineEditorCategory.IMAGE_SHARE ->
                SearchEngineType.SHARE_IMAGE_TO_APP
            isLegacyExternalLink -> SearchEngineType.DIRECT_LINK
            else -> initialEngine?.engineType ?: SearchEngineType.DIRECT_LINK
        }
        val normalizedSearchLink = initialEngine?.searchLink.orEmpty().ifBlank {
            if (isLegacyExternalLink) initialEngine.externJumpLink.orEmpty() else ""
        }
        val normalizedTargetPackage = initialEngine?.targetPackage.orEmpty().ifBlank {
            if (isLegacyExternalLink) initialEngine.externJumpPackage.orEmpty() else ""
        }
        _editorDraft.value = SearchEngineDraft(
            engineId = initialEngine?.id.orEmpty(),
            name = initialEngine?.name.orEmpty(),
            aliasCode = initialEngine?.aliasCode.orEmpty(),
            engineType = normalizedEngineType,
            searchLink = normalizedSearchLink,
            externJumpLink = initialEngine?.externJumpLink.orEmpty(),
            externJumpPackage = initialEngine?.externJumpPackage.orEmpty(),
            targetPackage = normalizedTargetPackage,
            targetActivity = initialEngine?.targetActivity.orEmpty(),
            autoInputEnter = initialEngine?.autoInputEnter ?: true,
            pendingIconPath = initialEngine?.iconPath?.takeIf { initialEngine.iconType == SearchIconType.URI },
            pendingTextIcon = initialEngine?.textIcon?.takeIf { initialEngine.iconType == SearchIconType.TEXT },
            pendingIconSourcePackage = initialEngine?.iconSourcePackage,
        )
        draftCategory = category
    }

    fun updateDraft(transform: (SearchEngineDraft) -> SearchEngineDraft) {
        _editorDraft.value = _editorDraft.value?.let(transform)
    }

    fun clearDraft() {
        _editorDraft.value = null
        draftCategory = null
    }

    private val _groupDraft = MutableStateFlow<SearchGroupDraft?>(null)
    val groupDraft: StateFlow<SearchGroupDraft?> = _groupDraft.asStateFlow()
    private var groupDraftKey: Pair<String, SearchGroupKind>? = null

    fun initGroupDraft(initialEngine: SearchEngineConfig?, kind: SearchGroupKind) {
        val key = initialEngine?.id.orEmpty() to kind
        if (_groupDraft.value != null && groupDraftKey == key) return
        val group = initialEngine?.takeIf {
            it.engineType == SearchEngineType.SEARCH_GROUP && it.groupKind == kind
        }
        val defaultCountText = group?.groupMemberIds?.size?.toString()
        val textIcon = group?.textIcon?.takeUnless {
            group.iconType == SearchIconType.TEXT && it == defaultCountText
        }
        _groupDraft.value = SearchGroupDraft(
            groupId = group?.id.orEmpty(),
            kind = kind,
            name = group?.name.orEmpty(),
            enabled = group?.showInPickPanel ?: true,
            memberIds = group?.groupMemberIds.orEmpty(),
            intervalMillis = group?.groupIntervalMillis
                ?: SearchGroupDefaults.DEFAULT_INTERVAL_MILLIS,
            iconType = if (textIcon == null && group?.iconType == SearchIconType.TEXT) {
                SearchIconType.OTHER
            } else {
                group?.iconType ?: SearchIconType.OTHER
            },
            iconPath = group?.iconPath,
            textIcon = textIcon,
            iconSourcePackage = group?.iconSourcePackage,
        )
        groupDraftKey = key
    }

    fun updateGroupDraft(transform: (SearchGroupDraft) -> SearchGroupDraft) {
        _groupDraft.value = _groupDraft.value?.let(transform)
    }

    fun clearGroupDraft() {
        _groupDraft.value = null
        groupDraftKey = null
    }

    fun addGroupMembers(ids: List<String>) {
        if (ids.isEmpty()) return
        val current = _groupDraft.value ?: return
        _groupDraft.value = current.copy(memberIds = (current.memberIds + ids).distinct())
    }

    fun removeGroupMember(id: String) {
        val current = _groupDraft.value ?: return
        _groupDraft.value = current.copy(memberIds = current.memberIds.filterNot { it == id })
    }

    fun reorderGroupMembers(orderedIds: List<String>) {
        val current = _groupDraft.value ?: return
        _groupDraft.value = current.copy(memberIds = orderedIds.distinct())
    }

    fun saveGroupDraft(onSaved: () -> Unit) {
        val draft = _groupDraft.value ?: return
        val name = draft.name.trim()
        if (name.isBlank()) {
            userMessageBus.showError(appContext.getString(R.string.search_group_name_required))
            return
        }
        if (draft.memberIds.isEmpty()) {
            userMessageBus.showError(
                appContext.getString(
                    if (draft.kind == SearchGroupKind.IMAGE_SHARE) {
                        R.string.search_group_image_empty_subtitle
                    } else {
                        R.string.search_group_empty_subtitle
                    },
                ),
            )
            return
        }
        val previousIconPath = settings.value.searchEngines
            .find { it.id == draft.groupId }
            ?.iconPath
        viewModelScope.launch {
            var savedEngine: SearchEngineConfig? = null
            settingsRepository.updateSearchEngines { current ->
                val existing = current.find { it.id == draft.groupId }
                val iconType = when {
                    draft.iconSourcePackage != null -> SearchIconType.OTHER
                    draft.iconPath != null -> SearchIconType.URI
                    !draft.textIcon.isNullOrBlank() -> SearchIconType.TEXT
                    else -> SearchIconType.OTHER
                }
                val engine = SearchEngineConfig(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    engineType = SearchEngineType.SEARCH_GROUP,
                    iconType = iconType,
                    iconPath = draft.iconPath.takeIf { iconType == SearchIconType.URI },
                    textIcon = draft.textIcon.takeIf { iconType == SearchIconType.TEXT },
                    iconSourcePackage = draft.iconSourcePackage.takeIf {
                        iconType == SearchIconType.OTHER
                    },
                    showInPickPanel = draft.enabled,
                    groupKind = draft.kind,
                    groupMemberIds = draft.memberIds,
                    groupIntervalMillis = draft.intervalMillis.coerceIn(
                        SearchGroupDefaults.MIN_INTERVAL_MILLIS,
                        SearchGroupDefaults.MAX_INTERVAL_MILLIS,
                    ),
                )
                savedEngine = engine
                val engines = current.toMutableList()
                val index = engines.indexOfFirst { it.id == engine.id }
                if (index >= 0) {
                    engines[index] = engine.copy(sortOrder = engines[index].sortOrder)
                } else {
                    val order = (engines.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    engines += engine.copy(sortOrder = order)
                }
                engines.sortedBy { it.sortOrder }
                    .mapIndexed { index, item -> item.copy(sortOrder = index) }
            }.onSuccess {
                savedEngine?.let { invalidateSearchEngineIconCache(it.id) }
                if (previousIconPath != draft.iconPath) {
                    SearchEngineIconStorage.deleteIconIfOwned(appContext, previousIconPath)
                }
                userMessageBus.showSuccess(appContext.getString(R.string.search_group_saved))
                onSaved()
            }.onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
        }
    }

    fun deleteGroup(id: String, onDeleted: () -> Unit) {
        if (id.isBlank()) return
        viewModelScope.launch {
            settingsRepository.updateSearchEngines { current ->
                current.filterNot { it.id == id }
                    .sortedBy { it.sortOrder }
                    .mapIndexed { index, engine -> engine.copy(sortOrder = index) }
            }.onSuccess {
                invalidateSearchEngineIconCache(id)
                userMessageBus.showSuccess(appContext.getString(R.string.search_group_deleted))
                onDeleted()
            }.onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
        }
    }

    private val _importPreviewState = MutableStateFlow<SearchEngineImportPreviewState?>(null)
    val importPreviewState: StateFlow<SearchEngineImportPreviewState?> = _importPreviewState.asStateFlow()

    val searchHistoryEntryCount: StateFlow<Int> = searchHistoryRepository.entries
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            val existing = settings.value.searchEngines
            SearchEngineImporter.importFromUri(
                context = appContext,
                uri = uri,
                existing = existing,
                replaceExisting = false,
            ).fold(
                onSuccess = { result ->
                    _importPreviewState.value = result.toPreviewState(uri)
                },
                onFailure = { error ->
                    userMessageBus.showError(
                        appContext.getString(
                            R.string.search_engine_import_failed,
                            error.message.orEmpty(),
                        ),
                    )
                },
            )
        }
    }

    fun dismissImportPreview() {
        _importPreviewState.value = null
    }

    fun importJsonText(raw: String) {
        viewModelScope.launch {
            runCatching { SearchEngineImporter.parseJsonText(raw) }
                .onSuccess { imported ->
                    importParsedJson(imported)
                }
                .onFailure { error ->
                    userMessageBus.showError(
                        appContext.getString(
                            R.string.search_engine_import_failed,
                            error.message.orEmpty(),
                        ),
                    )
                }
        }
    }

    fun importParsedJson(imported: List<SearchEngineConfig>) {
        viewModelScope.launch {
            SearchEngineImporter.importParsed(
                context = appContext,
                imported = imported,
                existing = settings.value.searchEngines,
                replaceExisting = false,
            ).onSuccess { result ->
                persistEngines(result.mergedEngines) {
                    when {
                        result.importedCount > 0 && result.skippedCount > 0 -> {
                            userMessageBus.showSuccess(
                                appContext.getString(
                                    R.string.search_engine_import_json_success_skipped,
                                    result.importedCount,
                                    result.skippedCount,
                                ),
                            )
                        }
                        result.importedCount > 0 -> {
                            userMessageBus.showSuccess(
                                appContext.getString(
                                    R.string.search_engine_import_json_success,
                                    result.importedCount,
                                ),
                            )
                        }
                        result.skippedCount > 0 -> {
                            userMessageBus.showError(
                                appContext.getString(
                                    R.string.search_engine_import_json_all_skipped,
                                    result.skippedCount,
                                ),
                            )
                        }
                        else -> {
                            userMessageBus.showError(
                                appContext.getString(R.string.search_engine_import_json_invalid),
                            )
                        }
                    }
                }
            }.onFailure { error ->
                userMessageBus.showError(
                    if (error.message == "no_search_engines") {
                        appContext.getString(R.string.search_engine_import_json_no_engines)
                    } else {
                        appContext.getString(
                            R.string.search_engine_import_failed,
                            error.message.orEmpty(),
                        )
                    },
                )
            }
        }
    }

    fun confirmImport(replaceExisting: Boolean) {
        val preview = _importPreviewState.value ?: return
        viewModelScope.launch {
            val existing = settings.value.searchEngines
            val result = if (replaceExisting) {
                SearchEngineImporter.importFromUri(
                    context = appContext,
                    uri = preview.uri,
                    existing = existing,
                    replaceExisting = true,
                )
            } else {
                Result.success(
                    SearchEngineImportResult(
                        importedCount = preview.importedCount,
                        skippedCount = preview.skippedCount,
                        sourceLabel = preview.sourceLabel,
                        mergedEngines = preview.mergedEngines,
                    ),
                )
            }
            result.fold(
                onSuccess = { importResult ->
                    persistEngines(importResult.mergedEngines) {
                        _importPreviewState.value = null
                        userMessageBus.showSuccess(
                            appContext.resources.getQuantityString(
                                R.plurals.search_engine_import_success,
                                importResult.importedCount,
                                importResult.importedCount,
                                importResult.sourceLabel,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    userMessageBus.showError(
                        appContext.getString(
                            R.string.search_engine_import_failed,
                            error.message.orEmpty(),
                        ),
                    )
                },
            )
        }
    }

    fun upsertEngine(result: SearchEngineEditorResult) {
        if (!SearchEngineValidator.validate(result.engine)) {
            userMessageBus.showError(appContext.getString(R.string.search_engine_validation_failed))
            return
        }
        viewModelScope.launch {
            val existing = settings.value.searchEngines
            val previous = existing.find { it.id == result.engine.id }
            var engine = result.engine
            when {
                result.iconUri != null -> {
                    val iconPath = SearchEngineIconStorage.saveIconFromUri(appContext, result.iconUri)
                    if (iconPath != null) {
                        previous?.iconPath?.let { oldPath ->
                            if (oldPath != iconPath) {
                                SearchEngineIconStorage.deleteIconIfOwned(appContext, oldPath)
                            }
                        }
                        engine = engine.copy(
                            iconType = SearchIconType.URI,
                            iconPath = iconPath,
                            textIcon = null,
                        )
                    }
                }
                result.savedIconPath != null -> {
                    previous?.iconPath?.let { oldPath ->
                        if (oldPath != result.savedIconPath) {
                            SearchEngineIconStorage.deleteIconIfOwned(appContext, oldPath)
                        }
                    }
                    engine = engine.copy(
                        iconType = SearchIconType.URI,
                        iconPath = result.savedIconPath,
                        textIcon = null,
                    )
                }
                result.engine.iconSourcePackage != null -> {
                    // 「用应用图标」：不落盘，清掉旧快照，渲染时实时取该应用的当前图标。
                    previous?.iconPath?.let { SearchEngineIconStorage.deleteIconIfOwned(appContext, it) }
                    engine = engine.copy(
                        iconType = SearchIconType.OTHER,
                        iconPath = null,
                        textIcon = null,
                    )
                }
                result.engine.iconType == SearchIconType.TEXT -> {
                    previous?.iconPath?.let { oldPath ->
                        SearchEngineIconStorage.deleteIconIfOwned(appContext, oldPath)
                    }
                    engine = engine.copy(
                        iconType = SearchIconType.TEXT,
                        iconPath = null,
                        textIcon = result.engine.textIcon,
                    )
                }
            }
            val engines = existing.toMutableList()
            val index = engines.indexOfFirst { it.id == engine.id }
            if (index >= 0) {
                engines[index] = engine.copy(sortOrder = engines[index].sortOrder)
            } else {
                val order = (engines.maxOfOrNull { it.sortOrder } ?: -1) + 1
                engines += engine.copy(sortOrder = order)
            }
            persistEngines(engines.sortedBy { it.sortOrder }) {
                invalidateSearchEngineIconCache(engine.id)
                userMessageBus.showSuccess(appContext.getString(R.string.search_engine_saved))
            }
        }
    }

    fun addEngines(newEngines: List<SearchEngineConfig>) {
        if (newEngines.isEmpty()) return
        viewModelScope.launch {
            appendEngines(newEngines).onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
        }
    }

    /**
     * 原子追加一批引擎。
     *
     * 不能用 `settings.value` 当基线：它是 `stateIn(WhileSubscribed)` 的快照，页面没有
     * 订阅者（例如批量选择页）时不会更新，拿它拼新列表会把上次新增的引擎整表覆盖掉。
     */
    private suspend fun appendEngines(newEngines: List<SearchEngineConfig>): Result<Unit> =
        settingsRepository.updateSearchEngines { current ->
            SearchEngineStore.appendEngines(current, newEngines)
        }.onSuccess {
            newEngines.forEach { engine -> invalidateSearchEngineIconCache(engine.id) }
        }

    /**
     * 批量添加分享引擎：每个勾选的分享入口生成一条 [engineType] 引擎（图片 / 文件分享共用）。
     *
     * 同包名 + 同 Activity 的已存在目标直接跳过；名称用「应用名 · 入口名」，
     * 这样同一个应用勾了多个入口时彼此仍能区分。
     */
    fun addShareEngines(targets: List<ShareImageTarget>, engineType: SearchEngineType) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            // 判重同样不能用 settings.value（同上：可能是旧快照，会让重复目标漏过判重）。
            val existing = withContext(Dispatchers.IO) {
                settingsRepository.readFreshSnapshot().searchEngines
            }
            val fresh = targets
                .distinctBy { "${it.packageName}/${it.activityClassName}" }
                .filterNot { target ->
                    existing.any { engine ->
                        engine.engineType == engineType &&
                            engine.targetPackage == target.packageName &&
                            engine.targetActivity == target.activityClassName
                    }
                }
            if (fresh.isEmpty()) {
                userMessageBus.showSuccess(
                    appContext.getString(R.string.share_target_batch_exists),
                )
                return@launch
            }
            // 不再落盘快照：记录图标来源包名，运行期实时取该应用的当前图标（含图标库覆盖）。
            val newEngines = fresh.mapIndexed { index, target ->
                SearchEngineConfig(
                    id = "engine_${System.currentTimeMillis()}_$index",
                    name = ShareImageTargetResolver.displayGroupTitle(target),
                    engineType = engineType,
                    iconType = SearchIconType.OTHER,
                    iconSourcePackage = target.packageName,
                    targetPackage = target.packageName,
                    targetActivity = target.activityClassName,
                )
            }
            appendEngines(newEngines)
                .onSuccess {
                    userMessageBus.showSuccess(
                        appContext.resources.getQuantityString(
                            R.plurals.share_target_batch_added,
                            newEngines.size,
                            newEngines.size,
                        ),
                    )
                }
                .onFailure {
                    userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
                }
        }
    }

    fun deleteEngine(id: String) {
        viewModelScope.launch {
            val engines = settings.value.searchEngines
            val removed = engines.find { it.id == id } ?: return@launch
            val remaining = engines
                .filter { it.id != id }
                .map { engine ->
                    if (engine.engineType == SearchEngineType.SEARCH_GROUP) {
                        engine.copy(groupMemberIds = engine.groupMemberIds.filterNot { it == id })
                    } else {
                        engine
                    }
                }
                .sortedBy { it.sortOrder }
                .mapIndexed { index, engine -> engine.copy(sortOrder = index) }
            persistEngines(remaining) {
                invalidateSearchEngineIconCache(removed.id)
                SearchEngineIconStorage.deleteIconIfOwned(appContext, removed.iconPath)
                userMessageBus.showSuccess(appContext.getString(R.string.search_engine_deleted))
            }
        }
    }

    fun moveEngine(id: String, direction: Int) {
        moveEngineInCategory(id, direction) { true }
    }

    /**
     * 把一个搜索引擎复制一份进「拖拽流转栏」。
     *
     * 独立副本：id 加前缀区分，源引擎之后改名/改地址都不会联动到这里，删源引擎也不影响。
     * 同一个引擎重复拖入按 id 去重，只留一份。
     */
    fun addDragFlowTextEngine(engine: SearchEngineConfig) {
        viewModelScope.launch {
            val current = settings.value.dragFlowTextEngines
            val copyId = "dragflow-${engine.id}"
            if (current.any { it.id == copyId }) return@launch
            val copy = engine.copy(
                id = copyId,
                sortOrder = current.size,
                showInPickPanel = true,
            )
            settingsRepository.setDragFlowTextEngines(current + copy)
        }
    }

    fun removeDragFlowTextEngine(id: String) {
        viewModelScope.launch {
            val remaining = settings.value.dragFlowTextEngines
                .filter { it.id != id }
                .mapIndexed { index, engine -> engine.copy(sortOrder = index) }
            settingsRepository.setDragFlowTextEngines(remaining)
        }
    }

    fun moveImageShareEngine(id: String, direction: Int) {
        moveEngineInCategory(id, direction) { it.engineType == SearchEngineType.SHARE_IMAGE_TO_APP }
    }

    private fun moveEngineInCategory(
        id: String,
        direction: Int,
        categoryFilter: (SearchEngineConfig) -> Boolean,
    ) {
        viewModelScope.launch {
            val sorted = settings.value.searchEngines.sortedBy { it.sortOrder }.toMutableList()
            val categoryIndices = sorted.mapIndexedNotNull { index, engine ->
                index.takeIf { categoryFilter(engine) }
            }
            val categoryPosition = categoryIndices.indexOfFirst { sorted[it].id == id }
            if (categoryPosition < 0) return@launch
            val targetPosition = categoryPosition + direction
            if (targetPosition !in categoryIndices.indices) return@launch
            val indexA = categoryIndices[categoryPosition]
            val indexB = categoryIndices[targetPosition]
            val itemA = sorted[indexA]
            sorted[indexA] = sorted[indexB]
            sorted[indexB] = itemA
            persistEngines(sorted.mapIndexed { i, engine -> engine.copy(sortOrder = i) })
        }
    }

    fun setGridColumns(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchEngineGridColumns(value)
    }

    fun setGridRows(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchEngineGridRows(value)
    }

    fun setShowLabels(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchEngineShowLabels(enabled)
    }

    fun setIconShape(shape: com.slideindex.app.settings.SearchEngineIconShape) = launchSettingsWrite {
        settingsRepository.setSearchEngineIconShape(shape)
    }

    fun setDefaultEngineId(id: String?) = launchSettingsWrite {
        settingsRepository.setSearchPanelDefaultEngineId(id)
    }

    fun setSearchPanelInputBehavior(behavior: SearchPanelInputBehavior) = launchSettingsWrite {
        settingsRepository.setSearchPanelInputBehavior(behavior)
    }

    fun setSearchPanelEnterAction(action: SearchPanelEnterAction) = launchSettingsWrite {
        settingsRepository.setSearchPanelEnterAction(action)
    }

    fun setSearchPanelContactSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelContactSearchEnabled(enabled)
    }

    fun setSearchPanelFileSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileSearchEnabled(enabled)
    }

    fun setSearchPanelAppSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelAppSearchEnabled(enabled)
    }

    fun setSearchPanelSettingsSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelSettingsSearchEnabled(enabled)
    }

    fun setSearchPanelShortcutSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelShortcutSearchEnabled(enabled)
    }

    fun setSearchPanelClipboardSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelClipboardSearchEnabled(enabled)
    }

    fun setSearchPanelFileTypesEnabled(types: Set<String>) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileTypesEnabled(types)
    }

    fun setSearchPanelFileShowFolders(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileShowFolders(enabled)
    }

    fun setSearchPanelFileShowSystemFiles(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileShowSystemFiles(enabled)
    }

    fun setSearchPanelFilePreviewsEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFilePreviewsEnabled(enabled)
    }

    fun setSearchPanelFileFolderWhitelist(patterns: Set<String>) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileFolderWhitelist(patterns)
    }

    fun setSearchPanelFileFolderBlacklist(patterns: Set<String>) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileFolderBlacklist(patterns)
    }

    fun setSearchPanelBarPosition(position: com.slideindex.app.settings.SearchPanelBarPosition) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelBarPosition(position)
        }

    fun setSearchPanelListOrder(order: com.slideindex.app.settings.SearchPanelListOrder) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelListOrder(order)
        }

    fun setSearchPanelAppDisplayStyle(style: com.slideindex.app.settings.SearchPanelAppDisplayStyle) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelAppDisplayStyle(style)
        }

    fun setSearchPanelIconShape(shape: com.slideindex.app.overlay.layout.PanelIconShape) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelIconShape(shape)
        }

    fun setSearchPanelCalculatorEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelCalculatorEnabled(enabled)
    }

    fun setSearchPanelWebSuggestionsEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelWebSuggestionsEnabled(enabled)
    }

    fun setSearchPanelWebSuggestionsCount(count: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelWebSuggestionsCount(count)
    }

    fun setSearchPanelShowAggregatedImageEntry(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelShowAggregatedImageEntry(enabled)
    }

    fun setSearchPanelHistoryMaxEntries(maxEntries: Int) = launchSettingsWrite {
        val coerced = SearchPanelHistoryCapacity.coerce(maxEntries)
        val result = settingsRepository.setSearchPanelHistoryMaxEntries(coerced)
        if (result.isSuccess) {
            searchHistoryRepository.trimToMax(coerced)
        }
        result
    }

    fun setSearchPanelSectionAliases(aliases: SearchPanelSectionAliasSettings) = launchSettingsWrite {
        settingsRepository.setSearchPanelSectionAliases(aliases)
    }

    fun clearSearchHistory() = launchRepositoryWrite {
        runCatching { searchHistoryRepository.clear() }
    }

    val clipboardHistoryEntryCount: StateFlow<Int> = clipboardHistoryRepository.entryCount

    fun clearClipboardHistory() = launchRepositoryWrite {
        runCatching { clipboardHistoryRepository.clearAll() }
    }

    fun setSearchPanelBackgroundStyle(style: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelBackgroundStyle(style)
    }

    fun setSearchPanelBlurRadiusDp(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelBlurRadiusDp(value)
    }

    fun setSearchPanelDimPercent(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelDimPercent(value)
    }

    fun updateTextEngines(updatedTextEngines: List<SearchEngineConfig>) {
        viewModelScope.launch {
            val nonTextEngines = settings.value.searchEngines.filter {
                it.engineType == SearchEngineType.SHARE_IMAGE_TO_APP || it.isImageShareGroup()
            }
            val merged = updatedTextEngines + nonTextEngines
            persistEngines(merged.mapIndexed { index, engine -> engine.copy(sortOrder = index) })
        }
    }

    fun toggleEnginePickPanelVisibility(engineId: String) {
        viewModelScope.launch {
            val engines = settings.value.searchEngines.map { engine ->
                if (engine.id == engineId) {
                    engine.copy(showInPickPanel = !engine.showInPickPanel)
                } else {
                    engine
                }
            }
            persistEngines(engines)
        }
    }

    fun reorderPickPanelEngines(ordered: List<SearchEngineConfig>) {
        viewModelScope.launch {
            val sorted = settings.value.searchEngines.sortedBy { it.sortOrder }
            val orderedIds = ordered.map { it.id }.toSet()
            val remaining = sorted.filter { it.id !in orderedIds }
            val merged = ordered + remaining
            persistEngines(merged.mapIndexed { index, engine -> engine.copy(sortOrder = index) })
        }
    }

    fun reorderImageShareEngines(ordered: List<SearchEngineConfig>) {
        viewModelScope.launch {
            val sorted = settings.value.searchEngines.sortedBy { it.sortOrder }
            val orderedIds = ordered.map { it.id }.toSet()
            val remaining = sorted.filter { it.id !in orderedIds }
            val merged = ordered + remaining
            persistEngines(merged.mapIndexed { index, engine -> engine.copy(sortOrder = index) })
        }
    }

    fun reorderFileShareEngines(ordered: List<SearchEngineConfig>) {
        viewModelScope.launch {
            val sorted = settings.value.searchEngines.sortedBy { it.sortOrder }
            val orderedIds = ordered.map { it.id }.toSet()
            val remaining = sorted.filter { it.id !in orderedIds }
            val merged = ordered + remaining
            persistEngines(merged.mapIndexed { index, engine -> engine.copy(sortOrder = index) })
        }
    }

    fun reorderAggregatedImageSearchEngines(ordered: List<AggregatedImageSearchEngineConfig>) {
        viewModelScope.launch {
            persistAggregatedEngines(ordered.mapIndexed { index, config -> config.copy(sortOrder = index) })
        }
    }

    fun moveAggregatedImageSearchEngine(engineId: String, direction: Int) {
        viewModelScope.launch {
            val sorted = settings.value.aggregatedImageSearchEngines
                .sortedBy { it.sortOrder }
                .toMutableList()
            val index = sorted.indexOfFirst { it.engineId == engineId }
            if (index < 0) return@launch
            val target = index + direction
            if (target !in sorted.indices) return@launch
            val item = sorted.removeAt(index)
            sorted.add(target, item)
            persistAggregatedEngines(sorted.mapIndexed { i, config -> config.copy(sortOrder = i) })
        }
    }

    fun setAggregatedImageSearchEngineShowInPanel(engineId: String, show: Boolean) {
        viewModelScope.launch {
            val updated = settings.value.aggregatedImageSearchEngines.map { config ->
                if (config.engineId != engineId) {
                    config
                } else {
                    config.copy(
                        showInPanel = show,
                        preloadOnOpen = if (!show) false else config.preloadOnOpen,
                    )
                }
            }
            persistAggregatedEngines(updated)
        }
    }

    fun setAggregatedImageSearchEnginePreload(engineId: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = settings.value.aggregatedImageSearchEngines.map { config ->
                if (config.engineId == engineId) config.copy(preloadOnOpen = enabled) else config
            }
            persistAggregatedEngines(updated)
        }
    }

    private suspend fun persistAggregatedEngines(configs: List<AggregatedImageSearchEngineConfig>) {
        val merged = AggregatedImageSearchEnginePreferencesStore.mergeWithCatalog(configs)
        settingsRepository.setAggregatedImageSearchEngines(merged)
            .onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
    }

    private suspend fun persistEngines(
        engines: List<SearchEngineConfig>,
        onSuccess: () -> Unit = {},
    ) {
        settingsRepository.setSearchEngines(engines)
            .onSuccess { onSuccess() }
            .onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
    }

    private fun SearchEngineImportResult.toPreviewState(uri: Uri) = SearchEngineImportPreviewState(
        uri = uri,
        sourceLabel = sourceLabel,
        importedCount = importedCount,
        skippedCount = skippedCount,
        mergedEngines = mergedEngines,
    )
}
