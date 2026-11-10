package com.slideindex.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutCatalog
import com.slideindex.app.activity.ActivityShortcutKind
import com.slideindex.app.activity.ActivityShortcutLauncher
import com.slideindex.app.activity.ActivityShortcutPreset
import com.slideindex.app.activity.ManagedShortcutIconResolver
import com.slideindex.app.activity.ShortcutIconPackBridge
import com.slideindex.app.activity.ShortcutIconStorage
import com.slideindex.app.activity.subtitleDetail
import com.slideindex.app.iconpack.IconPackTarget
import com.slideindex.app.service.IconPackPickerTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.util.PackageActivityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit,
    onAdd: () -> Unit,
    onAddAppShortcut: () -> Unit,
    onAddShellCommand: () -> Unit,
    onOpenPresets: () -> Unit,
    onEdit: (ActivityShortcut) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appRepository = rememberAppRepository()
    var shortcuts by remember(settings.activityShortcuts) { mutableStateOf(settings.activityShortcuts) }
    var pendingDelete by remember { mutableStateOf<ActivityShortcut?>(null) }
    var changingIconShortcut by remember { mutableStateOf<ActivityShortcut?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    fun persist(items: List<ActivityShortcut>) {
        shortcuts = items
        onSaveShortcuts(items)
    }

    val pickIconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val target = changingIconShortcut
        changingIconShortcut = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                ShortcutIconStorage.saveIconFromUri(context, uri)
            } ?: return@launch
            withContext(Dispatchers.IO) {
                ShortcutIconStorage.deleteIconIfOwned(context, target.iconPath)
            }
            persist(
                shortcuts.map { shortcut ->
                    if (shortcut.id == target.id) shortcut.copy(iconPath = path) else shortcut
                }
            )
        }
    }

    // 「从图标包选择」：选择页回传的是全局图标目录里的 PNG，复制进 shortcut_icons 再用
    // （快捷方式的 iconPath 只认这个目录，见 ShortcutIconStorage.loadBitmap）。
    val packIconLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = changingIconShortcut
        changingIconShortcut = null
        if (result.resultCode != Activity.RESULT_OK || target == null) {
            return@rememberLauncherForActivityResult
        }
        val picked = result.data?.getStringExtra(IconPackPickerTrampolineActivity.EXTRA_PICKED_PATH)
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                ShortcutIconPackBridge.importPickedIcon(context, target.id, picked)
            } ?: return@launch
            withContext(Dispatchers.IO) {
                ShortcutIconStorage.deleteIconIfOwned(context, target.iconPath)
            }
            persist(
                shortcuts.map { shortcut ->
                    if (shortcut.id == target.id) shortcut.copy(iconPath = path) else shortcut
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!appRepository.hasCachedApps()) {
            appRepository.loadApps()
        }
    }

    fun launchShortcut(shortcut: ActivityShortcut) {
        ActivityShortcutLauncher.launch(context, shortcut, settings)
    }

    ActivityShortcutDeleteDialog(
        target = pendingDelete,
        onDismiss = { pendingDelete = null },
        onConfirm = { target ->
            ShortcutIconStorage.deleteIconIfOwned(context, target.iconPath)
            persist(shortcuts.filter { it.id != target.id })
            pendingDelete = null
        }
    )

    ActivityShortcutAddBottomSheet(
        show = showAddSheet,
        onDismiss = { showAddSheet = false },
        onOpenPresets = {
            showAddSheet = false
            onOpenPresets()
        },
        onBrowseActivity = {
            showAddSheet = false
            onAdd()
        },
        onAddAppShortcut = {
            showAddSheet = false
            onAddAppShortcut()
        },
        onAddShellCommand = {
            showAddSheet = false
            onAddShellCommand()
        }
    )

    val shortcutHint = stringResource(R.string.activity_shortcut_hint)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_title),
        pageHint = stringResource(R.string.activity_shortcut_entry_desc),
        onBack = onBack,
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = { showAddSheet = true },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.activity_shortcut_add)
            )
        },
        fabInset = MiuixSettingsFabInset
    ) {
        settingsLazyTipCard(key = "hint", text = shortcutHint)

        item(key = "mine-title") {
            SmallTitle(
                text = stringResource(R.string.activity_shortcut_mine_title),
                modifier = Modifier
                    .fillMaxWidth()
                    
            )
        }

        activityShortcutMineListItems(
            context = context,
            shortcuts = shortcuts,
            onLaunch = ::launchShortcut,
            onEdit = onEdit,
            onChangeIcon = {
                changingIconShortcut = it
                pickIconLauncher.launch("image/*")
            },
            onChangeIconFromPack = { target ->
                changingIconShortcut = target
                packIconLauncher.launch(
                    IconPackPickerTrampolineActivity.createIntent(
                        context = context,
                        target = IconPackTarget(
                            packageName = target.packageName.trim(),
                            activityName = target.activityClassName.trim()
                                .takeIf { it.isNotBlank() },
                            label = target.label,
                        ),
                        storageKey = ShortcutIconPackBridge.storageKey(target.id),
                    ),
                )
            },
            onResetIcon = { target ->
                ShortcutIconStorage.deleteIconIfOwned(context, target.iconPath)
                persist(
                    shortcuts.map { shortcut ->
                        if (shortcut.id == target.id) shortcut.copy(iconPath = null) else shortcut
                    }
                )
            },
            onDelete = { pendingDelete = it }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutPresetsScreen(
    settings: AppSettings,
    shortcuts: List<ActivityShortcut>,
    onBack: () -> Unit,
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var localShortcuts by remember(shortcuts) { mutableStateOf(shortcuts) }
    val presets = remember { ActivityShortcutCatalog.presets() }

    LaunchedEffect(Unit) {
        if (!appRepository.hasCachedApps()) {
            appRepository.loadApps()
        }
    }

    fun persist(items: List<ActivityShortcut>) {
        localShortcuts = items
        onSaveShortcuts(items)
    }

    fun addShortcut(shortcut: ActivityShortcut) {
        if (localShortcuts.any { it.identityKey() == shortcut.identityKey() }) {
            return
        }
        persist(localShortcuts + shortcut)
    }

    fun launchShortcut(shortcut: ActivityShortcut) {
        ActivityShortcutLauncher.launch(context, shortcut, settings)
    }

    val presetsDesc = stringResource(R.string.activity_shortcut_presets_desc)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_presets_title),
        pageHint = stringResource(R.string.activity_shortcut_presets_desc),
        onBack = onBack
    ) {
        settingsLazyTipCard(key = "presets-hint", text = presetsDesc)

        if (presets.isEmpty()) {
            item(key = "presets-empty") {
                Text(
                    text = stringResource(R.string.activity_shortcut_empty),
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )
            }
        } else {
            groupedCardItems(
                keyPrefix = "activity-shortcut-presets",
                items = presets.map { preset ->
                    val saved = localShortcuts.any {
                        it.identityKey() == preset.toShortcut(context).identityKey()
                    }
                    CardItem("${preset.packageName}/${preset.activityClassName}") {
                        ActivityShortcutPresetRowContent(
                            preset = preset,
                            saved = saved,
                            onLaunch = { launchShortcut(preset.toShortcut(context)) },
                            onAdd = { addShortcut(preset.toShortcut(context)) }
                        )
                    }
                }
            )
        }
    }
}

private fun LazyListScope.activityShortcutMineListItems(
    context: android.content.Context,
    shortcuts: List<ActivityShortcut>,
    onLaunch: (ActivityShortcut) -> Unit,
    onEdit: (ActivityShortcut) -> Unit,
    onChangeIcon: (ActivityShortcut) -> Unit,
    onChangeIconFromPack: (ActivityShortcut) -> Unit,
    onResetIcon: (ActivityShortcut) -> Unit,
    onDelete: (ActivityShortcut) -> Unit
) {
    if (shortcuts.isEmpty()) {
        groupedCardItems(
            keyPrefix = "activity-shortcut-mine-empty",
            items = listOf(
                CardItem("empty") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.activity_shortcut_empty),
                            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        Text(
                            text = stringResource(R.string.activity_shortcut_empty_add_hint),
                            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            )
        )
        return
    }
    groupedCardItems(
        keyPrefix = "activity-shortcut-mine",
        items = shortcuts.map { shortcut ->
            CardItem(shortcut.id) {
                val appLabel = remember(shortcut.packageName) {
                    resolveAppLabel(context, shortcut.packageName)
                }
                val exported = remember(shortcut.packageName, shortcut.activityClassName, shortcut.kind) {
                    if (shortcut.kind != ActivityShortcutKind.COMPONENT) {
                        true
                    } else {
                        PackageActivityResolver.isActivityExported(
                            context,
                            shortcut.packageName,
                            shortcut.activityClassName
                        )
                    }
                }
                ActivityShortcutSavedRowContent(
                    shortcut = shortcut,
                    appLabel = appLabel,
                    exported = exported,
                    onLaunch = { onLaunch(shortcut) },
                    onEdit = { onEdit(shortcut) },
                    onChangeIcon = { onChangeIcon(shortcut) },
                    onChangeIconFromPack = { onChangeIconFromPack(shortcut) },
                    onResetIcon = { onResetIcon(shortcut) },
                    onDelete = { onDelete(shortcut) }
                )
            }
        }
    )
}

@Composable
private fun ActivityShortcutPresetRowContent(
    preset: ActivityShortcutPreset,
    saved: Boolean,
    onLaunch: () -> Unit,
    onAdd: () -> Unit
) {
    val context = LocalContext.current
    val appLabel = remember(preset.packageName) {
        resolveAppLabel(context, preset.packageName)
    }
    val presetLabel = preset.label(context)
    val title = if (appLabel.isNotBlank()) {
        "$appLabel·$presetLabel"
    } else {
        presetLabel
    }
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        summary = preset.activityClassName,
        onClick = onLaunch,
        startAction = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Launch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        endActions = {
            TextButton(onClick = onAdd, enabled = !saved) {
                Text(
                    text = stringResource(
                        if (saved) {
                            R.string.activity_shortcut_preset_added
                        } else {
                            R.string.activity_shortcut_preset_add
                        }
                    )
                )
            }
        }
    )
}

@Composable
private fun ActivityShortcutSavedRowContent(
    shortcut: ActivityShortcut,
    appLabel: String,
    exported: Boolean,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onChangeIcon: () -> Unit,
    onChangeIconFromPack: () -> Unit,
    onResetIcon: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val openText = stringResource(R.string.activity_shortcut_open)
    val changeIconText = stringResource(R.string.activity_shortcut_change_icon)
    val iconFromPackText = stringResource(R.string.activity_shortcut_icon_from_icon_pack)
    val resetIconText = stringResource(R.string.activity_shortcut_reset_icon)
    val deleteText = stringResource(R.string.stash_action_delete)
    val moreMenuDesc = stringResource(R.string.notification_filter_more_menu)
    val menuEntry = DropdownEntry(
        items = buildList {
            add(DropdownItem(text = changeIconText, onClick = onChangeIcon))
            add(DropdownItem(text = iconFromPackText, onClick = onChangeIconFromPack))
            if (!shortcut.iconPath.isNullOrBlank()) {
                add(DropdownItem(text = resetIconText, onClick = onResetIcon))
            }
            add(DropdownItem(text = deleteText, onClick = onDelete))
        }
    )
    val title = if (appLabel.isNotBlank()) {
        "$appLabel·${shortcut.label}"
    } else {
        shortcut.label
    }
    val subtitle = buildString {
        append(shortcut.subtitleDetail())
        if (!exported) {
            append(" · ")
            append(stringResource(R.string.activity_shortcut_not_exported))
        }
    }
    val iconBitmap = remember(
        shortcut.id,
        shortcut.iconPath,
        shortcut.identityKey(),
        // 宿主应用的全局图标覆盖变了，快捷方式回退到应用图标时要跟着换
        com.slideindex.app.ui.GlobalIconStore.revision,
    ) {
        ManagedShortcutIconResolver.drawableForManaged(context, shortcut)
            ?.toBitmap(96, 96)
    }
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        summary = subtitle,
        onClick = onEdit,
        startAction = {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                MiuixIcon(
                    imageVector = when (shortcut.kind) {
                        ActivityShortcutKind.COMPONENT -> Icons.AutoMirrored.Filled.Launch
                        else -> Icons.AutoMirrored.Filled.Shortcut
                    },
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        },
        endActions = {
            IconButton(onClick = onLaunch) {
                MiuixIcon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = openText,
                    tint = MiuixTheme.colorScheme.primary
                )
            }
            OverlayIconDropdownMenu(entry = menuEntry) {
                MiuixIcon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = moreMenuDesc,
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        }
    )
}

@Composable
private fun ActivityShortcutAddBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenPresets: () -> Unit,
    onBrowseActivity: () -> Unit,
    onAddAppShortcut: () -> Unit,
    onAddShellCommand: () -> Unit
) {
    MiuixBottomSheet(
        show = show,
        title = stringResource(R.string.activity_shortcut_add),
        onDismissRequest = onDismiss
    ) {
        top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            ArrowPreference(
                title = stringResource(R.string.activity_shortcut_add_from_presets),
                summary = stringResource(R.string.activity_shortcut_add_from_presets_sub),
                onClick = onOpenPresets
            )
            ArrowPreference(
                title = stringResource(R.string.activity_shortcut_browse_custom),
                summary = stringResource(R.string.activity_shortcut_browse_custom_sub),
                onClick = onBrowseActivity
            )
            ArrowPreference(
                title = stringResource(R.string.activity_shortcut_add_from_shortcuts),
                summary = stringResource(R.string.activity_shortcut_add_from_shortcuts_sub),
                onClick = onAddAppShortcut
            )
            ArrowPreference(
                title = stringResource(R.string.activity_shortcut_add_from_shell),
                summary = stringResource(R.string.activity_shortcut_add_from_shell_sub),
                onClick = onAddShellCommand
            )
        }
    }
}

@Composable
private fun ActivityShortcutDeleteDialog(
    target: ActivityShortcut?,
    onDismiss: () -> Unit,
    onConfirm: (ActivityShortcut) -> Unit
) {
    MiuixConfirmDialog(
        show = target != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.activity_shortcut_delete_title),
        message = target?.let {
            stringResource(R.string.activity_shortcut_delete_message, it.label)
        },
        confirmText = stringResource(R.string.search_engine_delete_confirm),
        onConfirm = {
            target?.let(onConfirm)
        }
    )
}

private fun resolveAppLabel(context: android.content.Context, packageName: String): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault("")

@Composable
fun SettingsCardScope.ActivityShortcutEntryCard(
    shortcutCount: Int,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.activityShortcut(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.activity_shortcut_entry_title),
        subtitle = if (shortcutCount > 0) {
            stringResource(R.string.activity_shortcut_entry_desc_count, shortcutCount)
        } else {
            stringResource(R.string.activity_shortcut_entry_desc)
        },
        onClick = onClick
    )
}
