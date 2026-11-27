package com.slideindex.app.ui.link

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.link.LinkExtractPattern
import com.slideindex.app.link.LinkExtractor
import com.slideindex.app.link.LinkRuleRepository
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.LocalMiuixSheetHeaderPadding
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

private data class ExtractionTestHit(
    val url: String,
    val ruleName: String,
)

@Composable
fun LinkExtractionRulesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val extracts by LinkRuleRepository.observeExtractPatterns(context)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newRegex by remember { mutableStateOf("") }
    var showTestSheet by remember { mutableStateOf(false) }
    var testInput by remember { mutableStateOf("") }
    val testHits = remember(testInput, extracts) {
        if (testInput.isBlank()) {
            emptyList()
        } else {
            LinkExtractor.findAllConfigured(testInput, extracts)
                .distinctBy { it.url }
                .map { match ->
                    val ruleName = extracts.firstOrNull { pattern ->
                        pattern.isEnabled && pattern.pattern.isNotBlank() &&
                            runCatching { Regex(pattern.pattern).containsMatchIn(match.url) }
                                .getOrDefault(false)
                    }?.name?.ifBlank { "未命名规则" } ?: "内置链接提取"
                    ExtractionTestHit(match.url, ruleName)
                }
        }
    }

    SettingsLazyScreenScaffold(
        title = "链接提取规则",
        subtitle = "从文本中找出链接的正则，按顺序匹配",
        onBack = onBack,
        actions = {
            GlassIconButton(onClick = { showTestSheet = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_science_lab),
                    contentDescription = "测试链接提取",
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            GlassIconButton(onClick = {
                scope.launch {
                    LinkRuleRepository.updateExtractPatterns(context) {
                        LinkExtractPattern.builtinDefaults()
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "恢复默认提取规则",
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = { showAddDialog = true },
                icon = Icons.Default.Add,
                contentDescription = "添加提取规则",
            )
        },
        fabInset = MiuixSettingsFabInset,
    ) {
        items(extracts, key = { it.id }) { pattern ->
            ExtractPatternCard(
                pattern = pattern,
                onToggle = { enabled ->
                    scope.launch {
                        LinkRuleRepository.updateExtractPatterns(context) { list ->
                            list.map { if (it.id == pattern.id) it.copy(isEnabled = enabled) else it }
                        }
                    }
                },
                onDelete = {
                    scope.launch {
                        LinkRuleRepository.updateExtractPatterns(context) { list ->
                            list.filterNot { it.id == pattern.id }
                        }
                    }
                },
                onSave = { updated ->
                    scope.launch {
                        LinkRuleRepository.updateExtractPatterns(context) { list ->
                            list.map { if (it.id == updated.id) updated else it }
                        }
                    }
                },
            )
        }

        item(key = "extract_hint") {
            MiuixText(
                text = "正则留空时使用默认链接提取；卡片展开可编辑名称和正则。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }

    MiuixFormDialog(
        show = showAddDialog,
        onDismissRequest = { showAddDialog = false },
        title = "添加提取规则",
        confirmText = "添加",
        confirmEnabled = newRegex.isNotBlank(),
        onConfirm = {
            val trimmed = newRegex.trim()
            val added = LinkExtractPattern(
                id = UUID.randomUUID().toString(),
                name = newName.trim().ifBlank { trimmed },
                pattern = trimmed,
            )
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { it + added }
            }
            showAddDialog = false
            newName = ""
            newRegex = ""
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixLabeledTextField(
                value = newName,
                onValueChange = { newName = it },
                label = "规则名称",
                singleLine = true,
            )
            MiuixLabeledTextField(
                value = newRegex,
                onValueChange = { newRegex = it },
                label = "正则表达式",
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )
        }
    }

    MiuixBottomSheet(
        show = showTestSheet,
        title = "测试链接提取效果",
        onDismissRequest = { showTestSheet = false },
        scrollUnderHeader = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(LocalMiuixSheetHeaderPadding.current)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixLabeledTextField(
                value = testInput,
                onValueChange = { testInput = it },
                label = "粘贴含链接的文本进行测试…",
                singleLine = false,
                minLines = 4,
                maxLines = 8,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassDialogButton(
                    role = GlassDialogButtonRole.Normal,
                    onClick = { testInput = "" },
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText(
                        text = "清空",
                        style = MiuixTheme.textStyles.button,
                        maxLines = 1,
                    )
                }
                GlassDialogButton(
                    role = GlassDialogButtonRole.Primary,
                    onClick = {
                        clipboardManager.getText()?.text?.let { testInput = it }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText(
                        text = "粘贴最新",
                        style = MiuixTheme.textStyles.button,
                        maxLines = 1,
                    )
                }
            }

            when {
                testInput.isBlank() -> {
                    MiuixText(
                        text = "输入或粘贴文本后会自动提取链接",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                testHits.isEmpty() -> {
                    MiuixText(
                        text = "未提取到链接",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                else -> {
                    MiuixText(
                        text = "提取结果 (${testHits.size})",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    testHits.forEach { hit ->
                        CardSegment(
                            isFirst = true,
                            isLast = true,
                            outerHorizontalPadding = 0.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    MiuixText(
                                        text = hit.url,
                                        style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(hit.url))
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "复制链接",
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    IconButton(onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.url)))
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "打开链接",
                                            tint = MiuixTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    MiuixText(
                                        text = "命中规则：${hit.ruleName}",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractPatternCard(
    pattern: LinkExtractPattern,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSave: (LinkExtractPattern) -> Unit,
) {
    var expanded by remember(pattern.id) { mutableStateOf(false) }
    var name by remember(pattern.id) { mutableStateOf(pattern.name) }
    var regex by remember(pattern.id) { mutableStateOf(pattern.pattern) }

    CardSegment(
        isFirst = true,
        isLast = true,
        outerHorizontalPadding = 12.dp,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                modifier = Modifier.fillMaxWidth(),
                title = pattern.name,
                summary = pattern.pattern,
                startAction = {
                    Icon(
                        painter = painterResource(R.drawable.ic_iconoir_code),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                },
                endActions = {
                    Switch(
                        checked = pattern.isEnabled,
                        onCheckedChange = onToggle,
                    )
                },
                onClick = { expanded = !expanded },
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixLabeledTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "规则名称",
                        singleLine = true,
                    )
                    MiuixLabeledTextField(
                        value = regex,
                        onValueChange = { regex = it },
                        label = "正则表达式",
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlassDialogButton(
                            role = GlassDialogButtonRole.Normal,
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        ) {
                            MiuixText(
                                text = "删除",
                                style = MiuixTheme.textStyles.button,
                                maxLines = 1,
                            )
                        }
                        GlassDialogButton(
                            role = GlassDialogButtonRole.Primary,
                            enabled = regex.trim().isNotBlank(),
                            onClick = {
                                onSave(pattern.copy(name = name.trim().ifBlank { regex.trim() }, pattern = regex.trim()))
                                expanded = false
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
}
