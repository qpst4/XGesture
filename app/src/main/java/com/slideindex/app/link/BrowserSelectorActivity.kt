package com.slideindex.app.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.ui.components.reorderable.ReorderableItem
import com.slideindex.app.ui.components.reorderable.rememberReorderableLazyGridState
import com.slideindex.app.ui.link.LinkAppIcon
import com.slideindex.app.ui.link.linkAppNameCache
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 未命中任何分发规则且未配置兜底浏览器时的多引擎选择弹窗（4列网格 BottomSheet，深度对标 LinkGo 原版）。
 * 支持管理模式：长按拖拽重排（Reorderable）、单击隐藏/显示、首位应用描边倒计时自启。
 */
class BrowserSelectorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val bootstrap = intent.getStringExtra(EXTRA_BOOTSTRAP)
            ?.let { encoded -> runCatching { Json.decodeFromString<BrowserSelectorBootstrap>(encoded) }.getOrNull() }

        setContent {
            MiuixTheme {
                var sheetVisible by remember { mutableStateOf(true) }
                var pendingLaunch by remember { mutableStateOf<PendingLaunch?>(null) }

                BrowserSelectorSheet(
                    url = url,
                    bootstrap = bootstrap,
                    show = sheetVisible,
                    onDismiss = {
                        pendingLaunch = null
                        sheetVisible = false
                    },
                    onPick = { app, forceWindowMode, historyLabel ->
                        if (pendingLaunch == null) {
                            pendingLaunch = PendingLaunch(
                                packageName = app.packageName,
                                forceWindowMode = forceWindowMode,
                                historyLabel = historyLabel,
                            )
                            sheetVisible = false
                        }
                    },
                    onDismissFinished = {
                        val pick = pendingLaunch
                        pendingLaunch = null
                        if (pick == null) {
                            finish()
                        } else {
                            lifecycleScope.launch {
                                LinkLauncher.launch(
                                    context = this@BrowserSelectorActivity,
                                    url = url,
                                    targetPackage = pick.packageName,
                                    excludeFromRecents = false,
                                    forceWindowMode = pick.forceWindowMode,
                                )
                                LinkDispatchHistoryRepository.record(
                                    context = this@BrowserSelectorActivity,
                                    rawUrl = url,
                                    finalUrl = url,
                                    matchedRuleName = pick.historyLabel,
                                    targetPackage = pick.packageName,
                                    isSuccess = true,
                                )
                                finish()
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_BOOTSTRAP = "EXTRA_BOOTSTRAP"

        fun encodeBootstrap(bootstrap: BrowserSelectorBootstrap): String =
            Json.encodeToString(bootstrap)

        fun queryBrowsers(context: Context, url: String = "https://www.example.com"): List<BrowserAppOption> {
            val pm = context.packageManager
            val ownPkg = context.packageName

            // 1. 探测已安装的所有外部浏览器
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
            val resolvedBrowsers = runCatching {
                pm.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList())

            val browsers = resolvedBrowsers.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPkg) return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
                linkAppNameCache[pkg] = label
                BrowserAppOption(packageName = pkg, label = label, isBrowser = true)
            }.distinctBy { it.packageName }

            // 2. 探测能响应该特定 URL 的特定原生应用（如哔哩哔哩、知乎、贴吧等推荐应用）
            val targetUri = runCatching { Uri.parse(url) }.getOrNull()
            val recommendedApps = if (targetUri != null) {
                val appIntent = Intent(Intent.ACTION_VIEW, targetUri)
                val resolvedApps = runCatching {
                    pm.queryIntentActivities(appIntent, PackageManager.MATCH_DEFAULT_ONLY)
                }.getOrDefault(emptyList())
                val browserPkgs = browsers.map { it.packageName }.toSet()
                resolvedApps.mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ownPkg || pkg in browserPkgs) return@mapNotNull null
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
                    linkAppNameCache[pkg] = label
                    BrowserAppOption(packageName = pkg, label = label, isBrowser = false)
                }.distinctBy { it.packageName }
            } else {
                emptyList()
            }

            // 推荐应用排在前面，外部浏览器按名称排序排在后方
            return (recommendedApps + browsers.sortedBy { it.label.lowercase() }).distinctBy { it.packageName }
        }
    }
}

@Serializable
data class BrowserAppOption(
    val packageName: String,
    val label: String,
    val isBrowser: Boolean = true,
)

/**
 * 选择器首帧所需的全部数据。由 [LinkDispatcherActivity] 在跳板里一次查好带过来，
 * 让 BottomSheet 一出现就是最终高度与最终内容，避免弹出动画播到一半被数据撑高。
 */
@Serializable
data class BrowserSelectorBootstrap(
    val apps: List<BrowserAppOption> = emptyList(),
    val hidden: List<String> = emptyList(),
    val timerEnabled: Boolean = false,
    val timerSeconds: Int = LinkRuleRepository.DEFAULT_SELECTOR_TIMER_SECONDS,
    val smallWindow: Boolean = false,
)

/** 动画播完后再执行的启动动作（先让面板滑下去，再拉目标应用）。 */
private data class PendingLaunch(
    val packageName: String,
    val forceWindowMode: Boolean,
    val historyLabel: String,
)

private sealed class SelectorGridItem {
    data class App(val app: BrowserAppOption) : SelectorGridItem()
    data class Header(val title: String) : SelectorGridItem()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserSelectorSheet(
    url: String,
    bootstrap: BrowserSelectorBootstrap?,
    show: Boolean,
    onDismiss: () -> Unit,
    onPick: (app: BrowserAppOption, forceWindowMode: Boolean, historyLabel: String) -> Unit,
    onDismissFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf(bootstrap?.apps.orEmpty()) }
    var hiddenSet by remember { mutableStateOf(bootstrap?.hidden?.toSet().orEmpty()) }
    var isEditing by remember { mutableStateOf(false) }
    var timerEnabled by remember { mutableStateOf(bootstrap?.timerEnabled ?: false) }
    var timerSeconds by remember {
        mutableStateOf(bootstrap?.timerSeconds ?: LinkRuleRepository.DEFAULT_SELECTOR_TIMER_SECONDS)
    }
    var defaultSmallWindow by remember { mutableStateOf(bootstrap?.smallWindow ?: false) }
    var isTimerActive by remember {
        mutableStateOf(
            bootstrap?.let { data ->
                data.timerEnabled && data.timerSeconds > 0 &&
                    data.apps.any { app -> app.packageName !in data.hidden }
            } ?: false,
        )
    }
    var remainingProgress by remember { mutableFloatStateOf(0f) }

    // 跳板带来完整数据时这里不再查询任何东西：首帧就是最终内容与最终高度。
    LaunchedEffect(url, bootstrap) {
        if (bootstrap == null) {
            val loadedApps = withContext(Dispatchers.IO) {
                BrowserSelectorActivity.queryBrowsers(context, url)
            }
            val isEnabled = withContext(Dispatchers.IO) {
                LinkRuleRepository.isSelectorTimerEnabled(context)
            }
            val seconds = withContext(Dispatchers.IO) {
                LinkRuleRepository.getSelectorTimerSeconds(context)
            }
            val savedOrder = withContext(Dispatchers.IO) {
                LinkRuleRepository.getBrowserOrderList(context)
            }
            val savedHidden = withContext(Dispatchers.IO) {
                LinkRuleRepository.getBrowserHiddenList(context)
            }
            val isSmallWindow = withContext(Dispatchers.IO) {
                LinkRuleRepository.isOpenInSmallWindowEnabled(context)
            }

            // 依据用户自定义排序排列，若未自定义则保持默认探测顺序
            val sortedApps = loadedApps.sortedBy { app ->
                val idx = savedOrder.indexOf(app.packageName)
                if (idx != -1) idx else 1000
            }
            apps = sortedApps
            hiddenSet = savedHidden.toSet()
            timerEnabled = isEnabled
            timerSeconds = seconds
            defaultSmallWindow = isSmallWindow
        }
        if (timerEnabled && timerSeconds > 0 && apps.any { it.packageName !in hiddenSet }) {
            isTimerActive = true
        }
    }

    val saveOrderAndHidden = {
        scope.launch(Dispatchers.IO) {
            LinkRuleRepository.setBrowserOrderList(context, apps.map { it.packageName })
            LinkRuleRepository.setBrowserHiddenList(context, hiddenSet.toList())
        }
    }

    BackHandler(enabled = isEditing) {
        saveOrderAndHidden()
        isEditing = false
    }

    val visibleApps = remember(apps, hiddenSet) { apps.filter { it.packageName !in hiddenSet } }
    val hiddenApps = remember(apps, hiddenSet) { apps.filter { it.packageName in hiddenSet } }

    val flatList = remember(apps, hiddenSet, isEditing) {
        if (isEditing) {
            val list = mutableListOf<SelectorGridItem>()
            visibleApps.forEach { list.add(SelectorGridItem.App(it)) }
            if (hiddenApps.isNotEmpty()) {
                list.add(SelectorGridItem.Header("隐藏应用 (点击取消隐藏)"))
                hiddenApps.forEach { list.add(SelectorGridItem.App(it)) }
            }
            list
        } else {
            visibleApps.map { SelectorGridItem.App(it) }
        }
    }

    // 1:1 对标 LinkGo 的首位应用倒计时驱动
    LaunchedEffect(isTimerActive, timerSeconds, visibleApps, isEditing, show) {
        if (show && isTimerActive && timerSeconds > 0 && visibleApps.isNotEmpty() && !isEditing) {
            val totalMillis = timerSeconds * 1000L
            val startTime = System.currentTimeMillis()
            while (show && isTimerActive && !isEditing) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = 1f - (elapsed.toFloat() / totalMillis).coerceIn(0f, 1f)
                remainingProgress = progress
                if (progress <= 0f) {
                    break
                }
                withFrameMillis { }
            }
            if (show && isTimerActive && !isEditing && visibleApps.isNotEmpty()) {
                val firstApp = visibleApps[0]
                onPick(
                    firstApp,
                    !defaultSmallWindow,
                    "倒计时自动分发: ${firstApp.label}",
                )
            }
        } else {
            remainingProgress = 0f
        }
    }

    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromItem = flatList.getOrNull(from.index) as? SelectorGridItem.App ?: return@rememberReorderableLazyGridState
        val toItem = flatList.getOrNull(to.index) ?: return@rememberReorderableLazyGridState

        // 场景 A：拖拽越过分割线
        if (toItem is SelectorGridItem.Header) {
            val isNowHidden = fromItem.app.packageName !in hiddenSet
            hiddenSet = if (isNowHidden) hiddenSet + fromItem.app.packageName else hiddenSet - fromItem.app.packageName
            return@rememberReorderableLazyGridState
        }

        // 场景 B：应用之间交换位置
        val toApp = (toItem as SelectorGridItem.App).app
        val fromIndex = apps.indexOfFirst { it.packageName == fromItem.app.packageName }
        val toIndex = apps.indexOfFirst { it.packageName == toApp.packageName }

        if (fromIndex != -1 && toIndex != -1) {
            val fromIsVisible = fromItem.app.packageName !in hiddenSet
            val toIsVisible = toApp.packageName !in hiddenSet
            if (fromIsVisible != toIsVisible) {
                hiddenSet = if (toIsVisible) hiddenSet - fromItem.app.packageName else hiddenSet + fromItem.app.packageName
            }
            val mutable = apps.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            apps = mutable
        }
    }

    val requestClose = {
        if (isEditing) {
            saveOrderAndHidden()
        }
        onDismiss()
    }

    MiuixBottomSheet(
        show = show,
        title = if (isEditing) "管理应用" else "选择应用打开",
        startAction = {
            MiuixTopBarIconButton(onClick = requestClose) {
                Icon(
                    imageVector = MiuixIcons.Medium.Close,
                    contentDescription = "关闭",
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        endAction = {
            MiuixTopBarIconButton(
                onClick = {
                    if (isEditing) {
                        saveOrderAndHidden()
                        isEditing = false
                    } else {
                        isTimerActive = false
                        isEditing = true
                    }
                }
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Settings,
                    contentDescription = if (isEditing) "完成" else "管理",
                    tint = if (isEditing) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        onDismissRequest = requestClose,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = "单击切换隐藏/显示 · 长按图标拖动自定义排序",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                // 1:1 对标 LinkGo 的精致单行横滑胶囊链接卡片
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTimerActive = false },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )

                        MiuixText(
                            text = url,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                        )

                        IconButton(
                            onClick = {
                                isTimerActive = false
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("url", url))
                                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制链接",
                                modifier = Modifier.size(16.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // 模式提示与倒计时状态
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = if (defaultSmallWindow) "单击小窗打开 · 长按全屏打开" else "单击全屏打开 · 长按小窗打开",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (isTimerActive && visibleApps.isNotEmpty()) {
                        val sec = (timerSeconds * remainingProgress).toInt() + 1
                        MiuixText(
                            text = "${sec}s 后自动打开",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // 中间：4 列应用网格（支持长按拖动排序与隐藏分栏）
            if (flatList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(text = "未检测到可用的外部应用", style = MiuixTheme.textStyles.body2)
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = flatList,
                        key = { item ->
                            when (item) {
                                is SelectorGridItem.App -> item.app.packageName
                                is SelectorGridItem.Header -> "grid_item_divider_header"
                            }
                        },
                        span = { item ->
                            GridItemSpan(if (item is SelectorGridItem.Header) 4 else 1)
                        }
                    ) { item ->
                        when (item) {
                            is SelectorGridItem.Header -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(0.5.dp)
                                            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    )
                                    MiuixText(
                                        text = item.title,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(0.5.dp)
                                            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    )
                                }
                            }
                            is SelectorGridItem.App -> {
                                val app = item.app
                                val isHidden = app.packageName in hiddenSet
                                val isFirst = !isEditing && visibleApps.firstOrNull()?.packageName == app.packageName

                                ReorderableItem(
                                    state = reorderableState,
                                    key = app.packageName,
                                    enabled = isEditing,
                                ) { isDragging ->
                                    val scale by animateFloatAsState(if (isDragging) 1.15f else 1f, label = "dragScale")
                                    Column(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .alpha(if (isHidden) 0.45f else 1.0f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(
                                                if (isEditing) {
                                                    Modifier
                                                        .longPressDraggableHandle(enabled = true)
                                                        .clickable {
                                                            hiddenSet = if (isHidden) hiddenSet - app.packageName else hiddenSet + app.packageName
                                                        }
                                                } else {
                                                    Modifier.combinedClickable(
                                                        onClick = {
                                                            isTimerActive = false
                                                            if (show) {
                                                                onPick(
                                                                    app,
                                                                    !defaultSmallWindow,
                                                                    if (defaultSmallWindow) {
                                                                        "小窗打开: ${app.label}"
                                                                    } else {
                                                                        "全屏打开: ${app.label}"
                                                                    },
                                                                )
                                                            }
                                                        },
                                                        onLongClick = {
                                                            isTimerActive = false
                                                            if (show) {
                                                                val isSmallWindow = !defaultSmallWindow
                                                                onPick(
                                                                    app,
                                                                    !isSmallWindow,
                                                                    if (isSmallWindow) {
                                                                        "长按反转小窗: ${app.label}"
                                                                    } else {
                                                                        "长按反转全屏: ${app.label}"
                                                                    },
                                                                )
                                                            }
                                                        },
                                                    )
                                                }
                                            )
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier.size(60.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            LinkAppIcon(packageName = app.packageName, size = 48.dp)

                                            // 推荐应用右上角小蓝点
                                            if (!app.isBrowser && !isEditing) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .clip(CircleShape)
                                                        .background(MiuixTheme.colorScheme.primary)
                                                        .size(8.dp),
                                                )
                                            }

                                            // 编辑模式下的可见性角标
                                            if (isEditing) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isHidden) MiuixTheme.colorScheme.surfaceVariant
                                                            else MiuixTheme.colorScheme.primary
                                                        )
                                                        .size(18.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null,
                                                        tint = if (isHidden) MiuixTheme.colorScheme.onSurfaceVariantSummary else Color.White,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                            }

                                            // 首位应用外轮廓描边倒计时 (非编辑模式下)
                                            if (isFirst && isTimerActive && remainingProgress > 0f) {
                                                val primaryColor = MiuixTheme.colorScheme.primary
                                                val trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                                Canvas(modifier = Modifier.size(60.dp)) {
                                                    val sw = 2.5.dp.toPx()
                                                    val iconRadius = 10.dp.toPx()
                                                    val inset = 2.dp.toPx()
                                                    val drawSize = size.width - inset * 2
                                                    val drawRadius = iconRadius + 3.dp.toPx()
                                                    val rect = Rect(Offset(inset, inset), Size(drawSize, drawSize))
                                                    val roundedRect = RoundRect(rect, CornerRadius(drawRadius))

                                                    drawRoundRect(
                                                        color = trackColor,
                                                        topLeft = rect.topLeft,
                                                        size = rect.size,
                                                        cornerRadius = CornerRadius(drawRadius),
                                                        style = Stroke(width = sw),
                                                    )

                                                    val path = Path().apply { addRoundRect(roundedRect) }
                                                    val pathMeasure = PathMeasure()
                                                    pathMeasure.setPath(path, false)
                                                    val pathLength = pathMeasure.length
                                                    val resultPath = Path()
                                                    pathMeasure.getSegment(0f, pathLength * remainingProgress, resultPath, true)
                                                    drawPath(
                                                        path = resultPath,
                                                        color = primaryColor,
                                                        style = Stroke(width = sw, cap = StrokeCap.Round),
                                                    )
                                                }
                                            }
                                        }

                                        // 单行应用标题
                                        MiuixText(
                                            text = app.label,
                                            style = MiuixTheme.textStyles.footnote2,
                                            fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isFirst) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth(),
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
}
