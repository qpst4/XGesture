package com.slideindex.app.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import com.slideindex.app.ui.CloudBackupScreen
import com.slideindex.app.ui.CloudStorageConfigEditorScreen
import com.slideindex.app.ui.CloudStorageSettingsScreen
import com.slideindex.app.ui.DiagnosticLogDetailScreen
import com.slideindex.app.ui.DiagnosticLogListScreen
import com.slideindex.app.ui.ExtensionAboutScreen
import com.slideindex.app.ui.ExtensionHubScreen
import com.slideindex.app.ui.ExternalInvocationHelpScreen
import com.slideindex.app.ui.FreezerAppsPickerScreen
import com.slideindex.app.ui.FreezerHomeScreen
import com.slideindex.app.ui.LicenseTextScreen
import com.slideindex.app.ui.LauncherShortcutMenuScreen
import com.slideindex.app.ui.MissingGesturePermissionsScreen
import com.slideindex.app.ui.PrivacyPolicyScreen
import com.slideindex.app.ui.SettingsBackupScreen
import com.slideindex.app.ui.ThirdPartyNoticesScreen
import com.slideindex.app.ui.viewmodel.CloudBackupViewModel
import com.slideindex.app.ui.viewmodel.CloudStorageSettingsViewModel
import com.slideindex.app.ui.viewmodel.DiagnosticLogViewModel
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel

fun NavEntryBuilder.extensionHubNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.ExtensionHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: ExtensionHubViewModel = hiltViewModel()
        val hubSettings by viewModel.extensionHubSettings.collectAsStateWithLifecycle()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val stashEntryCount by viewModel.stashEntryCount.collectAsStateWithLifecycle()
        ExtensionHubScreen(
            settings = hubSettings,
            gestureActive = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            stashEntryCount = stashEntryCount,
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
            onOpenLayoutSettings = { ctx.navigate(AppNavKey.HomeLayout) },
            onOpenQuickLauncher = { ctx.navigate(AppNavKey.QuickLauncher) },
            onOpenQuickWheel = { ctx.navigate(AppNavKey.QuickWheelList) },
            onOpenHoneycombLauncher = { ctx.navigate(AppNavKey.HoneycombLauncher) },
            onOpenRingLauncher = { ctx.navigate(AppNavKey.RingLauncherSettings) },
            onOpenHolographicLauncher = { ctx.navigate(AppNavKey.HolographicLauncherSettings) },
            onOpenActivityShortcuts = { ctx.navigate(AppNavKey.ActivityShortcuts) },
            onOpenExternalInvocations = { ctx.navigate(AppNavKey.ExtensionExternalInvocations) },
            onOpenShellCommands = { ctx.navigate(AppNavKey.ShellCommands) },
            onOpenWidgetPanel = { ctx.navigate(AppNavKey.WidgetPanel) },
            onOpenStashClipboard = { ctx.navigate(AppNavKey.StashClipboard) },
            onOpenFreezer = { ctx.navigate(AppNavKey.ExtensionFreezer) },
            onOpenSettingsBackup = { ctx.navigate(AppNavKey.ExtensionBackup) },
            onOpenNativeEnginePacks = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onOpenDiagnosticLogs = { ctx.navigate(AppNavKey.ExtensionDiagnosticLogs) },
            onOpenAbout = { ctx.navigate(AppNavKey.ExtensionAbout) },
        )
    }

    hiltEntry<AppNavKey.ExtensionFreezer> {
        FreezerHomeScreen(
            settingsRepository = ctx.deps.settingsRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenManageApps = { ctx.navigate(AppNavKey.ExtensionFreezerApps) },
        )
    }

    hiltEntry<AppNavKey.ExtensionFreezerApps> {
        FreezerAppsPickerScreen(
            settingsRepository = ctx.deps.settingsRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionFreezer) },
        )
    }

    hiltEntry<AppNavKey.ExtensionExternalInvocations> {
        ExternalInvocationHelpScreen(
            onOpenLauncherShortcutMenu = { ctx.navigate(AppNavKey.ExtensionLauncherShortcutMenu) },
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    hiltEntry<AppNavKey.ExtensionLauncherShortcutMenu> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        LauncherShortcutMenuScreen(
            launcherShortcutOrder = settings.launcherShortcutMenuOrder,
            launcherShortcutDisabled = settings.launcherShortcutMenuDisabled,
            onLauncherShortcutOrderChange = viewModel::setLauncherShortcutMenuOrder,
            onLauncherShortcutDisabledChange = viewModel::setLauncherShortcutMenuDisabled,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionExternalInvocations) },
        )
    }

    hiltEntry<AppNavKey.ExtensionAbout> {
        val updateViewModel: com.slideindex.app.update.UpdateViewModel = hiltViewModel(ctx.activity)
        val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
        ExtensionAboutScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPrivacyPolicy = { ctx.navigate(AppNavKey.ExtensionPrivacy) },
            onOpenThirdPartyNotices = { ctx.navigate(AppNavKey.ExtensionThirdPartyNotices) },
            onCheckUpdate = updateViewModel::checkManually,
            autoCheckUpdate = updateUiState.autoCheckUpdate,
            onAutoCheckUpdateChange = updateViewModel::setAutoCheckUpdate,
        )
    }

    hiltEntry<AppNavKey.ExtensionDiagnosticLogs> {
        val viewModel: DiagnosticLogViewModel = hiltViewModel()
        DiagnosticLogListScreen(
            viewModel = viewModel,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenDetail = { fileName ->
                ctx.navigate(AppNavKey.ExtensionDiagnosticLogDetail(fileName))
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionDiagnosticLogDetail> { key ->
        val viewModel: DiagnosticLogViewModel = hiltViewModel()
        DiagnosticLogDetailScreen(
            fileName = key.fileName,
            viewModel = viewModel,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionDiagnosticLogs) },
        )
    }

    hiltEntry<AppNavKey.ExtensionThirdPartyNotices> {
        ThirdPartyNoticesScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionAbout) },
            onOpenLicenseText = { fileName ->
                ctx.navigate(AppNavKey.ExtensionLicenseText(fileName))
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionLicenseText> { key ->
        LicenseTextScreen(
            assetFileName = key.assetFileName,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionThirdPartyNotices) },
        )
    }

    hiltEntry<AppNavKey.ExtensionPrivacy> {
        PrivacyPolicyScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackup> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val importPreviewState by viewModel.importPreviewState.collectAsStateWithLifecycle()
        val navigateToMissingPermissions by viewModel.navigateToMissingPermissions.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var missingCount by remember {
            mutableIntStateOf(GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, settings) {
            missingCount = GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    missingCount = GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        LaunchedEffect(navigateToMissingPermissions) {
            if (navigateToMissingPermissions) {
                ctx.navigate(AppNavKey.ExtensionMissingPermissions)
                viewModel.consumeNavigateToMissingPermissions()
            }
        }
        SettingsBackupScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onExport = viewModel::exportSettings,
            onImport = viewModel::previewImport,
            importPreviewState = importPreviewState,
            onDismissPreview = viewModel::dismissPreview,
            onConfirmImport = viewModel::confirmImport,
            missingPermissionCount = missingCount,
            onOpenMissingPermissions = { ctx.navigate(AppNavKey.ExtensionMissingPermissions) },
            onOpenCloudBackup = { ctx.navigate(AppNavKey.ExtensionCloudBackup) },
        )
    }

    hiltEntry<AppNavKey.ExtensionCloudBackup> {
        val viewModel: CloudBackupViewModel = hiltViewModel()
        val cloudSettings by viewModel.cloudSettings.collectAsStateWithLifecycle()
        val backups by viewModel.backups.collectAsStateWithLifecycle()
        val loadingBackups by viewModel.loadingBackups.collectAsStateWithLifecycle()
        val busy by viewModel.busy.collectAsStateWithLifecycle()
        val progress by viewModel.progress.collectAsStateWithLifecycle()
        val includeSensitiveData by viewModel.includeSensitiveData.collectAsStateWithLifecycle()
        val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
        val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()

        // 进页面就拉一次远端列表，避免用户看到空列表以为备份丢了
        LaunchedEffect(Unit) { viewModel.refresh() }

        CloudBackupScreen(
            settings = cloudSettings,
            backups = backups,
            loadingBackups = loadingBackups,
            busy = busy,
            progress = progress,
            includeSensitiveData = includeSensitiveData,
            restorePreview = restorePreview,
            pendingDelete = pendingDelete,
            onRefresh = viewModel::refresh,
            onBackupNow = viewModel::backupNow,
            onSetIncludeSensitive = viewModel::setIncludeSensitiveData,
            onRestore = viewModel::startRestore,
            onConfirmRestore = viewModel::confirmRestore,
            onDismissRestore = viewModel::dismissRestorePreview,
            onRequestDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onPruneNow = viewModel::pruneNow,
            onCancel = viewModel::cancelRunning,
            onOpenStorageSettings = { ctx.navigate(AppNavKey.ExtensionCloudStorageSettings) },
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionBackup) },
        )
    }

    hiltEntry<AppNavKey.ExtensionCloudStorageSettings> {
        val viewModel: CloudStorageSettingsViewModel = hiltViewModel()
        val cloudSettings by viewModel.cloudSettings.collectAsStateWithLifecycle()

        CloudStorageSettingsScreen(
            settings = cloudSettings,
            onOpenEditor = { configId -> ctx.navigate(AppNavKey.ExtensionCloudStorageEditor(configId)) },
            onSetRetention = viewModel::setRetentionCount,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionCloudBackup) },
        )
    }

    hiltEntry<AppNavKey.ExtensionCloudStorageEditor> { key ->
        val viewModel: CloudStorageSettingsViewModel = hiltViewModel()
        val cloudSettings by viewModel.cloudSettings.collectAsStateWithLifecycle()
        val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
        // 配置从 DataStore 流出，首帧可能还没有：编辑已有配置时必须等它到位，
        // 否则会先渲染一份空表单（编辑器以 id 为种子 key，配置到达后会自行重填）。
        val initial = cloudSettings.configs.firstOrNull { it.id == key.configId }

        CloudStorageConfigEditorScreen(
            initial = initial,
            isActiveTarget = cloudSettings.activeConfigId == key.configId,
            connectionTest = connectionTest,
            onTestConnection = viewModel::testConnection,
            onDismissConnectionTest = viewModel::dismissConnectionTest,
            onSave = { config, makeActive ->
                viewModel.upsertConfig(config)
                if (makeActive) viewModel.setActiveConfig(config.id)
                ctx.navigateBackTo(AppNavKey.ExtensionCloudStorageSettings)
            },
            onRemove = { configId ->
                viewModel.removeConfig(configId)
                ctx.navigateBackTo(AppNavKey.ExtensionCloudStorageSettings)
            },
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionCloudStorageSettings) },
        )
    }

    hiltEntry<AppNavKey.ExtensionMissingPermissions> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MissingGesturePermissionsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionBackup) },
        )
    }
}
