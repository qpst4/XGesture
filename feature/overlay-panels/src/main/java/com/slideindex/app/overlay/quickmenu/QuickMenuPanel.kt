package com.slideindex.app.overlay.quickmenu

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.filebrowser.FileBrowserQuickCard
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 「快捷菜单」面板内容。
 *
 * 形态刻意对齐"聚合"这个产品目标：
 * - **出厂即满**：没有任何"添加/收藏/绑定"操作，[QuickMenuTool] 里注册的工具全都在；
 * - **搜索框**用于「查找」而不是「配置」——工具变多以后靠打字直达；
 * - **最近使用**是自动排前（用户在做什么，什么就靠前），不需要手动置顶；
 * - 每行带**一句描述**，因为九宫格只能靠图标猜，而这里的工具彼此不像。
 *
 * ⚠️ 本文件是纯 UI，**没有单测**（Compose 交互需要真机/仪器测试）。其依赖的逻辑全部放在
 * 可单测的纯 JVM 件里：`UnitConversionEngine` / `CurrencyRateEngine` / `PasswordGenerator` /
 * `StopwatchLogic` / `QrCodeGenerator` / `QrPayloadParser`。
 */
@Composable
internal fun QuickMenuPanel(
    visible: Boolean,
    page: QuickMenuTool?,
    query: String,
    recents: List<QuickMenuTool>,
    /** 识码页的原文。由宿主的 Session 承载，这样外部 Activity 期间窗口被摘掉也不丢。 */
    scanContent: String,
    /** 识码页的候选列表（含归一化定位点，用于在图上画标记）；只有一个码时为空列表。 */
    scanResults: List<BarcodeScanResult>,
    /** 被扫描的那张图；为 null 时只显示候选列表（例如相机路径在定格之前）。 */
    scanImage: Bitmap?,
    /** 「屏幕取色」的状态。同样是 Session 级的：全屏取色层画在本面板根部，要和工具页共用。 */
    colorPicker: QuickMenuColorPickerState,
    onQueryChange: (String) -> Unit,
    onScanContentChange: (String) -> Unit,
    onScanResultsChange: (List<BarcodeScanResult>) -> Unit,
    onScanImageChange: (Bitmap?) -> Unit,
    onOpenTool: (QuickMenuTool) -> Unit,
    onOpenFileBrowser: (String?) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val titles = QuickMenuTool.entries.associateWith { stringResource(it.titleRes) }
    val descs = QuickMenuTool.entries.associateWith { stringResource(it.descRes) }
    val context = LocalContext.current
    // **必须放在 if (page == null) 之外**：列表在切到子页时会离开组合，状态若留在里面就会被重置，
    // 表现就是"从子页返回后回到了顶部"（真机反馈）。
    val homeScrollState = rememberLazyListState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 罩层：点空白处关闭。指示涟漪在这里是噪音，显式关掉。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (page == null) {
                        ListHeader(onDismiss = onDismiss)
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                )
                            },
                            placeholder = { Text(stringResource(R.string.quick_menu_search_hint)) },
                            // 顶部一键扫码：用户要的是"从最上面直接扫"，不必先翻到识码页。
                            // 先切到识码页（结果在那里展示），再拉起相机；与识码页的按钮共用 launchCameraScan。
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        onOpenTool(QuickMenuTool.SCAN_DECODER)
                                        launchCameraScan(
                                            context = context,
                                            onContentChange = onScanContentChange,
                                            onResultsChange = onScanResultsChange,
                                            onImageChange = onScanImageChange,
                                        )
                                    },
                                ) {
                                    Icon(
                                        // 用二维码图标（与 quickdash 的扫码入口一致），相机图标更像"拍照"
                                        imageVector = Icons.Filled.QrCodeScanner,
                                        contentDescription = stringResource(R.string.quick_menu_scan_camera),
                                    )
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ToolList(
                            query = query,
                            recents = recents,
                            titles = titles,
                            descs = descs,
                            scrollState = homeScrollState,
                            onOpenTool = onOpenTool,
                            onOpenFileBrowser = onOpenFileBrowser,
                        )
                    } else {
                        ToolPageHeader(
                            title = titles.getValue(page),
                            onBack = onBack,
                            onDismiss = onDismiss,
                        )
                        QuickMenuToolScreen(
                            tool = page,
                            onDismiss = onDismiss,
                            scanContent = scanContent,
                            scanResults = scanResults,
                            scanImage = scanImage,
                            colorPicker = colorPicker,
                            onScanContentChange = onScanContentChange,
                            onScanResultsChange = onScanResultsChange,
                            onScanImageChange = onScanImageChange,
                        )
                    }
                }
            }

            // 全屏取色：盖在面板卡片与罩层**之上**（同一个窗口，不用再开第二个窗）。它只在
            // `picking` 非空时存在，退出就是把它置空——下面的工具页一直在组合里，历史和格式不丢。
            colorPicker.picking?.let { bitmap ->
                QuickMenuColorPickFullscreen(
                    bitmap = bitmap,
                    state = colorPicker,
                    onExit = { colorPicker.picking = null },
                )
            }
        }
    }
}

@Composable
private fun ListHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.quick_menu_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(imageVector = MiuixIcons.Medium.Close, contentDescription = stringResource(R.string.quick_menu_back))
        }
    }
}

@Composable
private fun ToolPageHeader(title: String, onBack: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.quick_menu_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onDismiss) {
            Icon(imageVector = MiuixIcons.Medium.Close, contentDescription = stringResource(R.string.quick_menu_back))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ToolList(
    query: String,
    recents: List<QuickMenuTool>,
    titles: Map<QuickMenuTool, String>,
    descs: Map<QuickMenuTool, String>,
    scrollState: LazyListState,
    onOpenTool: (QuickMenuTool) -> Unit,
    onOpenFileBrowser: (String?) -> Unit,
) {
    val matched = remember(query, titles, descs) {
        QuickMenuTool.matching(query, { titles.getValue(it) }, { descs.getValue(it) })
    }
    val grouped = remember(matched) { matched.groupBy { it.group } }
    // 长分组默认折叠，卡片不会又变回一整块长列表；搜索时始终展开。
    val collapsed = remember { mutableStateMapOf<QuickMenuGroup, Boolean>() }
    val searching = query.isNotBlank()
    val haptic = LocalHapticFeedback.current

    if (searching && matched.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.quick_menu_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val modules = if (searching) emptyList() else QuickMenuPreferences.homeModules()
    val reorderState = rememberReorderableLazyListState(scrollState) { from, to ->
        QuickMenuPreferences.moveHomeModule(from.index, to.index)
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (searching) {
            item(key = "search-results") {
                HomeModuleCard(
                    title = stringResource(R.string.quick_menu_home_search_results),
                ) {
                    matched.forEach { tool ->
                        ToolRow(
                            tool = tool,
                            title = titles.getValue(tool),
                            description = descs.getValue(tool),
                            onClick = { onOpenTool(tool) },
                        )
                    }
                }
            }
            return@LazyColumn
        }

        items(modules, key = { it.id }) { module ->
            ReorderableItem(
                state = reorderState,
                key = module.id,
            ) { isDragging ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .longPressDraggableHandle(
                            onDragStarted = { _ ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        ),
                ) {
                    when (module) {
                        QuickMenuHomeModule.Recents -> HomeModuleCard(
                            title = stringResource(R.string.quick_menu_section_recent),
                        ) {
                            if (recents.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.quick_menu_home_recents_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 8.dp,
                                    ),
                                )
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(recents, key = { "recent-${it.name}" }) { tool ->
                                        RecentChip(
                                            title = titles.getValue(tool),
                                        ) { onOpenTool(tool) }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        QuickMenuHomeModule.Files -> FileBrowserQuickCard(
                            onOpen = onOpenFileBrowser,
                        )

                        is QuickMenuHomeModule.ToolGroup -> {
                            val group = module.group
                            val tools = grouped[group].orEmpty()
                            val isCollapsed = collapsed[group] ?: (tools.size > 4)
                            val contentScroll = rememberScrollState()
                            HomeModuleCard(
                                title = stringResource(group.titleRes),
                                expanded = !isCollapsed,
                                onToggle = { collapsed[group] = !isCollapsed },
                            ) {
                                if (!isCollapsed) {
                                    // 展开后的分组卡也可能非常高；限制高度并内部滚动，
                                    // 避免拖拽时用整张长卡的高度做让位计算。
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 320.dp)
                                            .verticalScroll(contentScroll),
                                    ) {
                                        tools.forEach { tool ->
                                            // 文件浏览器已经有首页文件卡，避免工具列表里再出现一条重复入口。
                                            if (tool == QuickMenuTool.FILE_BROWSER) {
                                                return@forEach
                                            }
                                            ToolRow(
                                                tool = tool,
                                                title = titles.getValue(tool),
                                                description = descs.getValue(tool),
                                                onClick = { onOpenTool(tool) },
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
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
private fun HomeModuleCard(
    title: String,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onToggle != null) { onToggle?.invoke() }
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (expanded != null) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun RecentChip(title: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ToolRow(tool: QuickMenuTool, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 工具页分发：新增工具时**只需要**在这里加一个分支（外加注册表里的一项与两条字符串）。 */
@Composable
private fun QuickMenuToolScreen(
    tool: QuickMenuTool,
    onDismiss: () -> Unit,
    scanContent: String,
    scanResults: List<BarcodeScanResult>,
    scanImage: Bitmap?,
    colorPicker: QuickMenuColorPickerState,
    onScanContentChange: (String) -> Unit,
    onScanResultsChange: (List<BarcodeScanResult>) -> Unit,
    onScanImageChange: (Bitmap?) -> Unit,
) {
    when (tool) {
        QuickMenuTool.UNIT_CONVERTER -> QuickMenuUnitConverterScreen()
        QuickMenuTool.CURRENCY_CONVERTER -> QuickMenuCurrencyConverterScreen()
        QuickMenuTool.QR_GENERATOR -> QuickMenuQrGeneratorScreen()
        QuickMenuTool.SCAN_DECODER -> QuickMenuScanDecoderScreen(
            content = scanContent,
            results = scanResults,
            image = scanImage,
            onContentChange = onScanContentChange,
            onResultsChange = onScanResultsChange,
            onImageChange = onScanImageChange,
        )
        QuickMenuTool.PASSWORD -> QuickMenuPasswordScreen()
        QuickMenuTool.TEXT_TOOL -> QuickMenuTextToolScreen()
        QuickMenuTool.TRANSLATE -> QuickMenuTranslateScreen()
        QuickMenuTool.STOPWATCH -> QuickMenuStopwatchScreen()
        QuickMenuTool.COUNTDOWN -> QuickMenuCountdownScreen()
        QuickMenuTool.REMINDER -> QuickMenuReminderScreen()
        QuickMenuTool.TIMER_HISTORY -> QuickMenuHistoryScreen()
        QuickMenuTool.CALCULATOR -> QuickMenuCalculatorScreen()
        QuickMenuTool.POMODORO -> QuickMenuPomodoroScreen()
        QuickMenuTool.COLOR_PICKER -> QuickMenuColorPickerScreen(state = colorPicker)
        QuickMenuTool.MACRO_RUN -> QuickMenuMacroScreen(onRan = onDismiss)
        // 预置入口**不切页**（点击处已直接执行并收起菜单），这里只是让 when 穷尽
        QuickMenuTool.ENTRY_FOREGROUND_ACTIVITY,
        QuickMenuTool.ENTRY_FREEZER,
        QuickMenuTool.ENTRY_SHELL_PANEL,
        QuickMenuTool.ENTRY_SEARCH_PANEL,
        QuickMenuTool.ENTRY_CLIPBOARD_PANEL,
        QuickMenuTool.ENTRY_PIN_TO_SCREEN,
        QuickMenuTool.ENTRY_STASH_PANEL,
        QuickMenuTool.PAGE_SCROLL,
        QuickMenuTool.WIDGET_PANEL,
        QuickMenuTool.FILE_BROWSER,
        -> Unit
    }
}
