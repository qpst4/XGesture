package com.slideindex.app.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R

/**
 * 一墨 / LinkGo 分发规则页长按卡片后弹出的底部工具栏原样移植。
 */
@Composable
fun LinkSelectionBottomBar(
    onToggleSelectAll: () -> Unit,
    allSelected: Boolean,
    onToggleEnabled: () -> Unit,
    allEnabled: Boolean,
    onShare: () -> Unit,
    onSaveToFile: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    anySelected: Boolean,
    showDelete: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleSelectAll) {
                        Icon(
                            painter = painterResource(
                                if (allSelected) R.drawable.ic_iconoir_check_square
                                else R.drawable.ic_iconoir_square,
                            ),
                            contentDescription = if (allSelected) "取消全选" else "全选",
                            tint = if (allSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    VerticalDivider(
                        modifier = Modifier.height(24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onToggleEnabled, enabled = anySelected) {
                        Icon(
                            painter = painterResource(
                                if (allEnabled) R.drawable.ic_iconoir_toggle_on
                                else R.drawable.ic_iconoir_toggle_off,
                            ),
                            contentDescription = if (allEnabled) "批量停用选中" else "批量启用选中",
                            tint = if (allEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                IconButton(onClick = onShare, enabled = anySelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconoir_share_ios),
                        contentDescription = "分享选中的规则",
                    )
                }
                IconButton(onClick = onSaveToFile, enabled = anySelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconoir_download),
                        contentDescription = "导出为文件",
                    )
                }
                IconButton(onClick = onDuplicate, enabled = anySelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconoir_copy),
                        contentDescription = "批量复刻",
                    )
                }
                if (showDelete) {
                    IconButton(onClick = onDelete, enabled = anySelected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_iconoir_trash),
                            contentDescription = "删除选中",
                            tint = if (anySelected) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        )
                    }
                }
            }
        }
    }
}
