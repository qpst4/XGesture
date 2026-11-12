package com.slideindex.app.overlay.volumepanel

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.launcher.QuickLauncherPanel

/**
 * 展开面板"槽位选择器"的请求对象。
 *
 * 选择器本体（`ExpandPanelSlotPicker`，581 行）依赖 3 个设置页 picker，
 * 因此由 `:app` 实现并通过 [PanelSlotPickerHook] 注入。
 */
data class SlotPickerRequest(
    val allApps: List<AppInfo>,
    val activityShortcuts: List<ActivityShortcut>,
    val current: GestureAction?,
    val editingSubtitle: String? = null,
    val wheels: List<QuickWheel> = emptyList(),
    val panels: List<QuickLauncherPanel> = emptyList(),
    /** 「小组件面板」动作的页数据源（动作里存页 id）。 */
    val widgetPanelPages: List<com.slideindex.app.widget.WidgetPanelPage> = emptyList(),
    val imeBottom: Dp = 0.dp,
    val onSelect: (GestureAction?) -> Unit,
    val onCancel: () -> Unit,
    val onClearSlot: (() -> Unit)? = null,
)

/** 见 [PanelSlotPickerHook]。 */
fun interface PanelSlotPicker {
    @Composable
    fun content(request: SlotPickerRequest)
}

/** 模块内服务定位点：`:app` 注入真正的槽位选择器。 */
object PanelSlotPickerHook {

    @Volatile
    var picker: PanelSlotPicker? = null
}
