package com.slideindex.app.ui

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.IconButton
import com.slideindex.app.settings.forLandscapeEditing
import com.slideindex.app.ui.trigger.TriggerSettingsLandscapeSession
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerCollectionEntry
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.allTriggerHandles
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.SwitchNavigationTrailingContent
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.MiuixIcons

private const val TRIGGER_PAIR_ENTER_MS = 260
private const val TRIGGER_PAIR_EXIT_MS = 200
private const val TRIGGER_ACTION_ENTER_MS = 220
private const val TRIGGER_ACTION_EXIT_MS = 180

private data class PendingSideRemove(val side: PanelSide, val handleId: String)

/**
 * 触钮集合页。
 *
 * [profileScope] 为 null = 全局（现有调用点一行不用改）；非 null = 「按应用分档」里某个档的
 * **同构整页**（issue #26 v2）：档名当标题、页面顶部多出「绑定的应用 / 恢复为全局」，
 * 每个触钮组行尾标「已覆盖 / 跟随全局」并可就地恢复该组，其余内容与全局完全一致。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TriggerCollectionScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    profileScope: GestureProfileScopeUi? = null,
    initialManualLandscapeOverride: Boolean? = null,
    onEnsureLandscapeInitialized: () -> Unit = {},
    onCopyPortraitToLandscape: () -> Unit = {},
    onBack: () -> Unit,
    onOpenLeftTrigger: (handleId: String) -> Unit,
    onOpenRightTrigger: (handleId: String) -> Unit,
    onOpenBottomTrigger: (handleId: String) -> Unit,
    onOpenTopTrigger: (handleId: String) -> Unit,
    onAddTriggerPair: () -> Unit,
    onAddBottomTrigger: () -> Unit,
    onAddTopTrigger: () -> Unit,
    onRemoveTriggerHandle: (PanelSide, String) -> Unit,
    onTriggerHandleEnabledChange: (PanelSide, String, Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val systemLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalActivity.current
    var manualLandscapeDisplay by remember {
        mutableStateOf(
            TriggerSettingsLandscapeSession.manualLandscapeDisplay ?: initialManualLandscapeOverride,
        )
    }
    LaunchedEffect(initialManualLandscapeOverride) {
        if (initialManualLandscapeOverride != null && manualLandscapeDisplay == null) {
            manualLandscapeDisplay = initialManualLandscapeOverride
            TriggerSettingsLandscapeSession.manualLandscapeDisplay = initialManualLandscapeOverride
        }
    }
    val landscapeMode = manualLandscapeDisplay ?: systemLandscape

    DisposableEffect(landscapeMode) {
        TriggerSettingsLandscapeSession.manualLandscapeDisplay = manualLandscapeDisplay
        TriggerSettingsLandscapeSession.updateActive(landscapeMode, activity)
        onDispose { }
    }
    LaunchedEffect(landscapeMode) {
        if (landscapeMode) {
            onEnsureLandscapeInitialized()
        }
    }

    val openLeftTrigger: (String) -> Unit = { handleId ->
        TriggerSettingsLandscapeSession.active = landscapeMode
        onOpenLeftTrigger(handleId)
    }
    val openRightTrigger: (String) -> Unit = { handleId ->
        TriggerSettingsLandscapeSession.active = landscapeMode
        onOpenRightTrigger(handleId)
    }
    val openBottomTrigger: (String) -> Unit = { handleId ->
        TriggerSettingsLandscapeSession.active = landscapeMode
        onOpenBottomTrigger(handleId)
    }
    val openTopTrigger: (String) -> Unit = { handleId ->
        TriggerSettingsLandscapeSession.active = landscapeMode
        onOpenTopTrigger(handleId)
    }

    val displaySettings = if (landscapeMode) settings.forLandscapeEditing() else settings
    var sideExpanded by rememberSaveable { mutableStateOf(true) }
    var pendingRemove by remember { mutableStateOf<PendingSideRemove?>(null) }
    var showRestoreProfileConfirm by remember { mutableStateOf(false) }
    var showCopyPortraitConfirm by remember { mutableStateOf(false) }
    val entries = displaySettings.triggerCollectionEntries()
    val bottomHandles = displaySettings.allTriggerHandles(PanelSide.BOTTOM)
    val topHandles = displaySettings.allTriggerHandles(PanelSide.TOP)
    val pairColors = listOf(
        Color(0xFF7E57C2),
        Color(0xFF26A69A),
        Color(0xFFFF7043),
    )

    val longPressRemoveHint = stringResource(R.string.trigger_collection_long_press_remove_hint)

    val collectionDesc = stringResource(
        if (landscapeMode) R.string.trigger_collection_landscape_desc else R.string.trigger_collection_desc,
    )
    // 档内页顶部醒目写明「这是独立副本」：不是"部分覆盖"，而是整档一份、不跟随全局。
    val pageHint = listOfNotNull(
        profileScope?.let { stringResource(R.string.gesture_app_profiles_not_following_global) },
        collectionDesc,
        longPressRemoveHint,
    ).joinToString("\n")

    val screenTitle = profileScope?.let {
        stringResource(R.string.gesture_app_profiles_detail_title, it.profileName)
    } ?: stringResource(
        if (landscapeMode) R.string.trigger_collection_landscape_title else R.string.trigger_collection_title,
    )
    // ⚠️ DSL 里的 `settingsLazySmallTitle` 不是 @Composable，文案要在组合期先取好。
    val profileBoundAppsTitle = stringResource(R.string.gesture_app_profiles_configured)

    SettingsScreenScaffold(
        title = screenTitle,
        pageHint = pageHint,
        onBack = onBack,
        actions = {
            if (landscapeMode) {
                IconButton(onClick = { showCopyPortraitConfirm = true }) {
                    Icon(
                        MiuixIcons.Medium.Copy,
                        contentDescription = stringResource(R.string.trigger_copy_portrait),
                    )
                }
            }
            IconButton(
                onClick = {
                    val nextLandscape = !(manualLandscapeDisplay ?: systemLandscape)
                    manualLandscapeDisplay = nextLandscape
                    TriggerSettingsLandscapeSession.setDisplayLandscape(nextLandscape, activity)
                },
            ) {
                Icon(
                    Icons.Default.ScreenRotation,
                    contentDescription = stringResource(
                        if (landscapeMode) {
                            R.string.trigger_collection_back_portrait
                        } else {
                            R.string.trigger_collection_open_landscape
                        },
                    ),
                )
            }
        },
    ) {
        if (profileScope != null) {
            settingsLazySmallTitle(
                key = "profile-bound-apps-section",
                title = profileBoundAppsTitle,
            )
            when {
                profileScope.boundAppEntries.isEmpty() -> {
                    managedAppListEmpty(key = "profile-bound-apps-empty") {
                        stringResource(R.string.gesture_app_profiles_empty)
                    }
                }
                else -> {
                    managedAppPackageRows(
                        keyPrefix = "profile-bound-apps",
                        entries = profileScope.boundAppEntries,
                        actionIcon = Icons.Default.Close,
                        actionDescription = { stringResource(R.string.gesture_app_profiles_remove) },
                        missingIcon = Icons.Default.Block,
                        onAction = { entry -> profileScope.onUnbindApp(entry.packageName) },
                    )
                }
            }
            if (profileScope.boundAppEntries.isNotEmpty()) {
                // 「已绑定应用」卡与「添加应用」卡之间补 12dp：两张独立圆角卡零间距贴在一起会出现
                // "双圆角掐腰"的观感（`managedAppPackageRows` / `managedAppListAddRow` 自身都不带上边距）。
                LazySettingsItem(key = "profile-bound-apps-gap") {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
            managedAppListAddRow(
                key = "profile-bound-apps-add",
                title = { stringResource(R.string.gesture_app_profiles_add) },
                onClick = profileScope.onOpenBindApp,
            )
            groupedCardItems(
                keyPrefix = "profile-restore-from-global",
                items = listOf(
                    settingsCardScopeItem("profile-restore-from-global-row") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("Restore", contentDescription = label)
                            },
                            title = stringResource(R.string.gesture_app_profiles_restore_from_global),
                            subtitle = stringResource(
                                R.string.gesture_app_profiles_restore_from_global_desc,
                            ),
                            onClick = { showRestoreProfileConfirm = true },
                        )
                    },
                ),
            )
        }

        LazySettingsItem(key = "trigger-side-section") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerEntryList(
                    entries = entries,
                    pairColors = pairColors,
                    settings = displaySettings,
                    serviceEnabled = serviceEnabled,
                    sideExpanded = sideExpanded,
                    profileScope = profileScope,
                    landscape = landscapeMode,
                    onToggleExpanded = { sideExpanded = !sideExpanded },
                    onOpenLeftTrigger = openLeftTrigger,
                    onOpenRightTrigger = openRightTrigger,
                    onRequestRemoveSide = { side, handleId ->
                        pendingRemove = PendingSideRemove(side, handleId)
                    },
                    onTriggerHandleEnabledChange = onTriggerHandleEnabledChange,
                )
                AnimatedVisibility(
                    visible = sideExpanded,
                    enter = expandVertically(
                        animationSpec = tween(TRIGGER_ACTION_ENTER_MS),
                        expandFrom = Alignment.Top,
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(TRIGGER_ACTION_EXIT_MS),
                        shrinkTowards = Alignment.Top,
                    ),
                ) {
                    TextButton(
                        onClick = onAddTriggerPair,
                        enabled = serviceEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    ) {
                        val hasIncompletePair = entries.any { (it.left == null) xor (it.right == null) }
                        Text(
                            stringResource(
                                if (hasIncompletePair) {
                                    R.string.trigger_handles_restore_opposite
                                } else {
                                    R.string.trigger_handles_add
                                },
                            ),
                        )
                    }
                }
            }
        }
        LazySettingsItem(key = "trigger-bottom-section-${bottomHandles.joinToString { it.id }}") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(
                    text = stringResource(R.string.trigger_collection_bottom),
                )
                bottomHandles.forEachIndexed { index, handle ->
                    val edgeCard = rememberTriggerEdgeHandleCard(
                        side = PanelSide.BOTTOM,
                        handle = handle,
                        handleIndex = index,
                        handleCount = bottomHandles.size,
                        settings = displaySettings,
                        serviceEnabled = serviceEnabled,
                        overrideBadge = profileOverrideBadge(
                            scope = profileScope,
                            side = PanelSide.BOTTOM,
                            handleId = handle.id,
                            landscape = landscapeMode,
                        ),
                        onOpenTrigger = openBottomTrigger,
                        onRequestRemove = {
                            pendingRemove = PendingSideRemove(PanelSide.BOTTOM, handle.id)
                        },
                        onTriggerHandleEnabledChange = { enabled ->
                            onTriggerHandleEnabledChange(PanelSide.BOTTOM, handle.id, enabled)
                        },
                    )
                    edgeCard.RenderRows()
                }
            }
        }
        LazySettingsItem(key = "trigger-add-bottom") {
            TextButton(
                onClick = onAddBottomTrigger,
                enabled = serviceEnabled && bottomHandles.size < 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(stringResource(R.string.trigger_collection_add_bottom))
            }
        }
        LazySettingsItem(key = "trigger-top-section-${topHandles.joinToString { it.id }}") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(
                    text = stringResource(R.string.trigger_collection_top),
                )
                topHandles.forEachIndexed { index, handle ->
                    val edgeCard = rememberTriggerEdgeHandleCard(
                        side = PanelSide.TOP,
                        handle = handle,
                        handleIndex = index,
                        handleCount = topHandles.size,
                        settings = displaySettings,
                        serviceEnabled = serviceEnabled,
                        overrideBadge = profileOverrideBadge(
                            scope = profileScope,
                            side = PanelSide.TOP,
                            handleId = handle.id,
                            landscape = landscapeMode,
                        ),
                        onOpenTrigger = openTopTrigger,
                        onRequestRemove = {
                            pendingRemove = PendingSideRemove(PanelSide.TOP, handle.id)
                        },
                        onTriggerHandleEnabledChange = { enabled ->
                            onTriggerHandleEnabledChange(PanelSide.TOP, handle.id, enabled)
                        },
                        allowRemoveDefault = false,
                    )
                    edgeCard.RenderRows()
                }
            }
        }
        LazySettingsItem(key = "trigger-add-top") {
            TextButton(
                onClick = onAddTopTrigger,
                enabled = serviceEnabled && topHandles.size < 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(stringResource(R.string.trigger_collection_add_top))
            }
        }
    }

    val removeTarget = pendingRemove
    val sideLabel = removeTarget?.let { pending ->
        when (pending.side) {
            PanelSide.LEFT -> stringResource(R.string.trigger_side_left_item)
            PanelSide.RIGHT -> stringResource(R.string.trigger_side_right_item)
            PanelSide.BOTTOM -> stringResource(R.string.trigger_collection_bottom)
            PanelSide.TOP -> stringResource(R.string.trigger_collection_top)
        }
    }
    MiuixConfirmDialog(
        show = removeTarget != null,
        onDismissRequest = { pendingRemove = null },
        title = sideLabel?.let {
            stringResource(R.string.trigger_remove_side_confirm_title, it)
        }.orEmpty(),
        message = stringResource(R.string.trigger_remove_side_confirm_message),
        onConfirm = {
            removeTarget?.let { pending ->
                onRemoveTriggerHandle(pending.side, pending.handleId)
                pendingRemove = null
            }
        },
    )

    MiuixConfirmDialog(
        show = showCopyPortraitConfirm,
        onDismissRequest = { showCopyPortraitConfirm = false },
        title = stringResource(R.string.trigger_copy_portrait_confirm_title),
        message = stringResource(R.string.trigger_copy_portrait_confirm_message),
        onConfirm = {
            onCopyPortraitToLandscape()
            showCopyPortraitConfirm = false
        },
    )

    MiuixConfirmDialog(
        show = showRestoreProfileConfirm,
        onDismissRequest = { showRestoreProfileConfirm = false },
        title = stringResource(R.string.gesture_app_profiles_restore_confirm_title),
        message = stringResource(R.string.gesture_app_profiles_restore_confirm_message),
        onConfirm = { profileScope?.onRestoreFromGlobal() },
    )
}

/**
 * 档内每个触钮组行尾的「已覆盖 / 跟随全局」——**纯只读徽标**。
 *
 * 恢复入口只有档页顶部那一个「恢复为全局」（整档重抄全局）：早先这里点一下"恢复该组"是不可靠的
 * （触钮几何在存储里按侧一份数组，恢复一组会把同侧别的组一起冲掉、还会凭空多出/少掉触钮），
 * 所以整块去掉。全局作用域（[scope] == null）返回 null，行尾什么都不加。
 */
@Composable
private fun profileOverrideBadge(
    scope: GestureProfileScopeUi?,
    side: PanelSide,
    handleId: String,
    landscape: Boolean,
): (@Composable () -> Unit)? {
    if (scope == null) return null
    return { TriggerProfileOverrideBadge(scope, side, handleId, landscape) }
}

@Composable
private fun TriggerProfileOverrideBadge(
    scope: GestureProfileScopeUi,
    side: PanelSide,
    handleId: String,
    landscape: Boolean,
) {
    val overridden = scope.groupOverridden(side, handleId, landscape)
    Text(
        text = stringResource(
            if (overridden) {
                R.string.gesture_app_profiles_slot_overridden
            } else {
                R.string.gesture_app_profiles_slot_follow_global
            },
        ),
        style = MaterialTheme.typography.labelMedium,
        color = if (overridden) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberTriggerEdgeHandleCard(
    side: PanelSide,
    handle: TriggerHandle,
    /** 该侧触钮列表里的序号 / 总数：底部与顶部靠它显示「底部触钮 · 第 2 组」而不是内部 id。 */
    handleIndex: Int,
    handleCount: Int,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onOpenTrigger: (String) -> Unit,
    onRequestRemove: () -> Unit,
    onTriggerHandleEnabledChange: (Boolean) -> Unit,
    allowRemoveDefault: Boolean = true,
    overrideBadge: (@Composable () -> Unit)? = null,
) = settingsCardItems(handle.id, handle.enabled, settings) {
    TriggerEdgeHandleRow(
        side = side,
        handle = handle,
        handleIndex = handleIndex,
        handleCount = handleCount,
        settings = settings,
        serviceEnabled = serviceEnabled,
        onOpenTrigger = onOpenTrigger,
        onRequestRemove = onRequestRemove,
        onTriggerHandleEnabledChange = onTriggerHandleEnabledChange,
        allowRemoveDefault = allowRemoveDefault,
        overrideBadge = overrideBadge,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsCardScope.TriggerEdgeHandleRow(
    side: PanelSide,
    handle: TriggerHandle,
    handleIndex: Int,
    handleCount: Int,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onOpenTrigger: (String) -> Unit,
    onRequestRemove: () -> Unit,
    onTriggerHandleEnabledChange: (Boolean) -> Unit,
    allowRemoveDefault: Boolean = true,
    overrideBadge: (@Composable () -> Unit)? = null,
) {
    SettingSwitchNavigationRow(
        title = triggerCollectionHandleTitle(side, handle.id, handleIndex, handleCount),
        subtitle = triggerHandleActionSummary(settings, side, handle.id),
        checked = handle.enabled,
        enabled = serviceEnabled,
        onCheckedChange = onTriggerHandleEnabledChange,
        onNavigate = { onOpenTrigger(handle.id) },
        onLongClick = if (serviceEnabled && (allowRemoveDefault || handle.id != TriggerHandle.DEFAULT_ID)) {
            onRequestRemove
        } else {
            null
        },
        subtitleContent = overrideBadge,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriggerEntryList(
    entries: List<TriggerCollectionEntry>,
    pairColors: List<Color>,
    settings: AppSettings,
    serviceEnabled: Boolean,
    sideExpanded: Boolean,
    profileScope: GestureProfileScopeUi?,
    landscape: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenLeftTrigger: (handleId: String) -> Unit,
    onOpenRightTrigger: (handleId: String) -> Unit,
    onRequestRemoveSide: (PanelSide, String) -> Unit,
    onTriggerHandleEnabledChange: (PanelSide, String, Boolean) -> Unit,
) {
    val targetIds = entries.map { it.handleId }
    var renderingIds by remember(targetIds) { mutableStateOf(targetIds) }
    var exitingIds by remember { mutableStateOf(setOf<String>()) }
    val entryCache = remember { mutableStateMapOf<String, TriggerCollectionEntry>() }
    entries.forEach { entryCache[it.handleId] = it }

    SideEffect {
        if (exitingIds.isEmpty() && renderingIds != targetIds) {
            renderingIds = targetIds
        }
    }

    LaunchedEffect(targetIds) {
        val renderingSet = renderingIds.toSet()
        val targetSet = targetIds.toSet()
        val removed = renderingSet - targetSet
        val added = targetSet - renderingSet

        when {
            removed.isNotEmpty() -> {
                exitingIds = removed
                delay(TRIGGER_PAIR_EXIT_MS.toLong())
                exitingIds = emptySet()
                renderingIds = targetIds
            }
            added.isNotEmpty() -> {
                renderingIds = targetIds
            }
        }
    }

    val visibleRowCount = renderingIds.sumOf { handleId ->
        val entry = entryCache[handleId] ?: return@sumOf 0
        listOfNotNull(entry.left, entry.right).size
    }
    val segmentCount = if (sideExpanded) 1 + visibleRowCount else 1
    var segmentIndex = 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(TRIGGER_PAIR_ENTER_MS)),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        BasicComponent(
            modifier = Modifier.miuixGroupedCardItem(0, segmentCount),
            title = stringResource(R.string.trigger_collection_side),
            onClick = onToggleExpanded,
            endActions = {
                Icon(
                    imageVector = if (sideExpanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = stringResource(
                        if (sideExpanded) R.string.cd_collapse_section else R.string.cd_expand_section,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        if (sideExpanded) {
            renderingIds.forEachIndexed { displayIndex, handleId ->
                val entry = entryCache[handleId] ?: return@forEachIndexed
                val entryIndex = targetIds.indexOf(handleId).takeIf { it >= 0 } ?: displayIndex
                val dotColor = pairColors[entryIndex % pairColors.size]
                val pairLabel = if (targetIds.size > 1) {
                    stringResource(R.string.trigger_pair_index, entryIndex + 1)
                } else {
                    null
                }
                AnimatedVisibility(
                    visible = handleId !in exitingIds,
                    enter = expandVertically(
                        animationSpec = tween(TRIGGER_PAIR_ENTER_MS),
                        expandFrom = Alignment.Top,
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(TRIGGER_PAIR_EXIT_MS),
                        shrinkTowards = Alignment.Top,
                    ),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
                    ) {
                        entry.left?.let { left ->
                            val currentSegment = segmentIndex++
                            TriggerSideRow(
                                segmentIndex = currentSegment,
                                segmentCount = segmentCount,
                                dotColor = dotColor,
                                title = stringResource(R.string.trigger_side_left_item),
                                pairLabel = pairLabel,
                                summary = triggerHandleActionSummary(
                                    settings,
                                    settings.gestureConfigSide(PanelSide.LEFT, handleId),
                                    handleId,
                                ),
                                handleEnabled = left.enabled,
                                enabled = serviceEnabled,
                                overrideBadge = profileOverrideBadge(
                                    scope = profileScope,
                                    side = PanelSide.LEFT,
                                    handleId = handleId,
                                    landscape = landscape,
                                ),
                                onClick = { onOpenLeftTrigger(handleId) },
                                onLongClick = if (serviceEnabled) {
                                    { onRequestRemoveSide(PanelSide.LEFT, handleId) }
                                } else {
                                    null
                                },
                                onHandleEnabledChange = {
                                    onTriggerHandleEnabledChange(PanelSide.LEFT, handleId, it)
                                },
                            )
                        }
                        entry.right?.let { right ->
                            val currentSegment = segmentIndex++
                            TriggerSideRow(
                                segmentIndex = currentSegment,
                                segmentCount = segmentCount,
                                dotColor = dotColor,
                                title = stringResource(R.string.trigger_side_right_item),
                                pairLabel = pairLabel,
                                summary = triggerHandleActionSummary(
                                    settings,
                                    settings.gestureConfigSide(PanelSide.RIGHT, handleId),
                                    handleId,
                                ),
                                handleEnabled = right.enabled,
                                enabled = serviceEnabled,
                                overrideBadge = profileOverrideBadge(
                                    scope = profileScope,
                                    side = PanelSide.RIGHT,
                                    handleId = handleId,
                                    landscape = landscape,
                                ),
                                onClick = { onOpenRightTrigger(handleId) },
                                onLongClick = if (serviceEnabled) {
                                    { onRequestRemoveSide(PanelSide.RIGHT, handleId) }
                                } else {
                                    null
                                },
                                onHandleEnabledChange = {
                                    onTriggerHandleEnabledChange(PanelSide.RIGHT, handleId, it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriggerSideRow(
    segmentIndex: Int,
    segmentCount: Int,
    dotColor: Color,
    title: String,
    pairLabel: String?,
    summary: String,
    handleEnabled: Boolean,
    enabled: Boolean,
    overrideBadge: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onHandleEnabledChange: (Boolean) -> Unit,
) {
    val displayTitle = if (pairLabel != null) {
        "$title · $pairLabel"
    } else {
        title
    }
    val rowModifier = Modifier
        .miuixGroupedCardItem(segmentIndex, segmentCount)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            } else {
                Modifier
            },
        )
    BasicComponent(
        modifier = rowModifier,
        title = displayTitle,
        summary = summary,
        enabled = enabled,
        onClick = if (onLongClick == null) onClick else null,
        startAction = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            ) {}
        },
        endActions = {
            overrideBadge?.invoke()
            SwitchNavigationTrailingContent(
                checked = handleEnabled,
                enabled = enabled,
                onCheckedChange = onHandleEnabledChange,
            )
        },
    )
}

/**
 * 底部 / 顶部触钮行的标题。
 *
 * ⚠️ 这里曾经只给"默认 id"起名字，**其余直接回显 `handleId`** —— 而新增触钮的 id 是
 * `TriggerHandle.newId()` 生成的 8 位随机串，于是列表里会出现"一串数字字母"。
 * 现在与左右触钮同一套口径：一个时只显示「底部触钮」，多个时补「· 第 N 组」。
 */
@Composable
private fun triggerCollectionHandleTitle(
    side: PanelSide,
    handleId: String,
    index: Int,
    count: Int,
): String {
    val base = when {
        side == PanelSide.BOTTOM -> stringResource(R.string.trigger_bottom_default_title)
        side == PanelSide.TOP -> stringResource(R.string.trigger_top_default_title)
        // 左右两侧走自己的 TriggerSideRow 标题，不会到这儿；留个兜底避免再暴露内部 id。
        else -> handleId
    }
    return if (count <= 1) base else "$base · ${stringResource(R.string.trigger_pair_index, index + 1)}"
}

@Composable
private fun triggerHandleActionSummary(
    settings: AppSettings,
    side: PanelSide,
    handleId: String,
): String {
    val labels = buildList {
        GestureTriggerType.shortDistanceEntries().forEach { trigger ->
            val action = settings.actionFor(side, trigger, handleId)
            if (action.isEffective()) add(gestureActionLabel(action))
        }
        GestureTriggerType.compoundGestureEntries().forEach { trigger ->
            val action = settings.actionFor(side, trigger, handleId)
            if (action.isEffective()) add(gestureActionLabel(action))
        }
        GestureTriggerType.pressTapEntries().forEach { trigger ->
            val action = settings.actionFor(side, trigger, handleId)
            if (action.isEffective()) add(gestureActionLabel(action))
        }
        GestureTriggerType.longDistanceEntries().forEach { trigger ->
            val action = settings.actionFor(side, trigger, handleId)
            if (action.isEffective()) add(gestureActionLabel(action))
        }
    }.distinct()
    return if (labels.isEmpty()) {
        stringResource(R.string.trigger_summary_none)
    } else {
        labels.take(5).joinToString(" · ")
    }
}
