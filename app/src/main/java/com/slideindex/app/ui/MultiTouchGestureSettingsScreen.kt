package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.MultiTouchGestureEntry
import com.slideindex.app.gesture.multitouch.MultiTouchTriggerArea
import com.slideindex.app.multitouch.MultiTouchGestureSettings
import com.slideindex.app.multitouch.MultiTouchStatus
import com.slideindex.app.multitouch.encodePathStrokes
import com.slideindex.app.multitouch.multiTouchPresetKinds
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.components.reorderable.ReorderableItem
import com.slideindex.app.ui.components.reorderable.rememberReorderableLazyListState
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.SettingsCardScopeContent
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.slideindex.app.ui.miuix.CardSegment
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.delay

private val PreviewColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
)

/** 手势条目在 lazy 列表里的 key 前缀（拖拽排序时用它反查 entry id）。 */
private const val ENTRY_ITEM_KEY_PREFIX = "multitouch-entry-"

/**
 * 多指手势设置页（Shizuku 观察模式）。
 *
 * 手势条目 = 类型 + 接触点数 + 动作；“记录手势”只是其中一种类型。
 * 列表项显示手势预览；点条目打开编辑弹窗（预览 + 动作 + 删除）。
 */
@Composable
fun MultiTouchGestureSettingsScreen(
    settings: MultiTouchGestureSettings,
    status: MultiTouchStatus,
    diagnostics: String = "",
    touchDevices: List<String> = emptyList(),
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProtocolChange: (String) -> Unit = {},
    onRefreshDiagnostics: () -> Unit = {},
    onRefreshTouchDevices: () -> Unit = {},
    onDevicePathChange: (String) -> Unit = {},
    onEntryAreaChange: (entryId: String, areaKey: String) -> Unit = { _, _ -> },
    onEntryNameChange: (entryId: String, name: String) -> Unit = { _, _ -> },
    onEntryEnabledChange: (entryId: String, enabled: Boolean) -> Unit = { _, _ -> },
    onEntryShowHintChange: (entryId: String, showHint: Boolean) -> Unit = { _, _ -> },
    onEntryReorder: (entryId: String, toEntryId: String) -> Unit = { _, _ -> },
    onKeyboardBehaviorChange: (String) -> Unit = {},
    onRetry: () -> Unit,
    onOpenActionPick: (MultiPointerGesture, Int, String, String) -> Unit = { _, _, _, _ -> },
    onOpenRecorder: () -> Unit = {},
    onOpenPathEditor: (MultiTouchGestureEntry) -> Unit = {},
    onRemoveEntry: (String) -> Unit = {},
) {
    var showKindPicker by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<MultiPointerGesture?>(null) }
    var editingEntry by remember { mutableStateOf<MultiTouchGestureEntry?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showKeyboardPicker by remember { mutableStateOf(false) }
    // 手势条目支持长按拖动排序：需要外部持有 listState 才能算出 from/to 项。
    val entryListState = rememberLazyListState()
    val entryReorderState = rememberReorderableLazyListState(lazyListState = entryListState) { from, to ->
        val fromKey = from.key as? String
        val toKey = to.key as? String
        if (fromKey != null && toKey != null) {
            onEntryReorder(
                fromKey.removePrefix(ENTRY_ITEM_KEY_PREFIX),
                toKey.removePrefix(ENTRY_ITEM_KEY_PREFIX),
            )
        }
    }

    // 打开状态/诊断 sheet 时取一次；协议或设备切换会重启观察器，延时再刷新。
    LaunchedEffect(showDiagnostics) {
        if (showDiagnostics) {
            onRefreshDiagnostics()
            onRefreshTouchDevices()
        }
    }
    LaunchedEffect(showDiagnostics, settings.protocol, settings.devicePath) {
        if (showDiagnostics) {
            delay(600)
            onRefreshDiagnostics()
            onRefreshTouchDevices()
        }
    }

    // SettingsScreenScaffold 的 content 是 LazyListScope 扩展，不是 @Composable 上下文，
    // 所以标题/说明/底部提示的文案要先在这里取出来。
    val screenTitle = stringResource(R.string.multitouch_title)
    val tipText = stringResource(R.string.multitouch_tip)
    val bottomHint = stringResource(R.string.multitouch_hint)

    SettingsScreenScaffold(title = screenTitle, onBack = onBack, listState = entryListState) {
        settingsLazyTipCard(
            key = "multitouch-tip",
            text = tipText,
        )
        groupedCardItems(
            keyPrefix = "multitouch-status",
            items = buildList {
                add(
                    settingsCardScopeItem("multitouch-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.multitouch_enable_title),
                            subtitle = stringResource(R.string.multitouch_enable_summary),
                            checked = settings.enabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("multitouch-protocol") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            MiuixText(
                                text = stringResource(R.string.multitouch_protocol_title),
                                style = MiuixTheme.textStyles.headline1,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(4.dp))
                            MiuixText(
                                text = stringResource(R.string.multitouch_protocol_summary),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(8.dp))
                            SegmentedControl(
                                items = listOf(
                                    MultiTouchGestureSettings.PROTOCOL_AUTO,
                                    MultiTouchGestureSettings.PROTOCOL_TYPE_A,
                                    MultiTouchGestureSettings.PROTOCOL_TYPE_B,
                                ),
                                selected = settings.protocol,
                                labelRes = { protocol ->
                                    when (protocol) {
                                        MultiTouchGestureSettings.PROTOCOL_TYPE_A ->
                                            R.string.multitouch_protocol_type_a

                                        MultiTouchGestureSettings.PROTOCOL_TYPE_B ->
                                            R.string.multitouch_protocol_type_b

                                        else -> R.string.multitouch_protocol_auto
                                    }
                                },
                                onSelect = onProtocolChange,
                                containerColor = MiuixTheme.colorScheme.surface,
                                indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
                            )
                        }
                    }
                )
                add(
                    settingsCardScopeItem("multitouch-status") {
                        val conflict = status.conflictDevices
                        val enabled = settings.enabled
                        val tone = when {
                            !enabled -> StatusTone.Neutral
                            conflict != null -> StatusTone.Bad
                            status.running -> StatusTone.Good
                            else -> StatusTone.Bad
                        }
                        val pill = when {
                            !enabled -> stringResource(R.string.multitouch_status_disabled)
                            conflict != null ->
                                stringResource(R.string.multitouch_status_conflict_pill)

                            status.running -> stringResource(R.string.multitouch_status_running)
                            else -> stringResource(R.string.multitouch_status_stopped)
                        }
                        StatusRow(
                            title = stringResource(R.string.multitouch_status_title),
                            pill = pill,
                            tone = tone,
                            detail = multiTouchStatusDetail(status, enabled),
                            segmentKey = "multitouch-status",
                            onClick = {
                                showDiagnostics = true
                                onRefreshDiagnostics()
                                onRefreshTouchDevices()
                            },
                        )
                    }
                )
            },
        )
        groupedCardItems(
            keyPrefix = "multitouch-trigger",
            items = buildList {
                add(
                    settingsCardScopeItem("multitouch-trigger-keyboard") {
                        MiuixNavigationRow(
                            title = stringResource(R.string.multitouch_trigger_keyboard_title),
                            summary = keyboardBehaviorLabel(settings.keyboardBehavior),
                            enabled = settings.enabled,
                            onClick = { showKeyboardPicker = true },
                        )
                    }
                )
            },
        )
        // 手势条目：一条卡片，首段是「添加」，其余段可长按拖动排序（同组内重排，不跨卡片）。
        item(key = "multitouch-entries:multitouch-add") {
            CardSegment(
                isFirst = true,
                isLast = settings.entries.isEmpty(),
                outerHorizontalPadding = 12.dp,
                outerBottomPadding = if (settings.entries.isEmpty()) 12.dp else 0.dp,
            ) {
                SettingsCardScopeContent {
                    MiuixNavigationRow(
                        title = stringResource(R.string.multitouch_add_title),
                        summary = stringResource(R.string.multitouch_add_summary),
                        enabled = settings.enabled,
                        onClick = { showKindPicker = true },
                    )
                }
            }
        }
        val lastEntryIndex = settings.entries.lastIndex
        settings.entries.forEachIndexed { index, entry ->
            val entryKey = ENTRY_ITEM_KEY_PREFIX + entry.id
            // 反馈 #1：拖拽手柄不能包住整行 —— 行自己带 clickable，会把 down/up 吃掉，
            // 父级的 longPressDraggableHandle 永远收不到事件。手柄只挂在左侧图标上。
            item(key = entryKey) {
                ReorderableItem(state = entryReorderState, key = entryKey) { _ ->
                    val dragHandle = Modifier.longPressDraggableHandle()
                    CardSegment(
                        isFirst = false,
                        isLast = index == lastEntryIndex,
                        outerHorizontalPadding = 12.dp,
                        outerBottomPadding = if (index == lastEntryIndex) 12.dp else 0.dp,
                    ) {
                        EntryRow(
                            entry = entry,
                            enabled = settings.enabled,
                            onClick = { editingEntry = entry },
                            onEnabledChange = { value -> onEntryEnabledChange(entry.id, value) },
                            dragHandleModifier = dragHandle,
                        )
                    }
                }
            }
        }
        settingsLazyHint(
            key = "multitouch-hint",
            text = bottomHint,
        )
    }

    MiuixBottomSheet(
        show = showKindPicker,
        title = stringResource(R.string.multitouch_pick_kind_title),
        onDismissRequest = { showKindPicker = false },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            MiuixArrowRow(
                title = stringResource(R.string.multitouch_kind_path),
                summary = stringResource(R.string.multitouch_draw_summary),
                onClick = {
                    showKindPicker = false
                    onOpenRecorder()
                },
            )
            multiTouchPresetKinds.forEach { kind ->
                MiuixArrowRow(
                    title = kind.displayName(),
                    onClick = {
                        showKindPicker = false
                        pendingKind = kind
                    },
                )
            }
        }
    }

    val selectedKind = pendingKind
    // 给个非空兜底值，保证 sheet 在关闭动画期间仍可组合；显示与否由 pendingKind 控制。
    val kind = selectedKind ?: MultiPointerGesture.SWIPE_UP
    MiuixBottomSheet(
        show = selectedKind != null,
        title = stringResource(R.string.multitouch_finger_count_title),
        onDismissRequest = { pendingKind = null },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    R.string.multitouch_finger_count_summary,
                    kind.displayName(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (fingers in 1..5) {
                    // 捏合/张开/旋转是几何手势：质心与半径在多指下才有意义，单指必然触发不了。
                    val geometry = kind in GEOMETRY_GESTURES
                    FingerCell(
                        fingers = fingers,
                        modifier = Modifier.weight(1f),
                        enabled = !geometry || fingers >= 2,
                        onClick = {
                            pendingKind = null
                            onOpenActionPick(kind, fingers, "", "")
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = { pendingKind = null },
                modifier = Modifier.align(Alignment.End),
            )
        }
    }

    val currentDeviceName = Regex("name=(\\S+)")
        .find(diagnostics)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    MiuixBottomSheet(
        show = showDiagnostics,
        title = stringResource(R.string.multitouch_status_title),
        startAction = {
            IconButton(onClick = { showDiagnostics = false }) {
                MiuixIcon(
                    imageVector = MiuixIcons.Medium.Close,
                    contentDescription = stringResource(R.string.multitouch_diagnostics_close),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    showDiagnostics = false
                    onRetry()
                },
            ) {
                MiuixIcon(
                    imageVector = MiuixIcons.Medium.Refresh,
                    contentDescription = stringResource(R.string.multitouch_diagnostics_retry),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        onDismissRequest = { showDiagnostics = false },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SmallTitle(
                    text = stringResource(R.string.multitouch_diagnostics_title),
                    insideMargin = PaddingValues(16.dp, 8.dp),
                )
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    color = MiuixTheme.colorScheme.secondaryContainer,
                    outerHorizontalPadding = 0.dp,
                    outerBottomPadding = 16.dp,
                    insidePadding = PaddingValues(16.dp),
                ) {
                    MiuixText(
                        text = diagnostics.ifBlank {
                            stringResource(R.string.multitouch_diagnostics_empty)
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            item {
                SmallTitle(
                    text = stringResource(R.string.multitouch_device_title),
                    insideMargin = PaddingValues(16.dp, 8.dp),
                )
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    val hasCustomDevices = touchDevices.isNotEmpty()
                    CardSegment(
                        isFirst = true,
                        isLast = !hasCustomDevices,
                        color = MiuixTheme.colorScheme.secondaryContainer,
                        outerHorizontalPadding = 0.dp,
                    ) {
                        RadioButtonPreference(
                            title = stringResource(R.string.multitouch_device_auto),
                            summary = if (currentDeviceName.isNotBlank()) {
                                stringResource(
                                    R.string.multitouch_device_summary_auto_current,
                                    currentDeviceName,
                                )
                            } else {
                                stringResource(R.string.multitouch_device_summary_auto)
                            },
                            selected = settings.devicePath.isBlank(),
                            onClick = { onDevicePathChange("") },
                            radioButtonLocation = RadioButtonLocation.End,
                        )
                    }
                    touchDevices.forEachIndexed { index, line ->
                        val parts = line.split('|')
                        val path = parts.getOrNull(0).orEmpty()
                        val name = parts.getOrNull(1).orEmpty()
                        val protocol = parts.getOrNull(2).orEmpty()
                        val range = "${parts.getOrNull(3).orEmpty()}×" +
                            parts.getOrNull(4).orEmpty()
                        CardSegment(
                            isFirst = false,
                            isLast = index == touchDevices.lastIndex,
                            color = MiuixTheme.colorScheme.secondaryContainer,
                            outerHorizontalPadding = 0.dp,
                        ) {
                            RadioButtonPreference(
                                title = name.ifBlank { path },
                                summary = "$path · $protocol · $range",
                                selected = settings.devicePath == path,
                                onClick = { onDevicePathChange(path) },
                                radioButtonLocation = RadioButtonLocation.End,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(
                    Modifier.padding(
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() +
                            WindowInsets.captionBar
                                .asPaddingValues()
                                .calculateBottomPadding(),
                    ),
                )
            }
        }
    }

    MiuixBottomSheet(
        show = showKeyboardPicker,
        title = stringResource(R.string.multitouch_trigger_keyboard_title),
        onDismissRequest = { showKeyboardPicker = false },
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            val options = MultiTouchGestureSettings.keyboardBehaviorOptions
            options.forEachIndexed { index, behavior ->
                CardSegment(
                    isFirst = index == 0,
                    isLast = index == options.lastIndex,
                    color = MiuixTheme.colorScheme.secondaryContainer,
                    outerHorizontalPadding = 0.dp,
                ) {
                    RadioButtonPreference(
                        title = keyboardBehaviorLabel(behavior),
                        selected = settings.keyboardBehavior == behavior,
                        onClick = {
                            showKeyboardPicker = false
                            onKeyboardBehaviorChange(behavior)
                        },
                        radioButtonLocation = RadioButtonLocation.End,
                    )
                }
            }
        }
    }

    val editing = editingEntry
    if (editing != null) {
        // 名称输入框的本地草稿：跟着条目 id 重置，输入即时回写。
        var nameText by remember(editing.id) { mutableStateOf(editing.name) }
        WindowDialog(
            show = true,
            title = stringResource(R.string.multitouch_edit_title),
            onDismissRequest = { editingEntry = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = editing.isPath) {
                            editingEntry = null
                            onOpenPathEditor(editing)
                        },
                ) {
                    GesturePreview(entry = editing, modifier = Modifier.fillMaxWidth().height(140.dp))
                }
                if (editing.isPath) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.multitouch_edit_rerecord_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                MiuixArrowRow(
                    title = stringResource(
                        R.string.multitouch_entry_label,
                        editing.fingerCount,
                        editing.kind.displayName(),
                    ),
                    summary = gestureActionLabel(editing.action),
                    onClick = {
                        editingEntry = null
                        onOpenActionPick(
                            editing.kind,
                            editing.fingerCount,
                            encodePathStrokes(editing.pathStrokes),
                            editing.id,
                        )
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 自定义名称：多条「绘制手势」默认都叫「1 指 · 绘制手势」，改名后才能区分（反馈 #396）。
                MiuixLabeledTextField(
                    value = nameText,
                    onValueChange = { value ->
                        nameText = value
                        onEntryNameChange(editing.id, value)
                    },
                    label = stringResource(R.string.multitouch_entry_name_label),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 反馈 #2：这两行原来用 Material 的 bodyMedium，比页面其它行小一号、字体也不统一；
                // 统一成 Miuix 文本体系，并给到 48dp 行高（开关更好点）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.multitouch_entry_enabled_label),
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        modifier = Modifier.weight(1f),
                    )
                    MiuixSwitch(
                        checked = editing.enabled,
                        onCheckedChange = { value ->
                            editingEntry = editing.copy(enabled = value)
                            onEntryEnabledChange(editing.id, value)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.multitouch_entry_hint_label),
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.multitouch_entry_hint_desc),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    MiuixSwitch(
                        checked = editing.showHint,
                        onCheckedChange = { value ->
                            editingEntry = editing.copy(showHint = value)
                            onEntryShowHintChange(editing.id, value)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.multitouch_trigger_area_title),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = MultiTouchTriggerArea.entries.map { it.key },
                    selected = editing.triggerArea.key,
                    labelRes = ::triggerAreaLabelRes,
                    onSelect = { key ->
                        val area = MultiTouchTriggerArea.fromKey(key)
                        editingEntry = editing.copy(triggerArea = area)
                        onEntryAreaChange(editing.id, key)
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.multitouch_delete),
                        onClick = {
                            editingEntry = null
                            onRemoveEntry(editing.id)
                        },
                        modifier = Modifier.weight(1f),
                        minHeight = 44.dp,
                    )
                    TextButton(
                        text = stringResource(R.string.multitouch_done),
                        onClick = { editingEntry = null },
                        modifier = Modifier.weight(1f),
                        minHeight = 44.dp,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: MultiTouchGestureEntry,
    enabled: Boolean,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    /** 挂在左侧图标上的拖拽手柄（反馈 #1：不能包整行，否则点击会吃掉手势）。 */
    dragHandleModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GesturePreview(
            entry = entry,
            modifier = Modifier
                .size(44.dp)
                .then(dragHandleModifier),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // 起过名字就显示名字（多条绘制手势才能区分开），否则沿用「位置 · 手指数 · 类型」。
                text = entry.name.ifBlank {
                    stringResource(
                        R.string.multitouch_entry_label_area,
                        entry.fingerCount,
                        entry.kind.displayName(),
                        triggerAreaLabel(entry.triggerArea.key),
                    )
                },
                style = MiuixTheme.textStyles.title4,
                color = if (entry.enabled) {
                    MiuixTheme.colorScheme.onSurfaceContainer
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (entry.enabled) {
                    gestureActionLabel(entry.action)
                } else {
                    stringResource(R.string.multitouch_entry_disabled_label)
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        MiuixSwitch(
            checked = entry.enabled,
            enabled = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

/** 手势预览：记录手势画轨迹，预设类型画方向符号。 */
@Composable
private fun GesturePreview(entry: MultiTouchGestureEntry, modifier: Modifier) {
    if (entry.pathStrokes.isNotEmpty()) {
        Canvas(modifier = modifier) {
            val height = size.height
            entry.pathStrokes.forEachIndexed { index, stroke ->
                if (stroke.points.size < 2) return@forEachIndexed
                val path = Path()
                stroke.points.forEachIndexed { pointIndex, point ->
                    val x = point.x * height
                    val y = point.y * height
                    if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = PreviewColors[index % PreviewColors.size],
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
    } else {
        val iconRes = gestureIconRes(entry.kind)
        val icon = gestureIcon(entry.kind)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            when {
                iconRes != null -> Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                else -> Text(
                    text = gestureSymbol(entry.kind),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 点按/捏合/张开用 Material Symbols 图标（用户提供）。 */
private fun gestureIconRes(kind: MultiPointerGesture): Int? = when (kind) {
    MultiPointerGesture.PINCH -> R.drawable.ic_gesture_pinch
    MultiPointerGesture.SPREAD -> R.drawable.ic_gesture_spread
    MultiPointerGesture.TAP,
    MultiPointerGesture.TOUCH,
    -> R.drawable.ic_gesture_touch
    MultiPointerGesture.DOUBLE_TAP -> R.drawable.ic_gesture_double_tap
    MultiPointerGesture.TRIPLE_TAP -> R.drawable.ic_gesture_triple_tap
    else -> null
}

/** 其余预设类型用 Compose 图标（旋转/长按/记录手势）。 */
private fun gestureIcon(kind: MultiPointerGesture): ImageVector? = when (kind) {
    MultiPointerGesture.ROTATE_CLOCKWISE -> Icons.Outlined.RotateRight
    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE -> Icons.Outlined.RotateLeft
    MultiPointerGesture.LONG_PRESS -> Icons.Outlined.Timer
    MultiPointerGesture.PATH -> Icons.Outlined.Draw
    else -> null
}

private fun gestureSymbol(kind: MultiPointerGesture): String = when (kind) {
    MultiPointerGesture.SWIPE_LEFT -> "←"
    MultiPointerGesture.SWIPE_RIGHT -> "→"
    MultiPointerGesture.SWIPE_UP -> "↑"
    MultiPointerGesture.SWIPE_DOWN -> "↓"
    MultiPointerGesture.SWIPE_UP_LEFT -> "↖"
    MultiPointerGesture.SWIPE_UP_RIGHT -> "↗"
    MultiPointerGesture.SWIPE_DOWN_LEFT -> "↙"
    MultiPointerGesture.SWIPE_DOWN_RIGHT -> "↘"
    else -> ""
}

@Composable
private fun FingerCell(
    fingers: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fingers.toString(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/** 需要多指才有意义的几何手势（单指时质心/半径失效）。 */
private val GEOMETRY_GESTURES = setOf(
    MultiPointerGesture.PINCH,
    MultiPointerGesture.SPREAD,
    MultiPointerGesture.ROTATE_CLOCKWISE,
    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE,
)

/** 状态行说明：设备 / 模式 / 特权通道 / 冲突，全部收在这一行里。 */
@Composable
private fun multiTouchStatusDetail(status: MultiTouchStatus, enabled: Boolean): String {
    val base = when {
        !enabled -> stringResource(R.string.multitouch_detail_disabled)
        status.detail == "recording" -> stringResource(R.string.multitouch_detail_recording)
        status.running -> {
            val channel = when (status.channel) {
                "root" -> stringResource(R.string.multitouch_channel_root)
                "shizuku" -> stringResource(R.string.multitouch_channel_shizuku)
                else -> stringResource(R.string.multitouch_channel_observer)
            }
            val unknownDevice = stringResource(R.string.multitouch_device_unknown)
            val device = status.device.ifBlank { unknownDevice }
            stringResource(R.string.multitouch_detail_running, device, channel)
        }
        status.detail == "root-unavailable" ->
            stringResource(R.string.multitouch_detail_root_unavailable)

        status.detail == "root-start-failed" ->
            stringResource(R.string.multitouch_detail_root_start_failed)

        !status.shizukuAvailable ->
            stringResource(R.string.multitouch_detail_shizuku_unavailable)

        status.detail == "service-unavailable" ->
            stringResource(R.string.multitouch_detail_service_unavailable)

        status.detail == "device-unavailable" ->
            stringResource(R.string.multitouch_detail_device_unavailable)

        else -> stringResource(
            R.string.multitouch_detail_not_running,
            status.detail.ifBlank { "unknown" },
        )
    }
    val conflict = status.conflictDevices ?: return base
    val name = if (conflict.length > 30) conflict.take(30) + "…" else conflict
    return base + stringResource(R.string.multitouch_detail_conflict, name)
}

/** 触发区域（GMD 四档）的字符串资源。 */
private fun triggerAreaLabelRes(areaKey: String): Int = when (areaKey) {
    MultiTouchTriggerArea.TOP.key -> R.string.multitouch_trigger_top
    MultiTouchTriggerArea.BOTTOM.key -> R.string.multitouch_trigger_bottom
    else -> R.string.multitouch_trigger_anywhere
}

/** 触发区域（GMD 四档）的显示名。 */
@Composable
internal fun triggerAreaLabel(areaKey: String): String =
    stringResource(triggerAreaLabelRes(areaKey))

/** 键盘可见时策略（GMD 三档）的显示名。 */
@Composable
internal fun keyboardBehaviorLabel(behavior: String): String = stringResource(
    when (behavior) {
        MultiTouchGestureSettings.KEYBOARD_BLOCKED -> R.string.multitouch_keyboard_blocked
        MultiTouchGestureSettings.KEYBOARD_EVERYWHERE -> R.string.multitouch_keyboard_everywhere
        else -> R.string.multitouch_keyboard_above
    },
)
