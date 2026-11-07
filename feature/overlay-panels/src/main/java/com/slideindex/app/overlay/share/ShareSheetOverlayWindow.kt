package com.slideindex.app.overlay.share

import com.slideindex.app.overlay.overlayHostContext

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayToastWindow
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.overlay.overlayBottomPanelHeightCap
import com.slideindex.app.overlay.overlayBottomPanelWidth
import com.slideindex.app.search.ShareImageTarget
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.launch

private const val TAG = "ShareSheetOverlay"
private const val SHARE_SHEET_SCRIM_ANIM_MS = 180
private const val SHARE_SHEET_EXIT_MS = 180L

/**
 * 内置分享面板。
 *
 * 为什么要有它：从**我们自己的浮窗**里发起分享时，系统分享面板（普通 Activity）
 * 必然被浮层（`TYPE_APPLICATION_OVERLAY` / `TYPE_ACCESSIBILITY_OVERLAY`）盖住 ——
 * 既看不见也点不到；现有代码只能"先关掉浮窗再拉起 chooser"（见
 * `FileBrowserImageViewer` / `FileBrowserShare` 里的旧补丁），代价是用户丢掉当前面板、
 * 选完还得重新滑一次。
 *
 * 面板把分享目标平铺成网格，点中即用 `ComponentName` 直发，全程不离开浮层；
 * 长按可把常用目标钉住置顶（系统面板没有这个能力）。
 */
object ShareSheetOverlayWindow {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var visibilityState: MutableTransitionState<Boolean>? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var screenOffDismissReceiver: ScreenOffDismissReceiver? = null
    private var dismissToken = 0

    /** 发出去之前要先收起的来源浮窗（取词面板 / 文件浏览器 …），由调用方传入。 */
    private var beforeSend: (() -> Unit)? = null
    private var payload: ShareSheetPayload? = null
    private var hostContext: Context? = null

    val isShowing: Boolean
        get() = composeView != null

    fun show(
        context: Context,
        payload: ShareSheetPayload,
        beforeSend: (() -> Unit)? = null,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context, payload, beforeSend)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        val host = overlayHostContext() ?: run {
            Log.w(TAG, "show: accessibility service not connected")
            return false
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: overlays not available")
            return false
        }
        // 连续点两次分享：直接换掉旧面板，不叠两层。
        if (composeView != null) removeImmediately()
        this.payload = payload
        this.beforeSend = beforeSend
        hostContext = host
        return ensureWindow(host, payload)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (composeView == null) return
        val token = ++dismissToken
        visibilityState?.targetState = false
        mainHandler.postDelayed({
            if (token != dismissToken) return@postDelayed
            cleanup()
        }, SHARE_SHEET_EXIT_MS)
    }

    /** 立刻拆窗（点中目标 / 屏幕熄灭时用）：不需要退场动画，且必须马上让出图层。 */
    private fun removeImmediately() {
        ++dismissToken
        cleanup()
    }

    private fun handleBack(): Boolean {
        if (composeView == null) return false
        dismiss()
        return true
    }

    private fun ensureWindow(host: Context, payload: ShareSheetPayload): Boolean {
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            Log.w(TAG, "ensureWindow: no WindowManager")
            return false
        }
        windowManager = wm
        val visibility = MutableTransitionState(false).apply { targetState = true }
        visibilityState = visibility
        val overlayContext = OverlayCompose.themedContext(host)
        val composeOwner = OverlayComposeOwner()
        owner = composeOwner
        val view = OverlayCompose.createComposeView(overlayContext, composeOwner).apply {
            setContent {
                val content: @Composable () -> Unit = {
                    OverlayAwareModuleTheme {
                        ShareSheetRoot(
                            payload = payload,
                            visibilityState = visibility,
                            onDismiss = ::dismiss,
                            onSend = ::sendTarget,
                            onOpenSystemPanel = ::openSystemPanel,
                        )
                    }
                }
                val provider = com.slideindex.app.overlay.panels.PanelRuntimeHooks.appDependenciesComposition
                if (provider != null) provider.provide(content) else content()
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(host),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "XG:share-sheet"
        }
        backHandler = OverlayViewBackHandler(view) { handleBack() }.also { it.attach() }
        screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }.also { it.register(host) }
        return runCatching {
            wm.addView(view, params)
            composeView = view
            OverlaySceneController.onContentPanelShown()
            true
        }.getOrElse { error ->
            Log.w(TAG, "addView failed", error)
            cleanup()
            false
        }
    }

    private fun cleanup() {
        val view = composeView
        val wm = windowManager
        val dialogOwner = owner
        backHandler?.detach()
        backHandler = null
        screenOffDismissReceiver?.unregister()
        screenOffDismissReceiver = null
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
        composeView = null
        windowManager = null
        owner = null
        visibilityState = null
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
        if (view != null) OverlaySceneController.onContentPanelHidden()
    }

    /** 点中目标：直发。目标已被卸载时退回系统面板，别让用户白点一下。 */
    private fun sendTarget(target: ShareImageTarget) {
        val current = payload ?: return fail()
        val context = hostContext ?: return fail()
        val intent = current.intent().apply {
            component = ComponentName(target.packageName, target.activityClassName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val dismissSource = beforeSend
        removeImmediately()
        runCatching { dismissSource?.invoke() }
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started) {
            Log.w(TAG, "target launch failed: ${target.packageName}/${target.activityClassName}")
            openSystemChooser(context, current)
        }
    }

    /** 面板底部的「使用系统分享面板」：仍然可用，只是先收起浮层再拉起（系统面板必须在最上面）。 */
    private fun openSystemPanel() {
        val current = payload ?: return fail()
        val context = hostContext ?: return fail()
        val dismissSource = beforeSend
        removeImmediately()
        runCatching { dismissSource?.invoke() }
        openSystemChooser(context, current)
    }

    private fun openSystemChooser(context: Context, payload: ShareSheetPayload) {
        val chooser = Intent.createChooser(payload.intent(), null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!runCatching { context.startActivity(chooser) }.isSuccess) {
            Log.w(TAG, "system chooser failed")
        }
    }

    private fun fail() {
        removeImmediately()
    }
}

/**
 * 钉住的排前面，其余保持解析器给的顺序（应用名拼音）。
 *
 * 只存目标、不存图标：图标在格子真正可见时才由 [PickerAppIconBitmap] 取（它自带缓存），
 * 一屏 20 格就只解码 20 个，不再像以前那样把几十个目标的图标全解一遍。
 */
private fun orderItems(items: List<ShareImageTarget>, pins: List<String>): List<ShareImageTarget> {
    if (pins.isEmpty()) return items
    val byKey = items.associateBy { it.shareKey }
    val pinned = pins.mapNotNull { byKey[it] }
    if (pinned.isEmpty()) return items
    val pinnedKeys = pinned.map { it.shareKey }.toSet()
    return pinned + items.filter { it.shareKey !in pinnedKeys }
}

private val ShareImageTarget.shareKey: String
    get() = "$packageName/$activityClassName"

@Composable
private fun ShareSheetRoot(
    payload: ShareSheetPayload,
    visibilityState: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    onSend: (ShareImageTarget) -> Unit,
    onOpenSystemPanel: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 首帧直接用缓存（有就立刻画，不等 PackageManager）；冷启动才先转圈。
    var items by remember(payload) {
        mutableStateOf(ShareSheetTargetCache.peek(payload.kind).orEmpty())
    }
    var pins by remember(payload) { mutableStateOf(ShareSheetPins.peek().orEmpty()) }
    var loading by remember(payload) { mutableStateOf(items.isEmpty()) }

    LaunchedEffect(payload) {
        pins = ShareSheetPins.read(context)
        // 缓存命中时 [loading] 已经是 false，这里只是后台把列表刷新到最新。
        val fresh = ShareSheetTargetCache.load(context, payload.kind)
        items = fresh
        loading = false
    }

    val ordered = remember(items, pins) { orderItems(items, pins) }
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(tween(SHARE_SHEET_SCRIM_ANIM_MS)),
            exit = fadeOut(tween(SHARE_SHEET_SCRIM_ANIM_MS)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .combinedClickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss,
                        onLongClick = {},
                    )
            )
        }
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(tween(SHARE_SHEET_SCRIM_ANIM_MS)) +
                slideInVertically(tween(SHARE_SHEET_SCRIM_ANIM_MS)) { it },
            exit = fadeOut(tween(SHARE_SHEET_SCRIM_ANIM_MS)) +
                slideOutVertically(tween(SHARE_SHEET_SCRIM_ANIM_MS)) { it },
            modifier = Modifier
                .overlayBottomPanelWidth()
                .fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .overlayBottomPanelHeightCap()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.share_sheet_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.share_sheet_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    when {
                        loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }

                        ordered.isEmpty() -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.share_sheet_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(items = ordered, key = { it.shareKey }) { target ->
                                // 资源文案必须在**组合期**用 stringResource 取：在 onClick 回调里
                                // 走 LocalContext.current.getString(...) 不是配置感知的，Compose lint 会判
                                // Error（LocalContextGetResourceValueCall）并让 lintLiteDebug 直接失败。
                                val pinnedToast = stringResource(
                                    R.string.share_sheet_toast_pinned,
                                    target.appLabel,
                                )
                                val unpinnedToast = stringResource(
                                    R.string.share_sheet_toast_unpinned,
                                    target.appLabel,
                                )
                                ShareSheetCell(
                                    target = target,
                                    pinned = target.shareKey in pins,
                                    onClick = { onSend(target) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch {
                                            val updated = ShareSheetPins.toggle(context, target.shareKey)
                                            pins = updated
                                            val pinnedNow = target.shareKey in updated
                                            OverlayToastWindow.show(
                                                context,
                                                if (pinnedNow) pinnedToast else unpinnedToast,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onOpenSystemPanel) {
                            Text(
                                text = stringResource(R.string.share_sheet_use_system),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareSheetCell(
    target: ShareImageTarget,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ShareTargetIcon(target = target)
            if (pinned) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(12.dp),
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = target.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 单个目标的图标：先同步 peek 共享缓存（命中就直接画），未命中才异步加载。
 *
 * 关键点：这段只在格子**真正组合**时执行，`LazyVerticalGrid` 只组合可见的十几格，
 * 于是图标解码量 = 屏幕上的格子数，而不是目标总数。
 */
@Composable
private fun ShareTargetIcon(target: ShareImageTarget) {
    val context = LocalContext.current
    val cacheKey = target.shareKey
    var bitmap by remember(cacheKey) {
        mutableStateOf(
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.peekActivityIcon?.invoke(target.packageName, target.activityClassName)
        )
    }
    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            bitmap =
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.loadActivityIcon?.invoke(context, target.packageName, target.activityClassName)
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = target.label,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        // 图标还没加载/解码失败也别留空：给一个占位方块，布局不跳。
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}
