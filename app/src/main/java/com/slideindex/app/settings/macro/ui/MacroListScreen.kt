package com.slideindex.app.settings.macro.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.macro.Macro
import com.slideindex.app.settings.macro.MacroListState
import com.slideindex.app.settings.macro.MacroPresentation
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixGroupedCard
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.PickerAppIconBitmap

/** 列表页悬浮按钮的强调色（与仓库其它列表页同一支蓝）。 */
internal val MacroAccent = Color(0xFF3478F6)

/**
 * 「手势宏」列表页。
 *
 * 数据来源是 `MacroStore`（不是可观察流），所以列表由调用方（导航层）负责在进入页面
 * 与增删改后重新读取；这里只负责搜索过滤、空态与删除确认。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MacroListScreen(
    macros: List<Macro>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenMacro: (String) -> Unit,
    onDeleteMacro: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Macro?>(null) }
    val state = remember(macros, query) { MacroListState(macros, query) }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.macro_list_title),
        pageHint = stringResource(R.string.macro_list_hint),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        hintResId = R.string.macro_search_hint,
        floatingActionButton = {
            // 用仓库自己的悬浮按钮封装（和冰箱/命令面板一致），避免 Material3 与 Miuix 的按钮不是一套。
            MiuixSettingsFab(
                onClick = onCreate,
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.macro_create),
            )
        },
        // 悬浮按钮压在列表上：底部预留出它的高度，最后一行不会被切掉。
        fabInset = MiuixSettingsFabInset,
    ) {
        if (state.isEmpty) {
            item(key = "macro-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (state.sourceIsEmpty) {
                                R.string.macro_list_empty
                            } else {
                                R.string.macro_list_no_match
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@SettingsLazyScreenScaffoldWithExpandableSearch
        }

        itemsIndexed(
            items = state.visible,
            key = { _, macro -> "macro-${macro.id}" },
        ) { index, macro ->
            // 不要额外加水平内边距：`MiuixGroupedCard` 自身已经带了左右 12dp（会叠加成 24dp）。
            MiuixGroupedCard(
                index = index,
                count = state.visible.size,
            ) {
                MacroListRow(
                    macro = macro,
                    onClick = { onOpenMacro(macro.id) },
                    onLongClick = { pendingDelete = macro },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        MiuixConfirmDialog(
            confirmDestructive = true,
            show = true,
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.macro_delete_title),
            message = stringResource(
                R.string.macro_delete_message,
                MacroPresentation.displayName(target.name),
            ),
            confirmText = stringResource(R.string.macro_delete_confirm),
            onConfirm = { onDeleteMacro(target.id) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MacroListRow(
    macro: Macro,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val targetPackage = macro.targetPackage
    // 左侧显示这条宏"绑定到哪个 App"：直接用它自己的图标，一眼可辨。
    var targetIcon by remember(targetPackage) {
        mutableStateOf(
            if (targetPackage.isBlank()) null else PickerAppIconBitmap.peek(targetPackage),
        )
    }
    var iconFailed by remember(targetPackage) { mutableStateOf(false) }
    LaunchedEffect(targetPackage) {
        if (targetPackage.isNotBlank() && targetIcon == null && !iconFailed) {
            val loaded = PickerAppIconBitmap.load(context, targetPackage)
            if (loaded == null) iconFailed = true else targetIcon = loaded
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bitmap = targetIcon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(MACRO_ROW_ICON_DP),
                contentScale = ContentScale.Fit,
            )
        } else {
            // 没选目标 App / 图标读不出来时退回矢量图标（与编辑页那一行一致）。
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = null,
                modifier = Modifier.size(MACRO_ROW_ICON_DP),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = MacroPresentation.displayName(macro.name),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.macro_row_summary,
                    MacroPresentation.displayName(macro.name),
                    MacroPresentation.actionCount(macro),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 列表行左侧目标 App 图标的尺寸。 */
private val MACRO_ROW_ICON_DP = 32.dp
