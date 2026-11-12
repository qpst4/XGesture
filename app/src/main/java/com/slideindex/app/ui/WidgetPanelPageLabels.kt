package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.widget.WidgetPanelDefaults

/**
 * 「小组件面板」动作里页 id 的显示名。
 *
 * 与 `quickLauncherPanelLabel` 同语义：优先用户起的页名，没起名就是「第 N 页」；
 * 页已被删除时按第一页显示（运行期也是回落到第一页）。
 */
@Composable
fun widgetPanelPageLabel(settings: AppSettings, pageId: String): String {
    val pages = WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
    val index = pages.indexOfFirst { it.id.toString() == pageId }.takeIf { it >= 0 } ?: 0
    val page = pages[index]
    return page.name.ifBlank {
        stringResource(R.string.widget_panel_page_default_name, index + 1)
    }
}
