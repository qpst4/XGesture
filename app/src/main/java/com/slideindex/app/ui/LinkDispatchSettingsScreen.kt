package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.link.BrowserAppOption
import com.slideindex.app.link.BrowserSelectorActivity
import com.slideindex.app.link.LinkConfigBackup
import com.slideindex.app.link.LinkConfigCodec
import com.slideindex.app.link.LinkConfigTransfer
import com.slideindex.app.link.LinkDispatchResult
import com.slideindex.app.link.LinkDispatchRule
import com.slideindex.app.link.LinkDispatcher
import com.slideindex.app.link.LinkExemptDomain
import com.slideindex.app.link.LinkExtractPattern
import com.slideindex.app.link.LinkLauncher
import com.slideindex.app.link.LinkMatchType
import com.slideindex.app.link.LinkResolveStrategy
import com.slideindex.app.link.LinkRouteTrace
import com.slideindex.app.link.LinkRuleRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.link.LinkAppIcon
import com.slideindex.app.ui.link.LinkRuleIcon
import com.slideindex.app.ui.link.LinkTemplateImportDialog
import com.slideindex.app.ui.link.linkAppNameCache
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixGlassDropdownMenu
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixRadioSelectDialog
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 链接分发与雷达主设置页面。
 *
 * 结合 Gevo 全折叠仪表盘架构 + LinkGo-X 分门别类机制：
 * 1. 所有功能块默认折叠（极简一屏展示所有控制台大块）；
 * 2. 规则列表按「分发规则 (短链重分发)」与「跳转规则 (应用直达)」分栏清晰展示；
 * 3. 规则展示项全面精致化（40dp 图标 + 等宽 Pattern 微标签 + 胶囊徽章 + Switch）；
 * 4. 具备系统默认浏览器与备选兜底浏览器能力；
 * 5. 顶栏提供独立「跳转记录」流水日志入口。
 */
@Composable
fun LinkDispatchSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onNavigateToRules: () -> Unit = {},
    onNavigateToEditRule: (String?) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val pageTitle = stringResource(R.string.link_dispatch_title)

    val rules by LinkRuleRepository.observeRules(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val exempts by LinkRuleRepository.observeExemptDomains(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val extracts by LinkRuleRepository.observeExtractPatterns(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val openInSmallWindow by LinkRuleRepository.observeOpenInSmallWindow(context).collectAsStateWithLifecycle(initialValue = true)
    val radarScreenContent by LinkRuleRepository.observeRadarScreenContent(context).collectAsStateWithLifecycle(initialValue = true)
    val radarPointer by LinkRuleRepository.observeRadarPointer(context).collectAsStateWithLifecycle(initialValue = true)
    val radarHoverHoldMs by LinkRuleRepository.observeRadarHoverHoldMs(context).collectAsStateWithLifecycle(
        initialValue = LinkRuleRepository.DEFAULT_RADAR_HOVER_HOLD_MS,
    )
    val fallbackBrowser by LinkRuleRepository.observeFallbackBrowser(context).collectAsStateWithLifecycle(initialValue = null)

    val holdValues = listOf(650, 800, 1000, 1250, 1500, 2000, 3000, 0)
    val holdLabels = listOf("0.65 秒", "0.8 秒", "1 秒", "1.25 秒", "1.5 秒", "2 秒", "3 秒", "始终保持（不自动变黄）")
    val currentHoldIndex = holdValues.indexOf(radarHoverHoldMs).let { if (it < 0) 2 else it }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedRuleIds = remember { mutableStateListOf<String>() }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedRuleIds.clear()
    }

    // Gevo 风格全折叠仪表盘状态（默认全部折叠）
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }
    var behaviorExpanded by rememberSaveable { mutableStateOf(false) }
    var fallbackExpanded by rememberSaveable { mutableStateOf(false) }
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }

    val selectorTimerEnabled by LinkRuleRepository.observeSelectorTimerEnabled(context).collectAsStateWithLifecycle(initialValue = false)
    val selectorTimerSeconds by LinkRuleRepository.observeSelectorTimerSeconds(context).collectAsStateWithLifecycle(initialValue = LinkRuleRepository.DEFAULT_SELECTOR_TIMER_SECONDS)

    var showBuiltinImportDialog by remember { mutableStateOf(false) }
    var clipboardParsedBackup by remember { mutableStateOf<LinkConfigBackup?>(null) }
    var showExemptsSheet by remember { mutableStateOf(false) }
    var showExtractsSheet by remember { mutableStateOf(false) }
    var showBrowserPickerSheet by remember { mutableStateOf(false) }
    var showHoldPickerSheet by remember { mutableStateOf(false) }
    var appNamesVersion by remember { mutableStateOf(0) }

    var fallbackAppDisplayName by remember(fallbackBrowser) { mutableStateOf("") }

    // 预解析规则中的目标应用中文名
    LaunchedEffect(rules, fallbackBrowser) {
        var changed = false
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packagesToResolve = (rules.map { it.targetPackage } + listOfNotNull(fallbackBrowser))
                .filter { it.isNotBlank() }
                .distinct()

            packagesToResolve.forEach { pkg ->
                if (!linkAppNameCache.containsKey(pkg)) {
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrNull()
                    if (!label.isNullOrBlank()) {
                        linkAppNameCache[pkg] = label
                        changed = true
                    }
                }
            }
        }
        if (changed) appNamesVersion++
        if (!fallbackBrowser.isNullOrBlank()) {
            fallbackAppDisplayName = linkAppNameCache[fallbackBrowser].orEmpty().ifBlank { fallbackBrowser!! }
        } else {
            fallbackAppDisplayName = ""
        }
    }

    val lazyListState = rememberLazyListState()
    val enabledCount = remember(rules) { rules.count { it.isEnabled } }

    SettingsScreenScaffold(
        title = pageTitle,
        onBack = onBack,
        actions = {
            MiuixTopBarIconButton(onClick = onNavigateToHistory) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "跳转记录",
                )
            }
        },
    ) {
        // 页面简述
        item(key = "link_dispatch_desc_tip") {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MiuixText(
                    text = "全功能链接分发中枢：支持规则精准重定向、短链深度追踪、指针与屏幕雷达感知，并具备系统默认浏览器与备选兜底能力。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        // ==========================================
        // 模块 1：【分发规则】（独立页面导航卡片）
        // ==========================================
        item(key = "rule_navigation_card") {
            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = 12.dp,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                BasicComponent(
                    modifier = Modifier.fillMaxWidth(),
                    title = "分发规则",
                    summary = "共 ${rules.size} 条规则 · 已启用 $enabledCount 条",
                    onClick = onNavigateToRules,
                    endActions = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "进入规则管理",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }

        // ==========================================
        // 模块 2：【分发与雷达行为】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_behavior") {
            val holdText = if (radarHoverHoldMs == 0) "始终保持" else "${radarHoverHoldMs}ms"
            val behaviorSummary = "指针: ${if (radarPointer) "开" else "关"} · 屏幕: ${if (radarScreenContent) "开" else "关"} · 停留 $holdText · 小窗: ${if (openInSmallWindow) "开" else "关"}"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSegment(
                    isFirst = true,
                    isLast = !behaviorExpanded,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "分发与雷达行为",
                        summary = behaviorSummary,
                        onClick = { behaviorExpanded = !behaviorExpanded },
                        endActions = {
                            Icon(
                                imageVector = if (behaviorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = behaviorExpanded,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "指针雷达",
                                summary = "指针或悬浮球十字光标靠近屏幕链接时高亮并显示打开气泡，轻点直接跳转",
                                onClick = {
                                    scope.launch { LinkRuleRepository.setRadarPointerEnabled(context, !radarPointer) }
                                },
                                endActions = {
                                    Switch(
                                        checked = radarPointer,
                                        onCheckedChange = { checked ->
                                            scope.launch { LinkRuleRepository.setRadarPointerEnabled(context, checked) }
                                        },
                                    )
                                },
                            )
                        }

                        val holdDropdownEntry = remember(radarHoverHoldMs) {
                            DropdownEntry(
                                items = holdValues.mapIndexed { idx, ms ->
                                    DropdownItem(
                                        text = holdLabels[idx],
                                        selected = radarHoverHoldMs == ms,
                                        onClick = {
                                            scope.launch { LinkRuleRepository.setRadarHoverHoldMs(context, ms) }
                                        },
                                    )
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "雷达横幅停留时长",
                                summary = "指针停在链接上保持显示横幅的时间，超时自动切换为取词",
                                endActions = {
                                    MiuixGlassDropdownMenu(entry = holdDropdownEntry) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            MiuixText(
                                                text = holdLabels[currentHoldIndex],
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "屏幕内容链接雷达",
                                summary = "全局复制或框选悬浮窗中扫描屏幕链接，轻点直达目标应用",
                                onClick = {
                                    scope.launch { LinkRuleRepository.setRadarScreenContentEnabled(context, !radarScreenContent) }
                                },
                                endActions = {
                                    Switch(
                                        checked = radarScreenContent,
                                        onCheckedChange = { checked ->
                                            scope.launch { LinkRuleRepository.setRadarScreenContentEnabled(context, checked) }
                                        },
                                    )
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = true,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "小窗打开",
                                summary = "分发规则触发时优先用自由窗口小窗打开目标应用",
                                onClick = {
                                    scope.launch { LinkRuleRepository.setOpenInSmallWindowEnabled(context, !openInSmallWindow) }
                                },
                                endActions = {
                                    Switch(
                                        checked = openInSmallWindow,
                                        onCheckedChange = { checked ->
                                            scope.launch { LinkRuleRepository.setOpenInSmallWindowEnabled(context, checked) }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 模块 3：【系统默认与兜底设置】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_fallback") {
            val fallbackSummary = if (fallbackBrowser.isNullOrBlank()) {
                "未设兜底备选浏览器（未匹配时现场选择）"
            } else {
                "兜底备选: $fallbackAppDisplayName · $fallbackBrowser"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSegment(
                    isFirst = true,
                    isLast = !fallbackExpanded,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "系统默认与兜底设置",
                        summary = fallbackSummary,
                        onClick = { fallbackExpanded = !fallbackExpanded },
                        endActions = {
                            Icon(
                                imageVector = if (fallbackExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = fallbackExpanded,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "设为系统默认浏览器",
                                summary = "接管系统外部链接跳转，未匹配规则的网址交由备选浏览器打开",
                                startAction = {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    runCatching { context.startActivity(intent) }.onFailure {
                                        Toast.makeText(context, "请在系统设置中将本应用设为默认浏览器", Toast.LENGTH_LONG).show()
                                    }
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = !fallbackBrowser.isNullOrBlank(),
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "兜底备选浏览器",
                                summary = if (fallbackBrowser.isNullOrBlank()) {
                                    "未指定（未匹配规则时将弹出浏览器选择器）"
                                } else {
                                    "$fallbackAppDisplayName · $fallbackBrowser"
                                },
                                startAction = if (!fallbackBrowser.isNullOrBlank()) {
                                    {
                                        Box(modifier = Modifier.padding(end = 12.dp)) {
                                            LinkAppIcon(packageName = fallbackBrowser!!, size = 36.dp)
                                        }
                                    }
                                } else null,
                                endActions = if (!fallbackBrowser.isNullOrBlank()) {
                                    {
                                        MiuixTextButton(
                                            text = "清除",
                                            onClick = {
                                                scope.launch { LinkRuleRepository.setFallbackBrowser(context, null) }
                                            },
                                        )
                                    }
                                } else null,
                                onClick = { showBrowserPickerSheet = true },
                            )
                        }

                        // 未指定备选浏览器时，弹出选择器支持倒计时控制
                        if (fallbackBrowser.isNullOrBlank()) {
                            CardSegment(
                                isFirst = false,
                                isLast = !selectorTimerEnabled,
                                outerHorizontalPadding = 12.dp,
                            ) {
                                BasicComponent(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = "选择器自动倒计时打开",
                                    summary = "弹出选择器未操作时自动倒计时，超时以首选应用打开",
                                    onClick = {
                                        scope.launch {
                                            LinkRuleRepository.setSelectorTimerEnabled(context, !selectorTimerEnabled)
                                        }
                                    },
                                    endActions = {
                                        Switch(
                                            checked = selectorTimerEnabled,
                                            onCheckedChange = { checked ->
                                                scope.launch {
                                                    LinkRuleRepository.setSelectorTimerEnabled(context, checked)
                                                }
                                            },
                                        )
                                    },
                                )
                            }

                            if (selectorTimerEnabled) {
                                val timerOptions = listOf(3 to "3 秒 (推荐)", 5 to "5 秒", 8 to "8 秒")
                                val timerDropdownEntry = remember(selectorTimerSeconds) {
                                    DropdownEntry(
                                        items = timerOptions.map { (sec, label) ->
                                            DropdownItem(
                                                text = label,
                                                selected = selectorTimerSeconds == sec,
                                                onClick = {
                                                    scope.launch {
                                                        LinkRuleRepository.setSelectorTimerSeconds(context, sec)
                                                    }
                                                },
                                            )
                                        },
                                    )
                                }
                                CardSegment(
                                    isFirst = false,
                                    isLast = true,
                                    outerHorizontalPadding = 12.dp,
                                ) {
                                    BasicComponent(
                                        modifier = Modifier.fillMaxWidth(),
                                        title = "倒计时时长",
                                        summary = "首位应用外轮廓描边倒计时走满的时长",
                                        endActions = {
                                            MiuixGlassDropdownMenu(entry = timerDropdownEntry) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                ) {
                                                    val currentLabel = timerOptions.firstOrNull { it.first == selectorTimerSeconds }?.second ?: "${selectorTimerSeconds} 秒"
                                                    MiuixText(
                                                        text = currentLabel,
                                                        style = MiuixTheme.textStyles.footnote1,
                                                        color = MiuixTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = MiuixTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 模块 5：【工具与数据管理】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_tools") {
            val toolsSummary = "常用模板 · 豁免域名 (${exempts.size}) · 提取 (${extracts.size}) · 备份与还原"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSegment(
                    isFirst = true,
                    isLast = !toolsExpanded,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "工具与数据管理",
                        summary = toolsSummary,
                        onClick = { toolsExpanded = !toolsExpanded },
                        endActions = {
                            Icon(
                                imageVector = if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = toolsExpanded,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "导入常用模板",
                                summary = "一键添加常用站点的深链规则（自动识别已安装应用）",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.LibraryAdd,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                endActions = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onClick = { showBuiltinImportDialog = true },
                            )
                        }

                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "豁免域名",
                                summary = if (exempts.isEmpty()) "这些域名不会被规则接管" else "当前已配置 ${exempts.size} 项豁免域名",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                endActions = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onClick = { showExemptsSheet = true },
                            )
                        }

                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "链接提取规则",
                                summary = "从文本中找出链接的正则（当前 ${extracts.size} 项）",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.FindInPage,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                endActions = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onClick = { showExtractsSheet = true },
                            )
                        }

                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "导出配置到剪贴板",
                                summary = "复制全部规则、豁免域名与提取规则 JSON 到剪贴板",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    scope.launch {
                                        val success = LinkConfigTransfer.exportToClipboard(context)
                                        if (success) {
                                            Toast.makeText(context, "配置已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "剪贴板服务不可用", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        }

                        CardSegment(isFirst = false, isLast = true, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "从剪贴板导入规则",
                                summary = "识别剪贴板中的备份或 LinkGo 规则 JSON 并勾选导入",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    val parsed = LinkConfigTransfer.parseClipboardBackup(context)
                                    if (parsed == null || (parsed.rules.isEmpty() && parsed.exempts.isEmpty() && parsed.extracts.isEmpty())) {
                                        Toast.makeText(context, "剪贴板中未找到有效的规则配置", Toast.LENGTH_SHORT).show()
                                    } else {
                                        clipboardParsedBackup = parsed
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "link_dispatch_bottom_spacer") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    // 常用模板导入弹窗
    LinkTemplateImportDialog(
        show = showBuiltinImportDialog,
        existingRules = rules,
        onDismissRequest = { showBuiltinImportDialog = false },
        onImport = { selectedRules ->
            scope.launch {
                LinkRuleRepository.updateRules(context) { list ->
                    val existingPatterns = list.map { it.pattern }.toSet()
                    var nextPriority = (list.maxOfOrNull { it.priority } ?: -1) + 1
                    val toAdd = selectedRules
                        .filter { it.pattern !in existingPatterns }
                        .map { it.copy(priority = nextPriority++) }
                    list + toAdd
                }
            }
        },
    )

    // 剪贴板导入弹窗
    val activeClipboardBackup = clipboardParsedBackup
    LinkTemplateImportDialog(
        show = activeClipboardBackup != null,
        existingRules = rules,
        customCandidates = activeClipboardBackup?.rules,
        dialogTitle = "从剪贴板导入规则",
        dialogDescription = "已解析出 ${activeClipboardBackup?.rules?.size ?: 0} 条规则，请勾选要导入的规则。",
        preselectAllAvailable = true,
        onDismissRequest = { clipboardParsedBackup = null },
        onImport = { selectedRules ->
            val backupToImport = activeClipboardBackup?.copy(rules = selectedRules)
            clipboardParsedBackup = null
            if (backupToImport != null) {
                scope.launch {
                    LinkRuleRepository.importConfig(context, backupToImport)
                }
            }
        },
    )

    // 豁免域名弹窗
    LinkExemptsBottomSheet(
        show = showExemptsSheet,
        exempts = exempts,
        onDismissRequest = { showExemptsSheet = false },
        onAdd = { newExempt ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { it + newExempt }
            }
        },
        onDelete = { toDelete ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { list ->
                    list.filterNot { it.domain == toDelete.domain }
                }
            }
        },
        onToggle = { target, enabled ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { list ->
                    list.map { if (it.domain == target.domain) it.copy(isEnabled = enabled) else it }
                }
            }
        },
    )

    // 链接提取正则弹窗
    LinkExtractsBottomSheet(
        show = showExtractsSheet,
        extracts = extracts,
        onDismissRequest = { showExtractsSheet = false },
        onAdd = { newExtract ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { it + newExtract }
            }
        },
        onUpdate = { updated ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.map { if (it.id == updated.id) updated else it }
                }
            }
        },
        onDelete = { toDelete ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.filterNot { it.id == toDelete.id }
                }
            }
        },
        onToggle = { target, enabled ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.map { if (it.id == target.id) it.copy(isEnabled = enabled) else it }
                }
            }
        },
        onResetDefaults = {
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { LinkExtractPattern.builtinDefaults() }
            }
        },
    )

    // 兜底备选浏览器选择弹窗
    if (showBrowserPickerSheet) {
        var installedBrowsers by remember { mutableStateOf<List<BrowserAppOption>>(emptyList()) }
        LaunchedEffect(Unit) {
            installedBrowsers = withContext(Dispatchers.IO) {
                BrowserSelectorActivity.queryBrowsers(context)
            }
        }
        MiuixBottomSheet(
            show = true,
            title = "选择兜底备选浏览器",
            onDismissRequest = { showBrowserPickerSheet = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (installedBrowsers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        MiuixText(text = "未检测到外部已安装的浏览器", style = MiuixTheme.textStyles.body2)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items = installedBrowsers, key = { it.packageName }) { browser ->
                            CardSegment(isFirst = true, isLast = true, outerHorizontalPadding = 0.dp) {
                                BasicComponent(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = browser.label,
                                    summary = browser.packageName,
                                    startAction = {
                                        Box(modifier = Modifier.padding(end = 12.dp)) {
                                            LinkAppIcon(packageName = browser.packageName, size = 36.dp)
                                        }
                                    },
                                    onClick = {
                                        scope.launch {
                                            LinkRuleRepository.setFallbackBrowser(context, browser.packageName)
                                            showBrowserPickerSheet = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkExemptsBottomSheet(
    show: Boolean,
    exempts: List<LinkExemptDomain>,
    onDismissRequest: () -> Unit,
    onAdd: (LinkExemptDomain) -> Unit,
    onDelete: (LinkExemptDomain) -> Unit,
    onToggle: (LinkExemptDomain, Boolean) -> Unit,
) {
    if (!show) return
    var newDomain by remember { mutableStateOf("") }

    MiuixBottomSheet(
        show = true,
        title = "豁免域名",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = "属于豁免域名的网址不会被规则接管，直接交由系统默认方式打开。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MiuixLabeledTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        label = "添加豁免域名，例如 example.com",
                        useLabelAsPlaceholder = false,
                        singleLine = true,
                    )
                }
                MiuixTextButton(
                    text = "添加",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = newDomain.trim().isNotBlank(),
                    onClick = {
                        val trimmed = newDomain.trim()
                        if (trimmed.isNotEmpty()) {
                            onAdd(LinkExemptDomain(domain = trimmed))
                            newDomain = ""
                        }
                    },
                )
            }

            if (exempts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    MiuixText(text = "暂无豁免域名", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = exempts, key = { it.domain }) { item ->
                        CardSegment(isFirst = true, isLast = true, outerHorizontalPadding = 0.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = item.domain,
                                endActions = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = item.isEnabled,
                                            onCheckedChange = { onToggle(item, it) },
                                        )
                                        IconButton(onClick = { onDelete(item) }) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = "删除",
                                                tint = MiuixTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkExtractsBottomSheet(
    show: Boolean,
    extracts: List<LinkExtractPattern>,
    onDismissRequest: () -> Unit,
    onAdd: (LinkExtractPattern) -> Unit,
    onUpdate: (LinkExtractPattern) -> Unit = {},
    onDelete: (LinkExtractPattern) -> Unit,
    onToggle: (LinkExtractPattern, Boolean) -> Unit,
    onResetDefaults: () -> Unit,
) {
    if (!show) return
    var showAddDialog by remember { mutableStateOf(false) }

    MiuixBottomSheet(
        show = true,
        title = "链接提取规则",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiuixText(
                    text = "从长文本或屏幕中嗅探 URL 的正则规则库",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                MiuixTextButton(
                    text = "恢复默认",
                    onClick = onResetDefaults,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = extracts, key = { it.id }) { item ->
                    ExtractPatternAccordionItem(
                        pattern = item,
                        onToggle = { onToggle(item, it) },
                        onDelete = { onDelete(item) },
                        onSave = { updated -> onUpdate(updated) },
                    )
                }

                // 底部添加项：纯净大圆角卡片
                item(key = "add_new_extract_rule") {
                    CardSegment(
                        isFirst = true,
                        isLast = true,
                        outerHorizontalPadding = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddDialog = true }
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            MiuixText(
                                text = "添加链接提取规则",
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    // 新增提取规则弹窗
    if (showAddDialog) {
        var addName by remember { mutableStateOf("") }
        var addRegex by remember { mutableStateOf("") }
        MiuixBottomSheet(
            show = true,
            title = "添加提取规则",
            onDismissRequest = { showAddDialog = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixLabeledTextField(
                    value = addName,
                    onValueChange = { addName = it },
                    label = "规则名称（例如：磁力链接、微博短链）",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                )
                MiuixLabeledTextField(
                    value = addRegex,
                    onValueChange = { addRegex = it },
                    label = "正则表达式",
                    useLabelAsPlaceholder = false,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                MiuixTextButton(
                    text = "确认添加",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = addRegex.trim().isNotBlank(),
                    onClick = {
                        val trimmedRegex = addRegex.trim()
                        val trimmedName = addName.trim().ifBlank { trimmedRegex }
                        if (trimmedRegex.isNotEmpty()) {
                            onAdd(LinkExtractPattern(name = trimmedName, pattern = trimmedRegex))
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                )
            }
        }
    }
}

/**
 * 深度对齐 LinkGo 源码的单条提取规则风琴折叠展开卡片项：
 * 1. 日常态：纯净展示，无外露垃圾桶，右侧为开关；
 * 2. 点击卡片：原地展开编辑名称、正则表达式与底栏操作（删除、保存）。
 */
@Composable
private fun ExtractPatternAccordionItem(
    pattern: LinkExtractPattern,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSave: (LinkExtractPattern) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var editedName by remember(pattern.name) { mutableStateOf(pattern.name) }
    var editedPattern by remember(pattern.pattern) { mutableStateOf(pattern.pattern) }

    CardSegment(
        isFirst = true,
        isLast = true,
        outerHorizontalPadding = 0.dp,
        modifier = Modifier.animateContentSize(tween(200)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 首行：规则名称 + 正则缩略标签 + 开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MiuixText(
                        text = pattern.name,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        MiuixText(
                            text = pattern.pattern,
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Switch(
                    checked = pattern.isEnabled,
                    onCheckedChange = onToggle,
                )
            }

            // 原地风琴展开编辑面板
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MiuixLabeledTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = "规则名称",
                        useLabelAsPlaceholder = false,
                        singleLine = true,
                    )

                    MiuixLabeledTextField(
                        value = editedPattern,
                        onValueChange = { editedPattern = it },
                        label = "正则表达式",
                        useLabelAsPlaceholder = false,
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MiuixTextButton(
                            text = "删除此规则",
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.error,
                            ),
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        )

                        MiuixTextButton(
                            text = "完成并收起",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            enabled = editedPattern.trim().isNotBlank(),
                            onClick = {
                                val trimmedPattern = editedPattern.trim()
                                val trimmedName = editedName.trim().ifBlank { trimmedPattern }
                                onSave(pattern.copy(name = trimmedName, pattern = trimmedPattern))
                                isExpanded = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
