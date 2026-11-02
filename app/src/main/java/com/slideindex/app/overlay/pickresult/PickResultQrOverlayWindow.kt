package com.slideindex.app.overlay.pickresult

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayPanelSystemGestureExclusion
import com.slideindex.app.overlay.OverlayToastWindow
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.overlay.dragdrop.DragFlowSource
import com.slideindex.app.overlay.history.HistoryDragPreview
import com.slideindex.app.overlay.history.HistoryEntryDragHelper
import com.slideindex.app.overlay.screenshot.ScreenshotStorage
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 取词面板「生成二维码」的独立浮窗。
 *
 * - 二维码在 IO 上生成（H → Q → M → L 自适应），生成期间显示 loading；
 * - 长按二维码 = 起系统拖拽，接入流转栏的「恢复 / 取消」：
 *   恢复 = 浮窗原样回来，取消 / 命中目标 = 关掉浮窗；
 * - 保存 / 分享复用 `ScreenshotStorage`，不新增权限；
 * - 文字太长或生成失败不打开本浮窗，只用 [OverlayToastWindow] 在最上层提示。
 *
 * 浮窗盖在取词面板上面；点罩层 / 返回 / 关闭按钮只关自己，面板还在。
 */
internal object PickResultQrOverlayWindow {

    private const val TAG = "PickResultQrOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var owner: OverlayComposeOwner? = null
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var screenOffReceiver: ScreenOffDismissReceiver? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var bitmapState: MutableState<Bitmap?>? = null
    private var loadingState: MutableState<Boolean>? = null
    /** 生成时顺手落在 cache 里的 PNG URI：长按起拖 / 分享直接用，避免主线程压缩。 */
    private var qrFileUri: Uri? = null
    private var appContext: Context? = null
    private var generationToken = 0
    private var dragSuspended = false

    var isShowing: Boolean = false
        private set

    fun show(context: Context, settings: AppSettings, text: String) {
        runOnMain {
            if (text.isBlank()) return@runOnMain
            if (PickResultQr.isTooLong(text)) {
                OverlayToastWindow.show(context, context.getString(R.string.pick_result_qr_too_long))
                return@runOnMain
            }
            if (isShowing) dismiss()

            val hostContext = OverlayDependencyAccess.overlayHostContext()
                ?: context.applicationContext
            if (!ensureWindow(hostContext, settings)) {
                OverlayToastWindow.show(context, context.getString(R.string.float_ball_action_failed))
                return@runOnMain
            }

            isShowing = true
            dragSuspended = false
            bitmapState?.value = null
            loadingState?.value = true
            composeView?.visibility = View.VISIBLE
            composeView?.requestFocus()

            val app = hostContext.applicationContext
            val token = ++generationToken
            scope.launch {
                val generated = withContext(Dispatchers.IO) {
                    val bitmap = PickResultQr.generate(text) ?: return@withContext null
                    bitmap to runCatching {
                        ScreenshotStorage.createShareUri(app, bitmap)
                    }.getOrNull()
                }
                if (token != generationToken || !isShowing) return@launch
                if (generated == null) {
                    dismiss()
                    OverlayToastWindow.show(app, app.getString(R.string.pick_result_qr_too_long))
                } else {
                    bitmapState?.value = generated.first
                    qrFileUri = generated.second
                    loadingState?.value = false
                }
            }
        }
    }

    fun dismiss() {
        runOnMain {
            if (!isShowing && composeView == null) return@runOnMain
            isShowing = false
            dragSuspended = false
            generationToken++
            val view = composeView
            val wm = windowManager
            val currentOwner = owner
            composeView = null
            windowManager = null
            layoutParams = null
            owner = null
            bitmapState?.value = null
            bitmapState = null
            loadingState = null
            qrFileUri = null
            if (view != null && wm != null) {
                runCatching { wm.removeView(view) }
            }
            screenOffReceiver?.unregister()
            screenOffReceiver = null
            backHandler?.detach()
            backHandler = null
            OverlayCompose.teardownOverlayCompose(view, currentOwner)
            appContext = null
        }
    }

    /** 起拖时把浮窗藏起来（保留窗口与状态），让流转栏和拖影露出来。 */
    fun suspendForDrag() {
        runOnMain {
            if (!isShowing || dragSuspended) return@runOnMain
            dragSuspended = true
            composeView?.clearFocus()
            composeView?.visibility = View.INVISIBLE
            updateDragFlags(suspended = true)
        }
    }

    /** 拖拽落回【恢复】：浮窗原样回来。 */
    fun resumeFromDrag() {
        runOnMain {
            if (!isShowing || !dragSuspended) return@runOnMain
            dragSuspended = false
            composeView?.visibility = View.VISIBLE
            updateDragFlags(suspended = false)
            composeView?.requestFocus()
        }
    }

    private fun ensureWindow(context: Context, settings: AppSettings): Boolean {
        if (composeView != null) return true

        val bitmapHolder = mutableStateOf<Bitmap?>(null)
        val loadingHolder = mutableStateOf(true)
        bitmapState = bitmapHolder
        loadingState = loadingHolder

        val overlayOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, overlayOwner).apply {
            setContent {
                OverlayAwareModuleTheme(settings) {
                    PickResultQrOverlayContent(
                        bitmap = bitmapHolder.value,
                        loading = loadingHolder.value,
                        onStartDrag = { startDrag() },
                        onSave = { saveToGallery() },
                        onShare = { share() },
                        onClose = { dismiss() },
                    )
                }
            }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: run {
                overlayOwner.destroy()
                return false
            }
        val params = buildLayoutParams(context)
        val added = runCatching { wm.addView(compose, params) }.isSuccess
        if (!added) {
            overlayOwner.destroy()
            Log.e(TAG, "failed to add QR overlay window")
            return false
        }

        windowManager = wm
        composeView = compose
        owner = overlayOwner
        layoutParams = params
        appContext = context.applicationContext
        backHandler = OverlayViewBackHandler(compose) { dismiss() }.also { it.attach() }
        screenOffReceiver = ScreenOffDismissReceiver { dismiss() }.also { it.register(context) }
        OverlayPanelSystemGestureExclusion.attach(compose)
        OverlaySceneController.onContentPanelShown()
        return true
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }

    private fun updateDragFlags(suspended: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = if (suspended) {
            params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            params.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    /**
     * 长按二维码 → 系统拖拽 + 流转栏源会话。
     *
     * 和搜索面板 / 剪贴板浮窗同一条链路：`DragFlowSource` 把"结束后恢复还是关掉"
     * 交给流转栏；没被认领时由 [HistoryEntryDragHelper] 的 `onDragEnd` 自己兜底恢复。
     */
    private fun startDrag() {
        runOnMain {
            if (!isShowing || dragSuspended) return@runOnMain
            val view = composeView ?: return@runOnMain
            val bitmap = bitmapState?.value ?: return@runOnMain
            val app = appContext ?: return@runOnMain

            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val uri = qrFileUri
            if (uri == null) {
                OverlayToastWindow.show(app, app.getString(R.string.float_ball_action_failed))
                return@runOnMain
            }
            val clip = ClipData(
                ClipDescription(
                    app.getString(R.string.quick_menu_qr_title),
                    arrayOf("image/png"),
                ),
                ClipData.Item(uri),
            )
            val source = DragFlowSource(
                onRestore = { resumeFromDrag() },
                onDiscard = { dismiss() },
            )
            val started = HistoryEntryDragHelper.startDrag(
                view = view,
                clipData = clip,
                preview = HistoryDragPreview(bitmap = bitmap),
                onDragStart = { suspendForDrag() },
                onDragEnd = { resumeFromDrag() },
                dragShadow = QrDragShadowBuilder(view, bitmap),
                dragFlowSource = source,
            )
            if (!started) resumeFromDrag()
        }
    }

    private fun saveToGallery() {
        val bitmap = bitmapState?.value ?: return
        val app = appContext ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                ScreenshotStorage.saveToGallery(app, bitmap)
            }
            OverlayToastWindow.show(
                app,
                app.getString(
                    if (saved != null) {
                        R.string.quick_menu_qr_saved
                    } else {
                        R.string.quick_menu_qr_save_failed
                    },
                ),
            )
        }
    }

    private fun share() {
        val bitmap = bitmapState?.value ?: return
        val app = appContext ?: return
        scope.launch {
            val uri = qrFileUri ?: withContext(Dispatchers.IO) {
                ScreenshotStorage.createShareUri(app, bitmap)
            }
            if (uri == null) {
                OverlayToastWindow.show(app, app.getString(R.string.float_ball_action_failed))
                return@launch
            }
            ScreenshotStorage.share(app, uri) { dismiss() }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickResultQrOverlayContent(
    bitmap: Bitmap?,
    loading: Boolean,
    onStartDrag: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onClose,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .widthIn(max = 360.dp)
                // 吞掉卡片内的点击，避免穿到罩层把浮窗关掉。
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (loading || imageBitmap == null) {
                    Box(
                        modifier = Modifier.size(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = stringResource(R.string.quick_menu_qr_title),
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(10.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = onStartDrag,
                            ),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.pick_result_qr_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onSave, enabled = imageBitmap != null) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = stringResource(R.string.quick_menu_qr_save),
                        )
                    }
                    IconButton(onClick = onShare, enabled = imageBitmap != null) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.quick_menu_qr_share),
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.panel_close),
                        )
                    }
                }
            }
        }
    }
}

/** 拖影：一块白色圆角方块 + 缩小后的二维码，避免 1024 整张挡住目标。 */
private class QrDragShadowBuilder(
    view: View,
    private val qr: Bitmap,
) : View.DragShadowBuilder(view) {

    private val density = view.resources.displayMetrics.density
    private val sizePx = (132 * density).toInt().coerceAtLeast(1)
    private val padPx = 10 * density
    private val radiusPx = 16 * density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(sizePx, sizePx)
        outShadowTouchPoint.set(sizePx / 2, sizePx / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.drawRoundRect(
            0f,
            0f,
            sizePx.toFloat(),
            sizePx.toFloat(),
            radiusPx,
            radiusPx,
            backgroundPaint,
        )
        val left = padPx
        val right = sizePx - padPx
        canvas.drawBitmap(qr, null, RectF(left, left, right, right), bitmapPaint)
    }
}
