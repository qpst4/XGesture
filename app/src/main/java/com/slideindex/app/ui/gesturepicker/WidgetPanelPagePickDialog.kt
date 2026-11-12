package com.slideindex.app.ui.gesturepicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.widget.WidgetPanelPage
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「小组件面板」动作的二级选择弹窗：选打开**哪一页**。
 *
 * 与 [ShakeMenuPickDialog] / [MacroPickDialog] 同一套路：只保存页 id，
 * 页之后改名、改网格都不需要重新绑定。只能在普通 Activity 窗口里用。
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
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

            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
