package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.ui.ActionIconPickerHost
import com.slideindex.app.ui.miuix.MiuixFormDialog

/**
 * 「启动面板」自定义图标的小弹窗：点图标走 [ActionIconPickerHost] 的来源菜单
 *（图标库 / 文字 / 应用图标 / 相册 / 恢复默认）。
 *
 * 图标存在 [com.slideindex.app.ui.GlobalIconStore] 的 `panel:<id>` 键上，
 * 这样圆环、蜂窝、角落轮盘、启动面板里「打开另一个面板」的条目、
 * 以及动作列表/槽位行都会通过 `ActionIconKeyResolver` 自动显示它。
 *
 * **容器只能用 [MiuixFormDialog]，不能换回 miuix `WindowDialog`**：`WindowDialog` 是平台
 * `Dialog` 窗口，而 [ActionIconPickerHost] 的来源菜单是 `MiuixGlassDropdownMenu`——它靠
 * `TopLayer` 提升到**当前窗口**的根层宿主。跨窗口时菜单会被弹窗窗口压在下面（点开只在
 * 弹窗后面露一角、还点不动，真机已复现）。`MiuixFormDialog` 在页面有采样层时走窗口内
 * `GlassDialog`，菜单与弹窗同窗口；根层 `TopLayerHost` 又画在 root `Scaffold` 之后，
 * 所以菜单稳定在弹窗之上。
 */
@Composable
internal fun PanelIconPickerDialog(
    show: Boolean,
    panel: QuickLauncherPanel,
    displayName: String,
    initialIconKey: String?,
    onDismissRequest: () -> Unit,
    onIconChange: (String?) -> Unit,
) {
    var iconKey by remember(panel.id, initialIconKey) { mutableStateOf(initialIconKey) }
    MiuixFormDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.quick_launcher_panel_custom_icon),
        confirmText = stringResource(R.string.confirm),
        // 关闭由 dismissOnConfirm（默认 true）统一走 onDismissRequest。
        onConfirm = {},
        // 原来那版就是单个「确认」键，这里不再加取消键。
        dismissText = null,
    ) {
        Column {
            Text(
                text = stringResource(R.string.quick_launcher_panel_icon_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIconPickerHost(
                    action = GestureAction.QuickLauncher(panel.id),
                    iconKey = iconKey,
                    sizeDp = 56,
                    storageKey = panelIconStorageKey(panel.id),
                    onIconChange = { key ->
                        iconKey = key
                        onIconChange(key)
                    },
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 面板图标的图片落盘键。
 *
 * 用 `global:` 前缀（`ActionIconStorage` 的「任意 key」通道），和 `GlobalIconStore` 里的
 * `panel:` 值前缀分开命名空间，避免将来动作 key 与面板 key 撞名。
 */
internal fun panelIconStorageKey(panelId: String): String = "global:panel:$panelId"
