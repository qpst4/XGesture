package com.slideindex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsVerticalReorderList
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileShareEngineSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onAddTarget: () -> Unit,
    onDeleteEngine: (String) -> Unit,
    onReorderEngines: (List<SearchEngineConfig>) -> Unit,
) {
    val fileEngines = remember(settings.searchEngines) {
        SearchEngineStore.fileSharePanelEngines(settings.searchEngines)
    }
    var deletingEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    SettingsScreenScaffold(
        title = "文件分享引擎",
        subtitle = "管理拖拽音频或文件时的流转分享应用",
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "file-share-add-section",
            title = "添加目标",
        )
        groupedCardItems(
            keyPrefix = "file-share-add-group",
            items = listOf(
                settingsCardScopeItem("add-share-target") {
                    SettingNavigationRow(
                        icon = { label ->
                            Icon(
                                MiuixIcons.Add,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        title = "添加分享目标",
                        subtitle = "选择支持接收文件分享的应用接口并加入流转栏",
                        onClick = onAddTarget,
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "file-share-list-section",
            title = "已有文件分享引擎 (${fileEngines.size})",
        )
        settingsLazyTipCard(
            key = "file-share-tip",
            text = "• 拖拽音频或通用文件时，流转栏将按照上方顺序展示已启用的文件分享目标。\n• 长按条目右侧区域即可上下拖拽调整显示顺序。\n• 点击删除可将该应用从文件流转栏移除。",
        )

        if (fileEngines.isEmpty()) {
            settingsLazyHint(key = "file-share-empty", text = "暂无配置的文件分享引擎，拖拽文件时将提供系统默认选项")
        } else {
            LazySettingsItem(key = "file-share-list") {
                SettingsVerticalReorderList(
                    items = fileEngines,
                    key = { it.id },
                    onReorder = onReorderEngines,
                ) { engine, _, segmentIndex, segmentCount, dragModifier ->
                    val summaryText = engine.targetActivity?.takeIf { it.isNotBlank() }?.let { act ->
                        "${engine.targetPackage} · ${act.substringAfterLast('.')}"
                    } ?: engine.targetPackage ?: "已安装应用"
                    BasicComponent(
                        modifier = dragModifier.miuixGroupedCardItem(segmentIndex, segmentCount),
                        title = engine.name,
                        summary = summaryText,
                        startAction = {
                            SearchEngineIcon(engine = engine, modifier = Modifier.size(24.dp))
                        },
                        endActions = {
                            IconButton(onClick = { deletingEngine = engine }) {
                                MiuixIcon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = stringResource(R.string.search_engine_delete_confirm),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    val engineToDelete = deletingEngine
    MiuixConfirmDialog(
        confirmDestructive = true,
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
