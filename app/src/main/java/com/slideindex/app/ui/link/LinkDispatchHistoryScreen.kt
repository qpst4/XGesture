package com.slideindex.app.ui.link

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.link.LinkDispatchHistoryItem
import com.slideindex.app.link.LinkDispatchHistoryRepository
import com.slideindex.app.link.LinkLauncher
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 链接分发历史流水日志页面（毫秒级本地过滤，清晰记录每次跳转与命中）。
 */
@Composable
fun LinkDispatchHistoryScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val historyList by LinkDispatchHistoryRepository.observeHistory(context).collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<LinkDispatchHistoryItem?>(null) }

    val filteredList = remember(historyList, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) historyList
        else historyList.filter {
            it.rawUrl.contains(q, ignoreCase = true) ||
                it.finalUrl.contains(q, ignoreCase = true) ||
                (it.matchedRuleName?.contains(q, ignoreCase = true) == true) ||
                (it.targetPackage?.contains(q, ignoreCase = true) == true)
        }
    }

    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    SettingsScreenScaffold(
        title = "跳转记录",
        onBack = onBack,
        actions = {
            if (historyList.isNotEmpty()) {
                MiuixTopBarIconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = MiuixIcons.Glass.Delete,
                        contentDescription = "清空记录",
                        tint = MiuixTheme.colorScheme.error,
                    )
                }
            }
        },
    ) {
        // 搜索栏
        item(key = "history_search_field") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                MiuixLabeledTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索网址、规则名称或目标应用",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                )
            }
        }

        if (filteredList.isEmpty()) {
            item(key = "history_empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiuixText(
                            text = if (searchQuery.isNotBlank()) "没有匹配的记录" else "暂无链接跳转记录",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredList,
                key = { it.id },
            ) { item ->
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    CardSegment(
                        isFirst = true,
                        isLast = true,
                        outerHorizontalPadding = 0.dp,
                    ) {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = item.matchedRuleName ?: "系统默认打开",
                            summary = "${dateFormat.format(Date(item.timestamp))}\n${item.rawUrl}",
                            startAction = if (!item.targetPackage.isNullOrBlank()) {
                                {
                                    Box(modifier = Modifier.padding(end = 12.dp)) {
                                        LinkAppIcon(packageName = item.targetPackage, size = 38.dp)
                                    }
                                }
                            } else null,
                            endActions = {
                                if (item.isReDispatch) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        MiuixText(
                                            text = "重分发",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                            onClick = { selectedItemForDetail = item },
                        )
                    }
                }
            }
        }
    }

    // 单项详情弹窗
    val detail = selectedItemForDetail
    if (detail != null) {
        MiuixBottomSheet(
            show = true,
            title = "跳转详情",
            onDismissRequest = { selectedItemForDetail = null },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 原始网址
                Column {
                    MiuixText(text = "原始网址", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp),
                    ) {
                        MiuixText(text = detail.rawUrl, style = MiuixTheme.textStyles.footnote1, fontFamily = FontFamily.Monospace)
                    }
                }

                // 最终跳转目标
                if (detail.finalUrl != detail.rawUrl) {
                    Column {
                        MiuixText(text = "解析后最终地址", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .padding(8.dp),
                        ) {
                            MiuixText(text = detail.finalUrl, style = MiuixTheme.textStyles.footnote1, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }

                // 目标应用
                if (!detail.targetPackage.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        LinkAppIcon(packageName = detail.targetPackage, size = 36.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            val displayName = linkAppNameCache[detail.targetPackage].orEmpty().ifBlank { detail.targetPackage }
                            MiuixText(text = displayName, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold)
                            MiuixText(text = detail.targetPackage, style = MiuixTheme.textStyles.footnote2, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }

                // 复制网址与重新打开按钮
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixTextButton(
                        text = "复制网址",
                        onClick = {
                            clipboardManager.setText(AnnotatedString(detail.finalUrl))
                            Toast.makeText(context, "网址已复制", Toast.LENGTH_SHORT).show()
                            selectedItemForDetail = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = "重新打开",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            val pkg = detail.targetPackage.orEmpty()
                            scope.launch {
                                LinkLauncher.launch(
                                    context = context,
                                    url = detail.finalUrl,
                                    targetPackage = pkg,
                                    excludeFromRecents = false,
                                )
                            }
                            selectedItemForDetail = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // 清空确认弹窗
    MiuixConfirmDialog(
        show = showClearDialog,
        title = "清空记录",
        message = "确定要清空所有跳转历史记录吗？",
        onDismissRequest = { showClearDialog = false },
        confirmText = "清空",
        dismissText = "取消",
        onConfirm = {
            showClearDialog = false
            scope.launch {
                LinkDispatchHistoryRepository.clearHistory(context)
                Toast.makeText(context, "历史记录已清空", Toast.LENGTH_SHORT).show()
            }
        },
    )
}
