package com.slideindex.app.overlay.pickresult

import com.slideindex.app.ui.theme.LocalAppDarkTheme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.OverlaySelectionToolbarActions
import com.slideindex.app.overlay.searchpanel.SearchPanelQueryBridge
import com.slideindex.app.overlay.OverlaySelectionToolbarOverlay
import com.slideindex.app.overlay.cutTextFieldValue
import com.slideindex.app.overlay.fieldModifier
import com.slideindex.app.overlay.overlayContainerWidthDp
import com.slideindex.app.overlay.pasteIntoTextFieldValue
import com.slideindex.app.overlay.rememberOverlaySelectionToolbarState
import com.slideindex.app.overlay.suppressSystemTextContextMenu
import com.slideindex.app.overlay.viewportModifier
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val pickResultTokenCache = android.util.LruCache<String, List<String>>(20)

@Composable
internal fun PickResultInteractiveTextSection(
    text: String,
    textMode: PickResultTextMode,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 15f,
    textSource: PickResultTextSource = PickResultTextSource.A11Y,
    ocrAvailable: Boolean = false,
    /** 屏幕取词：OCR 不可用时也让这颗来源芯片留在左上角（置灰），而不是整颗消失。 */
    forceOcrChip: Boolean = false,
    ocrLoading: Boolean = false,
    showBackgroundOcrAction: Boolean = false,
    onBackgroundOcr: () -> Unit = {},
    onTextSourceChange: (PickResultTextSource) -> Unit = {},
    showSourceChips: Boolean = true,
    a11yAvailable: Boolean = true,
    barcodeResults: List<BarcodeScanResult> = emptyList(),
    showingTranslation: Boolean = false,
    translateLoading: Boolean = false,
    showEditingToolbar: Boolean = true,
    showActionBar: Boolean = true,
    sectionTitle: String? = null,
    pinActionBarOutside: Boolean = false,
    expandTextBlock: Boolean = false,
    auxiliaryDragEnabled: Boolean = false,
    bodyMaxHeight: Dp? = null,
    showSearch: Boolean = false,
    searchSelected: Boolean = false,
    searchQuickLaunch: PickResultSearchQuickLaunch? = null,
    translateEnabled: Boolean = true,
    onActiveTextChange: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onShare: (String) -> Unit,
    /** 用其他应用处理；null 时动作条不显示这一项（翻译浮窗等不需要的宿主）。 */
    onProcessText: ((String, PickResultProcessTextTarget) -> Unit)? = null,
    onCopy: (String, keepPanelOpen: Boolean) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
    onZoomText: ((Boolean) -> Unit)? = null,
    onToolbarDragDelta: (dragAmount: Float) -> Unit = {},
    onActionBarDragDelta: (dragAmount: Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onSearchDragEnd: () -> Unit = onDragEnd,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
    /** 生成二维码；null 或没有文字时不显示这一项。 */
    onGenerateQr: ((String) -> Unit)? = null,
    actionBarBottomPadding: Dp = PickResultTextActionBarBottomPaddingWhenAlone,
    actionBarDragActive: Boolean = false,
    autoSelectAll: Boolean? = null,
    hasImageContent: Boolean = false,
    onImageSearch: (() -> Unit)? = null,
    onSaveScreenshot: (() -> Unit)? = null,
    onShareScreenshot: (() -> Unit)? = null,
    onTextScrollableChange: ((Boolean) -> Unit)? = null,
) {
    // ?? remember(text)???? onTextChange ????text?key ??????????0??
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var selectedWordIndices by remember { mutableStateOf(setOf<Int>()) }
    var selectionStart by remember { mutableIntStateOf(0) }
    var selectionEnd by remember { mutableIntStateOf(0) }
    var selectAllRequest by remember { mutableIntStateOf(0) }
    var deselectAllRequest by remember { mutableIntStateOf(0) }
    val appContext = LocalContext.current.applicationContext
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(appContext) {
        OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.settings
            ?.collect { appSettings = it }
    }
    val effectiveAutoSelectAll = autoSelectAll ?: appSettings.floatBallPickAutoSelectAll
    var wordTokens by remember(text) { 
        mutableStateOf<List<String>>(pickResultTokenCache.get(text) ?: emptyList()) 
    }
    var wordTokenOverride by remember(text) { mutableStateOf<List<String>?>(null) }
    val effectiveWordTokens = wordTokenOverride ?: wordTokens

    fun currentWordTokens(): List<String> = wordTokenOverride ?: wordTokens

    LaunchedEffect(text) {
        wordTokenOverride = null
        if (text.isNotBlank() && pickResultTokenCache.get(text) == null) {
            val newTokens = withContext(Dispatchers.Default) {
                PickResultWordTokenizer.tokenizeSelectableWords(text)
            }
            pickResultTokenCache.put(text, newTokens)
            wordTokens = newTokens
        }
    }

    var autoSelectedKey by remember { mutableStateOf<Pair<String, PickResultTextMode>?>(null) }

    LaunchedEffect(text, textMode, effectiveAutoSelectAll) {
        if (!effectiveAutoSelectAll) {
            if (textMode != PickResultTextMode.EDIT) {
                textFieldValue = TextFieldValue(text)
                selectedWordIndices = emptySet()
                selectionStart = 0
                selectionEnd = 0
            } else if (text != textFieldValue.text) {
                textFieldValue = TextFieldValue(text)
            }
            autoSelectedKey = null
        } else if (textMode != PickResultTextMode.EDIT && text != textFieldValue.text) {
            // 自动全选时也要把「显示副本」同步到真文字：面板先开、文字后到（扫码 / 延迟 OCR）时，
            // 正文的空判定读的还是旧副本，会一直显示「未识别到文字」（真机：点开编辑后内容才出现）。
            // WORD_TAP / SELECT 下正文不可编辑，直接重建不会覆盖用户输入；正在编辑的同步由下面的
            // EDIT 分支负责（带选区）。
            textFieldValue = TextFieldValue(text)
        }
    }

    LaunchedEffect(text, textMode, effectiveAutoSelectAll, effectiveWordTokens) {
        if (effectiveAutoSelectAll && text.isNotBlank()) {
            val currentKey = text to textMode
            if (autoSelectedKey != currentKey) {
                when (textMode) {
                    PickResultTextMode.WORD_TAP -> {
                        if (effectiveWordTokens.isNotEmpty()) {
                            selectedWordIndices = effectiveWordTokens.indices.toSet()
                            autoSelectedKey = currentKey
                        }
                    }
                    PickResultTextMode.SELECT -> {
                        selectionStart = 0
                        selectionEnd = text.length
                        selectAllRequest++
                        autoSelectedKey = currentKey
                    }
                    PickResultTextMode.EDIT -> {
                        if (autoSelectedKey?.second != PickResultTextMode.EDIT || text != textFieldValue.text) {
                            textFieldValue = TextFieldValue(
                                text = text,
                                selection = TextRange(0, text.length),
                            )
                            autoSelectedKey = currentKey
                        }
                    }
                }
            }
        }
    }

    val allSelected = when (textMode) {
        PickResultTextMode.WORD_TAP -> {
            effectiveWordTokens.isNotEmpty() && selectedWordIndices.size == effectiveWordTokens.size
        }
        PickResultTextMode.SELECT -> {
            val length = text.length
            length > 0 && selectionStart == 0 && selectionEnd == length
        }
        PickResultTextMode.EDIT -> {
            val length = textFieldValue.text.length
            length > 0 &&
                textFieldValue.selection.min == 0 &&
                textFieldValue.selection.max == length
        }
    }

    fun activeText(): String {
        return when (textMode) {
            PickResultTextMode.WORD_TAP -> {
                if (selectedWordIndices.isEmpty()) text
                else if (effectiveWordTokens.isNotEmpty() && selectedWordIndices.size == effectiveWordTokens.size) text
                else selectedWordIndices.sorted().joinToString(separator = "") { index ->
                    val token = effectiveWordTokens.getOrElse(index) { "" }
                    if (PickResultWordTokenizer.isWhitespaceToken(token)) token else token.trim()
                }.ifBlank { text }
            }
            PickResultTextMode.SELECT -> {
                if (selectionEnd > selectionStart) {
                    text.substring(
                        selectionStart.coerceAtLeast(0),
                        selectionEnd.coerceAtMost(text.length),
                    )
                } else {
                    text
                }
            }
            PickResultTextMode.EDIT -> {
                val selection = textFieldValue.selection
                if (!selection.collapsed) {
                    textFieldValue.text.substring(
                        selection.min.coerceAtLeast(0),
                        selection.max.coerceAtMost(textFieldValue.text.length),
                    )
                } else {
                    textFieldValue.text.ifBlank { text }
                }
            }
        }
    }

    fun runOnActiveText(action: (String) -> Unit) {
        activeText().takeIf { it.isNotBlank() }?.let(action)
    }

    fun hasActiveSelection(): Boolean = when (textMode) {
        PickResultTextMode.WORD_TAP -> selectedWordIndices.isNotEmpty()
        PickResultTextMode.SELECT -> selectionEnd > selectionStart
        PickResultTextMode.EDIT -> !textFieldValue.selection.collapsed
    }

    LaunchedEffect(
        text,
        textMode,
        selectedWordIndices,
        selectionStart,
        selectionEnd,
        textFieldValue,
        effectiveWordTokens,
    ) {
        onActiveTextChange(activeText())
    }

    fun splitWordAt(index: Int) {
        val tokens = currentWordTokens()
        val split = PickResultWordTokenizer.splitTokenAtIndex(
            tokens = tokens,
            index = index,
        )
        if (split != null) {
            HapticHelper.appTick(view, appSettings)
            wordTokenOverride = split.tokens
            selectedWordIndices = PickResultWordTokenizer.mergeSelectionAfterSplitAt(
                splitIndex = index,
                expandedTokenCount = split.tokens.size - tokens.size + 1,
                oldSelected = selectedWordIndices,
                splitCharSelected = split.selectedIndices,
            )
        }
    }

    fun exitEditMode() {
        keyboardController?.hide()
        focusManager.clearFocus()
        selectedWordIndices = emptySet()
        onTextModeChange(PickResultTextMode.WORD_TAP)
        view.post { view.requestFocus() }
    }

    val isEditMode = textMode == PickResultTextMode.EDIT
    val showTopToolbar = showSourceChips || showEditingToolbar || sectionTitle != null

    val bodyScrollState = rememberScrollState()
    var wordTapScrollable by remember { mutableStateOf(false) }
    val isCurrentTextScrollable by remember(
        textMode,
        bodyScrollState.maxValue,
        wordTapScrollable
    ) {
        derivedStateOf<Boolean> {
            if (textMode == PickResultTextMode.WORD_TAP) {
                wordTapScrollable
            } else {
                bodyScrollState.maxValue > 0
            }
        }
    }
    val currentOnTextScrollableChange by rememberUpdatedState(onTextScrollableChange)
    LaunchedEffect(isCurrentTextScrollable) {
        currentOnTextScrollableChange?.invoke(isCurrentTextScrollable)
    }
    val showOcrLoading = ocrLoading &&
        textSource == PickResultTextSource.OCR &&
        text.isBlank() &&
        !showingTranslation
    var openLinkChooserExpanded by remember { mutableStateOf(false) }
    val selectionToolbarActions = remember(
        textMode,
        showSearch,
        translateEnabled,
    ) {
        OverlaySelectionToolbarActions(
            editable = textMode == PickResultTextMode.EDIT,
            showSearch = showSearch,
            showShare = true,
            showTranslate = translateEnabled,
            onCopy = { copied -> onCopy(copied, true) },
            onSearch = { query -> onSearch(query) },
            onShare = { value -> onShare(value) },
            onTranslate = { value -> onTranslate(value) },
        )
    }
    val openLinkAction = remember(
        text,
        textMode,
        selectedWordIndices,
        selectionStart,
        selectionEnd,
        textFieldValue,
    ) {
        PickResultUrl.resolveOpenLinkAction(
            fullText = text,
            activeText = activeText(),
            hasSelection = hasActiveSelection(),
        )
    }
    val openLinkChoices = remember(openLinkAction) {
        when (openLinkAction) {
            is PickResultOpenLinkAction.Choose -> openLinkAction.urls
            else -> emptyList()
        }
    }
    LaunchedEffect(openLinkAction) {
        openLinkChooserExpanded = false
    }
    val actionBar: @Composable () -> Unit = {
        if (showActionBar) {
            val actionBarContent: @Composable () -> Unit = {
                PickResultTextActionBar(
                    enabled = text.isNotBlank() || barcodeResults.isNotEmpty(),
                    translateEnabled = translateEnabled,
                    translateSelected = showingTranslation,
                    showSearch = showSearch,
                    searchSelected = searchSelected,
                    searchQuickLaunch = searchQuickLaunch,
                    showOpenLink = openLinkAction != null,
                    openLinkChooserExpanded = openLinkChooserExpanded,
                    openLinkChoices = openLinkChoices,
                    onSearch = { runOnActiveText(onSearch) },
                    onOpenLink = {
                        when (val action = openLinkAction) {
                            is PickResultOpenLinkAction.Open -> {
                                SearchPanelQueryBridge.rememberQuery(appContext, action.url)
                                FloatBallTextPick.openUrl(appContext, action.url, appSettings)
                            }
                            is PickResultOpenLinkAction.Choose -> {
                                openLinkChooserExpanded = true
                            }
                            null -> Unit
                        }
                    },
                    onOpenLinkChoice = { url ->
                        SearchPanelQueryBridge.rememberQuery(appContext, url)
                        FloatBallTextPick.openUrl(appContext, url, appSettings)
                    },
                    onDismissOpenLinkChooser = { openLinkChooserExpanded = false },
                    onShare = { runOnActiveText(onShare) },
                    onProcessText = onProcessText?.let { action ->
                        { target -> runOnActiveText { text -> action(text, target) } }
                    },
                    copyDismissEnabled = appSettings.floatBallPickCopyDismissPanel,
                    copyButtonPosition = appSettings.floatBallPickCopyButtonPosition,
                    onCopy = { runOnActiveText { onCopy(it, false) } },
                    onCopyKeepOpen = { runOnActiveText { onCopy(it, true) } },
                    onTranslate = { runOnActiveText(onTranslate) },
                    onPinToScreen = onPinToScreen,
                    onStash = onStash,
                    onGenerateQr = if (text.isNotBlank()) {
                        onGenerateQr?.let { action -> { runOnActiveText(action) } }
                    } else {
                        null
                    },
                    onTrimSpaces = { onRemoveSpaces(text, false) },
                    hasImageContent = hasImageContent,
                    onImageSearch = onImageSearch,
                    onSaveScreenshot = onSaveScreenshot,
                    onShareScreenshot = onShareScreenshot,
                    bottomPadding = actionBarBottomPadding,
                    lightweightDrag = actionBarDragActive,
                )
            }
            if (pinActionBarOutside) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = PickResultTextActionBarReservedHeight +
                                PickResultTextBodyActionBarSpacing,
                        )
                        .pickResultLinkedVerticalDrag(
                            onDragDelta = onActionBarDragDelta,
                            onDragEnd = onSearchDragEnd,
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    actionBarContent()
                }
            } else {
                actionBarContent()
            }
        }
    }

    Column(
        modifier = if (expandTextBlock && pinActionBarOutside) {
            modifier.fillMaxSize()
        } else {
            modifier
        },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (showTopToolbar) {
            if (auxiliaryDragEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = PickResultTextSectionToolbarReservedHeight +
                                PickResultTextToolbarBodySpacing,
                        )
                        .pickResultLinkedVerticalDrag(
                            onDragDelta = onToolbarDragDelta,
                            onDragEnd = onDragEnd,
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    PickResultTextToolbar(
                        textMode = textMode,
                        allSelected = allSelected,
                        activeSource = textSource,
                        ocrAvailable = ocrAvailable,
                        forceOcrChip = forceOcrChip,
                        ocrLoading = ocrLoading,
                        a11yAvailable = a11yAvailable,
                        barcodeResults = barcodeResults,
                        showSourceChips = showSourceChips,
                        showEditingToolbar = showEditingToolbar,
                        sectionTitle = sectionTitle,
                        onSourceChange = onTextSourceChange,
                        onEditToggle = {
                            if (textMode == PickResultTextMode.EDIT) {
                                exitEditMode()
                            } else {
                                selectedWordIndices = emptySet()
                                onTextModeChange(PickResultTextMode.EDIT)
                            }
                        },
                        onWordSelectToggle = {
                            val next = if (textMode == PickResultTextMode.WORD_TAP) {
                                PickResultTextMode.SELECT
                            } else {
                                PickResultTextMode.WORD_TAP
                            }
                            selectedWordIndices = emptySet()
                            onTextModeChange(next)
                        },
                        onTrimSpaces = { onRemoveSpaces(text, false) },
                        onRemoveAllSpaces = { onRemoveSpaces(text, true) },
                        onSelectAll = {
                            when (textMode) {
                                PickResultTextMode.WORD_TAP -> {
                                    selectedWordIndices = if (allSelected) {
                                        emptySet()
                                    } else {
                                        effectiveWordTokens.indices.toSet()
                                    }
                                }
                                PickResultTextMode.SELECT -> {
                                    val length = text.length
                                    if (allSelected) {
                                        deselectAllRequest++
                                        selectionStart = 0
                                        selectionEnd = 0
                                    } else {
                                        selectAllRequest++
                                        selectionStart = 0
                                        selectionEnd = length
                                    }
                                }
                                PickResultTextMode.EDIT -> {
                                    textFieldValue = if (allSelected) {
                                        textFieldValue.copy(
                                            selection = TextRange(textFieldValue.text.length),
                                        )
                                    } else {
                                        textFieldValue.copy(
                                            selection = TextRange(0, textFieldValue.text.length),
                                        )
                                    }
                                }
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(PickResultTextToolbarBodySpacing))
                }
            } else {
                Box(
                    modifier = Modifier.padding(bottom = PickResultTextToolbarBodySpacing),
                ) {
                    PickResultTextToolbar(
                        textMode = textMode,
                        allSelected = allSelected,
                        activeSource = textSource,
                        ocrAvailable = ocrAvailable,
                        forceOcrChip = forceOcrChip,
                        ocrLoading = ocrLoading,
                        a11yAvailable = a11yAvailable,
                        barcodeResults = barcodeResults,
                        showSourceChips = showSourceChips,
                        showEditingToolbar = showEditingToolbar,
                        sectionTitle = sectionTitle,
                        onSourceChange = onTextSourceChange,
                        onEditToggle = {
                            if (textMode == PickResultTextMode.EDIT) {
                                exitEditMode()
                            } else {
                                selectedWordIndices = emptySet()
                                onTextModeChange(PickResultTextMode.EDIT)
                            }
                        },
                        onWordSelectToggle = {
                            val next = if (textMode == PickResultTextMode.WORD_TAP) {
                                PickResultTextMode.SELECT
                            } else {
                                PickResultTextMode.WORD_TAP
                            }
                            selectedWordIndices = emptySet()
                            onTextModeChange(next)
                        },
                        onTrimSpaces = { onRemoveSpaces(text, false) },
                        onRemoveAllSpaces = { onRemoveSpaces(text, true) },
                        onSelectAll = {
                            when (textMode) {
                                PickResultTextMode.WORD_TAP -> {
                                    selectedWordIndices = if (allSelected) {
                                        emptySet()
                                    } else {
                                        effectiveWordTokens.indices.toSet()
                                    }
                                }
                                PickResultTextMode.SELECT -> {
                                    val length = text.length
                                    if (allSelected) {
                                        deselectAllRequest++
                                        selectionStart = 0
                                        selectionEnd = 0
                                    } else {
                                        selectAllRequest++
                                        selectionStart = 0
                                        selectionEnd = length
                                    }
                                }
                                PickResultTextMode.EDIT -> {
                                    textFieldValue = if (allSelected) {
                                        textFieldValue.copy(
                                            selection = TextRange(textFieldValue.text.length),
                                        )
                                    } else {
                                        textFieldValue.copy(
                                            selection = TextRange(0, textFieldValue.text.length),
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
        if (pinActionBarOutside) {
            val maxBodyHeight = bodyMaxHeight ?: pickResultMaxTextHeight(textSizeSp)
            val bodyScrollEnabled = textMode != PickResultTextMode.WORD_TAP
            val fillTextBlock = expandTextBlock
            val bodyContent: @Composable (expandToFill: Boolean, constrainedHeight: Dp) -> Unit =
                { expandToFill, constrainedHeight ->
                when {
                    showOcrLoading -> {
                        PickResultOcrLoadingBody(
                            showBackgroundAction = showBackgroundOcrAction,
                            onBackgroundProcess = onBackgroundOcr,
                        )
                    }
                    translateLoading -> {
                        PickResultTranslateLoadingBody()
                    }
                    else -> {
                        PickResultTextBody(
                            textMode = textMode,
                            textFieldValue = textFieldValue,
                            wordTokens = effectiveWordTokens,
                            selectedWordIndices = selectedWordIndices,
                            selectAllRequest = selectAllRequest,
                            deselectAllRequest = deselectAllRequest,
                            textSizeSp = textSizeSp,
                            bodyMaxHeight = if (fillTextBlock) constrainedHeight else maxBodyHeight,
                            expandToFill = expandToFill,
                            useInternalScroll = true,
                            autoSelectAll = effectiveAutoSelectAll,
                            hapticEnabled = appSettings.floatBallPickHapticEnabled,
                            onTextFieldValueChange = { updated ->
                                textFieldValue = updated
                                onTextChange(updated.text)
                            },
                            onSelectionChanged = { start, end ->
                                selectionStart = start
                                selectionEnd = end
                            },
                            onWordSelectionChange = { selectedWordIndices = it },
                            onWordLongPress = ::splitWordAt,
                            onZoomText = onZoomText,
                            onExitEditMode = ::exitEditMode,
                            selectionToolbarActions = selectionToolbarActions,
                            onWordTapScrollableChange = { wordTapScrollable = it },
                        )
                    }
                }
            }
            if (fillTextBlock) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val availableHeight = maxHeight
                    if (bodyScrollEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(bodyScrollState),
                        ) {
                            bodyContent(false, availableHeight)
                        }
                    } else {
                        bodyContent(true, availableHeight)
                    }
                }
                actionBar()
            } else if (bodyScrollEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = maxBodyHeight)
                        .verticalScroll(bodyScrollState),
                ) {
                    bodyContent(false, maxBodyHeight)
                }
                actionBar()
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = maxBodyHeight),
                ) {
                    bodyContent(false, maxHeight)
                }
                actionBar()
            }
        } else {
            when {
                showOcrLoading -> {
                    PickResultOcrLoadingBody(
                        showBackgroundAction = showBackgroundOcrAction,
                        onBackgroundProcess = onBackgroundOcr,
                    )
                }
                translateLoading -> {
                    PickResultTranslateLoadingBody()
                }
                else -> {
                    PickResultTextBody(
                    textMode = textMode,
                    textFieldValue = textFieldValue,
                    wordTokens = effectiveWordTokens,
                    selectedWordIndices = selectedWordIndices,
                    selectAllRequest = selectAllRequest,
                    deselectAllRequest = deselectAllRequest,
                    textSizeSp = textSizeSp,
                    bodyMaxHeight = bodyMaxHeight,
                    useInternalScroll = true,
                    autoSelectAll = effectiveAutoSelectAll,
                    hapticEnabled = appSettings.floatBallPickHapticEnabled,
                    onTextFieldValueChange = { updated ->
                        textFieldValue = updated
                        onTextChange(updated.text)
                    },
                    onSelectionChanged = { start, end ->
                        selectionStart = start
                        selectionEnd = end
                    },
                    onWordSelectionChange = { selectedWordIndices = it },
                    onWordLongPress = ::splitWordAt,
                    onZoomText = onZoomText,
                    onExitEditMode = ::exitEditMode,
                    selectionToolbarActions = selectionToolbarActions,
                    onWordTapScrollableChange = { wordTapScrollable = it },
                )
                }
            }
            actionBar()
        }
    }
}

@Composable
internal fun PickResultTextToolbar(
    textMode: PickResultTextMode,
    allSelected: Boolean,
    activeSource: PickResultTextSource,
    ocrAvailable: Boolean,
    /** 屏幕取词：OCR 不可用时也保留这颗芯片（置灰），避免左上角空出一块。 */
    forceOcrChip: Boolean = false,
    ocrLoading: Boolean = false,
    a11yAvailable: Boolean = true,
    barcodeResults: List<BarcodeScanResult> = emptyList(),
    showSourceChips: Boolean = true,
    showEditingToolbar: Boolean = true,
    sectionTitle: String? = null,
    onSourceChange: (PickResultTextSource) -> Unit,
    onEditToggle: () -> Unit,
    onWordSelectToggle: () -> Unit,
    onTrimSpaces: () -> Unit,
    onRemoveAllSpaces: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val selectAllText = if (allSelected) {
        stringResource(R.string.float_ball_action_deselect_all)
    } else {
        stringResource(R.string.float_ball_action_select_all)
    }
    val isDark = LocalAppDarkTheme.current
    val microBg = if (isDark) androidx.compose.ui.graphics.Color(0x28FFFFFF) else androidx.compose.ui.graphics.Color(0x14000000)
    val microBorder = if (isDark) androidx.compose.ui.graphics.Color(0x1EFFFFFF) else androidx.compose.ui.graphics.Color(0x10000000)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left side: Section title or Multi-source micro tags
        val showA11yChip = a11yAvailable
        // 屏幕取词（点词/截图）时 OCR 这颗常驻：识别开关没开或模型没装就置灰，而不是整颗消失。
        // 其余入口（分享图片、剪贴板/收纳等）仍按可用性出现。
        val showOcrChip = ocrAvailable || ocrLoading || forceOcrChip
        val showBarcodeChip = barcodeResults.isNotEmpty()
        val visibleSourceCount = (if (showA11yChip) 1 else 0) + (if (showOcrChip) 1 else 0) + (if (showBarcodeChip) 1 else 0)
        // 1 颗也要画：以前要求 ≥2 才画整行，导致只有无障碍（纯文本）或只有 OCR（纯图片）时左上角空着。
        val hasVisibleSources = showSourceChips && visibleSourceCount > 0

        if (sectionTitle != null) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (hasVisibleSources) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .background(color = microBg, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, microBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (showA11yChip) {
                    PickResultSourceChip(
                        label = stringResource(R.string.float_ball_pick_source_a11y),
                        selected = activeSource == PickResultTextSource.A11Y,
                        // 以前写死 true：单颗来源时这颗不会出现所以看不出来，常驻后必须跟随可用性置灰。
                        enabled = a11yAvailable,
                        compact = true,
                        onClick = { onSourceChange(PickResultTextSource.A11Y) },
                    )
                }
                if (showOcrChip) {
                    PickResultSourceChip(
                        label = stringResource(R.string.float_ball_pick_source_ocr),
                        selected = activeSource == PickResultTextSource.OCR,
                        enabled = ocrAvailable || ocrLoading,
                        compact = true,
                        onClick = {
                            if (ocrAvailable || ocrLoading) onSourceChange(PickResultTextSource.OCR)
                        },
                    )
                }
                if (showBarcodeChip) {
                    PickResultSourceChip(
                        label = stringResource(R.string.float_ball_pick_source_barcode),
                        selected = activeSource == PickResultTextSource.BARCODE,
                        enabled = true,
                        compact = true,
                        onClick = { onSourceChange(PickResultTextSource.BARCODE) },
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Right side: Micro controls (Edit, SelectAll, Word-tap, TrimSpaces in pure icons)
        if (showEditingToolbar) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .background(color = microBg, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, microBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Edit mode toggle icon (✏️)
                PickResultTitleIcon(
                    icon = Icons.Outlined.Edit,
                    selected = textMode == PickResultTextMode.EDIT,
                    contentDescription = stringResource(R.string.float_ball_action_edit),
                    onClick = onEditToggle,
                )

                // 2. Select All / Deselect All icon (做法一：全选线框，全选中切取消且变亮蓝)
                PickResultTitleIcon(
                    icon = if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                    selected = allSelected,
                    contentDescription = selectAllText,
                    onClick = onSelectAll,
                )

                // 3. Word tap / Select mode icon (▦)
                PickResultTitleIcon(
                    icon = Icons.Outlined.ViewModule,
                    selected = textMode == PickResultTextMode.WORD_TAP,
                    contentDescription = stringResource(R.string.float_ball_action_word_select),
                    onClick = onWordSelectToggle,
                )

                // 4. Trim / Remove all spaces icon (><)
                PickResultTitleIcon(
                    icon = Icons.Outlined.UnfoldLess,
                    selected = false,
                    contentDescription = stringResource(R.string.float_ball_action_trim_spaces),
                    modifier = Modifier.rotate(90f),
                    onClick = onTrimSpaces,
                    onLongClick = onRemoveAllSpaces,
                )
            }
        }
    }
}

@Composable
private fun PickResultSourceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        !enabled -> androidx.compose.ui.graphics.Color.Transparent
        selected -> colors.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> colors.onPrimary
        else -> colors.onSurfaceVariant
    }
    val chipStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 13.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
    )
    val shape = RoundedCornerShape(14.dp)
    val chipModifier = Modifier
        .clip(shape)
        .background(background)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 11.dp, vertical = 5.dp)

    Box(
        modifier = chipModifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = chipStyle,
            color = contentColor,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PickResultTitleIcon(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconContent: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(18.dp),
        )
    }
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        iconContent()
    }
}

@Composable
internal fun PickResultOcrLoadingBody(
    modifier: Modifier = Modifier,
    showBackgroundAction: Boolean = false,
    onBackgroundProcess: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.float_ball_recognizing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (showBackgroundAction) {
            TextButton(
                onClick = onBackgroundProcess,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.share_image_ocr_background_action))
            }
        }
    }
}

@Composable
internal fun PickResultTranslateLoadingBody(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.float_ball_translating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PickResultTextBody(
    textMode: PickResultTextMode,
    textFieldValue: TextFieldValue,
    wordTokens: List<String>,
    selectedWordIndices: Set<Int>,
    selectAllRequest: Int,
    deselectAllRequest: Int,
    textSizeSp: Float,
    bodyMaxHeight: Dp? = null,
    expandToFill: Boolean = false,
    useInternalScroll: Boolean = true,
    autoSelectAll: Boolean = false,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSelectionChanged: (start: Int, end: Int) -> Unit,
    onWordSelectionChange: (Set<Int>) -> Unit,
    onWordLongPress: (Int) -> Unit,
    onZoomText: ((Boolean) -> Unit)? = null,
    onExitEditMode: (() -> Unit)? = null,
    selectionToolbarActions: OverlaySelectionToolbarActions? = null,
    hapticEnabled: Boolean = true,
    onWordTapScrollableChange: ((Boolean) -> Unit)? = null,
) {
    val bodyTextSize = textSizeSp.sp
    val editLineHeight = (textSizeSp * 22f / 15f).sp
    val defaultMaxHeight = pickResultMaxTextHeight(textSizeSp)
    val effectiveMaxHeight = bodyMaxHeight?.let { allocated ->
        (allocated - PickResultTextBodyVerticalPadding).coerceAtLeast(0.dp)
    } ?: defaultMaxHeight
    val scrollState = rememberScrollState()
    val currentOnZoomText by rememberUpdatedState(onZoomText)
    val currentOnExitEditMode by rememberUpdatedState(onExitEditMode)
    val appContext = LocalContext.current
    val density = LocalDensity.current
    val viewportHeightPx = with(density) { effectiveMaxHeight.roundToPx().toFloat() }
    val paddedModifier = Modifier
        .fillMaxWidth()
        .then(
            when {
                expandToFill -> Modifier.fillMaxHeight()
                bodyMaxHeight != null -> Modifier.heightIn(max = bodyMaxHeight)
                else -> Modifier
            },
        )
        .padding(
            start = 4.dp,
            end = 4.dp,
            top = 4.dp,
            bottom = 4.dp,
        )
        .pointerInput(textMode) {
            if (textMode == PickResultTextMode.EDIT || textMode == PickResultTextMode.SELECT) {
                return@pointerInput
            }
            var cumulativeZoom = 1f
            awaitEachGesture {
                var pastTouchSlop = false
                val touchSlop = viewConfiguration.touchSlop

                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val canceled = event.changes.any { it.isConsumed }
                    val pressedPointers = event.changes.filter { it.pressed }
                    // 仅当至少 2 根手指按下时才判定双指缩放，单指滑动彻底放行给外层 HorizontalPager 或滚动容器
                    if (!canceled && pressedPointers.size >= 2) {
                        val zoomChange = event.calculateZoom()
                        if (!pastTouchSlop) {
                            cumulativeZoom *= zoomChange
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = kotlin.math.abs(1f - cumulativeZoom) * centroidSize
                            if (zoomMotion > touchSlop) {
                                pastTouchSlop = true
                            }
                        }
                        if (pastTouchSlop) {
                            if (zoomChange != 1f) {
                                cumulativeZoom *= zoomChange
                                when {
                                    cumulativeZoom > 1.2f -> {
                                        currentOnZoomText?.invoke(true)
                                        cumulativeZoom = 1f
                                    }
                                    cumulativeZoom < 0.8f -> {
                                        currentOnZoomText?.invoke(false)
                                        cumulativeZoom = 1f
                                    }
                                }
                            }
                            event.changes.forEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        }
                    }
                } while (!canceled && event.changes.any { it.pressed })
            }
        }

    if (textFieldValue.text.isBlank() && textMode != PickResultTextMode.EDIT) {
        Text(
            text = stringResource(R.string.float_ball_text_not_found),
            modifier = paddedModifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    when (textMode) {
        PickResultTextMode.EDIT -> {
            val editOuterModifier = when {
                expandToFill -> paddedModifier.fillMaxHeight()
                useInternalScroll -> paddedModifier.heightIn(
                    min = effectiveMaxHeight,
                    max = effectiveMaxHeight,
                )
                else -> paddedModifier
            }.onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.Back
                ) {
                    currentOnExitEditMode?.invoke()
                    true
                } else {
                    false
                }
            }
            val editScrollEnabled = expandToFill || useInternalScroll
            val placeholderStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = bodyTextSize,
                lineHeight = editLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val toolbarState = rememberOverlaySelectionToolbarState()
            val editToolbarActions = selectionToolbarActions?.copy(
                editable = true,
                onCut = {
                    val updated = cutTextFieldValue(textFieldValue) { copied ->
                        FloatBallTextPick.copyText(appContext, copied)
                    }
                    onTextFieldValueChange(updated)
                },
                onPaste = {
                    val updated = pasteIntoTextFieldValue(appContext, textFieldValue)
                    onTextFieldValueChange(updated)
                },
                onSelectAll = {
                    onTextFieldValueChange(
                        textFieldValue.copy(
                            selection = TextRange(0, textFieldValue.text.length),
                        ),
                    )
                },
            )
            Box(
                modifier = editOuterModifier
                    .clip(RoundedCornerShape(0.dp))
                    .then(Modifier.viewportModifier(toolbarState)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (editScrollEnabled) {
                                Modifier.verticalScroll(scrollState)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = onTextFieldValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .suppressSystemTextContextMenu()
                            .then(Modifier.fieldModifier(toolbarState)),
                        onTextLayout = { toolbarState.textLayoutResult = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = bodyTextSize,
                            lineHeight = editLineHeight,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.float_ball_text_not_found),
                                        style = placeholderStyle,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                if (editToolbarActions != null) {
                    OverlaySelectionToolbarOverlay(
                        visible = !textFieldValue.selection.collapsed,
                        selection = textFieldValue.selection,
                        text = textFieldValue.text,
                        textLayoutResult = toolbarState.textLayoutResult,
                        fieldCoordinates = toolbarState.fieldCoordinates,
                        viewportCoordinates = toolbarState.viewportCoordinates,
                        scrollOffsetYProvider = { if (editScrollEnabled) scrollState.value else 0 },
                        viewportHeightPx = viewportHeightPx,
                        actions = editToolbarActions,
                    )
                }
            }
        }
        PickResultTextMode.WORD_TAP -> {
            if (wordTokens.isEmpty() && textFieldValue.text.isNotBlank()) {
                Box(
                    modifier = paddedModifier.padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                PickResultWordTapBody(
                    wordTokens = wordTokens,
                    selectedWordIndices = selectedWordIndices,
                    onSelectionChange = onWordSelectionChange,
                    onWordLongPress = onWordLongPress,
                    maxHeight = effectiveMaxHeight,
                    fillAvailableHeight = expandToFill,
                    textSizeSp = textSizeSp,
                    hapticEnabled = hapticEnabled,
                    modifier = paddedModifier,
                    onScrollableChange = onWordTapScrollableChange,
                )
            }
        }
        PickResultTextMode.SELECT -> {
            var selection by remember(textFieldValue.text) {
                mutableStateOf(
                    if (autoSelectAll && textFieldValue.text.isNotEmpty()) {
                        TextRange(0, textFieldValue.text.length)
                    } else {
                        TextRange.Zero
                    }
                )
            }
            var lastSelectAllRequest by remember { mutableIntStateOf(0) }
            var lastDeselectAllRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(textFieldValue.text) {
                if (autoSelectAll && textFieldValue.text.isNotEmpty()) {
                    val range = TextRange(0, textFieldValue.text.length)
                    selection = range
                    onSelectionChanged(range.start, range.end)
                } else {
                    selection = TextRange.Zero
                }
            }
            LaunchedEffect(selectAllRequest) {
                if (selectAllRequest > lastSelectAllRequest) {
                    lastSelectAllRequest = selectAllRequest
                    selection = TextRange(0, textFieldValue.text.length)
                    onSelectionChanged(0, textFieldValue.text.length)
                }
            }
            LaunchedEffect(deselectAllRequest) {
                if (deselectAllRequest > lastDeselectAllRequest) {
                    lastDeselectAllRequest = deselectAllRequest
                    selection = TextRange.Zero
                    onSelectionChanged(0, 0)
                }
            }
            val selectScrollState = rememberScrollState()
            val toolbarState = rememberOverlaySelectionToolbarState()
            // 正文区高度：**先用测量器算出真实文字高度**，不再拿一个"垫数"顶着。
            //
            // 之前这里在"还没测到"时回退到 PickResultTextBodyMinVisibleHeight（240dp），
            // 而真机日志显示这段文字实测只有 32dp —— 首帧垫 240、第二帧掉到 32，
            // 贴底 + 分页样式下就是用户看到的"面板整体抬高再掉回来"（524 → 316 那一段）。
            //
            // 现在首帧就能拿到实测值（测量器与 BasicTextField 用同一套 TextStyle：
            // 同字号、同行高、同可用宽度），所以首帧即终态，那一段跳变消失。
            val textMeasurer = rememberTextMeasurer()
            // ⚠️ 这里用"容器宽 × 0.92"近似面板宽度：面板在竖屏贴底时约占屏宽 92%，居中/横屏样式会更窄。
            // 偏窄只会让测量结果偏保守（行数偏多、高度偏大），比偏宽安全——偏宽会少算行数、反而要二次纠正。
            // 若以后要更精确，把 panelLayoutWidth 沿 PickResultPanelTextSlot → 本函数传下来即可。
            val textBodyWidthPx = with(density) {
                (overlayContainerWidthDp() * 0.92f).toPx().toInt()
            }
            // ⚠️ 取值必须在可组合上下文里先做好：runCatching 的 lambda 不是 @Composable，不能读 MaterialTheme。
            val measureStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = bodyTextSize,
                lineHeight = editLineHeight,
            )
            val textVerticalPaddingPx = with(density) { PickResultTextBodyTopPadding.toPx() * 2f }
            val measuredTextHeight = remember(textFieldValue.text, textSizeSp, textBodyWidthPx) {
                if (textFieldValue.text.isBlank()) {
                    Dp.Unspecified
                } else {
                    runCatching {
                        val layout = textMeasurer.measure(
                            text = textFieldValue.text,
                            style = measureStyle,
                            constraints = androidx.compose.ui.unit.Constraints(
                                maxWidth = textBodyWidthPx.coerceAtLeast(1),
                            ),
                        )
                        with(density) {
                            (layout.size.height + textVerticalPaddingPx).toDp()
                        }
                    }.getOrDefault(Dp.Unspecified)
                }
            }
            var selectContentHeightDp by remember(textFieldValue.text) {
                mutableStateOf(measuredTextHeight)
            }
            // 兜底顺序：实测值 → 首帧没测到（例如刚切换模式）时仍用下限。
            val selectBoxHeight = selectContentHeightDp
                .takeIf { it != Dp.Unspecified }
                ?.coerceAtMost(effectiveMaxHeight)
                ?: measuredTextHeight.takeIf { it != Dp.Unspecified }
                ?: PickResultTextBodyMinVisibleHeight
            val selectToolbarActions = remember(selectionToolbarActions) {
                selectionToolbarActions?.copy(
                    editable = false,
                    onSelectAll = null,
                )
            }
            Box(
                modifier = paddedModifier
                    .heightIn(min = selectBoxHeight, max = selectBoxHeight)
                    .clip(RoundedCornerShape(0.dp))
                    .then(Modifier.viewportModifier(toolbarState)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(selectScrollState),
                ) {
                    BasicTextField(
                        value = TextFieldValue(textFieldValue.text, selection),
                        onValueChange = { updated ->
                            if (updated.text != textFieldValue.text) return@BasicTextField
                            selection = updated.selection
                            onSelectionChanged(updated.selection.start, updated.selection.end)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .suppressSystemTextContextMenu()
                            .then(Modifier.fieldModifier(toolbarState)),
                        onTextLayout = { layoutResult ->
                            toolbarState.textLayoutResult = layoutResult
                            // 正文区容器高度 = 文字真实高度 + paddedModifier 的上下 4dp。
                            // ⚠️ "可用空间"那部分不能重复加：effectiveMaxHeight 已经扣过
                            // PickResultTextBodyVerticalPadding，那是给分词模式的分配量，不是这里的间距。
                            val measured = with(density) {
                                (layoutResult.size.height +
                                    PickResultTextBodyTopPadding.toPx() * 2f).toDp()
                            }
                            if (measured != selectContentHeightDp) {
                                // 与上面的测量器取值同一口径（都夹在可用空间内），避免两边算出不同高度又来一次跳变。
                                selectContentHeightDp = measured.coerceAtMost(effectiveMaxHeight)
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = bodyTextSize,
                            lineHeight = editLineHeight,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        readOnly = true,
                    )
                }
                if (selectToolbarActions != null) {
                    OverlaySelectionToolbarOverlay(
                        visible = !selection.collapsed,
                        selection = selection,
                        text = textFieldValue.text,
                        textLayoutResult = toolbarState.textLayoutResult,
                        fieldCoordinates = toolbarState.fieldCoordinates,
                        viewportCoordinates = toolbarState.viewportCoordinates,
                        scrollOffsetYProvider = { selectScrollState.value },
                        viewportHeightPx = viewportHeightPx,
                        actions = selectToolbarActions.copy(
                            onSelectAll = {
                                selection = TextRange(0, textFieldValue.text.length)
                                onSelectionChanged(0, textFieldValue.text.length)
                            },
                        ),
                    )
                }
            }
        }
    }
}

