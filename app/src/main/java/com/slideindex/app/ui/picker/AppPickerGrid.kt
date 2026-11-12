package com.slideindex.app.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.ui.Md3PickerAppLeading
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 挑应用网格的列数：与快捷启动器 / 蜂窝添加页保持一致。 */
const val APP_PICKER_GRID_COLUMNS = 4

/**
 * 挑应用页面的统一网格（动作选择、小组件面板、快捷启动器添加都走它）。
 *
 * 格子内容复用 [Md3PickerAppLeading]，所以分身黄点、冻结/暂停角标、置灰与其它页面一致。
 * 选中态用右上角勾选圈表示，键统一是 `(包名, userId)`——主实例与分身互不影响。
 */
fun LazyListScope.appPickerGridItems(
    apps: List<AppInfo>,
    selectedKeys: Set<String>,
    onAppClick: (AppInfo) -> Unit,
    columns: Int = APP_PICKER_GRID_COLUMNS,
    /** 单选（槽位）用底色高亮，多选（加入面板）才用右上角勾选徽标。 */
    singleSelect: Boolean = false,
) {
    val rows = apps.chunked(columns)
    items(
        count = rows.size,
        key = { rowIndex -> rows[rowIndex].joinToString("|") { FreezerOperations.keyOf(it) } },
    ) { rowIndex ->
        val row = rows[rowIndex]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { app ->
                AppPickerGridCell(
                    app = app,
                    selected = FreezerOperations.keyOf(app) in selectedKeys,
                    onClick = { onAppClick(app) },
                    singleSelect = singleSelect,
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(columns - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AppPickerGridCell(
    app: AppInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    singleSelect: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            // 58dp 只是图标与选中勾共用的占位，不画底色：真实图标直接展示，
            // 一屏几十个应用不会有满屏浅灰格子把内容压扁（与冰箱网格、搜索结果一致）。
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        if (selected && singleSelect) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 图标铺满 58dp 格位：与「动作」tab 的 58dp 卡片、冰箱网格的 64dp 图标
                // 保持同一视觉密度（约占列宽 75%），否则应用页会比动作页稀一截。
                Md3PickerAppLeading(app, sizeDp = 58)
            }
            if (selected && !singleSelect) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
                        // 勾直接压在彩色图标上，描一圈页面底色才不会和图标糊在一起。
                        .border(1.dp, MiuixTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        Text(
            text = app.label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected && singleSelect) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}
