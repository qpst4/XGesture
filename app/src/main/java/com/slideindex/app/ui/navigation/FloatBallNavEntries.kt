package com.slideindex.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.FloatBallAppearanceSettingsScreen
import com.slideindex.app.ui.FloatBallGestureSettingsScreen
import com.slideindex.app.ui.floatBallGestureLabel
import com.slideindex.app.ui.FloatBallPickHandfeelSettingsScreen
import com.slideindex.app.ui.FloatBallPickPanelLayoutBehaviorSettingsScreen
import com.slideindex.app.ui.FloatBallPickSettingsScreen
import com.slideindex.app.ui.FloatBallSettingsScreen
import com.slideindex.app.ui.FloatBallStyleSettingsScreen
import com.slideindex.app.ui.CloudTranslateModelPickerScreen
import com.slideindex.app.ui.CloudTranslateSettingsScreen
import com.slideindex.app.ocr.vlm.VlmProvider
import com.slideindex.app.ui.FloatBallTranslationSettingsScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.ImageSearchEngineDetailScreen
import com.slideindex.app.ui.ImageSearchEngineSettingsScreen
import com.slideindex.app.ui.OcrModelSettingsScreen
import com.slideindex.app.ui.QuickLauncherPanelPickScreen
import com.slideindex.app.ui.SearchEngineEditorCategory
import com.slideindex.app.ui.SearchEngineEditorScreen
import com.slideindex.app.ui.SearchEnginePreviewSortScreen
import com.slideindex.app.ui.SearchEngineSettingsScreen
import com.slideindex.app.ui.ShareImageOcrHistoryScreen
import com.slideindex.app.ui.TranslateModelSettingsScreen
import com.slideindex.app.ui.VlmOcrSettingsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.picker.ShareImageTargetPickScreen
import com.slideindex.app.ui.picker.TranslateAppPickScreen
import com.slideindex.app.ui.picker.ShareTargetPickScreen
import com.slideindex.app.ui.resolveImageSearchEngine
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.FloatBallPickSettingsViewModel
import com.slideindex.app.ui.viewmodel.CloudTranslateSettingsViewModel
import com.slideindex.app.ui.viewmodel.OcrModelSettingsViewModel
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel
import com.slideindex.app.ui.viewmodel.ShareImageOcrHistoryViewModel
import com.slideindex.app.ui.viewmodel.TranslateSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavEntryBuilder.floatBallNavEntries(ctx: MainNavContext) {
    nativeEnginePackNavEntry(ctx)
    hiltEntry<AppNavKey.FloatBall> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setFloatBallEnabled,
            onDragPasteEnabledChange = viewModel::setFloatBallDragPasteEnabled,
            onOpenAppearanceSettings = { ctx.navigate(AppNavKey.FloatBallAppearance) },
            onOpenGestureSettings = { ctx.navigate(AppNavKey.FloatBallGesture) },
            onOpenPickHandfeelSettings = { ctx.navigate(AppNavKey.FloatBallPickHandfeel) },
        )
    }

    hiltEntry<AppNavKey.FloatBallSearchEngine> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val importPreviewState by viewModel.importPreviewState.collectAsStateWithLifecycle()
        SearchEngineSettingsScreen(
            settings = settings,
            importPreviewState = importPreviewState,
            onBack = { ctx.backStack.removeLastOrNull() },
            onOpenPresetPicker = { ctx.navigate(AppNavKey.FloatBallSearchEnginePresetPicker) },
            onImport = viewModel::previewImport,
            onDismissImportPreview = viewModel::dismissImportPreview,
            onConfirmImport = viewModel::confirmImport,
            onUpsertEngine = viewModel::upsertEngine,
            onDeleteEngine = viewModel::deleteEngine,
            onMoveEngine = viewModel::moveEngine,
            onUpdateEngines = viewModel::updateTextEngines,
            onGridColumnsChange = viewModel::setGridColumns,
            onGridRowsChange = viewModel::setGridRows,
            onShowLabelsChange = viewModel::setShowLabels,
            onOpenPreviewSort = { ctx.navigate(AppNavKey.FloatBallSearchEnginePreviewSort) },
            onOpenEditor = { engineId ->
                ctx.navigate(AppNavKey.FloatBallSearchEngineEditor(engineId.orEmpty()))
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallSearchEnginePresetPicker> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        com.slideindex.app.ui.PresetSearchEnginePickerScreen(
            currentEngines = settings.searchEngines,
            onBack = { ctx.backStack.removeLastOrNull() },
            onAddEngines = viewModel::addEngines,
        )
    }

    hiltEntry<AppNavKey.FloatBallSearchEngineEditor> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel(ctx.activity)
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val initialEngine = key.engineId.takeIf { it.isNotEmpty() }
            ?.let { id -> settings.searchEngines.find { it.id == id } }
        LaunchedEffect(key.engineId) {
            viewModel.initDraft(initialEngine, SearchEngineEditorCategory.TEXT)
        }
        val draft by viewModel.editorDraft.collectAsStateWithLifecycle()
        draft?.let { currentDraft ->
            SearchEngineEditorScreen(
                initialEngine = initialEngine,
                draft = currentDraft,
                editorCategory = SearchEngineEditorCategory.TEXT,
                onBack = { ctx.backStack.removeLastOrNull() },
                onSave = { result ->
                    viewModel.upsertEngine(result)
                    ctx.backStack.removeLastOrNull()
                },
                onUpdateDraft = viewModel::updateDraft,
                onPickApp = { target, titleResId, pkg ->
                    ctx.navigate(
                        AppNavKey.SearchEnginePickApp(
                            isImageSearch = false,
                            target = target,
                            titleResId = titleResId,
                            selectedPackageName = pkg,
                        ),
                    )
                },
                onPickActivity = { pkg, cls ->
                    ctx.navigate(
                        AppNavKey.SearchEnginePickActivity(
                            isImageSearch = false,
                            packageName = pkg,
                            selectedClassName = cls,
                        ),
                    )
                },
                onPickShareTarget = { pkg, cls ->
                    ctx.navigate(
                        AppNavKey.SearchEnginePickShareTarget(
                            isImageSearch = false,
                            selectedPackageName = pkg,
                            selectedActivityClassName = cls,
                        ),
                    )
                },
            )
        }
    }

    hiltEntry<AppNavKey.SearchEnginePickApp> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel(ctx.activity)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pickAppIconFailedMessage = stringResource(R.string.search_engine_pick_app_icon_failed)
        val editorDraft by viewModel.editorDraft.collectAsStateWithLifecycle()
        val returnKey = if (key.isImageSearch) {
            AppNavKey.FloatBallImageSearchEngineEditor(editorDraft?.engineId.orEmpty())
        } else {
            AppNavKey.FloatBallSearchEngineEditor(editorDraft?.engineId.orEmpty())
        }
        ActivityShortcutPickAppScreen(
            titleResId = key.titleResId,
            selectedPackageName = key.selectedPackageName,
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                when (key.target) {
                    "TARGET" -> {
                        viewModel.updateDraft { draft ->
                            val previousPackage = draft.targetPackage
                            draft.copy(
                                targetPackage = app.packageName,
                                targetActivity = if (previousPackage != app.packageName) "" else draft.targetActivity,
                            )
                        }
                        ctx.navigateBackTo(returnKey)
                    }
                    "EXTERN" -> {
                        viewModel.updateDraft { it.copy(externJumpPackage = app.packageName) }
                        ctx.navigateBackTo(returnKey)
                    }
                    "APP_ICON" -> {
                        scope.launch {
                            val iconPath = withContext(Dispatchers.IO) {
                                SearchEngineIconStorage.saveIconFromPackage(context, app.packageName)
                            }
                            if (iconPath != null) {
                                viewModel.updateDraft {
                                    it.copy(
                                        pendingIconPath = iconPath,
                                        pendingIconUri = null,
                                        pendingTextIcon = null,
                                    )
                                }
                                ctx.navigateBackTo(returnKey)
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    pickAppIconFailedMessage,
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }
            },
        )
    }

    hiltEntry<AppNavKey.SearchEnginePickActivity> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel(ctx.activity)
        val editorDraft by viewModel.editorDraft.collectAsStateWithLifecycle()
        val returnKey = if (key.isImageSearch) {
            AppNavKey.FloatBallImageSearchEngineEditor(editorDraft?.engineId.orEmpty())
        } else {
            AppNavKey.FloatBallSearchEngineEditor(editorDraft?.engineId.orEmpty())
        }
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            selectedClassName = key.selectedClassName,
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectActivity = { activity ->
                viewModel.updateDraft { it.copy(targetActivity = activity.className) }
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.SearchEnginePickShareTarget> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel(ctx.activity)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val editorDraft by viewModel.editorDraft.collectAsStateWithLifecycle()
        val returnKey = if (key.isImageSearch) {
            AppNavKey.FloatBallImageSearchEngineEditor(editorDraft?.engineId.orEmpty())
        } else {
            AppNavKey.FloatBallSearchEngineEditor(editorDraft?.engineId.orEmpty())
        }
        ShareTargetPickScreen(
            forImage = key.isImageSearch,
            selectedPackageName = key.selectedPackageName,
            selectedActivityClassName = key.selectedActivityClassName,
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectTarget = { target ->
                viewModel.updateDraft { draft ->
                    val nextName = if (draft.name.isBlank()) target.appLabel.ifBlank { target.label } else draft.name
                    draft.copy(
                        targetPackage = target.packageName,
                        targetActivity = target.activityClassName,
                        name = nextName,
                    )
                }
                scope.launch {
                    val iconPath = withContext(Dispatchers.IO) {
                        SearchEngineIconStorage.saveIconFromPackage(context, target.packageName)
                    }
                    if (iconPath != null) {
                        viewModel.updateDraft {
                            it.copy(
                                pendingIconPath = iconPath,
                                pendingIconUri = null,
                                pendingTextIcon = null,
                            )
                        }
                    }
                    ctx.navigateBackTo(returnKey)
                }
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallSearchEnginePreviewSort> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        SearchEnginePreviewSortScreen(
            settings = settings,
            onBack = { ctx.backStack.removeLastOrNull() },
            onReorder = viewModel::reorderPickPanelEngines,
        )
    }

    hiltEntry<AppNavKey.FloatBallAppearance> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallAppearanceSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onSizeChange = viewModel::setFloatBallSizeDp,
            onOpacityChange = viewModel::setFloatBallOpacity,
            onPositionModeChange = viewModel::setFloatBallPositionMode,
            onVisibleFractionChange = viewModel::setFloatBallVisibleFraction,
            onPositionYChange = viewModel::setFloatBallPositionYFraction,
            onLineHeightChange = viewModel::setFloatBallLineHeightFraction,
            onLineWidthChange = viewModel::setFloatBallLineWidthFraction,
            onLineOpacityChange = viewModel::setFloatBallLineOpacity,
            onOpenStyleSettings = { ctx.navigate(AppNavKey.FloatBallStyle) },
            onStripZonePreviewStart = { ctx.startFloatBallStripZonePreview() },
            onStripZonePreviewStop = { ctx.stopFloatBallStripZonePreview() },
            onPositionYPreviewStart = { /* preview starts on first drag tick */ },
            onPositionYPreviewChange = { ctx.previewFloatBallPositionY(it) },
            onPositionYPreviewStop = { restore -> ctx.endFloatBallPositionYPreview(restore) },
            onPreviewAppearance = { size, opacity, visible, lineHeight, lineWidth, lineOpacity ->
                ctx.previewFloatBallAppearance(
                    sizeDp = size,
                    opacity = opacity,
                    visibleFraction = visible,
                    lineHeightFraction = lineHeight,
                    lineWidthFraction = lineWidth,
                    lineOpacity = lineOpacity,
                )
            },
            onAppearancePreviewCommit = { ctx.clearFloatBallAppearancePreviewRestore() },
            onAppearancePreviewRestore = { ctx.endFloatBallAppearancePreview(true) },
        )
    }

    hiltEntry<AppNavKey.FloatBallStyle> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallStyleSettingsScreen(
            settings = settings,
            enabled = true,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallAppearance) },
            onStyleTypeChange = viewModel::setFloatBallStyleType,
            onCustomImageUriChange = viewModel::setFloatBallCustomImageUri,
            onSlideshowUrisChange = viewModel::setFloatBallSlideshowUris,
            onGifUriChange = viewModel::setFloatBallGifUri,
        )
    }

    hiltEntry<AppNavKey.FloatBallGesture> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        FloatBallGestureSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBall) },
            onOpenActionPick = { type ->
                ctx.navigate(AppNavKey.FloatBallGestureActionPick(type.id))
            },
            onOpenShellCommand = { type, command ->
                ctx.navigate(AppNavKey.FloatBallGestureShellCommand(type.id, command))
            },
            onOpenQuickLauncherPanel = { type, panelId ->
                ctx.navigate(AppNavKey.FloatBallGestureQuickLauncherPanel(type.id, panelId))
            },
            onDownSwipeShortPercentChange = viewModel::setFloatBallDownSwipeShortPercent,
            onSideSwipeShortPercentChange = viewModel::setFloatBallSideSwipeShortPercent,
            onUpSwipeShortPercentChange = viewModel::setFloatBallUpSwipeShortPercent,
            onLongPressMsChange = viewModel::setFloatBallLongPressMs,
            onOverlayAnchorAtTouchDownChange = viewModel::setFloatBallOverlayAnchorAtTouchDown,
        )
    }

    hiltEntry<AppNavKey.FloatBallGestureActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val returnKey = AppNavKey.FloatBallGesture
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            current = settings.floatBallGestureActions[gestureType] ?: GestureAction.None,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap),
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                viewModel.setFloatBallGestureAction(gestureType, action)
                ctx.navigateBackTo(returnKey)
            },
            onOpenMyShortcuts = { ctx.navigate(AppNavKey.FloatBallGestureMyShortcuts(key.gestureTypeId)) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.FloatBallGesturePresetShortcuts(key.gestureTypeId)) },
            onOpenPickApp = { ctx.navigate(AppNavKey.FloatBallGesturePickApp(key.gestureTypeId)) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.FloatBallGestureShellCommand(key.gestureTypeId, cmd)) },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.FloatBallGestureSimulateKeyEvent(
                        key.gestureTypeId,
                        keyEvent.keyCode,
                        keyEvent.keyName,
                        keyEvent.isLongPress,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallGestureMyShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val currentAction = appSettings.floatBallGestureActions[gestureType] ?: GestureAction.None
        val returnKey = AppNavKey.FloatBallGestureActionPick(key.gestureTypeId)
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.FloatBallGesturePickApp(key.gestureTypeId)) },
            currentAction = currentAction,
            onSelectRadio = { action ->
                viewModel.setFloatBallGestureAction(gestureType, action)
                ctx.navigateBackTo(AppNavKey.FloatBallGesture)
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallGesturePresetShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val currentAction = appSettings.floatBallGestureActions[gestureType] ?: GestureAction.None
        val returnKey = AppNavKey.FloatBallGestureActionPick(key.gestureTypeId)
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = currentAction,
            onSelectRadio = { action ->
                viewModel.setFloatBallGestureAction(gestureType, action)
                ctx.navigateBackTo(AppNavKey.FloatBallGesture)
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallGesturePickApp> { key ->
        val returnKey = AppNavKey.FloatBallGestureActionPick(key.gestureTypeId)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.FloatBallGesturePickActivity(key.gestureTypeId, app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallGesturePickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                viewModel.setFloatBallGestureAction(
                    gestureType,
                    GestureAction.LaunchShortcut.component(
                        "${activity.packageName}/${activity.className}",
                        activity.label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.FloatBallGesture)
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallGestureShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val returnKey = AppNavKey.FloatBallGesture
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatBallGestureAction(
                    gestureType,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    /**
     * 悬浮球手势绑的是「快速启动器」时，选要打开哪一个面板（页面）。
     * 与侧滑触钮那条 `AppNavKey.HomeSideGestureSlotQuickLauncherPanel` 同构，用同一个选择页。
     */
    hiltEntry<AppNavKey.FloatBallGestureQuickLauncherPanel> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val returnKey = AppNavKey.FloatBallGesture
        QuickLauncherPanelPickScreen(
            settings = settings,
            currentPanelId = key.panelId,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelect = { panel ->
                viewModel.setFloatBallGestureAction(
                    gestureType,
                    GestureAction.QuickLauncher(panel.id),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallGestureSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureType = FloatBallGestureType.fromId(key.gestureTypeId) ?: FloatBallGestureType.SINGLE_TAP
        val returnKey = AppNavKey.FloatBallGestureActionPick(key.gestureTypeId)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                viewModel.setFloatBallGestureAction(
                    gestureType,
                    keyEventAction,
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallImageSearchEngine> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ImageSearchEngineSettingsScreen(
            settings = settings,
            onBack = { ctx.backStack.removeLastOrNull() },
            onUpsertEngine = viewModel::upsertEngine,
            onDeleteEngine = viewModel::deleteEngine,
            onReorderShareEngines = viewModel::reorderImageShareEngines,
            onReorderAggregatedEngines = viewModel::reorderAggregatedImageSearchEngines,
            onOpenAggregatedEngine = { engineId ->
                ctx.navigate(AppNavKey.FloatBallImageSearchEngineDetail(engineId))
            },
            onImageSearchPickPanelTransparencyChange = viewModel::setImageSearchPickPanelTransparency,
            onOpenEditor = { engineId ->
                ctx.navigate(AppNavKey.FloatBallImageSearchEngineEditor(engineId.orEmpty()))
            },
        )
    }

    hiltEntry<AppNavKey.FloatBallImageSearchEngineEditor> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel(ctx.activity)
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val initialEngine = key.engineId.takeIf { it.isNotEmpty() }
            ?.let { id -> settings.searchEngines.find { it.id == id } }
        LaunchedEffect(key.engineId) {
            viewModel.initDraft(initialEngine, SearchEngineEditorCategory.IMAGE_SHARE)
        }
        val draft by viewModel.editorDraft.collectAsStateWithLifecycle()
        draft?.let { currentDraft ->
            SearchEngineEditorScreen(
                initialEngine = initialEngine,
                draft = currentDraft,
                editorCategory = SearchEngineEditorCategory.IMAGE_SHARE,
                onBack = { ctx.backStack.removeLastOrNull() },
                onSave = { result ->
                    viewModel.upsertEngine(result)
                    ctx.backStack.removeLastOrNull()
                },
                onUpdateDraft = viewModel::updateDraft,
                onPickApp = { target, titleResId, pkg ->
                    ctx.navigate(
                        AppNavKey.SearchEnginePickApp(
                            isImageSearch = true,
                            target = target,
                            titleResId = titleResId,
                            selectedPackageName = pkg,
                        ),
                    )
                },
                onPickActivity = { pkg, cls ->
                    ctx.navigate(
                        AppNavKey.SearchEnginePickActivity(
                            isImageSearch = true,
                            packageName = pkg,
                            selectedClassName = cls,
                        ),
                    )
                },
                onPickShareTarget = { pkg, cls ->
                    ctx.navigate(
                        AppNavKey.SearchEnginePickShareTarget(
                            isImageSearch = true,
                            selectedPackageName = pkg,
                            selectedActivityClassName = cls,
                        ),
                    )
                },
            )
        }
    }

    hiltEntry<AppNavKey.FloatBallImageSearchEngineDetail> { key ->
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val config = settings.aggregatedImageSearchEngines.find { it.engineId == key.engineId }
        val engine = resolveImageSearchEngine(key.engineId)
        if (config == null || engine == null) {
            LaunchedEffect(Unit) {
                ctx.navigateBackTo(AppNavKey.FloatBallImageSearchEngine)
            }
        } else {
            ImageSearchEngineDetailScreen(
                engine = engine,
                config = config,
                onBack = { ctx.navigateBackTo(AppNavKey.FloatBallImageSearchEngine) },
                onShowInPanelChange = { enabled ->
                    viewModel.setAggregatedImageSearchEngineShowInPanel(key.engineId, enabled)
                },
                onPreloadChange = { enabled ->
                    viewModel.setAggregatedImageSearchEnginePreload(key.engineId, enabled)
                },
            )
        }
    }

    hiltEntry<AppNavKey.FloatBallPick> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val pickViewModel: FloatBallPickSettingsViewModel = hiltViewModel()
        val historyViewModel: ShareImageOcrHistoryViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val imageViewerOptions by pickViewModel.imageViewerOptions.collectAsStateWithLifecycle()
        val historyEntries by historyViewModel.historyRepository.entries.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        FloatBallPickSettingsScreen(
            settings = settings,
            historyCount = historyEntries.size,
            imageViewerOptions = imageViewerOptions,
            onOcrFallbackChange = viewModel::setFloatBallOcrFallbackEnabled,
            onShareImageOcrHistoryEnabledChange = viewModel::setShareImageOcrHistoryEnabled,
            onDefaultImageViewerPackageChange = viewModel::setDefaultImageViewerPackage,
            onImageEditorDelayDeleteChange = viewModel::setImageEditorDelayDeleteEnabled,
            onOpenOcrModels = { ctx.navigate(AppNavKey.OcrModels) },
            onOpenShareImageOcrHistory = { ctx.navigate(AppNavKey.ShareImageOcrHistory) },
            onOpenTranslationSettings = { ctx.navigate(AppNavKey.FloatBallTranslation) },
            onOpenPanelLayoutBehaviorSettings = { ctx.navigate(AppNavKey.FloatBallPickPanelLayoutBehavior) },
            onOpenPickHandfeelSettings = { ctx.navigate(AppNavKey.FloatBallPickHandfeel) },
            onOpenSearchEngineSettings = { ctx.navigate(AppNavKey.FloatBallSearchEngine) },
            onOpenImageSearchEngineSettings = { ctx.navigate(AppNavKey.FloatBallImageSearchEngine) },
            onDefaultSearchEngineChange = viewModel::setFloatBallPickDefaultSearchEngineId,
            onBack = { ctx.backStack.removeLastOrNull() },
        )
    }

    // 从取词页或悬浮球页均可进入，返回键回到各自的来源页。
    hiltEntry<AppNavKey.FloatBallPickHandfeel> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatBallPickHandfeelSettingsScreen(
            settings = settings,
            onPickCrossArmChange = viewModel::setFloatBallPickCrossArmDp,
            onPickOffsetChange = viewModel::setFloatBallPickOffsetDp,
            onPickBottomTransitionChange = viewModel::setFloatBallPickBottomTransitionFraction,
            onPointerSpeedChange = viewModel::setFloatBallPointerSpeedFraction,
            onPointerSpeedVerticalChange = viewModel::setFloatBallPointerSpeedVerticalFraction,
            onPointerSlopChange = viewModel::setFloatBallPointerSlopDp,
            onHoverPauseDelayMsChange = viewModel::setFloatBallHoverPauseDelayMs,
            onRegionalCancelSlopDpChange = viewModel::setFloatBallRegionalCancelSlopDp,
            onBack = { ctx.backStack.removeLastOrNull() },
        )
    }

    hiltEntry<AppNavKey.FloatBallPickPanelLayoutBehavior> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatBallPickPanelLayoutBehaviorSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onPickPanelStyleChange = viewModel::setFloatBallPickPanelStyle,
            onPickPanelPlacementChange = viewModel::setFloatBallPickPanelPlacement,
            onPickSearchGridDefaultStateChange = viewModel::setFloatBallPickSearchGridDefaultState,
            onPickTextModeDefaultChange = viewModel::setFloatBallPickTextModeDefault,
            onPickTextFirstPanelChange = viewModel::setFloatBallPickTextFirstPanel,
            onPickAutoSelectAllChange = viewModel::setFloatBallPickAutoSelectAll,
            onPickCopyDismissPanelChange = viewModel::setFloatBallPickCopyDismissPanel,
            onPickCopyButtonPositionChange = viewModel::setFloatBallPickCopyButtonPosition,
            onPickImageToolbarPositionChange = viewModel::setFloatBallPickImageToolbarPosition,
            onPickTextSizeChange = viewModel::setFloatBallPickTextSizeSp,
            onPickHapticEnabledChange = viewModel::setFloatBallPickHapticEnabled,
            onPickPanelEnterAnimationMsChange = viewModel::setFloatBallPickPanelEnterAnimationMs,
            onPickPanelExitAnimationMsChange = viewModel::setFloatBallPickPanelExitAnimationMs,
        )
    }

    hiltEntry<AppNavKey.ShareImageOcrHistory> {
        val viewModel: ShareImageOcrHistoryViewModel = hiltViewModel()
        ShareImageOcrHistoryScreen(
            repository = viewModel.historyRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onClear = viewModel::clearHistory,
        )
    }

    hiltEntry<AppNavKey.FloatBallTranslation> {
        val viewModel: TranslateSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatBallTranslationSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onInstantTranslateChange = viewModel::setInstantTranslate,
            onEngineChange = viewModel::setTranslateEngine,
            onTargetLangChange = viewModel::setTranslateTargetLang,
            onOpenMlKitModels = { ctx.navigate(AppNavKey.TranslateModels) },
            onOpenCloudTranslateSettings = { ctx.navigate(AppNavKey.CloudTranslateSettings) },
            onOpenTranslateAppPicker = { ctx.navigate(AppNavKey.TranslateAppPicker) },
        )
    }

    hiltEntry<AppNavKey.TranslateAppPicker> {
        val viewModel: TranslateSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        TranslateAppPickScreen(
            selectedPackageName = overlaySettings.floatBallTranslateAppPackage,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallTranslation) },
            onSelect = { packageName ->
                viewModel.setTranslateAppPackage(packageName)
                ctx.navigateBackTo(AppNavKey.FloatBallTranslation)
            },
        )
    }

    hiltEntry<AppNavKey.CloudTranslateSettings> {
        val viewModel: CloudTranslateSettingsViewModel = hiltViewModel()
        val remoteModels by viewModel.remoteModels.collectAsStateWithLifecycle()
        val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
        CloudTranslateSettingsScreen(
            vlmConfigManager = viewModel.vlmConfigManager,
            remoteModelsState = remoteModels,
            connectionTestState = connectionTest,
            onLoadRemoteModels = viewModel::loadRemoteModels,
            onTestConnection = viewModel::testConnection,
            onApiCredentialsChanged = viewModel::onApiCredentialsChanged,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallTranslation) },
            onOpenModelPicker = { providerId ->
                ctx.navigate(AppNavKey.CloudTranslateModelPicker(providerId))
            },
        )
    }

    hiltEntry<AppNavKey.CloudTranslateModelPicker> { key ->
        val viewModel: CloudTranslateSettingsViewModel = hiltViewModel()
        val remoteModels by viewModel.remoteModels.collectAsStateWithLifecycle()
        val provider = VlmProvider.fromId(key.providerId)
        CloudTranslateModelPickerScreen(
            provider = provider,
            vlmConfigManager = viewModel.vlmConfigManager,
            remoteModelsState = remoteModels,
            onLoadRemoteModels = { viewModel.loadRemoteModels(provider, false) },
            onSelectModel = { modelId ->
                viewModel.vlmConfigManager.setTranslateModel(provider, modelId)
                ctx.navigateBackTo(AppNavKey.CloudTranslateSettings)
            },
            onBack = { ctx.navigateBackTo(AppNavKey.CloudTranslateSettings) },
        )
    }

    hiltEntry<AppNavKey.TranslateModels> {
        val viewModel: TranslateSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val installedLanguageCodes by viewModel.installedLanguageCodes.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        TranslateModelSettingsScreen(
            settings = settings,
            installedLanguageCodes = installedLanguageCodes,
            downloadState = downloadState,
            translateEngineInstalled = viewModel.translateEngineInstalled,
            translateEngineSizeBytes = viewModel.translateEngineSizeBytes,
            translateEngineVersionState = viewModel.translateEngineVersionState,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallTranslation) },
            onDownloadLanguage = viewModel::downloadLanguage,
            onDeleteLanguage = viewModel::deleteLanguage,
            onDeleteTranslateEngine = viewModel::deleteTranslateEngine,
            onOpenEngineManagement = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
        )
    }

    hiltEntry<AppNavKey.OcrModels> {
        val viewModel: OcrModelSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val installedModelIds by viewModel.installedModelIds.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        OcrModelSettingsScreen(
            settings = settings,
            catalogModels = viewModel.catalogModels,
            installedModelIds = installedModelIds,
            downloadState = downloadState,
            ocrEngineInstalled = viewModel.ocrEngineInstalled,
            ocrEngineSizeBytes = viewModel.ocrEngineSizeBytes,
            ocrEngineVersionState = viewModel.ocrEngineVersionState,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatBallPick) },
            onSelectModel = viewModel::selectModel,
            onClearSelectedModel = viewModel::clearSelectedModel,
            onDownloadModel = viewModel::downloadModel,
            onDeleteModel = viewModel::deleteModel,
            onDeleteOcrEngine = viewModel::deleteOcrEngine,
            onOpenEngineManagement = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
            vlmConfigManager = viewModel.vlmConfigManager,
            onNavigateToVlmSettings = { providerId -> ctx.navigate(AppNavKey.VlmOcrSettings(providerId)) },
            onSelectProviderAndModel = viewModel::selectProviderAndModel,
        )
    }

    hiltEntry<AppNavKey.VlmOcrSettings> { key ->
        val viewModel: OcrModelSettingsViewModel = hiltViewModel()
        VlmOcrSettingsScreen(
            vlmConfigManager = viewModel.vlmConfigManager,
            providerId = key.providerId,
            onBack = { ctx.navigateBackTo(AppNavKey.OcrModels) },
            onUpdateVlmConfig = { apiKey, baseUrl, model, promptEnabled, promptDraft ->
                viewModel.updateVlmConfig(apiKey, baseUrl, model, promptEnabled, promptDraft)
            },
            onUpdateProviderConfig = { provider, apiKey, baseUrl, model, promptEnabled, promptDraft ->
                viewModel.updateProviderConfig(provider, apiKey, baseUrl, model, promptEnabled, promptDraft)
            },
            onNotifyWarning = viewModel::showWarning,
        )
    }
}
