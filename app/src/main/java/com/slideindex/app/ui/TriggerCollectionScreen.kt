package com.slideindex.app.ui

import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ContentCopy
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
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.SwitchNavigationTrailingContent
import kotlinx.coroutines.delay

private const val TRIGGER_PAIR_ENTER_MS = 260
private const val TRIGGER_PAIR_EXIT_MS = 200
private const val TRIGGER_ACTION_ENTER_MS = 220
private const val TRIGGER_ACTION_EXIT_MS = 180

private data class PendingSideRemove(val side: PanelSide, val handleId: String)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TriggerCollectionScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
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
    val pageHint = "$collectionDesc\n$longPressRemoveHint"

    SettingsScreenScaffold(
        title = stringResource(
            if (landscapeMode) R.string.trigger_collection_landscape_title else R.string.trigger_collection_title,
        ),
        pageHint = pageHint,
        onBack = onBack,
        actions = {
            if (landscapeMode) {
                IconButton(onClick = { showCopyPortraitConfirm = true }) {
                    Icon(
                        Icons.Default.ContentCopy,
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
        LazySettingsItem(key = "trigger-side-section") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TriggerEntryList(
                    entries = entries,
                    pairColors = pairColors,
                    settings = displaySettings,
                    serviceEnabled = serviceEnabled,
                    sideExpanded = sideExpanded,
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
                bottomHandles.forEach { handle ->
                    val edgeCard = rememberTriggerEdgeHandleCard(
                        side = PanelSide.BOTTOM,
                        handle = handle,
                        settings = displaySettings,
                        serviceEnabled = serviceEnabled,
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
                topHandles.forEach { handle ->
                    val edgeCard = rememberTriggerEdgeHandleCard(
                        side = PanelSide.TOP,
                        handle = handle,
                        settings = displaySettings,
                        serviceEnabled = serviceEnabled,
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun rememberTriggerEdgeHandleCard(
    side: PanelSide,
    handle: TriggerHandle,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onOpenTrigger: (String) -> Unit,
    onRequestRemove: () -> Unit,
    onTriggerHandleEnabledChange: (Boolean) -> Unit,
    allowRemoveDefault: Boolean = true,
) = settingsCardItems(handle.id, handle.enabled, settings) {
    TriggerEdgeHandleRow(
        side = side,
        handle = handle,
        settings = settings,
        serviceEnabled = serviceEnabled,
        onOpenTrigger = onOpenTrigger,
        onRequestRemove = onRequestRemove,
        onTriggerHandleEnabledChange = onTriggerHandleEnabledChange,
        allowRemoveDefault = allowRemoveDefault,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsCardScope.TriggerEdgeHandleRow(
    side: PanelSide,
    handle: TriggerHandle,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onOpenTrigger: (String) -> Unit,
    onRequestRemove: () -> Unit,
    onTriggerHandleEnabledChange: (Boolean) -> Unit,
    allowRemoveDefault: Boolean = true,
) {
    SettingSwitchNavigationRow(
        title = triggerCollectionHandleTitle(side, handle.id),
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
            SwitchNavigationTrailingContent(
                checked = handleEnabled,
                enabled = enabled,
                onCheckedChange = onHandleEnabledChange,
            )
        },
    )
}

@Composable
private fun triggerCollectionHandleTitle(side: PanelSide, handleId: String): String {
    if (side == PanelSide.BOTTOM && handleId == TriggerHandle.DEFAULT_ID) {
        return stringResource(R.string.trigger_bottom_default_title)
    }
    if (side == PanelSide.TOP && handleId == TriggerHandle.DEFAULT_ID) {
        return stringResource(R.string.trigger_top_default_title)
    }
    return handleId
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
