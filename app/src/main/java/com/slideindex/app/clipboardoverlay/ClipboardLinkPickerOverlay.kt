package com.slideindex.app.clipboardoverlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.link.LinkLauncher
import com.slideindex.app.link.LinkRegion
import com.slideindex.app.link.previewLinkTarget
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.resolveActivityCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PICKER_ANIM_MS = 280

internal data class PickerLinkEntry(
    val url: String,
    val sourcePackage: String = "",
    val sourceText: String? = null,
    val useLinkLauncher: Boolean = false,
)

internal object ClipboardLinkPickerOverlay {
    private const val TAG = "ClipboardLinkPicker"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val launchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var panelVisible: MutableState<Boolean>? = null
    private var onHidden: (() -> Unit)? = null
    private val exitRunnable = Runnable {
        val extra = onHidden
        onHidden = null
        removeWindow()
        extra?.invoke()
    }

    val isShowing: Boolean
        get() = composeView?.isAttachedToWindow == true

    fun show(context: Context, urls: List<String>) {
        val entries = urls.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { PickerLinkEntry(url = it, useLinkLauncher = false) }
        showEntries(context, entries)
    }

    /**
     * 供「指针链接雷达」与「全局复制/屏幕内容链接雷达」命中同节点多条链接时弹出选择。
     */
    fun showRadarLinks(context: Context, links: List<LinkRegion>) {
        val entries = links
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url.trim() }
            .map {
                PickerLinkEntry(
                    url = it.url.trim(),
                    sourcePackage = it.sourcePackage,
                    sourceText = it.sourceText,
                    useLinkLauncher = true,
                )
            }
        showEntries(context, entries)
    }

    private fun showEntries(context: Context, entries: List<PickerLinkEntry>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showEntries(context, entries) }
            return
        }
        if (entries.isEmpty()) return
        dismissImmediate()
        val host = MessageOverlayHost.resolveHostContext(context) ?: context
        val overlayContext = OverlayCompose.themedContext(host)
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dialogOwner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner)
        val visibleState = mutableStateOf(false)
        panelVisible = visibleState
        view.setContent {
            OverlayAwareModuleTheme {
                ClipboardLinkPickerContent(
                    visible = visibleState.value,
                    entries = entries,
                    onOpen = { entry ->
                        dismiss {
                            if (entry.useLinkLauncher) {
                                launchScope.launch {
                                    LinkLauncher.openUrl(host, entry.url, entry.sourcePackage, entry.sourceText)
                                }
                            } else {
                                openUrl(host, entry.url)
                            }
                        }
                    },
                    onOpenBatch = { batch ->
                        dismiss {
                            if (batch.any { it.useLinkLauncher }) {
                                launchScope.launch {
                                    batch.forEach { entry ->
                                        LinkLauncher.openUrl(host, entry.url, entry.sourcePackage, entry.sourceText)
                                        delay(100)
                                    }
                                }
                            } else {
                                ClipboardLinkOpenAllActivity.start(host, batch.map { it.url })
                            }
                        }
                    },
                    onDismiss = { dismiss() },
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(host),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            title = "ClipboardLinkPicker"
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            panelVisible = null
            dialogOwner.destroy()
            return
        }
        windowManager = wm
        composeView = view
        owner = dialogOwner
        backHandler = OverlayViewBackHandler(view) { dismiss() }.also { it.attach() }
        view.requestFocus()
        view.post { visibleState.value = true }
    }

    fun dismiss(afterHide: (() -> Unit)? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss(afterHide) }
            return
        }
        if (composeView == null) {
            afterHide?.invoke()
            return
        }
        val visible = panelVisible
        if (visible == null || !visible.value) return
        onHidden = afterHide
        visible.value = false
        mainHandler.removeCallbacks(exitRunnable)
        mainHandler.postDelayed(exitRunnable, PICKER_ANIM_MS.toLong())
    }

    fun dismissImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissImmediate() }
            return
        }
        mainHandler.removeCallbacks(exitRunnable)
        onHidden = null
        removeWindow()
    }

    private fun removeWindow() {
        backHandler?.detach()
        backHandler = null
        val wm = windowManager
        val view = composeView
        if (wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        OverlayCompose.teardownOverlayCompose(composeView, owner)
        owner = null
        composeView = null
        windowManager = null
        panelVisible = null
    }

    private fun openUrl(context: Context, url: String) {
        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository?.readSnapshot()
            ?: AppSettings()
        val opened = runCatching {
            SearchEngineLauncher.launchOpenableUri(context, url, settings)
        }.getOrDefault(false)
        if (!opened) {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

private data class LinkHandlerInfo(
    val label: String?,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
)

@Composable
private fun ClipboardLinkPickerContent(
    visible: Boolean,
    entries: List<PickerLinkEntry>,
    onOpen: (PickerLinkEntry) -> Unit,
    onOpenBatch: (List<PickerLinkEntry>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val isDark = LocalAppDarkTheme.current
    val handlers = remember(entries) { mutableStateMapOf<String, LinkHandlerInfo>() }
    val selectedUrls = remember(entries) { mutableStateListOf<String>() }

    LaunchedEffect(entries) {
        for (entry in entries) {
            val predicted = try {
                previewLinkTarget(context, entry.url, entry.sourcePackage, entry.sourceText)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (predicted != null) {
                val bitmap = runCatching {
                    val drawable = predicted.rawIcon ?: return@runCatching null
                    val size = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                    drawable.toBitmap(width = size, height = size).asImageBitmap()
                }.getOrNull() ?: predicted.icon
                handlers[entry.url] = LinkHandlerInfo(predicted.label, bitmap)
            } else {
                handlers[entry.url] = resolveHandler(context, entry.url)
            }
        }
    }

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val cornerPx = with(density) { 28.dp.toPx() }
    val sheetTint = if (isDark) 0x991C1C1E.toInt() else 0x99F5F5F7.toInt()
    // 弹窗高度按当前配置取屏幕高度的 60%（跟随 LocalConfiguration，已随配置变化重组）。
    @android.annotation.SuppressLint("ConfigurationScreenWidthHeight")
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val scrimInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }
    val fadeSpec = tween<Float>(PICKER_ANIM_MS, easing = FastOutSlowInEasing)
    val slideSpec = tween<IntOffset>(PICKER_ANIM_MS, easing = FastOutSlowInEasing)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .clickable(
                        indication = null,
                        interactionSource = scrimInteraction,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(fadeSpec) + slideInVertically(slideSpec) { it },
            exit = fadeOut(fadeSpec) + slideOutVertically(slideSpec) { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sheetShape)
                    .clickable(
                        indication = null,
                        interactionSource = sheetInteraction,
                        onClick = {},
                    )
                    .navigationBarsPadding(),
            ) {
                Box {
                    LocalFrostedGlassBackdrop(
                        modifier = Modifier.matchParentSize(),
                        cornerRadiusPx = cornerPx,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
                    ) {
                        val allSelected = selectedUrls.size == entries.size && entries.isNotEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.clipboard_overlay_pick_links_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.clipboard_overlay_pick_links_count, entries.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (allSelected) "取消全选" else "全选",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (allSelected) {
                                            selectedUrls.clear()
                                        } else {
                                            selectedUrls.clear()
                                            selectedUrls.addAll(entries.map { it.url })
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxListHeight),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            items(entries, key = { it.url }) { entry ->
                                val handler = handlers[entry.url] ?: remember(entry.url) {
                                    resolveHandler(context, entry.url)
                                }
                                val isChecked = entry.url in selectedUrls
                                LinkRow(
                                    url = entry.url,
                                    handler = handler,
                                    checked = isChecked,
                                    onClick = { onOpen(entry) },
                                    onToggleCheck = {
                                        if (isChecked) {
                                            selectedUrls.remove(entry.url)
                                        } else {
                                            selectedUrls.add(entry.url)
                                        }
                                    },
                                    onLongClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("url", entry.url))
                                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(stringResource(R.string.clipboard_overlay_pick_links_cancel))
                            }
                            Button(
                                onClick = {
                                    val targetBatch = if (selectedUrls.isNotEmpty()) {
                                        entries.filter { it.url in selectedUrls }
                                    } else {
                                        entries
                                    }
                                    onOpenBatch(targetBatch)
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = CircleShape,
                            ) {
                                val confirmLabel = if (selectedUrls.isNotEmpty()) {
                                    "打开选中 (${selectedUrls.size})"
                                } else {
                                    stringResource(R.string.clipboard_overlay_pick_links_open_all)
                                }
                                Text(confirmLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkRow(
    url: String,
    handler: LinkHandlerInfo,
    checked: Boolean,
    onClick: () -> Unit,
    onToggleCheck: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val bgColor = if (checked) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val borderMod = if (checked) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), shape)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(borderMod)
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = handler.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_clipboard_overlay_open),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            AutoScrollingUrlText(
                text = displayUrl(url),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            val handlerLabel = handler.label
            if (!handlerLabel.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.clipboard_overlay_open_with, handlerLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggleCheck() },
        )
    }
}

private fun displayUrl(url: String): String {
    val withoutScheme = url.substringAfter("://", missingDelimiterValue = url)
    return withoutScheme.ifBlank { url }
}

@Composable
private fun AutoScrollingUrlText(
    text: String,
    color: Color,
    style: TextStyle,
) {
    // 用 basicMarquee 代替 horizontalScroll + animateScrollTo：
    // 后者会让 ScrollState 长期处于 isScrollInProgress，行内横向滚动因此在按下瞬间
    // 零阈值抢走手势（行点击失效、外层 LazyColumn 竖滑被取消）。
    // basicMarquee 是纯绘制动画，不消费触摸。
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                repeatDelayMillis = 900,
                initialDelayMillis = 700,
            ),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

private fun resolveHandler(context: Context, url: String): LinkHandlerInfo {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val resolved = runCatching {
        context.packageManager.resolveActivityCompat(intent)
    }.getOrNull() ?: return LinkHandlerInfo(null, null)
    val label = runCatching { resolved.loadLabel(context.packageManager).toString() }.getOrNull()
    val icon = runCatching {
        val drawable = resolved.loadIcon(context.packageManager) ?: return@runCatching null
        val size = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        drawable.toBitmap(width = size, height = size).asImageBitmap()
    }.getOrNull()
    return LinkHandlerInfo(label, icon)
}
