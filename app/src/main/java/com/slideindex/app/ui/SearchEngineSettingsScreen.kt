@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Widgets
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import kotlin.math.roundToInt
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.ui.viewmodel.SearchEngineImportPreviewState
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard

import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.searchengine.AggregatedSearchEngineManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineSettingsScreen(
    settings: AppSettings,
    importPreviewState: SearchEngineImportPreviewState?,
    onBack: () -> Unit,
    onOpenPresetPicker: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onDismissImportPreview: () -> Unit,
    onConfirmImport: (replaceExisting: Boolean) -> Unit,
    onUpsertEngine: (SearchEngineEditorResult) -> Unit,
    onDeleteEngine: (String) -> Unit,
    onMoveEngine: (String, Int) -> Unit,
    onOpenPreviewSort: () -> Unit,
    onOpenEditor: (String?) -> Unit,
    onUpdateEngines: (List<SearchEngineConfig>) -> Unit = {},
    dragFlowTextEngines: List<SearchEngineConfig> = emptyList(),
    onAddDragFlowTextEngine: (SearchEngineConfig) -> Unit = {},
    onRemoveDragFlowTextEngine: (String) -> Unit = {},
) {
    val engines = remember(settings.searchEngines) {
        SearchEngineStore.textSettingsEngines(settings.searchEngines)
    }
    var deletingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    var isDraggingAny by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var flowCardBounds by remember { mutableStateOf(Rect.Zero) }
    val hintTip = stringResource(R.string.search_engine_settings_hint_tip)

    SettingsScreenScaffold(
        title = stringResource(R.string.search_engine_settings_title),
        onBack = onBack,
        scrollContent = !isDraggingAny,
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = { showAddSheet = true },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.search_engine_add_import_description),
            )
        },
    ) {
        settingsLazyTipCard(
            key = "search-engine-settings-tip",
            text = hintTip,
        )
        item(key = "drag-flow-bar-card") {
            DragFlowBarCard(
                engines = dragFlowTextEngines.sortedBy { it.sortOrder },
                onRemove = onRemoveDragFlowTextEngine,
                onBoundsChanged = { flowCardBounds = it },
            )
        }
        item(key = "aggregated-search-manager") {
            AggregatedSearchEngineManager(
                engines = engines,
                gridColumns = settings.searchEngineGridColumns,
                gridRows = settings.searchEngineGridRows,
                showLabels = settings.searchEngineShowLabels,
                onDraggingStateChange = { isDraggingAny = it },
                onDragFinishedWindow = { engine, windowPos ->
                    // 松手时手指在「流转栏」卡片上（或卡片上方一点，手指很难精确压在边线上）：
                    // 加一份副本，源列表一个字都不动。
                    val bounds = flowCardBounds
                    val inside = windowPos != null &&
                        bounds != Rect.Zero &&
                        windowPos.x >= bounds.left - 12f &&
                        windowPos.x <= bounds.right + 12f &&
                        windowPos.y >= 0f &&
                        windowPos.y <= bounds.bottom + 16f
                    if (inside) {
                        onAddDragFlowTextEngine(engine)
                        true
                    } else {
                        false
                    }
                },
                onUpdateEngines = onUpdateEngines,
                iconShape = settings.searchEngineIconShape,
                onAddEngine = { onOpenEditor(null) },
                onEditEngine = onOpenEditor,
                onDeleteEngine = { id -> deletingEngine = engines.find { it.id == id } },
            )
        }
    }

    MiuixBottomSheet(
        show = showAddSheet,
        title = stringResource(R.string.search_engine_add_title),
        // 行自身带 12dp 外层边距，sheet 内部不再叠一层水平留白。
        insideMargin = DpSize(0.dp, 12.dp),
        onDismissRequest = { showAddSheet = false },
    ) {
        // 规范做法（ui/miuix/README「类型 1：列表」）：非懒加载容器用 settingsCardItems + RenderRows，
        // 行用 SettingNavigationRow —— 它自带图标容器、右箭头、分组卡片背景与行内 inset。
        // 原来手写 Card 包 ArrowPreference，会让 Card 的 insideMargin 与行自带 inset 叠加成
        // 水平双 padding（12 外层 + 16 + 16），视觉上就是"内容缩在中间、显得挤"。
        val addSheetRows = settingsCardItems("search-engine-add-sheet") {
            SettingNavigationRow(
                icon = { label ->
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = stringResource(R.string.search_engine_settings_preset_catalog),
                subtitle = stringResource(R.string.search_engine_settings_preset_catalog_subtitle),
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                onClick = {
                    showAddSheet = false
                    onOpenPresetPicker()
                },
            )
            SettingNavigationRow(
                icon = { label ->
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = stringResource(R.string.search_engine_settings_import),
                subtitle = stringResource(R.string.search_engine_settings_import_subtitle),
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                onClick = {
                    showAddSheet = false
                    importLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/json",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
            )
            SettingNavigationRow(
                icon = { label ->
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = stringResource(R.string.search_engine_add_title),
                subtitle = stringResource(R.string.search_engine_add_subtitle),
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                onClick = {
                    showAddSheet = false
                    onOpenEditor(null)
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            addSheetRows.RenderRows()
        }
    }

    if (importPreviewState != null) {
        SearchEngineImportPreviewDialog(
            preview = importPreviewState,
            onDismiss = onDismissImportPreview,
            onConfirmMerge = { onConfirmImport(false) },
            onConfirmReplace = { onConfirmImport(true) },
        )
    }

    val engineToDelete = deletingEngine
    MiuixConfirmDialog(
        show = engineToDelete != null,
        onDismissRequest = { deletingEngine = null },
        title = stringResource(R.string.search_engine_delete_title),
        message = engineToDelete?.let {
            stringResource(R.string.search_engine_delete_message, it.name)
        },
        confirmText = stringResource(R.string.search_engine_delete_confirm),
        onConfirm = {
            engineToDelete?.let { engine ->
                onDeleteEngine(engine.id)
                deletingEngine = null
            }
        },
    )
}

/**
 * 「流转栏」卡片：拖拽流转栏里「文字」类的目标都存这里。
 *
 * 从下面搜索引擎网格长按拖进来、松手加一份**独立副本**；原件不动也不消失。
 * 每个副本右上角的 × 可直接移除。
 */
@Composable
private fun DragFlowBarCard(
    engines: List<SearchEngineConfig>,
    onRemove: (String) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiuixText(
                text = stringResource(R.string.flow_bar_title),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.flow_bar_drag_targets_title),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.62f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${engines.size}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.62f),
            )
        }

        if (engines.isEmpty()) {
            Text(
                text = stringResource(R.string.flow_bar_drag_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.72f),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(engines, key = { it.id }) { engine ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(66.dp),
                    ) {
                        Box {
                            SearchEngineIcon(
                                engine = engine,
                                modifier = Modifier.size(44.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 5.dp, y = (-5).dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(scheme.surface)
                                    .border(1.dp, scheme.outline.copy(alpha = 0.28f), CircleShape)
                                    .clickable { onRemove(engine.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.flow_bar_remove_target),
                                    tint = scheme.onSurface,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Text(
                            text = engine.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SearchEngineImportPreviewDialog(
    preview: SearchEngineImportPreviewState,
    onDismiss: () -> Unit,
    onConfirmMerge: () -> Unit,
    onConfirmReplace: () -> Unit,
) {
    MiuixConfirmDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.search_engine_import_preview_title),
        confirmText = stringResource(R.string.search_engine_import_merge),
        onConfirm = onConfirmMerge,
        dismissText = stringResource(android.R.string.cancel),
        secondaryConfirmText = stringResource(R.string.search_engine_import_replace),
        onSecondaryConfirm = onConfirmReplace,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.search_engine_import_preview_source,
                        preview.sourceLabel,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    pluralStringResource(
                        R.plurals.search_engine_import_preview_count,
                        preview.importedCount,
                        preview.importedCount,
                        preview.skippedCount,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.search_engine_import_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
