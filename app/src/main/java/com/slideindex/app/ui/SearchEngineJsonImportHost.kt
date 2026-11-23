package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.search.SearchEngineImporter
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchGroupKind
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 粘贴 JSON → 解析预览 BottomSheet → 勾选导入。
 *
 * 预览不落盘；导入时把选中的 [SearchEngineConfig] 交给 ViewModel，由导入器统一
 * 处理重名合并和组内成员 id remap。
 */
@Composable
fun SearchEngineJsonImportHost(
    show: Boolean,
    existingEngines: List<SearchEngineConfig>,
    onDismissRequest: () -> Unit,
    onImport: (List<SearchEngineConfig>) -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    var dialogOpen by remember { mutableStateOf(true) }
    var candidates by remember { mutableStateOf<List<SearchEngineConfig>?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }

    SearchEngineImportJsonDialog(
        show = dialogOpen,
        onDismiss = {
            dialogOpen = false
            if (candidates == null) onDismissRequest()
        },
        onImport = { raw ->
            runCatching { SearchEngineImporter.parseJsonText(raw) }
                .onSuccess { parsed ->
                    if (parsed.isEmpty()) {
                        Toast.makeText(
                            context,
                            R.string.search_engine_import_json_no_engines,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        candidates = parsed
                        selectedIds.clear()
                        val candidateIds = parsed.map { it.id }.toSet()
                        parsed.filter { engine ->
                            engine.name.lowercase() !in existingEngines
                                .map { it.name.lowercase() }
                                .toSet() &&
                                (engine.engineType != SearchEngineType.SEARCH_GROUP ||
                                    engine.groupMemberIds.all { it in candidateIds })
                        }.forEach { selectedIds.add(it.id) }
                        dialogOpen = false
                    }
                }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.search_engine_import_failed,
                            error.message.orEmpty(),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        },
    )

    val items = candidates
    if (items != null) {
        SearchEngineImportPreviewSheet(
            items = items,
            existingEngines = existingEngines,
            selectedIds = selectedIds,
            onDismiss = {
                candidates = null
                selectedIds.clear()
                onDismissRequest()
            },
            onImport = { selected ->
                candidates = null
                selectedIds.clear()
                onDismissRequest()
                onImport(selected)
            },
        )
    }
}

@Composable
private fun SearchEngineImportPreviewSheet(
    items: List<SearchEngineConfig>,
    existingEngines: List<SearchEngineConfig>,
    selectedIds: MutableList<String>,
    onDismiss: () -> Unit,
    onImport: (List<SearchEngineConfig>) -> Unit,
) {
    val existingNames = remember(existingEngines) {
        existingEngines.map { it.name.trim().lowercase() }.toSet()
    }
    val candidateIds = remember(items) { items.map { it.id }.toSet() }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val listMaxHeight = (screenHeightDp * 0.52f).coerceIn(320f, 520f).dp
    val itemShape = RoundedCornerShape(16.dp)
    val primary = MiuixTheme.colorScheme.primary

    fun isAvailable(engine: SearchEngineConfig): Boolean {
        if (engine.name.trim().lowercase() in existingNames) return false
        if (engine.engineType == SearchEngineType.SEARCH_GROUP) {
            if (engine.groupMemberIds.isEmpty() ||
                engine.groupMemberIds.any { it !in candidateIds }
            ) {
                return false
            }
        }
        return true
    }

    MiuixBottomSheet(
        show = true,
        title = stringResource(R.string.search_engine_import_config_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiuixText(
                text = "勾选要导入的搜索引擎或搜索组；已存在或缺少成员的项不会重复导入。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = listMaxHeight),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items, key = { it.id }) { engine ->
                    val available = isAvailable(engine)
                    val selected = engine.id in selectedIds
                    val group = engine.engineType == SearchEngineType.SEARCH_GROUP
                    val status = when {
                        engine.name.trim().lowercase() in existingNames -> "已存在"
                        group && (engine.groupMemberIds.isEmpty() ||
                            engine.groupMemberIds.any { it !in candidateIds }) -> "缺少成员"
                        else -> null
                    }
                    val cardBg = if (selected) {
                        primary.copy(alpha = 0.12f)
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(cardBg)
                            .border(
                                width = 1.dp,
                                color = if (selected) primary.copy(alpha = 0.55f) else Color.Transparent,
                                shape = itemShape,
                            )
                            .clickable(enabled = available) {
                                if (selected) selectedIds.remove(engine.id)
                                else selectedIds.add(engine.id)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .alpha(if (available) 1f else 0.55f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SearchEngineIcon(
                            engine = engine,
                            modifier = Modifier.size(40.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            MiuixText(
                                text = engine.name.ifBlank { "未命名" },
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MiuixText(
                                text = if (group) {
                                    if (engine.groupKind == SearchGroupKind.IMAGE_SHARE) {
                                        "分享组 · ${engine.groupMemberIds.size} 个成员"
                                    } else {
                                        "搜索组 · ${engine.groupMemberIds.size} 个成员"
                                    }
                                } else {
                                    engineTypeLabel(engine.engineType)
                                },
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            status?.let {
                                MiuixText(
                                    text = it,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = if (it == "已存在") {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    } else {
                                        primary
                                    },
                                )
                            }
                        }
                        Checkbox(
                            state = if (selected) ToggleableState.On else ToggleableState.Off,
                            enabled = available,
                            onClick = {
                                if (selected) selectedIds.remove(engine.id)
                                else selectedIds.add(engine.id)
                            },
                        )
                    }
                }
            }

            val availableItems = items.filter { isAvailable(it) }
            val selectedCount = selectedIds.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = "已选 $selectedCount 项",
                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                    color = primary,
                )
                MiuixText(
                    text = if (selectedCount == availableItems.size && availableItems.isNotEmpty()) {
                        "全不选"
                    } else {
                        "全选"
                    },
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    color = primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = availableItems.isNotEmpty()) {
                            if (selectedCount == availableItems.size && availableItems.isNotEmpty()) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                availableItems.forEach { selectedIds.add(it.id) }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            HorizontalDivider(
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f),
                thickness = 0.5.dp,
            )

            val selected = items.filter { it.id in selectedIds }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (selected.isNotEmpty()) primary.copy(alpha = 0.16f)
                        else MiuixTheme.colorScheme.surfaceContainer,
                    )
                    .clickable(enabled = selected.isNotEmpty()) {
                        if (selected.isNotEmpty()) onImport(selected)
                    }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiuixText(
                    text = if (selected.isEmpty()) "请选择要导入的项目" else "导入 ${selected.size} 项",
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                    color = if (selected.isNotEmpty()) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}

@Composable
private fun engineTypeLabel(type: SearchEngineType): String = when (type) {
    SearchEngineType.DIRECT_LINK -> stringResource(R.string.search_engine_type_direct_link)
    SearchEngineType.JUMP_TO_ACTIVITY -> stringResource(R.string.search_engine_type_jump_activity)
    SearchEngineType.EXTERN_JUMP_LINK -> stringResource(R.string.search_engine_type_extern_jump)
    SearchEngineType.SHARE_TO_APP -> stringResource(R.string.search_engine_type_share)
    SearchEngineType.SHARE_IMAGE_TO_APP -> stringResource(R.string.search_engine_type_share_image)
    SearchEngineType.SHARE_FILE_TO_APP -> "文件分享"
    SearchEngineType.SEARCH_GROUP -> stringResource(R.string.search_group_entry_title)
}
