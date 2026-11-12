package com.slideindex.app.ui.gesturepicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.widget.WidgetPanelPage
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「小组件面板」动作的二级选择弹窗：选打开**哪一页**。
 *
 * 与 [ShakeMenuPickDialog] / [MacroPickDialog] 同一套路：只保存页 id，
 * 页之后改名、改网格都不需要重新绑定。只能在普通 Activity 窗口里用
 * （悬浮面板 window type 2038 里用 [WidgetPanelPagePickOverlay]）。
 */
@Composable
fun WidgetPanelPagePickDialog(
    pages: List<WidgetPanelPage>,
    /** 当前已绑定的页 id：列表里标出来，换页时不用自己找。 */
    selectedPageId: String?,
    onDismissRequest: () -> Unit,
    onPick: (WidgetPanelPage) -> Unit,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.widget_panel_pick_page_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            WidgetPanelPagePickList(
                pages = pages,
                selectedPageId = selectedPageId,
                onPick = onPick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            )

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/**
 * 悬浮面板里的页选择：**整块换内容**而不是弹 Dialog。
 *
 * 与 `ExpandPanelSlotPicker` 的 LINK / WHEEL / LAUNCHER 子页同一形态：
 * window type 2038 里弹不了平台 Dialog，只能就地渲染。
 * 点一行即确认（和弹窗版一致），左上角「取消」回列表。
 */
@Composable
fun WidgetPanelPagePickOverlay(
    pages: List<WidgetPanelPage>,
    initialPageId: String?,
    listHeight: Dp,
    onBack: () -> Unit,
    onConfirm: (WidgetPanelPage) -> Unit,
) {
    // 必须是**单个**根节点：调用方（AnimatedContent 内容槽）按一个子节点摆放。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(listHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.widget_panel_pick_page_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.cancel))
            }
        }
        WidgetPanelPagePickList(
            pages = pages,
            selectedPageId = initialPageId,
            onPick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/** 页列表本体：弹窗与悬浮面板共用，避免两处行样式/文案漂移。 */
@Composable
fun WidgetPanelPagePickList(
    pages: List<WidgetPanelPage>,
    selectedPageId: String?,
    onPick: (WidgetPanelPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
    ) {
        pages.forEachIndexed { index, page ->
            val selected = page.id.toString() == selectedPageId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(page) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = page.name.ifBlank {
                            stringResource(R.string.widget_panel_page_default_name, index + 1)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.quick_launcher_panel_pick_summary,
                            page.items.size,
                            page.columnCount,
                            page.visibleRowCount,
                            page.items.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
