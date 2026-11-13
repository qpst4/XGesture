package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.RingerCapable
import com.slideindex.app.gesture.RingerMode
import com.slideindex.app.gesture.ToggleCapable
import com.slideindex.app.gesture.ToggleMode
import com.slideindex.app.ui.gesturepicker.ringerModeLabel
import com.slideindex.app.ui.gesturepicker.toggleModeLabel
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu

/** 菜单里的一个模式选项：显示文案 + 是否在当前绑定上打勾 + 选它得到的动作。 */
data class ActionModeOption(
    val label: String,
    val selected: Boolean,
    val action: GestureAction,
)

/**
 * 某个动作的「操作方式」选项；不支持模式的动作返回 null。
 *
 * 两套模式家族：
 * - [ToggleCapable]：切换 / 打开 / 关闭；
 * - [RingerCapable]：切换 / 响铃 / 振动 / 静音。
 *
 * @param bound 当前已绑定的同类型动作（网格里那一格已经绑过什么），用来给菜单打勾。
 */
@Composable
fun actionModeOptions(
    action: GestureAction,
    bound: GestureAction?,
): List<ActionModeOption>? {
    val context = LocalContext.current
    val boundSameType = bound?.takeIf { it.type == action.type }
    return when (action) {
        is ToggleCapable -> ToggleMode.entries.map { mode ->
            ActionModeOption(
                label = toggleModeLabel(context, mode),
                selected = (boundSameType as? ToggleCapable)?.mode == mode,
                action = action.withToggleMode(mode),
            )
        }

        is RingerCapable -> RingerMode.entries.map { mode ->
            ActionModeOption(
                label = ringerModeLabel(context, mode),
                selected = (boundSameType as? RingerCapable)?.ringerMode == mode,
                action = action.withRingerMode(mode),
            )
        }

        else -> null
    }
}

/**
 * 「操作方式」菜单：把图标格子包成菜单锚点，点格子弹出选项。
 *
 * 网格里没有行尾空间，格子本身是唯一可点的地方，所以菜单只能挂在格子上；
 * 用 Miuix 的 **Window 版**菜单（Activity 窗口里弹出、外点/返回正常）。
 *
 * ⚠️ 传进来的 [content] 必须**不带自己的 clickable**：锚点自己会接管点击，
 * 内层再挂点击会把事件吃掉，菜单就弹不出来。
 */
@Composable
fun ActionModeMenu(
    options: List<ActionModeOption>,
    onPick: (GestureAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val items = options.map { option ->
        DropdownItem(
            text = option.label,
            selected = option.selected,
            onClick = { onPick(option.action) },
        )
    }
    WindowIconDropdownMenu(
        entry = DropdownEntry(items = items),
        modifier = modifier,
        minWidth = 58.dp,
        minHeight = 58.dp,
        // 透明底：锚点只负责"可点 + 定位"，视觉仍由格子自己画。
        backgroundColor = Color.Transparent,
        cornerRadius = 17.dp,
    ) {
        content()
    }
}
