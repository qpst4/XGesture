package com.slideindex.app.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutFileSupport
import com.slideindex.app.activity.ActivityShortcutImageSupport
import com.slideindex.app.activity.ActivityShortcutLauncher
import com.slideindex.app.activity.ActivityShortcutShellIconBridge
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.activity.findForQuickLauncherItem
import com.slideindex.app.activity.toQuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.ActivityShortcutEditFileScreen
import com.slideindex.app.ui.ActivityShortcutEditImageScreen
import com.slideindex.app.ui.ActivityShortcutEditScreen
import com.slideindex.app.ui.ActivityShortcutPresetsScreen
import com.slideindex.app.ui.ActivityShortcutScreen
import com.slideindex.app.ui.DynamicShortcutImportScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppShortcutScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickFileAppScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickShellScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.viewmodel.ActivityShortcutPickerViewModel
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.activityShortcutNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.ActivityShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveShortcuts = viewModel::setActivityShortcuts,
            onAdd = { ctx.navigate(AppNavKey.ActivityShortcutPickApp) },
            onAddAppShortcut = { ctx.navigate(AppNavKey.ActivityShortcutPickAppShortcut) },
            onAddShellCommand = { ctx.navigate(AppNavKey.ActivityShortcutPickShell) },
            onAddImage = { ctx.navigate(AppNavKey.ActivityShortcutAddImage) },
            onAddFile = { ctx.navigate(AppNavKey.ActivityShortcutAddFile) },
            onImportDynamicShortcuts = { ctx.navigate(AppNavKey.DynamicShortcutImport) },
            onOpenPresets = { ctx.navigate(AppNavKey.ActivityShortcutPresets) },
            onEdit = { shortcut ->
                pickerViewModel.consume()
                if (ActivityShortcutImageSupport.isImageShortcut(shortcut)) {
                    ctx.navigate(AppNavKey.ActivityShortcutEditImage(shortcut.id))
                } else if (ActivityShortcutFileSupport.isFileShortcut(shortcut)) {
                    ctx.navigate(AppNavKey.ActivityShortcutEditFile(shortcut.id))
                } else {
                    ctx.navigate(AppNavKey.ActivityShortcutEdit(shortcut.id))
                }
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutEdit> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val pickerResult by pickerViewModel.result.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val shortcut = settings.activityShortcuts.firstOrNull { it.id == key.shortcutId }
        if (shortcut == null) {
            LaunchedEffect(key.shortcutId) {
                Toast.makeText(
                    ctx.activity,
                    R.string.activity_shortcut_entry_missing,
                    Toast.LENGTH_SHORT,
                ).show()
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            }
        } else {
            ActivityShortcutEditScreen(
                shortcut = shortcut,
                allShortcuts = settings.activityShortcuts,
                pickerResult = pickerResult,
                onPickerResultConsumed = pickerViewModel::consume,
                onPickApp = {
                    ctx.navigate(
                        AppNavKey.ActivityShortcutEditPickApp(shortcutId = shortcut.id),
                    )
                },
                onPickActivity = { packageName ->
                    ctx.navigate(
                        AppNavKey.ActivityShortcutEditPickActivity(
                            shortcutId = shortcut.id,
                            packageName = packageName,
                        ),
                    )
                },
                onPickIconApp = {
                    ctx.navigate(
                        AppNavKey.ActivityShortcutEditPickIconApp(shortcutId = shortcut.id),
                    )
                },
                onTest = { ActivityShortcutLauncher.launch(ctx.activity, it, settings) },
                onBack = {
                    pickerViewModel.consume()
                    ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
                },
                onSave = { updated ->
                    pickerViewModel.consume()
                    viewModel.setActivityShortcuts(updated)
                    ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
                },
            )
        }
    }

    hiltEntry<AppNavKey.ActivityShortcutEditPickApp> { key ->
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcutEdit(key.shortcutId)) },
            onSelectApp = { app ->
                pickerViewModel.pickPackage(app.packageName)
                ctx.navigate(
                    AppNavKey.ActivityShortcutEditPickActivity(
                        shortcutId = key.shortcutId,
                        packageName = app.packageName,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutEditPickActivity> { key ->
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcutEdit(key.shortcutId)) },
            onSelectActivity = { activity ->
                pickerViewModel.pickActivity(key.packageName, activity.className)
                ctx.navigateBackTo(AppNavKey.ActivityShortcutEdit(key.shortcutId))
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutEditPickIconApp> { key ->
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcutEdit(key.shortcutId)) },
            onSelectApp = { app ->
                pickerViewModel.pickIconPackage(app.packageName)
                ctx.navigateBackTo(AppNavKey.ActivityShortcutEdit(key.shortcutId))
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickShell> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickShellScreen(
            shellCommands = settings.shellCommands,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onPick = { cmd ->
                val shortcut = ActivityShortcutShellIconBridge.withCopiedIcon(ctx.activity, cmd)
                if (settings.activityShortcuts.none { it.identityKey() == shortcut.identityKey() }) {
                    viewModel.setActivityShortcuts(settings.activityShortcuts + shortcut)
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPresets> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPresetsScreen(
            settings = settings,
            shortcuts = settings.activityShortcuts,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSaveShortcuts = viewModel::setActivityShortcuts,
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickApp> {
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.ActivityShortcutPickActivity(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickAppShortcut> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickAppShortcutScreen(
            existingIdentityKeys = settings.activityShortcuts.map { it.identityKey() }.toSet(),
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.ActivityShortcutPickAppShortcutPresets) },
            onAddShortcut = { shortcut ->
                if (settings.activityShortcuts.none { it.identityKey() == shortcut.identityKey() }) {
                    viewModel.setActivityShortcuts(settings.activityShortcuts + shortcut)
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickAppShortcutPresets> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val configuredShortcutKeys = remember(settings.activityShortcuts) {
            settings.activityShortcuts.mapNotNull { shortcut ->
                com.slideindex.app.launcher.QuickLauncherItemCodec.shortcutItemKey(
                    shortcut.toQuickLauncherItem(),
                )
            }.toSet()
        }
        val returnKey = AppNavKey.ActivityShortcutPickAppShortcut
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                val existing = settings.activityShortcuts.findForQuickLauncherItem(item)
                if (added) {
                    if (existing != null) {
                        viewModel.setActivityShortcuts(settings.activityShortcuts - existing)
                    }
                } else {
                    val shortcut = activityShortcutFromQuickLauncherItem(item)
                    if (shortcut != null && existing == null) {
                        viewModel.setActivityShortcuts(settings.activityShortcuts + shortcut)
                    }
                }
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val candidate = ActivityShortcut.component(
                    label = activity.label,
                    packageName = activity.packageName,
                    activityClassName = activity.className,
                )
                val duplicate = settings.activityShortcuts.any {
                    it.identityKey() == candidate.identityKey()
                }
                if (!duplicate) {
                    viewModel.setActivityShortcuts(
                        settings.activityShortcuts + candidate,
                    )
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutAddImage> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutEditImageScreen(
            initialShortcut = null,
            allShortcuts = settings.activityShortcuts,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSave = { updated ->
                viewModel.setActivityShortcuts(updated)
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutEditImage> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val shortcut = settings.activityShortcuts.firstOrNull { it.id == key.shortcutId }
        if (shortcut == null) {
            LaunchedEffect(key.shortcutId) {
                Toast.makeText(ctx.activity, R.string.activity_shortcut_entry_missing, Toast.LENGTH_SHORT).show()
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            }
        } else {
            ActivityShortcutEditImageScreen(
                initialShortcut = shortcut,
                allShortcuts = settings.activityShortcuts,
                onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
                onSave = { updated ->
                    viewModel.setActivityShortcuts(updated)
                    ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
                },
            )
        }
    }

    hiltEntry<AppNavKey.ActivityShortcutAddFile> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val pickerResult by pickerViewModel.result.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutEditFileScreen(
            initialShortcut = null,
            allShortcuts = settings.activityShortcuts,
            pickedPackageName = pickerResult?.packageName,
            pickedActivityName = pickerResult?.activityClassName,
            pickedAppLabel = pickerResult?.appLabel,
            onPickerResultConsumed = pickerViewModel::consume,
            onPickApp = { mimeType, isShareMode ->
                ctx.navigate(AppNavKey.ActivityShortcutPickFileApp(mimeType, isShareMode))
            },
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSave = { updated ->
                viewModel.setActivityShortcuts(updated)
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutEditFile> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val pickerResult by pickerViewModel.result.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val shortcut = settings.activityShortcuts.firstOrNull { it.id == key.shortcutId }
        if (shortcut == null) {
            LaunchedEffect(key.shortcutId) {
                Toast.makeText(ctx.activity, R.string.activity_shortcut_entry_missing, Toast.LENGTH_SHORT).show()
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            }
        } else {
            ActivityShortcutEditFileScreen(
                initialShortcut = shortcut,
                allShortcuts = settings.activityShortcuts,
                pickedPackageName = pickerResult?.packageName,
                pickedActivityName = pickerResult?.activityClassName,
                pickedAppLabel = pickerResult?.appLabel,
                onPickerResultConsumed = pickerViewModel::consume,
                onPickApp = { mimeType, isShareMode ->
                    ctx.navigate(AppNavKey.ActivityShortcutPickFileApp(mimeType, isShareMode))
                },
                onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
                onSave = { updated ->
                    viewModel.setActivityShortcuts(updated)
                    ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
                },
            )
        }
    }

    hiltEntry<AppNavKey.ActivityShortcutPickFileApp> { key ->
        val pickerViewModel: ActivityShortcutPickerViewModel = hiltViewModel(ctx.activity)
        ActivityShortcutPickFileAppScreen(
            mimeType = key.mimeType,
            isShareMode = key.isShareMode,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectApp = { packageName, activityClassName, appLabel ->
                pickerViewModel.pickTargetApp(packageName, activityClassName, appLabel)
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.DynamicShortcutImport> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        DynamicShortcutImportScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSaveShortcuts = viewModel::setActivityShortcuts,
        )
    }


}
