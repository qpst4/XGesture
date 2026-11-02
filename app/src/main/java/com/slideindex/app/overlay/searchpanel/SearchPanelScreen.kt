package com.slideindex.app.overlay.searchpanel

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.overlay.BlurredWallpaperCache
import com.slideindex.app.overlay.FloatBallImageSearchPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.OverlayBlurGate
import com.slideindex.app.overlay.OverlaySelectionToolbarActions
import com.slideindex.app.overlay.OverlaySelectionToolbarPopup
import com.slideindex.app.overlay.OverlayToastWindow
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.cutTextFieldValue
import com.slideindex.app.overlay.fieldModifier
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.overlay.pasteIntoTextFieldValue
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGrid
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.overlay.rememberOverlaySelectionToolbarState
import com.slideindex.app.overlay.suppressSystemTextContextMenu
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.search.SearchHistoryAccess
import com.slideindex.app.search.SearchHistoryRecorder
import com.slideindex.app.search.calculator.CalculatorUtils
import com.slideindex.app.search.clipboard.ClipboardSearchIndex
import com.slideindex.app.search.contacts.ContactSearchEntry
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.contacts.ContactSearchLauncher
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.files.FileSearchFilterOptions
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.search.files.FileSearchLauncher
import com.slideindex.app.search.files.FileType
import com.slideindex.app.search.files.FileTypeUtils
import com.slideindex.app.search.shortcuts.ShortcutSearchEntry
import com.slideindex.app.search.shortcuts.ShortcutSearchIndex
import com.slideindex.app.search.settings.SystemSettingsSearchEntry
import com.slideindex.app.search.settings.SystemSettingsSearchIndex
import com.slideindex.app.search.settings.SystemSettingsSearchLauncher
import com.slideindex.app.search.websuggestions.WebSuggestionsUtils
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.settings.SearchPanelBarPosition
import com.slideindex.app.settings.SearchPanelEnterAction
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchPanelListOrder
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboardfloat.ClipboardPasteCoordinator
import com.slideindex.app.clipboardfloat.PasteResult
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.MiuixIcons

enum class SearchMode { TEXT, IMAGE }

private const val APP_CANDIDATE_LIMIT = 10
private const val SETTINGS_CANDIDATE_LIMIT = 6
private const val FILE_CANDIDATE_LIMIT = 8
private const val SHORTCUT_CANDIDATE_LIMIT = 6
private const val CLIPBOARD_CANDIDATE_LIMIT = 6
/** 粘贴前先收起面板并等待焦点回到目标窗口，否则会粘回自己的搜索框。 */
private const val CLIPBOARD_PASTE_AFTER_DISMISS_MS = 260L
private const val SEARCH_DEBOUNCE_MS = 200L
/** 呼出时抢输入框焦点的重试次数：窗口焦点可能早于输入框进入组合。 */
private const val SEARCH_PANEL_IME_FOCUS_ATTEMPTS = 3
/**
 * 面板呼出 / 退出的过渡时长，同时作为 [SearchPanelOverlayWindow] 退出后的收尾隐藏延迟。
 *
 * 超过 200ms 呼出会有明显「慢」的感觉，压到 0 又会像闪一下，这里取 140ms。
 */
internal const val SEARCH_PANEL_ANIM_MS = 140

/** 单行搜索框粘贴多行文本时，换行符会导致 TextField 内容不可见。 */
private fun normalizeSearchPanelQuery(input: String): String =
    input.replace('\r', ' ').replace('\n', ' ')

private fun textFieldValueForInputBehavior(
    behavior: SearchPanelInputBehavior,
    lastQuery: String,
): TextFieldValue = when (behavior) {
    SearchPanelInputBehavior.KEEP -> TextFieldValue(lastQuery)
    SearchPanelInputBehavior.CLEAR -> TextFieldValue("")
    SearchPanelInputBehavior.SELECT_ALL -> TextFieldValue(lastQuery, TextRange(0, lastQuery.length))
}

@Composable
fun SearchPanelScreen(
    visibilityState: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsHolder = remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(context) {
        val flow = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.settings
            ?: return@LaunchedEffect
        flow.collect { settingsHolder.value = it }
    }
    val settings = settingsHolder.value
    val longPressEnabled = settings.launchPolicyLongPressEligible()
    val appRepository = remember(context) {
        OverlayDependencyAccess.overlayDependencies(context)?.appRepository
    }

    // 呈现方式固定为全屏（原「呈现方式」设置项已移除）。
    val barAtBottom = settings.searchPanelBarPosition == SearchPanelBarPosition.BOTTOM
    val bottomUpListOrder = settings.searchPanelListOrder == SearchPanelListOrder.BOTTOM_UP
    val backgroundStyle = settings.searchPanelBackgroundStyle
    val blurRadiusDp = settings.searchPanelBlurRadiusDp
    val dimPercent = settings.searchPanelDimPercent
    val overlayBlurEnabled = settings.overlayBlurEnabled

    // 外部带图进来（拖拽动作 / 系统分享）：面板窗口保活、组合不重建，只能观察状态，不能用 remember 初值。
    val pendingImage by SearchPanelSessionState.pendingImage
    var mode by remember { mutableStateOf(SearchMode.TEXT) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(SearchPanelSessionState.lastTextQuery))
    }
    val textQuery = textFieldValue.text
    var showSearchHistory by remember { mutableStateOf(false) }
    val emptySearchHistoryFlow = remember {
        kotlinx.coroutines.flow.flowOf(emptyList<com.slideindex.app.search.SearchHistoryEntry>())
    }
    val searchHistoryEntries by (
        SearchHistoryAccess.repository?.entries ?: emptySearchHistoryFlow
        ).collectAsState(initial = emptyList())
    val searchHistoryQueries = remember(searchHistoryEntries) {
        searchHistoryEntries.map { it.query }
    }
    var debouncedQuery by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pendingImage) {
        val bitmap = pendingImage ?: return@LaunchedEffect
        SearchPanelSessionState.consumePendingImage()
        imageUri = null
        imageBitmap = bitmap
        mode = SearchMode.IMAGE
    }
    val appsForSearch by (appRepository?.appsForSearch ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = appRepository?.getCachedAppsForSearch().orEmpty())
    val appsForSearchRevision by (appRepository?.appsForSearchRevision ?: kotlinx.coroutines.flow.flowOf(0L))
        .collectAsState(initial = 0L)
    var settingsCandidates by remember { mutableStateOf<List<SystemSettingsSearchEntry>>(emptyList()) }
    var contactCandidates by remember { mutableStateOf<List<ContactSearchEntry>>(emptyList()) }
    var fileCandidates by remember { mutableStateOf<List<DeviceFileEntry>>(emptyList()) }
    var appCandidates by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var shortcutCandidates by remember { mutableStateOf<List<ShortcutSearchEntry>>(emptyList()) }
    var clipboardCandidates by remember { mutableStateOf<List<ClipboardEntry>>(emptyList()) }
    var webSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var contactsExpanded by remember { mutableStateOf(false) }
    var filesExpanded by remember { mutableStateOf(false) }
    var appsExpanded by remember { mutableStateOf(false) }
    var shortcutsExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var clipboardExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    var manuallySwitchedToNumberKeyboard by remember { mutableStateOf(false) }
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var usesNativeWindowBlur by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<DeviceFileEntry?>(null) }
    val previewVisibilityState = remember { MutableTransitionState(false) }

    var hasContactPermission by remember(context, debouncedQuery) {
        mutableStateOf(ContactSearchIndex.hasPermission(context))
    }
    var hasFilePermission by remember(context, debouncedQuery) {
        mutableStateOf(FileSearchIndex.hasPermission(context))
    }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    var lockedSection by remember { mutableStateOf(SearchPanelResultSection.ALL) }
    var lockedEngineId by remember { mutableStateOf<String?>(null) }
    var showSectionMenu by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun hideSearchKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun clearSearchLocks() {
        lockedSection = SearchPanelResultSection.ALL
        lockedEngineId = null
    }

    fun allowsResultSection(section: SearchPanelResultSection): Boolean =
        lockedSection == SearchPanelResultSection.ALL || lockedSection == section

    /** 开关只控制「全部」下的自动候选；图标/别名锁定到某类时仍主动搜索。 */
    fun shouldFetchCandidateSection(section: SearchPanelResultSection): Boolean {
        if (lockedSection == section) return true
        if (lockedSection != SearchPanelResultSection.ALL) return false
        return when (section) {
            SearchPanelResultSection.APPS -> settings.searchPanelAppSearchEnabled
            SearchPanelResultSection.SHORTCUTS -> settings.searchPanelShortcutSearchEnabled
            SearchPanelResultSection.CONTACTS -> settings.searchPanelContactSearchEnabled
            SearchPanelResultSection.FILES -> settings.searchPanelFileSearchEnabled
            SearchPanelResultSection.SETTINGS -> settings.searchPanelSettingsSearchEnabled
            SearchPanelResultSection.CLIPBOARD -> settings.searchPanelClipboardSearchEnabled
            SearchPanelResultSection.ALL -> false
        }
    }

    val engines = settings.searchEngines
    val textEngines = remember(engines) { SearchEngineStore.textPickPanelEngines(engines) }
    val imageEngines = remember(engines) { SearchEngineStore.imageSharePanelEngines(engines) }

    fun resolveTextSearchEngine(): SearchEngineConfig? {
        lockedEngineId?.let { id -> textEngines.find { it.id == id } }?.let { return it }
        // 默认引擎允许是已隐藏的引擎，故按全量文本引擎解析；解析不到再回退首个可见引擎。
        return SearchEngineStore.findTextEngineById(engines, settings.searchPanelDefaultEngineId)
            ?: textEngines.firstOrNull()
    }

    fun applyTextFieldInput(updated: TextFieldValue) {
        val normalized = normalizeSearchPanelQuery(updated.text)
        if (textQuery.isEmpty() && normalized.isNotEmpty() && normalized.all { it.isWhitespace() }) {
            textFieldValue = TextFieldValue("")
            showSearchHistory = true
            return
        }
        val aliasMatch = SearchPanelAliasResolver.detectPrefixAlias(
            normalized,
            textEngines,
            settings.searchPanelSectionAliases,
        )
        when (aliasMatch) {
            is SearchPanelAliasMatch.Engine -> {
                lockedEngineId = aliasMatch.engine.id
                lockedSection = SearchPanelResultSection.ALL
                val stripped = aliasMatch.queryWithoutAlias
                if (stripped.isNotEmpty()) {
                    showSearchHistory = false
                }
                textFieldValue = TextFieldValue(
                    text = stripped,
                    selection = TextRange(stripped.length),
                )
            }
            is SearchPanelAliasMatch.Section -> {
                lockedSection = aliasMatch.section
                lockedEngineId = null
                val stripped = aliasMatch.queryWithoutAlias
                if (stripped.isNotEmpty()) {
                    showSearchHistory = false
                }
                textFieldValue = TextFieldValue(
                    text = stripped,
                    selection = TextRange(stripped.length),
                )
            }
            null -> {
                // 历史模式下继续输入用于过滤历史，不退出；退出靠空框删除键等既有路径
                textFieldValue = updated.copy(text = normalized)
            }
        }
    }

    LaunchedEffect(appRepository) {
        val repository = appRepository ?: return@LaunchedEffect
        repository.loadAppsForSearch()
        withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.ensureLoaded(context)
        }
    }

    LaunchedEffect(appRepository, visibilityState.targetState) {
        val repository = appRepository ?: return@LaunchedEffect
        if (!visibilityState.targetState) return@LaunchedEffect
        repository.loadAppsForSearch(force = true)
        SearchPanelCandidateCache.clear()
        // 快捷方式目录解析较慢（要读各 App 清单），在面板刚呼出、用户还没输入时先预热。
        withContext(Dispatchers.IO) {
            ShortcutSearchIndex.ensureLoaded(context, repository.getCachedAppsForSearch())
        }
    }

    LaunchedEffect(backgroundStyle, blurRadiusDp, overlayBlurEnabled, visibilityState.targetState) {
        if (!visibilityState.targetState) {
            backgroundBitmap = null
            usesNativeWindowBlur = SearchPanelOverlayWindow.updateBackgroundBlur(
                context,
                SearchPanelBackgroundStyle.BLACK,
                0,
            )
            return@LaunchedEffect
        }
        usesNativeWindowBlur = SearchPanelOverlayWindow.updateBackgroundBlur(
            context,
            backgroundStyle,
            // 总开关只作用于跨窗模糊：壁纸模糊（WALLPAPER_BLUR）仍用原始半径。
            OverlayBlurGate.effectiveBlurRadiusDp(overlayBlurEnabled, blurRadiusDp),
        )
        when (backgroundStyle) {
            SearchPanelBackgroundStyle.WALLPAPER_BLUR -> {
                backgroundBitmap = SystemWallpaperBlurHelper.loadBlurred(context, blurRadiusDp)
            }
            SearchPanelBackgroundStyle.BLUR -> {
                // 总开关关闭：跨窗模糊与截图兜底都不走，由卡片自身的实色底承担。
                if (!overlayBlurEnabled || usesNativeWindowBlur || blurRadiusDp <= 0) {
                    backgroundBitmap = null
                    return@LaunchedEffect
                }
                val service = OverlayDependencyAccess.overlayHostContext() as? AccessibilityService
                if (service == null) {
                    backgroundBitmap = null
                    return@LaunchedEffect
                }
                val deferred = CompletableDeferred<Bitmap?>()
                val cached = BlurredWallpaperCache.captureFromDisplay(
                    service,
                    context,
                    blurRadiusDp,
                ) { bitmap ->
                    deferred.complete(bitmap)
                }
                backgroundBitmap = cached ?: deferred.await()
            }
            else -> {
                backgroundBitmap = null
            }
        }
    }

    LaunchedEffect(textQuery, showSearchHistory) {
        // 历史模式只做历史过滤，不触发普通搜索候选
        if (showSearchHistory || textQuery.isBlank()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = textQuery
    }

    LaunchedEffect(debouncedQuery) {
        contactsExpanded = false
        filesExpanded = false
        appsExpanded = false
        shortcutsExpanded = false
        settingsExpanded = false
        clipboardExpanded = false
        historyExpanded = false
    }

    LaunchedEffect(textQuery) {
        if (textQuery.any { it.isLetter() }) {
            manuallySwitchedToNumberKeyboard = false
        }
    }

    LaunchedEffect(
        debouncedQuery,
        settings.searchPanelWebSuggestionsEnabled,
        settings.searchPanelWebSuggestionsCount,
    ) {
        if (!settings.searchPanelWebSuggestionsEnabled || debouncedQuery.length < 2) {
            webSuggestions = emptyList()
            return@LaunchedEffect
        }
        val query = debouncedQuery
        val results = withContext(Dispatchers.IO) {
            WebSuggestionsUtils.getSuggestions(query)
                .take(settings.searchPanelWebSuggestionsCount)
        }
        if (debouncedQuery == query) {
            webSuggestions = results
        }
    }

    LaunchedEffect(
        debouncedQuery,
        lockedSection,
        appsForSearch,
        appsForSearchRevision,
        appRepository,
        settings.searchPanelContactSearchEnabled,
        settings.searchPanelFileSearchEnabled,
        settings.searchPanelAppSearchEnabled,
        settings.searchPanelSettingsSearchEnabled,
        settings.searchPanelShortcutSearchEnabled,
        settings.searchPanelClipboardSearchEnabled,
        settings.searchPanelFileTypesEnabled,
        settings.searchPanelFileShowFolders,
        settings.searchPanelFileShowSystemFiles,
        settings.searchPanelFileFolderWhitelist,
        settings.searchPanelFileFolderBlacklist,
        permissionRefreshKey,
    ) {
        if (debouncedQuery.isBlank()) {
            settingsCandidates = emptyList()
            contactCandidates = emptyList()
            fileCandidates = emptyList()
            appCandidates = emptyList()
            shortcutCandidates = emptyList()
            clipboardCandidates = emptyList()
            return@LaunchedEffect
        }
        val query = debouncedQuery
        val cacheKey = buildString {
            append(query)
            append('|')
            append(lockedSection.name)
            append('|')
            append(settings.searchPanelAppSearchEnabled)
            append('|')
            append(settings.searchPanelContactSearchEnabled)
            append('|')
            append(settings.searchPanelFileSearchEnabled)
            append('|')
            append(settings.searchPanelSettingsSearchEnabled)
            append('|')
            append(settings.searchPanelShortcutSearchEnabled)
            append('|')
            append(settings.searchPanelClipboardSearchEnabled)
            append('|')
            append(settings.searchPanelFileTypesEnabled.joinToString(","))
            append('|')
            append(settings.searchPanelFileShowFolders)
            append('|')
            append(settings.searchPanelFileShowSystemFiles)
            append('|')
            append(appsForSearchRevision)
        }
        val cached = SearchPanelCandidateCache.get(cacheKey)
        if (cached != null) {
            settingsCandidates = cached.settings
            contactCandidates = cached.contacts
            fileCandidates = cached.files
            appCandidates = cached.apps
            shortcutCandidates = cached.shortcuts
            clipboardCandidates = cached.clipboard
            return@LaunchedEffect
        }
        hasContactPermission = withContext(Dispatchers.IO) { ContactSearchIndex.hasPermission(context) }
        hasFilePermission = withContext(Dispatchers.IO) { FileSearchIndex.hasPermission(context) }
        val fetchApps = shouldFetchCandidateSection(SearchPanelResultSection.APPS)
        val fetchSettings = shouldFetchCandidateSection(SearchPanelResultSection.SETTINGS)
        val fetchContacts = shouldFetchCandidateSection(SearchPanelResultSection.CONTACTS) && hasContactPermission
        val fetchFiles = shouldFetchCandidateSection(SearchPanelResultSection.FILES) && hasFilePermission
        val fetchShortcuts = shouldFetchCandidateSection(SearchPanelResultSection.SHORTCUTS)
        val fetchClipboard = shouldFetchCandidateSection(SearchPanelResultSection.CLIPBOARD)
        coroutineScope {
            val settingsDeferred = async(Dispatchers.IO) {
                if (fetchSettings) {
                    SystemSettingsSearchIndex.search(context, query, SETTINGS_CANDIDATE_LIMIT)
                } else {
                    emptyList()
                }
            }
            val contactsDeferred = async(Dispatchers.IO) {
                if (fetchContacts) {
                    ContactSearchIndex.search(context, query, 5)
                } else {
                    emptyList()
                }
            }
            val filesDeferred = async(Dispatchers.IO) {
                if (fetchFiles) {
                    FileSearchIndex.search(
                        context = context,
                        query = query,
                        limit = FILE_CANDIDATE_LIMIT,
                        filterOptions = FileSearchFilterOptions(
                            enabledFileTypes = FileType.fromNames(settings.searchPanelFileTypesEnabled),
                            showFolders = settings.searchPanelFileShowFolders,
                            showSystemFiles = settings.searchPanelFileShowSystemFiles,
                            folderWhitelistPatterns = settings.searchPanelFileFolderWhitelist,
                            folderBlacklistPatterns = settings.searchPanelFileFolderBlacklist,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
            val appsDeferred = async(Dispatchers.Default) {
                val repository = appRepository
                if (!fetchApps || repository == null) {
                    emptyList()
                } else {
                    repository.searchApps(appsForSearch, query, APP_CANDIDATE_LIMIT)
                }
            }
            val shortcutsDeferred = async(Dispatchers.IO) {
                if (fetchShortcuts) {
                    ShortcutSearchIndex.search(
                        context = context,
                        apps = appsForSearch,
                        query = query,
                        limit = SHORTCUT_CANDIDATE_LIMIT,
                    )
                } else {
                    emptyList()
                }
            }
            val clipboardDeferred = async(Dispatchers.IO) {
                if (fetchClipboard) {
                    ClipboardSearchIndex.search(query, CLIPBOARD_CANDIDATE_LIMIT)
                } else {
                    emptyList()
                }
            }
            if (!currentCoroutineContext().isActive || debouncedQuery != query) return@coroutineScope
            val settingsResults = settingsDeferred.await()
            val contactsResults = contactsDeferred.await()
            val filesResults = filesDeferred.await()
            val appsResults = appsDeferred.await()
            if (!currentCoroutineContext().isActive || debouncedQuery != query) return@coroutineScope
            // 先落地「快」的分区，别让快捷方式/剪贴板这两路拖住首屏候选。
            settingsCandidates = settingsResults
            contactCandidates = contactsResults
            fileCandidates = filesResults
            appCandidates = appsResults
            val shortcutsResults = shortcutsDeferred.await()
            val clipboardResults = clipboardDeferred.await()
            if (!currentCoroutineContext().isActive || debouncedQuery != query) return@coroutineScope
            shortcutCandidates = shortcutsResults
            clipboardCandidates = clipboardResults
            SearchPanelCandidateCache.put(
                cacheKey,
                SearchPanelCandidateCacheEntry(
                    settings = settingsResults,
                    contacts = contactsResults,
                    files = filesResults,
                    apps = appsResults,
                    shortcuts = shortcutsResults,
                    clipboard = clipboardResults,
                ),
            )
        }
    }

    val linkUrls = remember(textQuery, showSearchHistory) {
        if (showSearchHistory) {
            emptyList()
        } else {
            PickResultUrl.extractOpenableUrls(textQuery).ifEmpty {
                PickResultUrl.normalizeOpenableUrl(textQuery.trim())?.let { listOf(it) } ?: emptyList()
            }
        }
    }
    val calculatorResult = remember(textQuery, settings.searchPanelCalculatorEnabled, showSearchHistory) {
        if (showSearchHistory ||
            !settings.searchPanelCalculatorEnabled ||
            !CalculatorUtils.isMathExpression(textQuery)
        ) {
            null
        } else {
            CalculatorUtils.evaluateExpression(textQuery)
        }
    }
    val showCalculator = calculatorResult != null
    val showContactPermissionPrompt =
        shouldFetchCandidateSection(SearchPanelResultSection.CONTACTS) &&
            !showSearchHistory &&
            !hasContactPermission &&
            textQuery.isNotBlank()
    val showFilePermissionPrompt =
        shouldFetchCandidateSection(SearchPanelResultSection.FILES) &&
            !showSearchHistory &&
            !hasFilePermission &&
            textQuery.isNotBlank()
    // Only manual pill toggles IME type. Binding to showCalculator restarts the overlay keyboard.
    val searchKeyboardType = if (manuallySwitchedToNumberKeyboard) {
        KeyboardType.Number
    } else {
        KeyboardType.Text
    }
    val keyboardSwitchText = when {
        showCalculator -> null
        manuallySwitchedToNumberKeyboard -> stringResource(R.string.keyboard_switch_back)
        textQuery.isNotEmpty() &&
            textQuery.none { it.isLetter() } &&
            linkUrls.isEmpty() -> stringResource(R.string.keyboard_switch_to_number)
        else -> null
    }
    val shouldShowPhoneCallAction = keyboardSwitchText != null && textQuery.isPhoneNumberQuery()
    val filteredSearchHistoryQueries = remember(searchHistoryQueries, textQuery, showSearchHistory) {
        if (!showSearchHistory) {
            emptyList()
        } else {
            val needle = textQuery.trim()
            if (needle.isEmpty()) {
                searchHistoryQueries
            } else {
                searchHistoryQueries.filter { it.contains(needle, ignoreCase = true) }
            }
        }
    }
    val showHistoryPanel = mode == SearchMode.TEXT &&
        showSearchHistory &&
        filteredSearchHistoryQueries.isNotEmpty()
    val hasCandidateSection = showCalculator ||
        showHistoryPanel ||
        linkUrls.isNotEmpty() ||
        webSuggestions.isNotEmpty() ||
        appCandidates.isNotEmpty() ||
        shortcutCandidates.isNotEmpty() ||
        settingsCandidates.isNotEmpty() ||
        contactCandidates.isNotEmpty() ||
        fileCandidates.isNotEmpty() ||
        clipboardCandidates.isNotEmpty() ||
        showContactPermissionPrompt ||
        showFilePermissionPrompt

    var wasPanelVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        SearchPanelSessionState.persistBeforeDismiss = {
            SearchPanelSessionState.lastTextQuery = textFieldValue.text
        }
        onDispose {
            SearchPanelSessionState.persistBeforeDismiss = null
        }
    }

    fun clearPreviewState() {
        previewVisibilityState.targetState = false
        previewFile = null
    }

    /** @return true if a visible preview was dismissed (consumes back). */
    fun dismissPreview(): Boolean {
        // Use targetState: once dismiss starts, further backs must reach the panel.
        if (previewFile == null || !previewVisibilityState.targetState) return false
        previewVisibilityState.targetState = false
        return true
    }

    LaunchedEffect(previewVisibilityState.isIdle, previewVisibilityState.currentState) {
        if (previewVisibilityState.isIdle && !previewVisibilityState.currentState) {
            previewFile = null
        }
    }

    val dismissPreviewForBack by rememberUpdatedState(newValue = { dismissPreview() })
    DisposableEffect(Unit) {
        SearchPanelSessionState.onBackPressed = { dismissPreviewForBack() }
        // 供浮层窗口在「窗口获得焦点」的第一时间抢焦点，绕开 Compose 重组延迟。
        SearchPanelSessionState.focusSearchTextField = {
            runCatching { focusRequester.requestFocus() }.isSuccess
        }
        onDispose {
            SearchPanelSessionState.onBackPressed = null
            SearchPanelSessionState.focusSearchTextField = null
        }
    }

    LaunchedEffect(visibilityState.targetState, settings.searchPanelInputBehavior) {
        val visible = visibilityState.targetState
        if (!visible) {
            clearPreviewState()
        } else if (!wasPanelVisible && mode == SearchMode.TEXT) {
            textFieldValue = textFieldValueForInputBehavior(
                behavior = settings.searchPanelInputBehavior,
                lastQuery = SearchPanelSessionState.lastTextQuery,
            )
            debouncedQuery = if (textFieldValue.text.isBlank()) "" else textFieldValue.text
        }
        wasPanelVisible = visible
    }

    // 文本搜索时让窗口保持「获得焦点即弹键盘」（窗口隐藏时不生效），图片模式关掉。
    // 提前常驻这个标志，窗口真正获得焦点的那一刻系统就会弹键盘，不用等我们后面再请求。
    LaunchedEffect(mode) {
        SearchPanelOverlayWindow.updateSoftInputAlwaysVisible(
            visible = mode == SearchMode.TEXT,
        )
    }

    // 焦点与输入法：呼出即抢输入框焦点，并按固定节奏补发几次输入法请求（覆盖窗口焦点稍晚到的情况）。
    LaunchedEffect(visibilityState.targetState, mode) {
        if (!visibilityState.targetState || mode != SearchMode.TEXT) return@LaunchedEffect
        var focused = false
        repeat(SEARCH_PANEL_IME_FOCUS_ATTEMPTS) { attempt ->
            if (focused) return@repeat
            focused = runCatching { focusRequester.requestFocus() }.isSuccess
            if (!focused && attempt < SEARCH_PANEL_IME_FOCUS_ATTEMPTS - 1) {
                withFrameNanos { }
            }
        }
        SearchPanelOverlayWindow.requestImeShow()
    }

    fun persistTextQuery() {
        SearchPanelSessionState.lastTextQuery = textFieldValue.text
    }

    fun dismissPanel() {
        persistTextQuery()
        clearPreviewState()
        onDismiss()
    }

    fun launchSearchEngine(
        engine: SearchEngineConfig,
        longPressTriggered: Boolean,
        queryOverride: String? = null,
    ) {
        val query = (queryOverride ?: textQuery).trim()
        if (mode == SearchMode.TEXT && query.isNotBlank()) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            )
            SearchPanelSessionState.lastTextQuery = query
            SearchHistoryRecorder.record(context, query)
            if (engine.engineType == SearchEngineType.SHARE_TO_APP) {
                SearchEngineLauncher.launchTextShare(context, engine, query)
            } else {
                showSearchHistory = false
                SearchEngineLauncher.launch(context, engine, query, settings, longPressTriggered)
            }
            dismissPanel()
        } else if (mode == SearchMode.IMAGE && imageBitmap != null) {
            if (engine.id == "slideindex_aggregate_image_search") {
                FloatBallImageSearchPanel.show(context, imageBitmap!!)
            } else {
                SearchEngineLauncher.launchImageShare(context, engine, imageBitmap!!)
            }
            dismissPanel()
        }
    }

    fun openUrl(url: String, longPressTriggered: Boolean) {
        persistTextQuery()
        SearchHistoryRecorder.record(context, url)
        showSearchHistory = false
        FloatBallTextPick.openUrl(context, url, settings, longPressTriggered)
        dismissPanel()
    }

    fun launchAppCandidate(app: AppInfo, longPressTriggered: Boolean) {
        val repository = appRepository ?: return
        val fullscreen = settings.shouldLaunchFullscreen(longPressTriggered)
        coroutineScope.launch {
            val wasInactive = !FreezerOperations.stateOf(context, app).isActive
            if (FreezerOperations.launchAndRestore(context, repository, settings, app, fullscreen)) {
                if (wasInactive) {
                    repository.loadAppsForSearch(force = true)
                    SearchPanelCandidateCache.clear()
                }
                dismissPanel()
            }
        }
    }

    fun handleAppQuickAction(app: AppInfo, action: SearchPanelAppQuickAction) {
        when (action) {
            SearchPanelAppQuickAction.FREE_WINDOW -> {
                val repository = appRepository ?: return
                coroutineScope.launch {
                    val wasInactive = !FreezerOperations.stateOf(context, app).isActive
                    if (FreezerOperations.launchAndRestore(context, repository, settings, app, fullscreen = false)) {
                        if (wasInactive) {
                            repository.loadAppsForSearch(force = true)
                            SearchPanelCandidateCache.clear()
                        }
                        dismissPanel()
                    }
                }
            }
            SearchPanelAppQuickAction.SHARE -> {
                coroutineScope.launch {
                    val shared = SearchPanelAppShare.shareApk(
                        context = context,
                        packageName = app.packageName,
                        // 内置面板点中目标前先收起搜索面板，否则目标 App 会被它盖住。
                        dismissSource = { dismissPanel() },
                    )
                    if (!shared) {
                        Toast.makeText(
                            context,
                            R.string.search_panel_app_share_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            SearchPanelAppQuickAction.FREEZE -> {
                coroutineScope.launch {
                    val frozen = FreezerOperations.isFrozen(context, app)
                    val ok = FreezerOperations.setFrozen(context, app, frozen = !frozen)
                    if (ok) {
                        appRepository?.loadAppsForSearch(force = true)
                        SearchPanelCandidateCache.clear()
                    }
                }
                dismissPanel()
            }
            SearchPanelAppQuickAction.PAUSE -> {
                coroutineScope.launch {
                    val paused = FreezerOperations.isPaused(context, app)
                    val ok = FreezerOperations.setPaused(context, app, paused = !paused)
                    if (ok) {
                        appRepository?.loadAppsForSearch(force = true)
                        SearchPanelCandidateCache.clear()
                    }
                }
                dismissPanel()
            }
            SearchPanelAppQuickAction.DETAILS -> {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${app.packageName}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
                dismissPanel()
            }
        }
    }

    fun launchSettingsCandidate(entry: SystemSettingsSearchEntry, longPressTriggered: Boolean) {
        if (SystemSettingsSearchLauncher.launch(context, entry, settings, longPressTriggered)) {
            dismissPanel()
        }
    }

    fun launchShortcutCandidate(entry: ShortcutSearchEntry, @Suppress("UNUSED_PARAMETER") longPressTriggered: Boolean) {
        val item = TaskSwitcherMenuItem(
            label = entry.label,
            type = TaskSwitcherMenuItemType.SHORTCUT,
            shortcutId = entry.shortcutId,
            intentUris = entry.intentUris.takeIf { it.isNotEmpty() },
            kind = entry.kind,
        )
        hideSearchKeyboard()
        AppShortcutLoader.launchShortcut(context, entry.packageName, item)
        dismissPanel()
    }

    /** 剪贴板候选：点按 = 复制回剪贴板。 */
    fun copyClipboardCandidate(entry: ClipboardEntry) {
        hideSearchKeyboard()
        ClipboardWriter.write(context, entry)
        dismissPanel()
    }

    /** 剪贴板候选：长按 = 复制并粘贴到目标输入框（面板收起后再粘，避免粘回搜索框）。 */
    fun pasteClipboardCandidate(entry: ClipboardEntry) {
        val service = OverlayDependencyAccess.overlayHostContext() as? AccessibilityService
        if (service == null) {
            copyClipboardCandidate(entry)
            return
        }
        hideSearchKeyboard()
        dismissPanel()
        coroutineScope.launch {
            delay(CLIPBOARD_PASTE_AFTER_DISMISS_MS)
            ClipboardPasteCoordinator.pasteEntry(
                service = service,
                context = context,
                entry = entry,
                writeToClipboardFirst = true,
                fvStyle = false,
            ) { result ->
                if (result is PasteResult.Failure) {
                    ClipboardPasteCoordinator.toastPasteFailure(context, result.reason)
                }
            }
        }
    }

    fun startCalculatorDrag(expression: String, result: String): Boolean {
        if (!settings.dragDropFlowEnabled) return false
        val view = SearchPanelOverlayWindow.dragHostView ?: return false
        // 截断显示会带 `…`，拖出去的是数据，不是给人看的省略号。
        val cleanResult = result.trimEnd('…').trim()
        val cleanExpression = expression.trim()
        val text = when {
            cleanExpression.isEmpty() -> cleanResult
            cleanResult.isEmpty() -> cleanExpression
            else -> "$cleanExpression = $cleanResult"
        }
        if (text.isEmpty()) return false
        hideSearchKeyboard()
        return SearchPanelDrag.text(view, text, text)
    }

    fun startContactDrag(contact: ContactSearchEntry): Boolean {
        if (!settings.dragDropFlowEnabled) return false
        val number = contact.formattedPhone.ifBlank { contact.phoneNumber }.trim()
        val text = listOf(contact.name.trim(), number)
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        if (text.isEmpty()) return false
        val view = SearchPanelOverlayWindow.dragHostView ?: return false
        hideSearchKeyboard()
        return SearchPanelDrag.text(view, text, contact.name.ifBlank { number })
    }

    fun startClipboardDrag(entry: ClipboardEntry): Boolean {
        if (!settings.dragDropFlowEnabled) return false
        val view = SearchPanelOverlayWindow.dragHostView ?: return false
        hideSearchKeyboard()
        return SearchPanelDrag.clipboard(view, context, entry)
    }

    fun startFileDrag(file: DeviceFileEntry): Boolean {
        if (!settings.dragDropFlowEnabled) return false
        if (file.isDirectory) {
            Toast.makeText(context, R.string.history_drag_unsupported, Toast.LENGTH_SHORT).show()
            return true
        }
        val view = SearchPanelOverlayWindow.dragHostView ?: return false
        hideSearchKeyboard()
        return SearchPanelDrag.file(view, context, file)
    }

    fun handleClipboardCandidate(entry: ClipboardEntry, longPressTriggered: Boolean) {
        // 点击 = 复制并粘贴：粘贴链路本身会先写回剪贴板（writeToClipboardFirst），
        // 所以"只复制"的旧路径没有丢；没有可粘贴目标时它会退回复制并提示。
        // 长按 = 拖拽；流转栏关闭 / 起拖失败时退回旧的"长按 = 粘贴"。
        if (longPressTriggered && startClipboardDrag(entry)) return
        pasteClipboardCandidate(entry)
    }

    fun launchContactCandidate(contact: ContactSearchEntry, longPressTriggered: Boolean) {
        // 长按 = 拖拽；流转栏关闭 / 起拖失败时退回原来的"打开联系人"。
        if (longPressTriggered && startContactDrag(contact)) return
        hideSearchKeyboard()
        if (ContactSearchLauncher.openContact(context, contact)) {
            dismissPanel()
        }
    }

    fun callContact(contact: ContactSearchEntry) {
        hideSearchKeyboard()
        if (ContactSearchLauncher.dial(context, contact.phoneNumber)) {
            dismissPanel()
        }
    }

    fun smsContact(contact: ContactSearchEntry) {
        hideSearchKeyboard()
        if (ContactSearchLauncher.sms(context, contact.phoneNumber)) {
            dismissPanel()
        }
    }

    fun launchFileCandidate(file: DeviceFileEntry, longPressTriggered: Boolean) {
        // 长按 = 拖拽；流转栏关闭 / 起拖失败时退回原来的"打开文件"。
        if (longPressTriggered && startFileDrag(file)) return
        hideSearchKeyboard()
        if (
            settings.searchPanelFilePreviewsEnabled &&
            (FileTypeUtils.isImage(file) || FileTypeUtils.isPdf(file))
        ) {
            previewFile = file
            previewVisibilityState.targetState = true
            return
        }
        if (FileSearchLauncher.open(context, file)) {
            dismissPanel()
        }
    }

    fun performSearchEnterKey() {
        if (textQuery.isBlank()) return
        if (
            settings.searchPanelEnterAction == SearchPanelEnterAction.FIRST_CANDIDATE &&
            !showSearchHistory
        ) {
            val calc = calculatorResult
            if (showCalculator && calc != null) {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText("calculator", calc).toClipEntry())
                }
                dismissPanel()
                return
            }
            if (appCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.APPS)) {
                launchAppCandidate(appCandidates.first(), longPressTriggered = false)
                return
            }
            if (shortcutCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.SHORTCUTS)) {
                launchShortcutCandidate(shortcutCandidates.first(), longPressTriggered = false)
                return
            }
            if (linkUrls.isNotEmpty()) {
                openUrl(linkUrls.first(), longPressTriggered = false)
                return
            }
            if (fileCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.FILES)) {
                launchFileCandidate(fileCandidates.first(), longPressTriggered = false)
                return
            }
            if (contactCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.CONTACTS)) {
                launchContactCandidate(contactCandidates.first(), longPressTriggered = false)
                return
            }
            if (settingsCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.SETTINGS)) {
                launchSettingsCandidate(settingsCandidates.first(), longPressTriggered = false)
                return
            }
            if (clipboardCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.CLIPBOARD)) {
                copyClipboardCandidate(clipboardCandidates.first())
                return
            }
        }
        val engineToUse = resolveTextSearchEngine()
        if (engineToUse != null) {
            launchSearchEngine(engineToUse, longPressTriggered = false)
        }
    }

    val dismissInteraction = remember { MutableInteractionSource() }
    val isLandscape = overlayIsLandscape()
    val landscapeImagePreviewMaxHeight = 160.dp
    val dimAlpha = (dimPercent.coerceIn(
        AppSettings.SEARCH_PANEL_DIM_MIN_PERCENT,
        AppSettings.SEARCH_PANEL_DIM_MAX_PERCENT,
    ) / 100f)
    // Blur follows background mode; dim only controls the black veil.
    val showBitmapBackground = backgroundBitmap != null &&
        (backgroundStyle == SearchPanelBackgroundStyle.BLUR
            || backgroundStyle == SearchPanelBackgroundStyle.WALLPAPER_BLUR)
    val hasBlurBackground = showBitmapBackground || usesNativeWindowBlur
    val showDimMask = dimAlpha > 0f
    val panelAnimSpec = tween<Float>(SEARCH_PANEL_ANIM_MS, easing = FastOutSlowInEasing)
    val panelSlideSpec = tween<IntOffset>(SEARCH_PANEL_ANIM_MS, easing = FastOutSlowInEasing)
    val enterTransition = fadeIn(panelAnimSpec) + slideInVertically(panelSlideSpec) { it / 8 }
    val exitTransition = fadeOut(panelAnimSpec) + slideOutVertically(panelSlideSpec) { it / 8 }
    val rootAlignment = if (barAtBottom) {
        Alignment.BottomCenter
    } else {
        Alignment.TopCenter
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = rootAlignment,
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(panelAnimSpec),
            exit = fadeOut(panelAnimSpec),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showDimMask) {
                            Modifier.background(Color.Black.copy(alpha = dimAlpha))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClick = ::dismissPanel,
                    ),
            ) {
                if (showBitmapBackground) {
                    Image(
                        bitmap = backgroundBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = enterTransition,
            exit = exitTransition,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // imePadding 留在 panelModifier 里：键盘弹出时面板内容（含引擎 Dock）整体上移，不被 IME 遮住。
            val panelModifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
            Box(modifier = panelModifier) {
                val hasQueryCandidates = mode == SearchMode.TEXT && textQuery.isNotBlank()
                val hasCandidatePanel = hasQueryCandidates || showHistoryPanel

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::dismissPanel,
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = SearchPanelCardHorizontalPadding,
                                vertical = 12.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(SearchPanelCardVerticalSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                            // 资源文案必须在组合期用 stringResource 读取：
                            // 在 onClick 回调里走 LocalContext.current.getString(...) 不是配置感知的，
                            // 会被 Compose lint 判成 **Error**（LocalContextGetResourceValueCall）并让 lintLiteDebug 直接失败。
                            val copiedToastText = stringResource(R.string.float_ball_text_copied)
                            val searchFieldBlock: @Composable () -> Unit = {
                                SearchPanelFrostedCard {
                                    Crossfade(targetState = mode, label = "SearchModeCrossfade") { currentMode ->
                                    when (currentMode) {
                                        SearchMode.TEXT -> {
                                            val toolbarState = rememberOverlaySelectionToolbarState()
                                            val searchToolbarActions = OverlaySelectionToolbarActions(
                                                editable = true,
                                                onCopy = { copied ->
                                                    FloatBallTextPick.copyText(context, copied)
                                                    // 复制后必须有反馈：本面板是全屏覆盖层窗口，
                                                    // 系统 toast 会被它整个盖住（用户反馈"没有反馈"），
                                                    // 所以走挂在无障碍层的 OverlayToastWindow。
                                                    OverlayToastWindow.show(
                                                        context,
                                                        copiedToastText
                                                    )
                                                },
                                                onCut = {
                                                    textFieldValue = cutTextFieldValue(textFieldValue) { copied ->
                                                        FloatBallTextPick.copyText(context, copied)
                                                        OverlayToastWindow.show(
                                                            context,
                                                            copiedToastText
                                                        )
                                                    }
                                                },
                                                onPaste = {
                                                    textFieldValue = pasteIntoTextFieldValue(context, textFieldValue)
                                                },
                                                onSelectAll = {
                                                    textFieldValue = textFieldValue.copy(
                                                        selection = TextRange(0, textFieldValue.text.length),
                                                    )
                                                },
                                            )
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                TextField(
                                                    value = textFieldValue,
                                                    onValueChange = { updated ->
                                                        applyTextFieldInput(updated)
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp)
                                                        .focusRequester(focusRequester)
                                                        .suppressSystemTextContextMenu()
                                                        .onPreviewKeyEvent { event ->
                                                            if (
                                                                event.type != KeyEventType.KeyDown ||
                                                                (event.key != Key.Backspace && event.key != Key.Delete)
                                                            ) {
                                                                return@onPreviewKeyEvent false
                                                            }
                                                            if (textQuery.isNotEmpty()) {
                                                                return@onPreviewKeyEvent false
                                                            }
                                                            if (showSearchHistory) {
                                                                showSearchHistory = false
                                                                return@onPreviewKeyEvent true
                                                            }
                                                            if (
                                                                lockedSection != SearchPanelResultSection.ALL ||
                                                                lockedEngineId != null
                                                            ) {
                                                                clearSearchLocks()
                                                                return@onPreviewKeyEvent true
                                                            }
                                                            false
                                                        }
                                                        .then(Modifier.fieldModifier(toolbarState)),
                                                    leadingIcon = {
                                                        Box {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(48.dp)
                                                                    .focusProperties { canFocus = false }
                                                                    .clickable(
                                                                        interactionSource = remember {
                                                                            MutableInteractionSource()
                                                                        },
                                                                        indication = ripple(
                                                                            bounded = false,
                                                                            radius = 24.dp,
                                                                        ),
                                                                        onClick = { showSectionMenu = true },
                                                                    ),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Icon(
                                                                    imageVector = lockedSection.icon(),
                                                                    contentDescription = stringResource(
                                                                        R.string.search_panel_section_menu,
                                                                    ),
                                                                )
                                                            }
                                                            DropdownMenu(
                                                                expanded = showSectionMenu,
                                                                onDismissRequest = { showSectionMenu = false },
                                                                properties = PopupProperties(focusable = false),
                                                            ) {
                                                                SearchPanelResultSection.entries.forEach { section ->
                                                                    DropdownMenuItem(
                                                                        text = {
                                                                            Text(stringResource(section.labelResId()))
                                                                        },
                                                                        onClick = {
                                                                            lockedSection = section
                                                                            if (section != SearchPanelResultSection.ALL) {
                                                                                lockedEngineId = null
                                                                            }
                                                                            showSectionMenu = false
                                                                        },
                                                                        leadingIcon = {
                                                                            Icon(
                                                                                imageVector = section.icon(),
                                                                                contentDescription = null,
                                                                            )
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    trailingIcon = {
                                                        Row {
                                                            if (textQuery.isNotEmpty()) {
                                                                IconButton(onClick = {
                                                                    textFieldValue = TextFieldValue("")
                                                                    clearSearchLocks()
                                                                }) {
                                                                    Icon(
                                                                        MiuixIcons.Medium.Close,
                                                                        contentDescription = null,
                                                                    )
                                                                }
                                                            }
                                                            IconButton(onClick = {
                                                                SearchPanelOverlayWindow.hide()
                                                                SearchPanelImagePickerActivity.launch(context) { uri ->
                                                                    SearchPanelOverlayWindow.restore()
                                                                    if (uri != null) {
                                                                        imageUri = uri
                                                                        mode = SearchMode.IMAGE
                                                                        coroutineScope.launch {
                                                                            val bitmap = loadBitmapFromUri(context, uri)
                                                                            imageBitmap = bitmap
                                                                        }
                                                                    }
                                                                }
                                                            }) {
                                                                Icon(
                                                                    MiuixIcons.Medium.Image,
                                                                    contentDescription = null,
                                                                )
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(28.dp),
                                                    singleLine = true,
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        disabledContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        disabledIndicatorColor = Color.Transparent,
                                                    ),
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = searchKeyboardType,
                                                        imeAction = ImeAction.Search,
                                                    ),
                                                    keyboardActions = KeyboardActions(onSearch = {
                                                        performSearchEnterKey()
                                                    }),
                                                )
                                                OverlaySelectionToolbarPopup(
                                                    visible = !textFieldValue.selection.collapsed,
                                                    selection = textFieldValue.selection,
                                                    text = textFieldValue.text,
                                                    textLayoutResult = toolbarState.textLayoutResult,
                                                    fieldCoordinates = toolbarState.fieldCoordinates,
                                                    actions = searchToolbarActions,
                                                )
                                            }
                                        }
                                        SearchMode.IMAGE -> {
                                            val imagePreviewMaxHeight =
                                                if (isLandscape) landscapeImagePreviewMaxHeight else 240.dp
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                                    .heightIn(min = 120.dp, max = imagePreviewMaxHeight)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (imageBitmap != null) {
                                                    Image(
                                                        bitmap = imageBitmap!!.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        mode = SearchMode.TEXT
                                                        imageUri = null
                                                        imageBitmap = null
                                                        focusRequester.requestFocus()
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                            RoundedCornerShape(50),
                                                        ),
                                                ) {
                                                    Icon(MiuixIcons.Medium.Close, contentDescription = null)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }


                            // 悬浮 chrome 的高度：胶囊 / 引擎 dock / 搜索框叠在列表之上、不占行高，
                            // 列表底部按实测高度留内边距，滚到底时最后一张卡片仍完整可见。
                            val actionPillsDensity = LocalDensity.current
                            var actionPillsHeight by remember { mutableStateOf(0.dp) }
                            var bottomChromeHeight by remember { mutableStateOf(0.dp) }

                            val candidatesContent: @Composable () -> Unit = {
                                val expandEdge = if (bottomUpListOrder) {
                                    Alignment.Bottom
                                } else {
                                    Alignment.Top
                                }
                                AnimatedVisibility(
                                    visible = hasCandidatePanel,
                                    enter = expandVertically(expandFrom = expandEdge) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = expandEdge) + fadeOut(),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    val candidateListState = rememberLazyListState()
                                    // 滑动候选列表时自动收起键盘：键盘挡着内容时用户想滚动，就别再让
                                    // 它跟着面板一起 resize 打架；只在真正发生滚动时收一次。
                                    LaunchedEffect(candidateListState) {
                                        snapshotFlow { candidateListState.isScrollInProgress }
                                            .distinctUntilChanged()
                                            .collect { scrolling ->
                                                if (scrolling) hideSearchKeyboard()
                                            }
                                    }
                                    val candidateSectionKeys = remember(
                                        linkUrls.isNotEmpty(),
                                        showCalculator,
                                        showHistoryPanel,
                                        appCandidates.isNotEmpty(),
                                        shortcutCandidates.isNotEmpty(),
                                        showFilePermissionPrompt,
                                        fileCandidates.isNotEmpty(),
                                        showContactPermissionPrompt,
                                        contactCandidates.isNotEmpty(),
                                        settingsCandidates.isNotEmpty(),
                                        clipboardCandidates.isNotEmpty(),
                                        webSuggestions.isNotEmpty(),
                                        lockedSection,
                                        bottomUpListOrder,
                                    ) {
                                        val list = mutableListOf<String>()
                                        if (linkUrls.isNotEmpty() && lockedSection == SearchPanelResultSection.ALL) list.add("links")
                                        if (showCalculator && lockedSection == SearchPanelResultSection.ALL) list.add("calculator")
                                        if (showHistoryPanel && lockedSection == SearchPanelResultSection.ALL) list.add("history")
                                        if (appCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.APPS)) list.add("apps")
                                        if (shortcutCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.SHORTCUTS)) list.add("shortcuts")
                                        if (showFilePermissionPrompt && allowsResultSection(SearchPanelResultSection.FILES)) list.add("file_permission")
                                        if (fileCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.FILES)) list.add("files")
                                        if (showContactPermissionPrompt && allowsResultSection(SearchPanelResultSection.CONTACTS)) list.add("contact_permission")
                                        if (contactCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.CONTACTS)) list.add("contacts")
                                        if (settingsCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.SETTINGS)) list.add("settings")
                                        if (clipboardCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.CLIPBOARD)) list.add("clipboard")
                                        if (webSuggestions.isNotEmpty() && lockedSection == SearchPanelResultSection.ALL) list.add("web_suggestions")
                                        if (bottomUpListOrder) list.asReversed() else list
                                    }
                                    // 自下而上的列表顺序要求候选**整体贴底**（第一个结果落在搜索引擎区上方）。
                                    // 原来 modifier 是 fillMaxSize()：子项撑满整块区域，外层
                                    // contentAlignment = BottomCenter 完全不起作用，条目因此贴顶、与引擎区之间空一截。
                                    // 修法（与重构版同一套、已真机验证）：
                                    //  - wrapContentHeight(Alignment.Bottom)：内容少时列表只有内容那么高、整体贴底；
                                    //    内容多时撑满并可滚动；
                                    //  - chrome 高度做成**布局留白**：让列表可视区本身止步于引擎坞/搜索框之上
                                    //    （只靠 contentPadding 不够 —— 它只在滚到最底时才让出空间，
                                    //     内容超出时底部那张卡片仍会压在浮层底下）。
                                    // 查询/分区变化时把列表锚定到该顺序下的"起点"（移植自 ec6c579e）：
                                    // - 自上而下：最顶（0）；
                                    // - 自下而上：**最底**（最后一项）—— 贴底顺序下用户看的就是贴近引擎区的分区；
                                    //   若锚到最顶，底部那些分区就被推出可视区（真机："本地应用被切掉/不贴底"）。
                                    LaunchedEffect(candidateSectionKeys) {
                                        if (candidateSectionKeys.isNotEmpty()) {
                                            val anchor = if (bottomUpListOrder) {
                                                candidateSectionKeys.size - 1
                                            } else {
                                                0
                                            }
                                            candidateListState.scrollToItem(anchor)
                                        }
                                    }

                                    val chromeReserve =
                                        if (barAtBottom) bottomChromeHeight else actionPillsHeight
                                    LazyColumn(
                                        state = candidateListState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (bottomUpListOrder) {
                                                    Modifier
                                                        .wrapContentHeight(Alignment.Bottom)
                                                        .padding(bottom = chromeReserve)
                                                } else {
                                                    Modifier.fillMaxSize()
                                                },
                                            ),
                                        contentPadding = PaddingValues(
                                            bottom = if (bottomUpListOrder) {
                                                // 贴底模式下 chrome 已由布局留白让出，这里只留条目间距，避免重复预留。
                                                SearchPanelCardVerticalSpacing
                                            } else {
                                                SearchPanelCardVerticalSpacing + chromeReserve
                                            },
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(SearchPanelCardVerticalSpacing),
                                    ) {
                                        items(
                                            count = candidateSectionKeys.size,
                                            key = { candidateSectionKeys[it] },
                                        ) { index ->
                                            when (candidateSectionKeys[index]) {
                                                "links" -> {
                                                    SearchPanelLinkResultCards(
                                                        urls = linkUrls,
                                                        onOpenUrl = ::openUrl,
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "calculator" -> {
                                                    val calcResult = calculatorResult
                                                    if (calcResult != null) {
                                                        SearchPanelCalculatorCard(
                                                            expression = textQuery.trim(),
                                                            result = calcResult,
                                                            onLongPressDrag = { expression, result ->
                                                                startCalculatorDrag(expression, result)
                                                            },
                                                        )
                                                    }
                                                }
                                                "history" -> {
                                                    SearchPanelSearchHistoryCard(
                                                        queries = filteredSearchHistoryQueries,
                                                        expanded = historyExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            historyExpanded = expanded
                                                        },
                                                        onQueryClick = { query ->
                                                            textFieldValue = TextFieldValue(
                                                                text = query,
                                                                selection = TextRange(query.length),
                                                            )
                                                            debouncedQuery = query
                                                            showSearchHistory = false
                                                        },
                                                    )
                                                }
                                                "apps" -> {
                                                    SearchPanelAppResultCards(
                                                        apps = appCandidates,
                                                        style = settings.searchPanelAppDisplayStyle,
                                                        settings = settings,
                                                        onLaunchApp = ::launchAppCandidate,
                                                        onAppQuickAction = ::handleAppQuickAction,
                                                        expanded = appsExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            appsExpanded = expanded
                                                        },
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "shortcuts" -> {
                                                    SearchPanelShortcutResultCards(
                                                        shortcuts = shortcutCandidates,
                                                        expanded = shortcutsExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            shortcutsExpanded = expanded
                                                        },
                                                        onLaunchShortcut = ::launchShortcutCandidate,
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "file_permission" -> {
                                                    SearchPanelPermissionResultCard(
                                                        label = stringResource(
                                                            R.string.search_panel_file_permission_prompt,
                                                        ),
                                                        leadingIcon = Icons.Default.Folder,
                                                        onRequestPermission = {
                                                            SearchPanelOverlayWindow.hide()
                                                            FilePermissionTrampolineActivity.launch(context) { granted ->
                                                                hasFilePermission = granted
                                                                permissionRefreshKey++
                                                                SearchPanelOverlayWindow.restore()
                                                            }
                                                        },
                                                    )
                                                }
                                                "files" -> {
                                                    SearchPanelFileResultCards(
                                                        files = fileCandidates,
                                                        expanded = filesExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            filesExpanded = expanded
                                                        },
                                                        onOpenFile = ::launchFileCandidate,
                                                        // 长按永远用于拖拽，不跟随「小窗启动策略」开关。
                                                        longPressEnabled = true,
                                                    )
                                                }
                                                "contact_permission" -> {
                                                    SearchPanelPermissionResultCard(
                                                        label = stringResource(
                                                            R.string.search_panel_contact_permission_prompt,
                                                        ),
                                                        leadingIcon = Icons.Default.Person,
                                                        onRequestPermission = {
                                                            SearchPanelOverlayWindow.hide()
                                                            ContactPermissionTrampolineActivity.launch(context) { granted ->
                                                                hasContactPermission = granted
                                                                permissionRefreshKey++
                                                                SearchPanelOverlayWindow.restore()
                                                            }
                                                        },
                                                    )
                                                }
                                                "contacts" -> {
                                                    SearchPanelContactResultCards(
                                                        contacts = contactCandidates,
                                                        expanded = contactsExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            contactsExpanded = expanded
                                                        },
                                                        onLaunchContact = ::launchContactCandidate,
                                                        onCallContact = ::callContact,
                                                        onSmsContact = ::smsContact,
                                                        // 长按永远用于拖拽，不跟随「小窗启动策略」开关。
                                                        longPressEnabled = true,
                                                    )
                                                }
                                                "settings" -> {
                                                    SearchPanelSettingsResultCards(
                                                        entries = settingsCandidates,
                                                        expanded = settingsExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            settingsExpanded = expanded
                                                        },
                                                        onLaunchEntry = ::launchSettingsCandidate,
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "clipboard" -> {
                                                    SearchPanelClipboardResultCards(
                                                        entries = clipboardCandidates,
                                                        expanded = clipboardExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            clipboardExpanded = expanded
                                                        },
                                                        onClipboardEntryAction = ::handleClipboardCandidate,
                                                        // 长按永远用于拖拽，不跟随「小窗启动策略」开关。
                                                        longPressEnabled = true,
                                                    )
                                                }
                                                "web_suggestions" -> {
                                                    SearchPanelWebSuggestionsCard(
                                                        suggestions = webSuggestions,
                                                        onSuggestionClick = { suggestion ->
                                                            val engineToUse = resolveTextSearchEngine()
                                                            if (engineToUse != null) {
                                                                launchSearchEngine(
                                                                    engine = engineToUse,
                                                                    longPressTriggered = false,
                                                                    queryOverride = suggestion,
                                                                )
                                                            } else {
                                                                textFieldValue = textFieldValue.copy(
                                                                    text = suggestion,
                                                                    selection = TextRange(suggestion.length),
                                                                )
                                                                debouncedQuery = suggestion
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val actionPillsBlock: @Composable () -> Unit = {
                                if (mode == SearchMode.TEXT && textQuery.isNotBlank()) {
                                    SearchPanelActionPillsRow(
                                        visible = keyboardSwitchText != null || shouldShowPhoneCallAction,
                                        keyboardSwitchText = keyboardSwitchText,
                                        showPhoneCallAction = shouldShowPhoneCallAction,
                                        phoneQuery = textQuery,
                                        onKeyboardSwitchToggle = {
                                            manuallySwitchedToNumberKeyboard = !manuallySwitchedToNumberKeyboard
                                        },
                                    )
                                }
                            }

                            val engineGridBlock: @Composable () -> Unit = {
                                val aggregateName = stringResource(R.string.search_panel_aggregated_image_search)
                                val aggregateIcon = stringResource(R.string.search_panel_aggregated_image_icon)
                                val aggregateSearchEngine = remember(aggregateName, aggregateIcon) {
                                    SearchEngineConfig(
                                        id = "slideindex_aggregate_image_search",
                                        name = aggregateName,
                                        engineType = SearchEngineType.SHARE_IMAGE_TO_APP,
                                        iconType = SearchIconType.TEXT,
                                        textIcon = aggregateIcon,
                                    )
                                }
                                val activeEngines = if (mode == SearchMode.TEXT) {
                                    textEngines
                                } else {
                                    listOf(aggregateSearchEngine) + imageEngines
                                }
                                SearchPanelEngineDockCard(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    PickResultTextSearchGrid(
                                        engines = activeEngines,
                                        query = if (mode == SearchMode.TEXT) {
                                            textQuery
                                        } else if (imageBitmap != null) {
                                            "image"
                                        } else {
                                            ""
                                        },
                                        columns = settings.searchEngineGridColumns,
                                        rows = settings.searchEngineGridRows,
                                        showLabels = settings.searchEngineShowLabels,
                                        longPressEnabled = longPressEnabled,
                                        showPageIndicator = true,
                                        pageIndicatorTopPadding = SearchPanelPageIndicatorTopPadding,
                                        pageIndicatorBottomPadding = SearchPanelPageIndicatorBottomPadding,
                                        onEngineClick = { engine, longPressTriggered ->
                                            launchSearchEngine(engine, longPressTriggered)
                                        },
                                    )
                                }
                            }

                            // 搜索框在底部时：引擎 dock 与搜索框一起做真悬浮，叠在列表之上（列表内容从它们后面滑过）。
                            // 搜索框在顶部时保持原来的布局流，只让操作胶囊悬浮。
                            val floatingBottomChrome = barAtBottom
                            val candidatesAndDockBlock: @Composable ColumnScope.() -> Unit = {
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = true)
                                        .fillMaxWidth(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = true)
                                            .fillMaxWidth(),
                                    ) {
                                        // 候选列表：底边按卡片圆角裁切，切口被悬浮卡片盖住。
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(SearchPanelCardCorner)),
                                            contentAlignment = if (bottomUpListOrder) {
                                                Alignment.BottomCenter
                                            } else {
                                                Alignment.TopCenter
                                            },
                                        ) {
                                            candidatesContent()
                                        }
                                        if (floatingBottomChrome) {
                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .onSizeChanged {
                                                        bottomChromeHeight =
                                                            with(actionPillsDensity) { it.height.toDp() }
                                                    },
                                                verticalArrangement = Arrangement.spacedBy(
                                                    SearchPanelCardVerticalSpacing,
                                                ),
                                            ) {
                                                actionPillsBlock()
                                                engineGridBlock()
                                                searchFieldBlock()
                                            }
                                        } else {
                                            // 搜索框在顶部时，只有操作胶囊悬浮在列表底边。
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .onSizeChanged {
                                                        actionPillsHeight =
                                                            with(actionPillsDensity) { it.height.toDp() }
                                                    },
                                            ) {
                                                actionPillsBlock()
                                            }
                                        }
                                    }
                                    if (!floatingBottomChrome) {
                                        engineGridBlock()
                                    }
                                }
                            }

                            if (floatingBottomChrome) {
                                candidatesAndDockBlock()
                            } else {
                                searchFieldBlock()
                                candidatesAndDockBlock()
                            }
                        }
                    }
                }
            }

        previewFile?.let { previewTarget ->
            FilePreviewBottomSheet(
                deviceFile = previewTarget,
                visibleState = previewVisibilityState,
                onDismissRequest = { dismissPreview() },
                onOpen = {
                    clearPreviewState()
                    if (FileSearchLauncher.open(context, previewTarget)) {
                        dismissPanel()
                    }
                },
                onShare = {
                    if (FileSearchLauncher.share(context, previewTarget) { dismissPanel() }) {
                        clearPreviewState()
                    }
                },
            )
        }
    }
}

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    }.getOrNull()
}


private fun SearchPanelResultSection.icon(): ImageVector = when (this) {
    SearchPanelResultSection.ALL -> Icons.Default.Search
    SearchPanelResultSection.APPS -> Icons.Default.Apps
    SearchPanelResultSection.SHORTCUTS -> Icons.Default.Bolt
    SearchPanelResultSection.CONTACTS -> Icons.Default.Person
    SearchPanelResultSection.FILES -> Icons.Default.Folder
    SearchPanelResultSection.SETTINGS -> Icons.Default.Settings
    SearchPanelResultSection.CLIPBOARD -> Icons.Default.ContentPaste
}

private fun SearchPanelResultSection.labelResId(): Int = when (this) {
    SearchPanelResultSection.ALL -> R.string.search_panel_section_all
    SearchPanelResultSection.APPS -> R.string.search_panel_section_apps
    SearchPanelResultSection.SHORTCUTS -> R.string.search_panel_section_shortcuts
    SearchPanelResultSection.CONTACTS -> R.string.search_panel_section_contacts
    SearchPanelResultSection.FILES -> R.string.search_panel_section_files
    SearchPanelResultSection.SETTINGS -> R.string.search_panel_section_settings
    SearchPanelResultSection.CLIPBOARD -> R.string.search_panel_section_clipboard
}
