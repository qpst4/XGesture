package com.slideindex.app.settings.macro.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.compose.collectPickerAppsAsState
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsLazyTipCard

/**
 * 目标 App 选择页：列出可启动的应用（图标 + 名称 + 包名），选一个作为宏的目标。
 *
 * 首行固定是「不指定应用」：选它就等于把目标清空 = 全局宏（不拉起任何 App，
 * 录制 / 回放都从当前界面开始）。没有这一行，绑过 App 的宏就再也回不到全局。
 *
 * 数据与其它挑应用页面同源（`PickerApps`）：分身也会列出来，选中后存 `(包名, userId)`，
 * 录制/执行时由 `MacroEngine` 负责跨 user 启动。旧数据是裸包名，等价于 user 0。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MacroTargetAppPickScreen(
    selectedTarget: String,
    onBack: () -> Unit,
    onSelect: (AppInfo) -> Unit,
    /** 选「不指定应用」：把目标清空（草稿里为空 = 全局宏）。 */
    onClearSelection: () -> Unit,
) {
    val apps by collectPickerAppsAsState(includeClones = true)
    var query by remember { mutableStateOf("") }
    val emptyText = stringResource(R.string.macro_target_empty)
    val noneSelected = selectedTarget.isBlank()

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        val matched = if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q) ||
                    it.pinyinKey.contains(q)
            }
        }
        matched.sortedBy { it.pinyinKey }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.macro_target_pick_title),
        pageHint = stringResource(R.string.macro_target_pick_subtitle),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
    ) {
        // 「不指定应用」与下面的应用行是同一张卡片：索引从 0 开始，应用行整体 +1，
        // 卡片计数也 +1（否则首行/末行的圆角切段会错位）。
        val segmentCount = filtered.size + 1
        item(key = "macro-target-none") {
            Md3PickerListRow(
                segmentIndex = 0,
                segmentCount = segmentCount,
                title = stringResource(R.string.macro_target_pick_none),
                subtitle = stringResource(R.string.macro_target_pick_none_desc),
                selected = noneSelected,
                onClick = onClearSelection,
                leadingContent = {
                    // 与编辑页「未选目标」时显示的图标保持一致。
                    Md3PickerIconLeading(
                        icon = Icons.Outlined.Apps,
                        selected = noneSelected,
                        contentDescription = stringResource(R.string.macro_target_pick_none),
                    )
                },
                trailingMode = PickerTrailingMode.Radio,
            )
        }
        if (filtered.isEmpty()) {
            settingsLazyTipCard(
                key = "macro-target-empty",
                text = emptyText,
            )
            return@SettingsLazyScreenScaffoldWithExpandableSearch
        }
        items(
            count = filtered.size,
            key = { index ->
                AppTarget.encode(filtered[index].packageName, filtered[index].cloneUserId)
            },
        ) { index ->
            val app = filtered[index]
            val target = AppTarget.encode(app.packageName, app.cloneUserId)
            val selected = target == selectedTarget
            Md3PickerListRow(
                segmentIndex = index + 1,
                segmentCount = segmentCount,
                title = app.label,
                subtitle = if (selected) {
                    stringResource(R.string.macro_target_selected, app.packageName)
                } else {
                    app.packageName
                },
                selected = selected,
                onClick = { onSelect(app) },
                leadingContent = { Md3PickerAppLeading(app) },
                trailingMode = PickerTrailingMode.Radio,
            )
        }
    }
}

/** 编辑页显示"已选目标 App"：优先应用名，取不到时退回包名；分身带「· 分身」后缀。 */
internal fun resolveAppLabel(context: Context, target: String): String {
    if (target.isBlank()) return ""
    val parsed = AppTarget.parse(target)
    val label = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(parsed.packageName, 0)).toString()
    }.getOrDefault(parsed.packageName)
    return if (parsed.isClone) {
        com.slideindex.app.overlay.CloneAppsIndex.labelForUser(context, parsed.cloneUserId, label)
    } else {
        label
    }
}
