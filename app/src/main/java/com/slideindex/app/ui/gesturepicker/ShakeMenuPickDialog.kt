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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shake.ShakeMenu
import com.slideindex.app.shake.ShakeMenuLimits
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「晃动菜单」动作的二级选择弹窗：从已建好的菜单里挑一个。
 *
 * 与 [MacroPickDialog] 同一个套路：只保存菜单 id，菜单之后改名/改动作都不需要重新绑定。
 * 只能在普通 Activity 窗口里用（选择器本身就是普通页面）。
 */
@Composable
fun ShakeMenuPickDialog(
    menus: List<ShakeMenu>,
    onDismissRequest: () -> Unit,
    onPick: (ShakeMenu) -> Unit,
    /** 当前已绑定的菜单 id：列表里标出来，换菜单时不用自己找。 */
    selectedMenuId: String? = null,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.shake_menu_pick_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            if (menus.isEmpty()) {
                Text(
                    text = stringResource(R.string.shake_menu_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    menus.forEachIndexed { index, menu ->
                        val selected = menu.id == selectedMenuId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(menu) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ShakeMenuLimits.displayName(menu, index),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = stringResource(
                                        R.string.shake_menu_direction_count,
                                        menu.configuredDirections.size,
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
