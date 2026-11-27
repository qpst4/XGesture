package com.slideindex.app.ui.link

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.link.LinkDispatchRule
import com.slideindex.app.link.LinkExemptDomain
import com.slideindex.app.link.LinkExtractPattern
import com.slideindex.app.link.LinkGoRuleConverter
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URL
import java.util.UUID

private data class BuiltinTemplateItem(
    val rule: LinkDispatchRule,
    val appName: String,
    val isInstalled: Boolean,
    val isRedispatch: Boolean,
    val alreadyPresent: Boolean,
)

/**
 * 常用链接模板导入 BottomSheet（1:1 还原 gevo bottom_sheet_import_selection.xml 与 item_import_selection.xml 的独立圆角卡片质感）。
 */
@Composable
fun LinkTemplateImportDialog(
    show: Boolean,
    existingRules: List<LinkDispatchRule>,
    customCandidates: List<LinkDispatchRule>? = null,
    customExtracts: List<LinkExtractPattern> = emptyList(),
    dialogTitle: String = "常用链接模板",
    dialogDescription: String = "已为你勾选已安装应用的可用规则，可按需调整后导入。",
    preselectAllAvailable: Boolean = false,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    onImport: (List<LinkDispatchRule>) -> Unit,
    onImportWithExtracts: ((List<LinkDispatchRule>, List<LinkExtractPattern>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val listMaxHeight = (screenHeightDp * 0.56f).coerceIn(360f, 540f).dp
    var isLoading by remember { mutableStateOf(true) }
    var templateItems by remember { mutableStateOf<List<BuiltinTemplateItem>>(emptyList()) }
    val selectedRuleIds = remember { mutableStateListOf<String>() }
    val selectedExtractIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(existingRules, customCandidates, customExtracts, preselectAllAvailable) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val existingPatterns = existingRules.map { it.pattern }.toSet()
            val sourceRules = (customCandidates ?: LinkGoRuleConverter.loadBuiltin(context))
                .sortedByDescending { it.resolveStrategy == com.slideindex.app.link.LinkResolveStrategy.REDISPATCH }
            val items = sourceRules.map { rule ->
                val isRedispatch = rule.resolveStrategy == com.slideindex.app.link.LinkResolveStrategy.REDISPATCH
                val isInstalled = isRedispatch || (rule.targetPackage.isNotBlank() && runCatching {
                    context.packageManager.getPackageInfo(rule.targetPackage, 0)
                }.isSuccess)
                val already = rule.pattern in existingPatterns
                val appName = if (rule.targetPackage.isBlank()) {
                    "重分发规则"
                } else {
                    runCatching {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(rule.targetPackage, 0)).toString()
                    }.getOrNull()?.ifBlank { rule.targetPackage } ?: rule.targetPackage
                }
                if (rule.targetPackage.isNotBlank()) {
                    linkAppNameCache[rule.targetPackage] = appName
                }
                BuiltinTemplateItem(
                    rule = rule,
                    appName = appName,
                    isInstalled = isInstalled,
                    isRedispatch = isRedispatch,
                    alreadyPresent = already,
                )
            }.let { list ->
                if (customCandidates != null) list
                else list.sortedWith(
                    compareByDescending<BuiltinTemplateItem> { it.isInstalled && !it.alreadyPresent }
                        .thenByDescending { it.isInstalled }
                )
            }
            templateItems = items
            selectedRuleIds.clear()
            selectedExtractIds.clear()
            customExtracts.forEach { selectedExtractIds.add(it.id) }
            if (preselectAllAvailable) {
                items.filter { !it.alreadyPresent }.forEach {
                    selectedRuleIds.add(it.rule.id)
                }
            } else {
                // 智能预勾选：本机已安装且尚未添加的规则
                items.filter { it.isInstalled && !it.alreadyPresent }.forEach {
                    selectedRuleIds.add(it.rule.id)
                }
            }
        }
        isLoading = false
    }

    MiuixBottomSheet(
        show = show,
        title = dialogTitle,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiuixText(
                text = dialogDescription,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    MiuixText(
                        text = "正在检测本机应用与内置规则…",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                val availableCount = templateItems.count { !it.alreadyPresent } + customExtracts.size
                val selectedCount = selectedRuleIds.size + selectedExtractIds.size
                val itemShape = RoundedCornerShape(16.dp)
                val primaryColor = MiuixTheme.colorScheme.primary

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(templateItems, key = { _, item -> item.rule.id }) { index, item ->
                        if (index == 0 ||
                            templateItems[index - 1].isRedispatch != item.isRedispatch
                        ) {
                            MiuixText(
                                text = if (item.isRedispatch) "重分发规则" else "跳转规则",
                                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp),
                            )
                        }
                        val isSelected = item.rule.id in selectedRuleIds
                        val statusText = when {
                            item.alreadyPresent -> "已存在"
                            item.isRedispatch -> "重分发"
                            !item.isInstalled -> "未安装"
                            else -> ""
                        }
                        val cardBgColor = when {
                            isSelected -> primaryColor.copy(alpha = 0.12f)
                            else -> MiuixTheme.colorScheme.surfaceContainer
                        }
                        val cardBorderColor = when {
                            isSelected -> primaryColor.copy(alpha = 0.55f)
                            else -> Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(cardBgColor)
                                .border(width = 1.dp, color = cardBorderColor, shape = itemShape)
                                .clickable(enabled = !item.alreadyPresent) {
                                    if (isSelected) {
                                        selectedRuleIds.remove(item.rule.id)
                                    } else {
                                        selectedRuleIds.add(item.rule.id)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .alpha(if (item.alreadyPresent) 0.55f else 1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LinkRuleIcon(
                                iconType = item.rule.iconType,
                                iconValue = item.rule.iconValue,
                                targetPackage = item.rule.targetPackage,
                                isRedispatch = item.isRedispatch,
                                size = 40.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                MiuixText(
                                    text = item.rule.name.ifBlank { item.rule.pattern },
                                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MiuixText(
                                    text = item.rule.pattern,
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (statusText.isNotEmpty()) {
                                    MiuixText(
                                        text = statusText,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = if (item.alreadyPresent) {
                                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        } else {
                                            primaryColor
                                        },
                                    )
                                }
                            }
                            Checkbox(
                                state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                                enabled = !item.alreadyPresent,
                                onClick = {
                                    if (isSelected) selectedRuleIds.remove(item.rule.id)
                                    else selectedRuleIds.add(item.rule.id)
                                },
                            )
                        }
                    }

                    if (customExtracts.isNotEmpty()) {
                        item(key = "extract_rules_header") {
                            MiuixText(
                                text = "提取规则（${customExtracts.size} 条）",
                                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(customExtracts, key = { "extract_${it.id}" }) { extract ->
                            val isSelected = extract.id in selectedExtractIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(itemShape)
                                    .background(
                                        if (isSelected) primaryColor.copy(alpha = 0.12f)
                                        else MiuixTheme.colorScheme.surfaceContainer
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) primaryColor.copy(alpha = 0.55f)
                                        else Color.Transparent,
                                        shape = itemShape,
                                    )
                                    .clickable {
                                        if (isSelected) selectedExtractIds.remove(extract.id)
                                        else selectedExtractIds.add(extract.id)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MiuixIcon(
                                        painter = painterResource(com.slideindex.app.R.drawable.ic_iconoir_code),
                                        contentDescription = "提取规则",
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    MiuixText(
                                        text = extract.name.ifBlank { "提取规则" },
                                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    MiuixText(
                                        text = extract.pattern,
                                        style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Checkbox(
                                    state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                                    onClick = {
                                        if (isSelected) selectedExtractIds.remove(extract.id)
                                        else selectedExtractIds.add(extract.id)
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = "已选 $selectedCount 项",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                        color = primaryColor,
                    )
                    val selectAllLabel = if (selectedCount == availableCount && availableCount > 0) "全不选" else "全选"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = availableCount > 0) {
                                if (selectedCount == availableCount && availableCount > 0) {
                                    selectedRuleIds.clear()
                                    selectedExtractIds.clear()
                                } else {
                                    selectedRuleIds.clear()
                                    selectedExtractIds.clear()
                                    templateItems.filter { !it.alreadyPresent }.forEach {
                                        selectedRuleIds.add(it.rule.id)
                                    }
                                    customExtracts.forEach {
                                        selectedExtractIds.add(it.id)
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        MiuixText(
                            text = selectAllLabel,
                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                            color = primaryColor,
                        )
                    }
                }

                HorizontalDivider(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f),
                    thickness = 0.5.dp,
                )

                val hasSelection = selectedCount > 0 || selectedExtractIds.isNotEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (hasSelection) primaryColor.copy(alpha = 0.16f)
                            else MiuixTheme.colorScheme.surfaceContainer
                        )
                        .clickable(enabled = hasSelection) {
                            val selected = templateItems
                                .filter { it.rule.id in selectedRuleIds }
                                .map { it.rule }
                            val selectedExtracts = customExtracts
                                .filter { it.id in selectedExtractIds }
                            if (selected.isNotEmpty() || selectedExtracts.isNotEmpty()) {
                                if (onImportWithExtracts != null) {
                                    onImportWithExtracts(selected, selectedExtracts)
                                } else {
                                    onImport(selected)
                                }
                                onDismissRequest()
                                Toast.makeText(
                                    context,
                                    "已导入 ${selected.size} 条规则、${selectedExtracts.size} 条提取规则",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            onDismissRequest()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = "导入",
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                        color = if (selectedCount > 0) MiuixTheme.colorScheme.onSurface
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/**
 * 豁免域名管理 BottomSheet（严格对齐 gevo bottom_sheet_link_exempts.xml：顶部说明 -> 域名列表 -> 底部「+ 添加豁免域名」按钮）。
 */
@Composable
fun LinkExemptsBottomSheet(
    show: Boolean,
    exempts: List<LinkExemptDomain>,
    onDismissRequest: () -> Unit,
    onAddExempt: (LinkExemptDomain) -> Unit,
    onToggleExempt: (LinkExemptDomain, Boolean) -> Unit,
    onDeleteExempt: (LinkExemptDomain) -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }

    MiuixBottomSheet(
        show = true,
        title = "豁免域名",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixText(
                text = "命中的链接会跳过所有规则，直接交给系统默认方式打开。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (exempts.isEmpty()) {
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    outerHorizontalPadding = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MiuixText(
                            text = "还没有豁免域名",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    outerHorizontalPadding = 0.dp,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        items(exempts, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    MiuixText(
                                        text = item.domain,
                                        style = MiuixTheme.textStyles.body1.copy(fontFamily = FontFamily.Monospace),
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    MiuixText(
                                        text = if (item.sourcePackage.isBlank()) "所有应用" else item.sourcePackage,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { onToggleExempt(item, it) },
                                )
                                IconButton(onClick = { onDeleteExempt(item) }) {
                                    MiuixIcon(imageVector = Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }

            GlassDialogButton(
                role = GlassDialogButtonRole.Primary,
                onClick = { showAddSheet = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                MiuixText(
                    text = "+ 添加豁免域名",
                    style = MiuixTheme.textStyles.button,
                    maxLines = 1,
                )
            }
        }
    }

    if (showAddSheet) {
        var newDomainInput by remember { mutableStateOf("") }
        MiuixBottomSheet(
            show = true,
            title = "添加豁免域名",
            onDismissRequest = { showAddSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixLabeledTextField(
                    value = newDomainInput,
                    onValueChange = { newDomainInput = it },
                    label = "域名（如 example.com）",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassDialogButton(
                        role = GlassDialogButtonRole.Normal,
                        onClick = { showAddSheet = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        MiuixText(
                            text = "取消",
                            style = MiuixTheme.textStyles.button,
                            maxLines = 1,
                        )
                    }
                    GlassDialogButton(
                        role = GlassDialogButtonRole.Primary,
                        onClick = {
                            val normalized = normalizeExemptDomain(newDomainInput.trim())
                            if (normalized == null) {
                                Toast.makeText(context, "请输入有效域名", Toast.LENGTH_SHORT).show()
                                return@GlassDialogButton
                            }
                            if (exempts.any { it.domain.equals(normalized, ignoreCase = true) }) {
                                Toast.makeText(context, "该域名已存在", Toast.LENGTH_SHORT).show()
                                return@GlassDialogButton
                            }
                            onAddExempt(LinkExemptDomain(id = UUID.randomUUID().toString(), domain = normalized))
                            showAddSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        MiuixText(
                            text = "添加",
                            style = MiuixTheme.textStyles.button,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeExemptDomain(raw: String): String? {
    if (raw.isEmpty()) return null
    val candidate = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        raw
    } else {
        "https://$raw"
    }
    val host = runCatching { URL(candidate).host?.lowercase()?.trim('.') }.getOrNull().orEmpty()
    if (host.isEmpty() || !host.contains('.') || host.any { it.isWhitespace() }) return null
    return host
}

/**
 * 链接提取规则管理 BottomSheet（严格对齐 gevo bottom_sheet_link_extracts.xml：
 * 顶部说明 -> 规则列表（点击任意项弹出编辑表单） -> 底部「+ 添加提取规则」按钮）。
 */
@Composable
fun LinkExtractsBottomSheet(
    show: Boolean,
    extracts: List<LinkExtractPattern>,
    onDismissRequest: () -> Unit,
    onAddExtract: (LinkExtractPattern) -> Unit,
    onUpdateExtract: (LinkExtractPattern) -> Unit,
    onToggleExtract: (LinkExtractPattern, Boolean) -> Unit,
    onDeleteExtract: (LinkExtractPattern) -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    var showEditorSheet by remember { mutableStateOf(false) }
    var editingExtract by remember { mutableStateOf<LinkExtractPattern?>(null) }

    MiuixBottomSheet(
        show = true,
        title = "链接提取规则",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixText(
                text = "从选中文本中找出链接用的正则。自定义规则优先匹配，都不命中时回退到内置识别（适合磁力链接这类特殊格式）。点击已有规则可编辑。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = 0.dp,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                ) {
                    items(extracts, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingExtract = item
                                    showEditorSheet = true
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                MiuixText(
                                    text = if (item.isBuiltin) "${item.name} · 内置" else item.name,
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                MiuixText(
                                    text = item.pattern,
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Switch(
                                checked = item.isEnabled,
                                onCheckedChange = { onToggleExtract(item, it) },
                            )
                            if (!item.isBuiltin) {
                                IconButton(onClick = { onDeleteExtract(item) }) {
                                    MiuixIcon(imageVector = Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }

            GlassDialogButton(
                role = GlassDialogButtonRole.Primary,
                onClick = {
                    editingExtract = null
                    showEditorSheet = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                MiuixText(
                    text = "+ 添加提取规则",
                    style = MiuixTheme.textStyles.button,
                    maxLines = 1,
                )
            }
        }
    }

    if (showEditorSheet) {
        val current = editingExtract
        var editName by remember(current) { mutableStateOf(current?.name.orEmpty()) }
        var editPattern by remember(current) { mutableStateOf(current?.pattern.orEmpty()) }

        MiuixBottomSheet(
            show = true,
            title = if (current == null) "添加提取规则" else "编辑提取规则",
            onDismissRequest = { showEditorSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixLabeledTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = "规则名称（如 磁力链接）",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                )
                MiuixLabeledTextField(
                    value = editPattern,
                    onValueChange = { editPattern = it },
                    label = "提取链接的正则表达式",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (current != null && !current.isBuiltin) {
                        GlassDialogButton(
                            role = GlassDialogButtonRole.Normal,
                            onClick = {
                                onDeleteExtract(current)
                                showEditorSheet = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText(
                                text = "删除",
                                style = MiuixTheme.textStyles.button,
                                maxLines = 1,
                            )
                        }
                    } else {
                        GlassDialogButton(
                            role = GlassDialogButtonRole.Normal,
                            onClick = { showEditorSheet = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText(
                                text = "取消",
                                style = MiuixTheme.textStyles.button,
                                maxLines = 1,
                            )
                        }
                    }
                    GlassDialogButton(
                        role = GlassDialogButtonRole.Primary,
                        onClick = {
                            val trimmed = editPattern.trim()
                            if (trimmed.isEmpty() || runCatching { Regex(trimmed) }.isFailure) {
                                Toast.makeText(context, "正则表达式无效", Toast.LENGTH_SHORT).show()
                                return@GlassDialogButton
                            }
                            val finalName = editName.trim().ifEmpty { trimmed.take(24) }
                            if (current == null) {
                                onAddExtract(
                                    LinkExtractPattern(
                                        id = UUID.randomUUID().toString(),
                                        name = finalName,
                                        pattern = trimmed,
                                        isEnabled = true,
                                        isBuiltin = false,
                                    )
                                )
                            } else {
                                onUpdateExtract(
                                    current.copy(
                                        name = finalName,
                                        pattern = trimmed,
                                    )
                                )
                            }
                            showEditorSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        MiuixText(
                            text = "保存",
                            style = MiuixTheme.textStyles.button,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
