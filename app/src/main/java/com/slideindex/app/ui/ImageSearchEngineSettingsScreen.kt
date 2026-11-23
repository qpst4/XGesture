@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.DpSize
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.painterResource
import com.slideindex.app.search.SearchEngineImporter
import com.slideindex.app.search.SearchEngineShareCodec
import com.slideindex.app.ui.link.LinkDataManagementSheet
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsVerticalReorderList
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.settingsCardItems
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSearchEngineSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpsertEngine: (SearchEngineEditorResult) -> Unit,
    onDeleteEngine: (String) -> Unit,
    onReorderShareEngines: (List<SearchEngineConfig>) -> Unit,
    onReorderAggregatedEngines: (List<AggregatedImageSearchEngineConfig>) -> Unit,
    onOpenAggregatedEngine: (String) -> Unit,
    onOpenEditor: (String?) -> Unit,
    onOpenBatchPicker: () -> Unit,
    onCreateGroup: () -> Unit = {},
    onImportParsed: (List<SearchEngineConfig>) -> Unit = {},
    onSetShowAggregatedImageEntry: (Boolean) -> Unit,
) {
    val shareEngines = remember(settings.searchEngines) {
        SearchEngineStore.imageShareSettingsEngines(settings.searchEngines)
    }
    val aggregatedConfigs = remember(settings.aggregatedImageSearchEngines) {
        settings.aggregatedImageSearchEngines.sortedBy { it.sortOrder }
    }
    val visibleAggregatedCount = remember(aggregatedConfigs) {
        aggregatedConfigs.count { it.showInPanel }
    }
    var deletingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showJsonImport by remember { mutableStateOf(false) }
    var showDataManagementSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val openDataDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (raw.isNullOrBlank()) {
                Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
            } else {
                runCatching { SearchEngineImporter.parseJsonText(raw) }
                    .onSuccess { onImportParsed(it) }
                    .onFailure {
                        Toast.makeText(context, "未找到可导入的分享目标", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    val createDataDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            val json = SearchEngineShareCodec.encode(shareEngines)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray())
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (ok) "备份已保存" else "保存失败",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val shareSectionTitle = stringResource(
        R.string.image_search_engine_share_section,
        shareEngines.size,
    )
    val shareHint = stringResource(R.string.image_search_engine_share_hint)
    val shareEmptyHint = stringResource(R.string.image_search_engine_share_empty)
    val aggregatedSectionTitle = pluralStringResource(
        R.plurals.image_search_engine_aggregated_section,
        visibleAggregatedCount,
        visibleAggregatedCount,
    )
    val aggregatedHint = stringResource(R.string.image_search_engine_aggregated_hint)
    val aggregatedEmptyHint = stringResource(R.string.image_search_engine_aggregated_empty)

    SettingsScreenScaffold(
        title = stringResource(R.string.image_search_engine_settings_title),
        subtitle = stringResource(R.string.image_search_engine_settings_subtitle),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "image-search-share-section",
            title = shareSectionTitle,
        )
        settingsLazyTipCard(key = "image-search-share-hint", text = shareHint)
        groupedCardItems(
            keyPrefix = "image-search-add-share",
            items = listOf(
                settingsCardScopeItem("add-share-target") {
                    SettingNavigationRow(
                        icon = {
                            label -> Icon(
                                MiuixIcons.Add,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        title = stringResource(R.string.image_search_engine_add_share_target),
                        subtitle = stringResource(R.string.image_search_engine_add_share_target_subtitle),
                        onClick = { showAddSheet = true },
                    )
                },
            ),
        )

        if (shareEngines.isEmpty()) {
            settingsLazyHint(key = "image-search-share-empty", text = shareEmptyHint)
        } else {
            LazySettingsItem(key = "image-search-share-list") {
                SettingsVerticalReorderList(
                    items = shareEngines,
                    key = { it.id },
                    onReorder = onReorderShareEngines,
                ) { engine, _, segmentIndex, segmentCount, dragModifier ->
                    ImageShareEngineReorderRow(
                        engine = engine,
                        segmentIndex = segmentIndex,
                        segmentCount = segmentCount,
                        onClick = { onOpenEditor(engine.id) },
                        onDelete = { deletingEngine = engine },
                        modifier = dragModifier,
                    )
                }
            }
        }

        settingsLazySmallTitle(
            key = "image-search-aggregated-section",
            title = aggregatedSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "image-search-aggregated-entry",
            items = listOf(
                settingsCardScopeItem("show-aggregated-entry") {
                    SettingSwitchRow(
                        title = stringResource(R.string.image_search_engine_show_aggregated_entry),
                        subtitle = stringResource(R.string.image_search_engine_show_aggregated_entry_desc),
                        checked = settings.searchPanelShowAggregatedImageEntry,
                        enabled = true,
                        onCheckedChange = onSetShowAggregatedImageEntry,
                    )
                },
            ),
        )
        settingsLazyTipCard(key = "image-search-aggregated-hint", text = aggregatedHint)
        LazySettingsItem(key = "image-search-aggregated-list") {
            if (visibleAggregatedCount == 0) {
                Text(
                    text = aggregatedEmptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            SettingsVerticalReorderList(
                items = aggregatedConfigs,
                key = { it.engineId },
                onReorder = onReorderAggregatedEngines,
            ) { config, _, segmentIndex, segmentCount, dragModifier ->
                val engine = resolveImageSearchEngine(config.engineId)
                if (engine != null) {
                    AggregatedImageSearchEngineReorderRow(
                        engine = engine,
                        subtitle = aggregatedImageSearchEngineRowSubtitle(engine, config),
                        segmentIndex = segmentIndex,
                        segmentCount = segmentCount,
                        onClick = { onOpenAggregatedEngine(config.engineId) },
                        modifier = dragModifier,
                    )
                }
            }
        }
    }

    MiuixBottomSheet(
        show = showAddSheet,
        title = stringResource(R.string.search_group_add_sheet_title),
        insideMargin = DpSize(0.dp, 12.dp),
        onDismissRequest = { showAddSheet = false },
    ) {
        val addSheetRows = settingsCardItems("image-search-add-share-sheet") {
            SettingNavigationRow(
                icon = { label ->
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = stringResource(R.string.search_group_pick_existing),
                subtitle = stringResource(R.string.search_group_pick_existing_subtitle),
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                onClick = {
                    showAddSheet = false
                    onOpenBatchPicker()
                },
            )
            SettingNavigationRow(
                icon = { label ->
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = stringResource(R.string.search_group_create_new),
                subtitle = stringResource(R.string.search_group_create_new_subtitle),
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                onClick = {
                    showAddSheet = false
                    onCreateGroup()
                },
            )
            SettingNavigationRow(
                icon = { label ->
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = stringResource(R.string.search_engine_import_paste_json),
                subtitle = stringResource(R.string.search_engine_import_paste_json_subtitle),
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                onClick = {
                    showAddSheet = false
                    showJsonImport = true
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

    SearchEngineJsonImportHost(
        show = showJsonImport,
        existingEngines = settings.searchEngines,
        onDismissRequest = { showJsonImport = false },
        onImport = { imported ->
            onImportParsed(imported)
            showJsonImport = false
        },
    )

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
