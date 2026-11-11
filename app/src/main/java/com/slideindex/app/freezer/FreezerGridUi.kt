package com.slideindex.app.freezer

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.overlay.CloneBadgeOverlay
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PickerAppIconBitmap
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.popup.OverlayDropdownDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun FreezerGridUi(
    settings: AppSettings,
    memberApps: List<AppInfo>,
    appRepository: AppRepository,
    settingsRepository: SettingsRepository,
    searchQuery: String,
    freezeStateRevision: Int,
    onFreezeStateRevisionBump: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onAppLaunched: (() -> Unit)? = null,
    onManageApps: (() -> Unit)? = null,
    overlayMode: Boolean = false,
    /**
     * 多选态：点卡片 = 勾选/取消，**不再启动应用**；长按也走勾选（不在多选里再弹单项菜单）。
     *
     * 单项菜单本身没被搬走 —— 它留在普通态的长按上（见 `FreezerPanelContent` 顶栏的「选择」入口，
     * 那才是进多选的正门）。这样「单独冻结一个应用」仍然是"长按 → 点冻结"两步，没有被多选拖累。
     */
    selectionMode: Boolean = false,
    selectedTargets: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    /**
     * 网格自身滚动区底部要额外留出的空间（悬浮按钮压在内容之上时会盖住最后一行）。
     *
     * ⚠️ 必须加在**网格自己的 `contentPadding`** 上，不能靠外层列表尾部加 spacer：网格是嵌套滚动，
     * 它的最后一行在它自己的滚动区里，外层留白再多也顶不上来（实测就是这么失败的）。
     */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionTarget by remember { mutableStateOf<AppInfo?>(null) }

    val filteredApps = remember(memberApps, searchQuery) {
        val query = searchQuery.trim().lowercase()
        memberApps.filter { app ->
            query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true) ||
                app.pinyinKey.contains(query)
        }
    }

    val columnCount = if (
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        8
    } else {
        4
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            memberApps.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.AcUnit,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    Text(
                        text = stringResource(R.string.freezer_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (onManageApps != null) {
                        TextButton(onClick = onManageApps, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.freezer_manage_apps))
                        }
                    }
                }
            }
            filteredApps.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_apps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        top = 8.dp,
                        end = 4.dp,
                        bottom = 8.dp + bottomInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = filteredApps,
                        key = { app -> "${FreezerOperations.keyOf(app)}_$freezeStateRevision" },
                    ) { app ->
                        val state = FreezerOperations.stateOf(context, app)
                        FreezerGridItem(
                            app = app,
                            state = state,
                            selectionMode = selectionMode,
                            selected = FreezerOperations.keyOf(app) in selectedTargets,
                            onClick = {
                                if (selectionMode) {
                                    onToggleSelection(FreezerOperations.keyOf(app))
                                } else {
                                    scope.launch {
                                        if (!FreezerOperations.launchAndRestore(
                                                context,
                                                appRepository,
                                                settings,
                                                app,
                                            )
                                        ) {
                                            Toast.makeText(
                                                context,
                                                R.string.freezer_launch_failed,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            return@launch
                                        }
                                        onFreezeStateRevisionBump()
                                        onAppLaunched?.invoke()
                                    }
                                }
                            },
                            onLongClick = {
                                if (selectionMode) {
                                    onToggleSelection(FreezerOperations.keyOf(app))
                                } else {
                                    actionTarget = app
                                }
                            },
                        )
                    }
                }
            }
        }

        actionTarget?.let { app ->
            val state = FreezerOperations.stateOf(context, app)
            val dismissMenu = { actionTarget = null }
            val menuActions = FreezerAppActionCallbacks(
                onLaunchFreeWindow = {
                    scope.launch {
                        if (!FreezerOperations.launchAndRestore(
                                context,
                                appRepository,
                                settings,
                                app,
                                fullscreen = false,
                            )
                        ) {
                            Toast.makeText(
                                context,
                                R.string.freezer_launch_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        onFreezeStateRevisionBump()
                        onAppLaunched?.invoke()
                        dismissMenu()
                    }
                },
                onToggleFrozen = {
                    scope.launch {
                        if (FreezerOperations.setFrozen(context, app, !state.isFrozen)) {
                            onFreezeStateRevisionBump()
                        }
                        dismissMenu()
                    }
                },
                onTogglePaused = {
                    scope.launch {
                        if (FreezerOperations.setPaused(context, app, !state.isPaused)) {
                            onFreezeStateRevisionBump()
                        }
                        dismissMenu()
                    }
                },
                onAddToHome = {
                    if (!FreezerAppShortcutHelper.requestPinAppShortcut(context, app)) {
                        FreezerAppShortcutHelper.showPinShortcutFailedToast(context)
                    }
                    dismissMenu()
                },
                onRemoveFromList = {
                    scope.launch {
                        if (FreezerListOperations.removeFromList(
                                context = context,
                                settingsRepository = settingsRepository,
                                target = FreezerOperations.targetOf(app),
                                appRepository = appRepository,
                            )
                        ) {
                            onFreezeStateRevisionBump()
                        }
                        dismissMenu()
                    }
                },
                onRestoreAndRemoveFromList = {
                    scope.launch {
                        if (FreezerListOperations.restoreAndRemoveFromList(
                                context = context,
                                settingsRepository = settingsRepository,
                                target = FreezerOperations.targetOf(app),
                                appRepository = appRepository,
                            )
                        ) {
                            onFreezeStateRevisionBump()
                        }
                        dismissMenu()
                    }
                },
            )
            if (overlayMode) {
                FreezerAppActionOverlayMenu(
                    app = app,
                    state = state,
                    onDismiss = dismissMenu,
                    callbacks = menuActions,
                )
            } else {
                FreezerAppActionOverlayDialog(
                    app = app,
                    state = state,
                    onDismiss = dismissMenu,
                    callbacks = menuActions,
                )
            }
        }
    }
}

private data class FreezerAppActionCallbacks(
    val onLaunchFreeWindow: () -> Unit,
    val onToggleFrozen: () -> Unit,
    val onTogglePaused: () -> Unit,
    val onAddToHome: () -> Unit,
    val onRemoveFromList: () -> Unit,
    val onRestoreAndRemoveFromList: () -> Unit,
)

@Composable
private fun FreezerAppActionOverlayDialog(
    app: AppInfo,
    state: FreezerAppState,
    onDismiss: () -> Unit,
    callbacks: FreezerAppActionCallbacks,
) {
    val launchFreeWindowLabel = stringResource(R.string.task_switcher_menu_free_window)
    val freezeLabel = stringResource(
        if (state.isFrozen) R.string.freezer_action_unfreeze else R.string.freezer_action_freeze,
    )
    val pauseLabel = stringResource(
        if (state.isPaused) R.string.freezer_action_unpause else R.string.freezer_action_pause,
    )
    val addToHomeLabel = stringResource(R.string.freezer_action_add_to_home)
    val removeFromListLabel = stringResource(R.string.freezer_remove_from_list)
    val unfreezeAndRemoveLabel = stringResource(R.string.freezer_unfreeze_and_remove)
    val unpauseAndRemoveLabel = stringResource(R.string.freezer_unpause_and_remove)
    val cancelLabel = stringResource(R.string.cancel)
    val menuEntry = DropdownEntry(
        items = buildList {
            add(DropdownItem(text = launchFreeWindowLabel, onClick = callbacks.onLaunchFreeWindow))
            add(DropdownItem(text = freezeLabel, onClick = callbacks.onToggleFrozen))
            add(DropdownItem(text = pauseLabel, onClick = callbacks.onTogglePaused))
            add(DropdownItem(text = addToHomeLabel, onClick = callbacks.onAddToHome))
            when {
                state.isFrozen -> add(
                    DropdownItem(text = unfreezeAndRemoveLabel, onClick = callbacks.onRestoreAndRemoveFromList),
                )
                state.isPaused -> add(
                    DropdownItem(text = unpauseAndRemoveLabel, onClick = callbacks.onRestoreAndRemoveFromList),
                )
                else -> add(DropdownItem(text = removeFromListLabel, onClick = callbacks.onRemoveFromList))
            }
        },
    )
    OverlayDropdownDialog(
        entry = menuEntry,
        title = app.label,
        dialogButtonString = cancelLabel,
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = {},
        dropdownColors = DropdownDefaults.dropdownColors(),
    )
}

@Composable
private fun FreezerAppActionOverlayMenu(
    app: AppInfo,
    state: FreezerAppState,
    onDismiss: () -> Unit,
    callbacks: FreezerAppActionCallbacks,
) {
    val context = LocalContext.current
    val launchFreeWindowLabel = stringResource(R.string.task_switcher_menu_free_window)
    val freezeLabel = stringResource(
        if (state.isFrozen) R.string.freezer_action_unfreeze else R.string.freezer_action_freeze,
    )
    val pauseLabel = stringResource(
        if (state.isPaused) R.string.freezer_action_unpause else R.string.freezer_action_pause,
    )
    val addToHomeLabel = stringResource(R.string.freezer_action_add_to_home)
    val removeFromListLabel = stringResource(R.string.freezer_remove_from_list)
    val unfreezeAndRemoveLabel = stringResource(R.string.freezer_unfreeze_and_remove)
    val unpauseAndRemoveLabel = stringResource(R.string.freezer_unpause_and_remove)
    val cancelLabel = stringResource(R.string.cancel)
    val menuItems = buildList {
        add(launchFreeWindowLabel to callbacks.onLaunchFreeWindow)
        add(freezeLabel to callbacks.onToggleFrozen)
        add(pauseLabel to callbacks.onTogglePaused)
        add(addToHomeLabel to callbacks.onAddToHome)
        when {
            state.isFrozen -> add(unfreezeAndRemoveLabel to callbacks.onRestoreAndRemoveFromList)
            state.isPaused -> add(unpauseAndRemoveLabel to callbacks.onRestoreAndRemoveFromList)
            else -> add(removeFromListLabel to callbacks.onRemoveFromList)
        }
    }
    var iconBitmap by remember(app.packageName) {
        mutableStateOf(PickerAppIconBitmap.peek(app.packageName))
    }
    LaunchedEffect(app.packageName) {
        iconBitmap = PickerAppIconBitmap.load(context, app.packageName)
    }
    val iconColorFilter = if (state.isActive) {
        null
    } else {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
    val dropdownColors = DropdownDefaults.dropdownColors()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.windowDimming)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap!!,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                            colorFilter = iconColorFilter,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        MiuixText(
                            text = app.label,
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MiuixText(
                            text = app.packageName,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MiuixTheme.colorScheme.dividerLine,
                )
                menuItems.forEach { (label, onClick) ->
                    FreezerOverlayMenuRow(
                        text = label,
                        textColor = dropdownColors.contentColor,
                        onClick = onClick,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MiuixTheme.colorScheme.dividerLine,
                )
                MiuixTextButton(
                    text = cancelLabel,
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun FreezerOverlayMenuRow(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        MiuixText(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = textColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreezerGridItem(
    app: AppInfo,
    state: FreezerAppState,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) {
        mutableStateOf(PickerAppIconBitmap.peek(app.packageName))
    }
    LaunchedEffect(app.packageName) {
        iconBitmap = PickerAppIconBitmap.load(context, app.packageName)
    }
    val grayscale = if (state.isActive) {
        null
    } else {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(64.dp)) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = grayscale,
                )
            }
            // 暂停与冻结都是灰度，靠角标区分：暂停图标仍在桌面，冻结图标已从桌面消失。
            // 多选态下它让位给勾选圈 —— 64dp 里塞两枚角标会挤到一起。
            if (app.isClone && !selectionMode) {
                CloneBadgeOverlay(
                    iconSize = 64.dp,
                    circular = false,
                )
            }
            if (state.isPaused && !selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.freezer_status_paused),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = stringResource(
                        if (selected) {
                            R.string.freezer_selection_selected
                        } else {
                            R.string.freezer_selection_unselected
                        },
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (state.isActive) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
