package com.slideindex.app.freezer

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.settings.FreezerWorkMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixExpandableSearchBottomContent
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreezerPanelContent(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onManageApps: (() -> Unit)? = null,
    onAppLaunched: (() -> Unit)? = null,
    overlayMode: Boolean = false,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot()
    )
    val appRepository = rememberAppRepository()
    var memberApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var freezeStateRevision by remember { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val screenTitle = title ?: stringResource(R.string.extension_freezer_title)
    val unfreezeAllLabel = stringResource(R.string.freezer_batch_unfreeze_all)
    val restoreIntentsLabel = stringResource(R.string.freezer_restore_intents)
    val pauseAllLabel = stringResource(R.string.freezer_pause_all)
    val unpauseAllLabel = stringResource(R.string.freezer_unpause_all)
    val importFrozenLabel = stringResource(R.string.freezer_import_frozen_apps)
    val switchWorkModeLabel = stringResource(
        if (settings.freezerWorkMode.isPause) {
            R.string.freezer_work_mode_switch_to_freeze
        } else {
            R.string.freezer_work_mode_switch_to_pause
        }
    )
    val switchWorkMode = {
        scope.launch {
            settingsRepository.setFreezerWorkMode(
                if (settings.freezerWorkMode.isPause) {
                    FreezerWorkMode.FREEZE.id
                } else {
                    FreezerWorkMode.PAUSE.id
                }
            )
        }
        Unit
    }

    LaunchedEffect(settings.freezerAppPackages) {
        if (settings.freezerAppPackages.isEmpty()) {
            memberApps = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        if (memberApps.isEmpty()) {
            isLoading = true
        }
        memberApps = appRepository.resolveFreezerMembers(settings.freezerAppPackages)
        isLoading = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                freezeStateRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun importFrozenApps() {
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                FreezerListOperations.importFrozenApps(context, settingsRepository)
            }
            val message = if (count > 0) {
                resources.getQuantityString(R.plurals.freezer_import_frozen_done, count, count)
            } else {
                resources.getString(R.string.freezer_import_frozen_none)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (count > 0) {
                freezeStateRevision++
            }
        }
    }

    val handleBack: () -> Unit = {
        if (
            onBack != null &&
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it }
            )
        ) {
            onBack()
        }
    }

    val batchRestore: suspend () -> Int = {
        FreezerOperations.restoreIntents(
            context = context,
            packages = settings.freezerAppPackages,
            fallbackPause = settings.freezerWorkMode.isPause,
        )
    }

    val overflowMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = importFrozenLabel,
                onClick = { importFrozenApps() }
            ),
            DropdownItem(
                text = switchWorkModeLabel,
                onClick = { switchWorkMode() }
            ),
            DropdownItem(
                text = restoreIntentsLabel,
                onClick = {
                    scope.launch {
                        if (batchRestore() > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            ),
            DropdownItem(
                text = pauseAllLabel,
                onClick = {
                    scope.launch {
                        if (FreezerOperations.pauseAll(context, settings.freezerAppPackages) > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            ),
            DropdownItem(
                text = unpauseAllLabel,
                onClick = {
                    scope.launch {
                        if (FreezerOperations.unpauseAll(context, settings.freezerAppPackages) > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            ),
            DropdownItem(
                text = unfreezeAllLabel,
                onClick = {
                    scope.launch {
                        if (FreezerOperations.unfreezeAll(context, settings.freezerAppPackages) > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            )
        )
    )

    SettingsLazyScreenScaffold(
        modifier = modifier,
        title = screenTitle,
        pageHint = stringResource(R.string.extension_freezer_subtitle),
        onBack = onBack?.let { handleBack },
        userScrollEnabled = false,
        actions = {
            MiuixExpandableSearchIconAction(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it }
            )
            if (onManageApps != null) {
                IconButton(onClick = onManageApps) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.freezer_manage_apps),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
            if (overlayMode) {
                Box {
                    IconButton(onClick = { overflowMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.freezer_batch_menu),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(importFrozenLabel) },
                            onClick = {
                                overflowMenuExpanded = false
                                importFrozenApps()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(switchWorkModeLabel) },
                            onClick = {
                                overflowMenuExpanded = false
                                switchWorkMode()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(restoreIntentsLabel) },
                            onClick = {
                                overflowMenuExpanded = false
                                scope.launch {
                                    if (batchRestore() > 0) {
                                        freezeStateRevision++
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(pauseAllLabel) },
                            onClick = {
                                overflowMenuExpanded = false
                                scope.launch {
                                    if (FreezerOperations.pauseAll(context, settings.freezerAppPackages) > 0) {
                                        freezeStateRevision++
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(unpauseAllLabel) },
                            onClick = {
                                overflowMenuExpanded = false
                                scope.launch {
                                    if (FreezerOperations.unpauseAll(context, settings.freezerAppPackages) > 0) {
                                        freezeStateRevision++
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(unfreezeAllLabel) },
                            onClick = {
                                overflowMenuExpanded = false
                                scope.launch {
                                    if (FreezerOperations.unfreezeAll(context, settings.freezerAppPackages) > 0) {
                                        freezeStateRevision++
                                    }
                                }
                            },
                        )
                    }
                }
            } else {
                OverlayIconDropdownMenu(entry = overflowMenuEntry) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.freezer_batch_menu),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        bottomContent = {
            MiuixExpandableSearchBottomContent(
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                focusRequester = searchFocusRequester,
                hintResId = R.string.freezer_grid_search_hint
            )
        },
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = {
                    scope.launch {
                        // 与「重冻应用」手势同一套语义：按每个成员记录的档位收回，
                        // 没记录的才落到工作模式。否则面板按钮会把用户设成暂停的成员一起冻成图标消失。
                        val changed = FreezerOperations.restoreIntents(
                            context = context,
                            packages = settings.freezerAppPackages,
                            fallbackPause = settings.freezerWorkMode.isPause,
                        )
                        if (changed > 0) {
                            freezeStateRevision++
                        }
                    }
                },
                icon = Icons.Default.AcUnit,
                contentDescription = stringResource(R.string.freezer_restore_intents)
            )
        }
    ) {
        if (isLoading) {
            item(key = "freezer-loading") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                }
            }
        } else {
            LazySettingsItem(key = "freezer-grid", fillParentMaxSize = true) {
                FreezerGridUi(
                    settings = settings,
                    memberApps = memberApps,
                    appRepository = appRepository,
                    settingsRepository = settingsRepository,
                    searchQuery = searchQuery,
                    freezeStateRevision = freezeStateRevision,
                    onFreezeStateRevisionBump = { freezeStateRevision++ },
                    modifier = Modifier.fillMaxSize(),
                    onAppLaunched = onAppLaunched,
                    onManageApps = onManageApps,
                    overlayMode = overlayMode,
                )
            }
        }
    }
}
