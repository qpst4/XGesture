package com.slideindex.app.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixBottomSheet

/**
 * 一墨「数据管理中心」BottomSheet 的原样移植。
 *
 * 布局、分组、图标、卡片尺寸与 LinkGo-X RulesScreen.DataManagementSheet 保持一致；
 * 仅将 Material ModalBottomSheet 换成项目的 Miuix 玻璃 BottomSheet。
 */
@Composable
fun LinkDataManagementSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportFile: () -> Unit,
    onManualImport: () -> Unit,
    onOpenBuiltinRules: (() -> Unit)? = null,
    onExportClipboard: () -> Unit,
    onExportFile: () -> Unit,
) {
    MiuixBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        insideMargin = DpSize(0.dp, 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "数据管理中心",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    ),
                )
            }

            DataSectionTitle("剪贴板")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataGridActionItem(
                    title = "导入剪贴板",
                    subtitle = "读取已复制内容",
                    icon = painterResource(R.drawable.ic_iconoir_paste_clipboard),
                    modifier = Modifier.weight(1f),
                    onClick = onImportClipboard,
                )
                DataGridActionItem(
                    title = "备份至剪贴板",
                    subtitle = "导出为文本格式",
                    icon = painterResource(R.drawable.ic_iconoir_copy),
                    modifier = Modifier.weight(1f),
                    onClick = onExportClipboard,
                )
            }

            Spacer(Modifier.height(16.dp))

            DataSectionTitle("本地文件")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataGridActionItem(
                    title = "恢复本地备份",
                    subtitle = "读取 .json 存档",
                    icon = painterResource(R.drawable.ic_iconoir_import),
                    modifier = Modifier.weight(1f),
                    onClick = onImportFile,
                )
                DataGridActionItem(
                    title = "创建本地备份",
                    subtitle = "将规则保存为.json",
                    icon = painterResource(R.drawable.ic_iconoir_download),
                    modifier = Modifier.weight(1f),
                    onClick = onExportFile,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (onOpenBuiltinRules != null) {
                DataSectionTitle("内置资源")
                DataWideActionItem(
                    title = "内置应用规则库推荐",
                    subtitle = "从内置规则库选择并导入",
                    icon = painterResource(R.drawable.ic_iconoir_archive),
                    onClick = onOpenBuiltinRules,
                )
                Spacer(Modifier.height(16.dp))
            }

            DataSectionTitle("智能辅助")
            DataWideActionItem(
                title = "手动输入识别",
                subtitle = "手动输入并解析规则片段",
                icon = painterResource(R.drawable.ic_iconoir_edit_pencil),
                onClick = onManualImport,
            )
        }
    }
}

@Composable
private fun DataSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun DataGridActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DataWideActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
