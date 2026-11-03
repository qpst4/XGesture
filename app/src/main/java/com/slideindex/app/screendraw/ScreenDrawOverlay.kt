package com.slideindex.app.screendraw

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.R
import com.slideindex.app.imageeditor.ImageEditorSavedImageDeleteScheduler
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.GlobalOverlayDismissHelper
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayDisplayRotationMonitor
import com.slideindex.app.overlay.OverlayPanelTheme
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.overlay.ScreenPinManager
import com.slideindex.app.overlay.dragdrop.DragFlowSession
import com.slideindex.app.overlay.dragdrop.DragFlowSource
import com.slideindex.app.overlay.screenshot.ScreenshotStorage
import com.slideindex.app.overlay.screenshot.SmartScreenshotOcr
import com.slideindex.app.service.ScreenshotCaptureOutcome
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.roundToInt

/**
 * 「屏幕绘制」：全屏实时标注层。
 *
 * 画布是透明全屏窗口，直接盖在实时屏幕上；
 * 工具栏包含 14 项完整操作，工具配置面板通过 Compose 磨砂胶囊实时展开，
 * 包含文字变换、序号角标、马赛克、框选矩形高亮等完整功能。
 */
object ScreenDrawOverlay {

    private const val TAG = "ScreenDraw"
    private const val CAPTURE_DELAY_MS = 150L

    private data class ComposedScreenDraw(
        val bitmap: Bitmap,
        val screenRect: Rect?,
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var hostContext: Context? = null
    private var canvas: ScreenDrawCanvasView? = null
    private var canvasHost: FrameLayout? = null
    private var canvasParams: WindowManager.LayoutParams? = null
    private var toolbar: View? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
    private var toolbarOwner: OverlayComposeOwner? = null
    private var toolbarBlurEnabled = false
    private val orientationState = mutableStateOf(ScreenDrawOrientation.HORIZONTAL)
    private val currentToolState = mutableStateOf(ScreenDrawTool.PEN)
    private val popupOptionsToolState = mutableStateOf<ScreenDrawTool?>(null)
    private val isFinishActionsShowingState = mutableStateOf(false)
    private var isOptionsPopupShowing = false
    private var optionsPopupView: View? = null
    private var optionsOwner: OverlayComposeOwner? = null
    private val passthroughState = mutableStateOf(false)
    private val canUndoState = mutableStateOf(false)
    private val canRedoState = mutableStateOf(false)
    private val strokeWidthState = mutableStateOf(3f)
    private val textFontSizeState = mutableStateOf(24f)
    private val numberSizeState = mutableStateOf(48f)
    private val mosaicRadiusState = mutableStateOf(28f)
    private val eraserRadiusState = mutableStateOf(20f)
    private val colorState = mutableStateOf(ScreenDrawCanvasView.DEFAULT_COLOR)
    private val shapeState = mutableStateOf(ScreenDrawShape.RECTANGLE)
    private val numberStyleState = mutableStateOf(ScreenDrawNumberStyle.SOLID_CIRCLE)
    private val eraserModeState = mutableStateOf(ScreenDrawEraserMode.STROKE)
    private var passthrough = false
    private var minimized = false
    private var ballView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var ballOwner: OverlayComposeOwner? = null
    private var ballLastX: Int? = null
    private var ballLastY: Int? = null
    private var textEditor: LinearLayout? = null
    private var textInput: EditText? = null
    private var textCallback: ((String) -> Unit)? = null

    private var density = 1f
    private var screenWidth = 1
    private var screenHeight = 1
    private var saving = false
    private var overlaysSuppressed = false

    private var rotationMonitor: OverlayDisplayRotationMonitor? = null
    private var screenOffReceiver: ScreenOffDismissReceiver? = null

    val isShowing: Boolean get() = canvas != null

    /** 绘制开/关回调（供录制 dock 等外部悬浮 UI 避让，不用轮询）。 */
    var onShowingChanged: ((Boolean) -> Unit)? = null

    fun toggle(context: Context): Boolean {
        if (isShowing) {
            dismiss()
            return true
        }
        val shown = show(context)
        if (shown) onShowingChanged?.invoke(true)
        return shown
    }

    fun show(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context) }
            return true
        }
        if (isShowing) return true

        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (service == null) {
            toast(context, R.string.screen_draw_accessibility_required)
            return false
        }
        val host = SlideIndexAccessibilityService.overlayHostContext() ?: context.applicationContext
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) return false

        val metrics = OverlayScreenMetrics.sizePx(host)
        screenWidth = metrics.first.coerceAtLeast(1)
        screenHeight = metrics.second.coerceAtLeast(1)
        density = host.resources.displayMetrics.density
        hostContext = host
        windowManager = wm
        saving = false
        passthrough = false
        passthroughState.value = false
        canUndoState.value = false
        canRedoState.value = false
        minimized = false
        orientationState.value = ScreenDrawOrientation.HORIZONTAL
        currentToolState.value = ScreenDrawTool.PEN
        popupOptionsToolState.value = null
        isOptionsPopupShowing = false
        strokeWidthState.value = 3f
        textFontSizeState.value = 24f
        numberSizeState.value = 48f
        mosaicRadiusState.value = 28f
        eraserRadiusState.value = 20f
        colorState.value = ScreenDrawCanvasView.DEFAULT_COLOR
        shapeState.value = ScreenDrawShape.RECTANGLE
        numberStyleState.value = ScreenDrawNumberStyle.SOLID_CIRCLE
        eraserModeState.value = ScreenDrawEraserMode.STROKE

        val drawCanvas = ScreenDrawCanvasView(host) { dismiss() }
        drawCanvas.onTouchDown = {
            hideOptionsPopup()
        }
        val canvasHost = FrameLayout(host).apply {
            addView(
                drawCanvas,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        // 独立的配置面板卡片（挂在全屏透明 canvasHost 内，横排 316×148dp，竖排 148×316dp，默认 GONE，决不自动打开）
        val optionsOwner = OverlayComposeOwner()
        OverlayCompose.bindOwners(canvasHost, optionsOwner)
        val optionsView = OverlayCompose.createComposeView(host, optionsOwner).apply {
            visibility = View.GONE
            setContent {
                OverlayAwareModuleTheme {
                    if (isFinishActionsShowingState.value) {
                        ScreenDrawFinishActionsCard(
                            orientation = orientationState.value,
                            onAction = { action ->
                                handleFinishAction(action)
                            },
                        )
                    } else {
                        ScreenDrawOptionsPanelCompose(
                            orientation = orientationState.value,
                            activeTool = popupOptionsToolState.value,
                            shape = shapeState.value,
                            color = colorState.value,
                            widthDp = strokeWidthState.value,
                            textFontSizeSp = textFontSizeState.value,
                            numberStyle = numberStyleState.value,
                            numberSizeDp = numberSizeState.value,
                            mosaicRadiusDp = mosaicRadiusState.value,
                            eraserMode = eraserModeState.value,
                            eraserRadiusDp = eraserRadiusState.value,
                            onShape = {
                                shapeState.value = it
                                canvas?.shape = it
                            },
                            onColor = {
                                colorState.value = it
                                canvas?.color = it
                            },
                            onWidth = {
                                strokeWidthState.value = it
                                canvas?.widthPx = it * density
                            },
                            onTextFontSize = {
                                textFontSizeState.value = it
                                canvas?.textFontSizeSp = it
                            },
                            onNumberStyle = {
                                numberStyleState.value = it
                                canvas?.numberStyle = it
                            },
                            onNumberSize = {
                                numberSizeState.value = it
                                canvas?.numberSizePx = it * density
                            },
                            onMosaicRadius = {
                                mosaicRadiusState.value = it
                                canvas?.mosaicRadiusPx = it * density
                            },
                            onEraserMode = {
                                eraserModeState.value = it
                                canvas?.eraserMode = it
                            },
                            onEraserRadius = {
                                eraserRadiusState.value = it
                                canvas?.eraserRadiusPx = it * density
                            },
                        )
                    }
                }
            }
        }
        canvasHost.addView(
            optionsView,
            FrameLayout.LayoutParams(dp(316f), dp(148f)),
        )
        this.optionsPopupView = optionsView
        this.optionsOwner = optionsOwner

        val editor = buildTextEditor(host)
        canvasHost.addView(
            editor,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = (screenHeight * 0.22f).roundToInt()
                leftMargin = dp(28f)
                rightMargin = dp(28f)
            },
        )
        val canvasLayout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(host),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        try {
            wm.addView(canvasHost, canvasLayout)
        } catch (error: Exception) {
            Log.e(TAG, "add canvas failed", error)
            return false
        }
        this.canvasHost = canvasHost
        canvas = drawCanvas
        canvasParams = canvasLayout
        textEditor = editor
        drawCanvas.requestFocus()

        drawCanvas.onContentChanged = {
            canUndoState.value = drawCanvas.canUndo
            canRedoState.value = drawCanvas.canRedo
        }
        drawCanvas.onRequestTextInput = { x, y, initial, onDone ->
            showTextEditor(x, y, initial, onDone)
        }
        drawCanvas.onRequestDragAndDrop = { cropRect ->
            startDragAndDropShare(cropRect)
        }

        // 异步获取当前屏幕底图供马赛克打码使用
        SlideIndexAccessibilityService.captureDisplayBitmap { outcome ->
            if (outcome is ScreenshotCaptureOutcome.Success) {
                drawCanvas.backgroundSnapshot = outcome.bitmap
            }
        }

        // 独立的主工具栏窗口（尺寸永久固定，零抖动，零重排）
        val toolbarOwner = OverlayComposeOwner()
        val drawToolbar = OverlayCompose.createComposeView(host, toolbarOwner).apply {
            setContent {
                OverlayAwareModuleTheme {
                    ScreenDrawMainToolbarCompose(
                        orientation = orientationState.value,
                        passthrough = passthroughState.value,
                        canUndo = canUndoState.value,
                        canRedo = canRedoState.value,
                        currentTool = currentToolState.value,
                        shape = shapeState.value,
                        isFinishActionsShowing = isFinishActionsShowingState.value,
                        onToolClick = { target ->
                            handleToolClick(target)
                        },
                        onUndo = { canvas?.undo() },
                        onRedo = { canvas?.redo() },
                        onClear = { canvas?.clear() },
                        onTogglePassthrough = { setPassthrough(!passthrough) },
                        onToggleOrientation = { toggleOrientation() },
                        onMinimize = { minimizeDraw() },
                        onFinish = { toggleFinishActions() },
                        onExit = { dismiss() },
                        onBlurEnabled = { setToolbarBlur(it) },
                        onMoveBy = { dx, dy -> moveToolbarBy(dx, dy) },
                        onMoveFinished = {},
                    )
                }
            }
        }

        val tbWidth = dp(316f)
        val tbHeight = dp(86f)
        val toolbarLayout = WindowManager.LayoutParams(
            tbWidth,
            tbHeight,
            OverlayWindowTypes.overlayWindowType(host),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((screenWidth - tbWidth) / 2).coerceAtLeast(0)
            y = (screenHeight - tbHeight - dp(28f)).coerceAtLeast(0)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        try {
            drawToolbar.alpha = 0f
            wm.addView(drawToolbar, toolbarLayout)
        } catch (error: Exception) {
            Log.e(TAG, "add toolbar failed", error)
            wm.removeView(canvasHost)
            canvas = null
            canvasParams = null
            return false
        }
        toolbar = drawToolbar
        toolbarParams = toolbarLayout
        this.toolbarOwner = toolbarOwner
        setToolbarBlur(toolbarBlurEnabled)
        drawToolbar.animate().alpha(1f).setDuration(120L).start()

        screenOffReceiver = ScreenOffDismissReceiver { dismiss() }.also { it.register(host) }
        rotationMonitor = OverlayDisplayRotationMonitor(host) { dismiss() }.also { it.start() }

        suppressOtherOverlays()

        Log.i(TAG, "shown")
        return true
    }

    private fun handleToolClick(target: ScreenDrawTool) {
        val current = currentToolState.value
        val canvasView = canvas ?: return
        canvasView.tool = target
        currentToolState.value = target

        if (isFinishActionsShowingState.value) {
            hideOptionsPopup()
        }

        if (target == ScreenDrawTool.BOX_SELECT) {
            hideOptionsPopup()
            return
        }

        if (current == target) {
            if (isOptionsPopupShowing && !isFinishActionsShowingState.value) {
                hideOptionsPopup()
            } else {
                showOptionsPopup(target)
            }
        } else {
            if (isOptionsPopupShowing && !isFinishActionsShowingState.value) {
                showOptionsPopup(target)
            } else {
                hideOptionsPopup()
            }
        }
    }

    private fun showOptionsPopup(tool: ScreenDrawTool) {
        val popup = optionsPopupView ?: return
        val tbParams = toolbarParams ?: return
        val isVertical = orientationState.value == ScreenDrawOrientation.VERTICAL
        val tbWidth = tbParams.width
        val tbHeight = tbParams.height
        val panelWidth = if (isVertical) dp(148f) else dp(316f)
        val panelHeight = dp(getOptionsPanelHeightDp(orientationState.value, tool, false))
        val gap = dp(8f)

        val lp = popup.layoutParams as? FrameLayout.LayoutParams
        if (lp != null && (lp.width != panelWidth || lp.height != panelHeight)) {
            lp.width = panelWidth
            lp.height = panelHeight
            popup.layoutParams = lp
        }

        val targetX: Float
        val targetY: Float

        if (isVertical) {
            // 竖排模式：工具栏高 316dp，宽 96dp。面板在左方或右方，高度 316dp，与工具栏齐高
            val tbCenterX = tbParams.x + tbWidth / 2f
            val rawX = if (tbCenterX > screenWidth / 2f) {
                (tbParams.x - panelWidth - gap).toFloat()
            } else {
                (tbParams.x + tbWidth + gap).toFloat()
            }
            targetX = rawX.coerceIn(dp(10f).toFloat(), (screenWidth - panelWidth - dp(10f)).toFloat())
            val rawY = tbParams.y + (tbHeight - panelHeight) / 2f
            targetY = rawY.coerceIn(dp(16f).toFloat(), (screenHeight - panelHeight - dp(16f)).toFloat())
        } else {
            // 横排模式：工具栏宽 316dp，高 80dp。面板在上方或下方，高度根据工具内容自适应
            val tbCenterY = tbParams.y + tbHeight / 2f
            if (tbCenterY > screenHeight * 0.45f) {
                // 工具栏在下半部，面板在上方，面板底部紧贴工具栏上方（相距 8dp）
                targetY = (tbParams.y - panelHeight - gap).toFloat()
            } else {
                // 工具栏在上半部，面板在下方，面板顶部紧贴工具栏下方（相距 8dp）
                targetY = (tbParams.y + tbHeight + gap).toFloat()
            }
            // 水平居中对齐工具栏，限制在安全屏幕内
            val rawX = tbParams.x + (tbWidth - panelWidth) / 2f
            targetX = rawX.coerceIn(dp(12f).toFloat(), (screenWidth - panelWidth - dp(12f)).toFloat())
        }

        isFinishActionsShowingState.value = false
        popupOptionsToolState.value = tool
        if (!isOptionsPopupShowing) {
            popup.translationX = targetX
            popup.translationY = targetY
            popup.alpha = 0f
            popup.visibility = View.VISIBLE
            popup.animate().alpha(1f).setDuration(140L).start()
            isOptionsPopupShowing = true
        } else {
            popup.animate().translationX(targetX).translationY(targetY).alpha(1f).setDuration(140L).start()
        }
    }

    private fun showFinishActionsPopup() {
        val popup = optionsPopupView ?: return
        val tbParams = toolbarParams ?: return
        val isVertical = orientationState.value == ScreenDrawOrientation.VERTICAL
        val tbWidth = tbParams.width
        val tbHeight = tbParams.height
        val panelWidth = if (isVertical) dp(52f) else dp(264f)
        val panelHeight = if (isVertical) dp(264f) else dp(52f)
        val gap = dp(8f)

        val lp = popup.layoutParams as? FrameLayout.LayoutParams
        if (lp != null && (lp.width != panelWidth || lp.height != panelHeight)) {
            lp.width = panelWidth
            lp.height = panelHeight
            popup.layoutParams = lp
        }

        val targetX: Float
        val targetY: Float

        if (isVertical) {
            val tbCenterX = tbParams.x + tbWidth / 2f
            val rawX = if (tbCenterX > screenWidth / 2f) {
                (tbParams.x - panelWidth - gap).toFloat()
            } else {
                (tbParams.x + tbWidth + gap).toFloat()
            }
            targetX = rawX.coerceIn(dp(10f).toFloat(), (screenWidth - panelWidth - dp(10f)).toFloat())
            val rawY = tbParams.y + (tbHeight - panelHeight) / 2f
            targetY = rawY.coerceIn(dp(16f).toFloat(), (screenHeight - panelHeight - dp(16f)).toFloat())
        } else {
            val tbCenterY = tbParams.y + tbHeight / 2f
            if (tbCenterY > screenHeight * 0.45f) {
                targetY = (tbParams.y - panelHeight - gap).toFloat()
            } else {
                targetY = (tbParams.y + tbHeight + gap).toFloat()
            }
            val rawX = tbParams.x + (tbWidth - panelWidth) / 2f
            targetX = rawX.coerceIn(dp(12f).toFloat(), (screenWidth - panelWidth - dp(12f)).toFloat())
        }

        popup.translationX = targetX
        popup.translationY = targetY
        popupOptionsToolState.value = null
        isFinishActionsShowingState.value = true
        if (!isOptionsPopupShowing) {
            popup.alpha = 0f
            popup.visibility = View.VISIBLE
            popup.animate().alpha(1f).setDuration(140L).start()
            isOptionsPopupShowing = true
        }
    }

    private fun toggleFinishActions() {
        if (isFinishActionsShowingState.value && isOptionsPopupShowing) {
            hideOptionsPopup()
        } else {
            showFinishActionsPopup()
        }
    }

    private fun hideOptionsPopup() {
        val popup = optionsPopupView ?: return
        isFinishActionsShowingState.value = false
        if (!isOptionsPopupShowing) return
        isOptionsPopupShowing = false
        popup.animate().alpha(0f).setDuration(120L).withEndAction {
            popup.visibility = View.GONE
            popupOptionsToolState.value = null
        }.start()
    }

    private fun toggleOrientation() {
        hideOptionsPopup()
        val isVertical = orientationState.value == ScreenDrawOrientation.VERTICAL
        val nextOrientation = if (isVertical) ScreenDrawOrientation.HORIZONTAL else ScreenDrawOrientation.VERTICAL
        orientationState.value = nextOrientation

        val view = toolbar ?: return
        val params = toolbarParams ?: return
        val wm = windowManager ?: return

        if (nextOrientation == ScreenDrawOrientation.VERTICAL) {
            params.width = dp(86f)
            params.height = dp(316f)
            params.x = (screenWidth - dp(86f) - dp(16f)).coerceAtLeast(0)
            params.y = ((screenHeight - dp(316f)) / 2).coerceAtLeast(0)
        } else {
            params.width = dp(316f)
            params.height = dp(86f)
            params.x = ((screenWidth - dp(316f)) / 2).coerceAtLeast(0)
            params.y = (screenHeight - dp(86f) - dp(28f)).coerceAtLeast(0)
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun moveToolbarBy(dx: Float, dy: Float) {
        val view = toolbar ?: return
        val params = toolbarParams ?: return
        val wm = windowManager ?: return
        val width = params.width
        val height = params.height
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view, params) }
        hideOptionsPopup()
    }

    private fun setToolbarBlur(enabled: Boolean) {
        toolbarBlurEnabled = enabled
    }

    /**
     * 「操作屏幕」：把触摸**和焦点**一起还给下面的 App。
     *
     * 只加 [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE] 不够：画布窗默认是可聚焦窗口
     * （要收返回键、要让文字工具的输入法挂上来，见 `show()` 里的 `requestFocus()`）。
     * 只关掉触摸时窗口焦点仍在本窗 —— 下面的 App 收得到点击，却不是焦点窗口，
     * 输入法不会挂到它上面，于是"点得到输入框、弹不出键盘"。
     *
     * `minimizeDraw()` 那条路径之所以没这问题，就是因为它同时加了 `FLAG_NOT_FOCUSABLE`；
     * 这里补齐同一个位，穿透态才真正等同"把屏幕交给下面的 App"。
     *
     * 代价（有意为之）：穿透态下画布收不到返回键，返回交给下面的 App —— 「操作屏幕」本来就是这个语义；
     * 退出绘制仍可用工具栏的 ×。
     */
    private fun setPassthrough(enabled: Boolean) {
        val view = canvasHost ?: return
        val params = canvasParams ?: return
        val wm = windowManager ?: return
        passthrough = enabled
        passthroughState.value = enabled
        params.flags = if (enabled) {
            params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            params.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
        // 窗口焦点由系统在 flags 变更后重算；视图侧也同步清/要一次，避免残留只对 view 有效的焦点。
        if (enabled) {
            canvas?.clearFocus()
        } else {
            canvas?.requestFocus()
        }
    }

    private fun minimizeDraw() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { minimizeDraw() }
            return
        }
        if (minimized) return
        minimized = true
        Log.i(TAG, "minimizeDraw: minimizing screen draw")
        hideOptionsPopup()
        val cView = canvasHost
        val cParams = canvasParams
        val wm = windowManager ?: return
        if (cView != null && cParams != null) {
            cView.visibility = View.GONE
            cParams.flags = cParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { wm.updateViewLayout(cView, cParams) }
                .onFailure { Log.e(TAG, "minimizeDraw: cView updateViewLayout failed", it) }
        }
        val tView = toolbar
        val tParams = toolbarParams
        if (tView != null && tParams != null) {
            tView.visibility = View.GONE
            tParams.flags = tParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { wm.updateViewLayout(tView, tParams) }
                .onFailure { Log.e(TAG, "minimizeDraw: tView updateViewLayout failed", it) }
        }

        val host = hostContext ?: return
        val size = dp(48f)

        val owner = OverlayComposeOwner()
        val ballComposeView = OverlayCompose.createComposeView(host, owner).apply {
            setContent {
                OverlayAwareModuleTheme {
                    ScreenDrawMinimizedBallCompose(
                        currentTool = currentToolState.value,
                        shape = shapeState.value,
                        color = colorState.value,
                        onClick = {
                            Log.i(TAG, "ball Compose onClick triggered")
                            restoreDraw()
                        },
                    )
                }
            }
        }

        val ballContainer = object : FrameLayout(host) {
            private var downRawX = 0f
            private var downRawY = 0f
            private var startWindowX = 0
            private var startWindowY = 0
            private var isDragging = false
            private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop

            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                val p = ballParams ?: return super.dispatchTouchEvent(ev)
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downRawX = ev.rawX
                        downRawY = ev.rawY
                        startWindowX = p.x
                        startWindowY = p.y
                        isDragging = false
                        super.dispatchTouchEvent(ev)
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - downRawX
                        val dy = ev.rawY - downRawY
                        if (!isDragging && (dx * dx + dy * dy > slop * slop)) {
                            isDragging = true
                            val cancel = android.view.MotionEvent.obtain(ev).apply {
                                action = android.view.MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                        }
                        if (isDragging) {
                            p.x = (startWindowX + dx.roundToInt())
                                .coerceIn(0, (screenWidth - size).coerceAtLeast(0))
                            p.y = (startWindowY + dy.roundToInt())
                                .coerceIn(0, (screenHeight - size).coerceAtLeast(0))
                            ballLastX = p.x
                            ballLastY = p.y
                            runCatching { wm.updateViewLayout(this, p) }
                            return true
                        }
                        return super.dispatchTouchEvent(ev)
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            isDragging = false
                            return true
                        }
                        return super.dispatchTouchEvent(ev)
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        return super.dispatchTouchEvent(ev)
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            OverlayCompose.bindOwners(this, owner)
            addView(
                ballComposeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        val defaultX = (screenWidth - size - dp(12f)).coerceAtLeast(0)
        val defaultY = ((screenHeight - size) / 2).coerceAtLeast(0)
        val targetX = (ballLastX ?: defaultX).coerceIn(0, (screenWidth - size).coerceAtLeast(0))
        val targetY = (ballLastY ?: defaultY).coerceIn(0, (screenHeight - size).coerceAtLeast(0))

        val params = WindowManager.LayoutParams(
            size,
            size,
            OverlayWindowTypes.overlayWindowType(host),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX
            y = targetY
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }

        runCatching { wm.addView(ballContainer, params) }
            .onSuccess { Log.i(TAG, "minimizeDraw: ballContainer added at ($targetX, $targetY)") }
            .onFailure { Log.e(TAG, "minimizeDraw: wm.addView ball failed", it) }
        ballView = ballContainer
        ballParams = params
        ballOwner = owner
    }

    private fun restoreDraw() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreDraw() }
            return
        }
        if (!minimized) return
        minimized = false
        Log.i(TAG, "restoreDraw: restoring screen draw full canvas and toolbar")
        val view = ballView
        val owner = ballOwner
        val compose = (view as? FrameLayout)?.getChildAt(0) as? ComposeView ?: view as? ComposeView
        view?.let { v ->
            runCatching { windowManager?.removeView(v) }
            OverlayCompose.clearViewTreeOwners(v)
        }
        OverlayCompose.teardownOverlayCompose(compose, owner)
        ballView = null
        ballParams = null
        ballOwner = null
        val cView = canvasHost
        val cParams = canvasParams
        val wm = windowManager
        if (cView != null && cParams != null && wm != null) {
            cView.visibility = View.VISIBLE
            // 穿透态恢复时**不要**清 NOT_FOCUSABLE：否则焦点又被画布抢回去，下面 App 的键盘再次弹不出来。
            if (!passthrough) {
                cParams.flags = cParams.flags and
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }
            runCatching { wm.updateViewLayout(cView, cParams) }
                .onFailure { Log.e(TAG, "restoreDraw: cView updateViewLayout failed", it) }
        }
        val tView = toolbar
        val tParams = toolbarParams
        if (tView != null && tParams != null && wm != null) {
            val isVertical = orientationState.value == ScreenDrawOrientation.VERTICAL
            tParams.width = if (isVertical) dp(86f) else dp(316f)
            tParams.height = if (isVertical) dp(316f) else dp(86f)
            tView.visibility = View.VISIBLE
            tParams.flags = tParams.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            runCatching { wm.updateViewLayout(tView, tParams) }
                .onFailure { Log.e(TAG, "restoreDraw: tView updateViewLayout failed", it) }
        }
    }

    private fun buildTextEditor(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(14f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(0xEE20232A.toInt()) // 深色半透明磨砂质感
                setStroke((1f * density).roundToInt(), 0x33FFFFFF)
            }
            elevation = 16f * density
            visibility = View.GONE
            isClickable = true
        }

        val input = EditText(context).apply {
            hint = "输入文字"
            setSingleLine(false)
            maxLines = 4
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x77FFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(0x33000000)
                setStroke((1f * density).roundToInt(), 0x22FFFFFF)
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    confirmText()
                    true
                } else {
                    false
                }
            }
        }
        container.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12f)
            }
            layoutParams = lp
        }

        val cancel = TextView(context).apply {
            text = context.getString(android.R.string.cancel)
            textSize = 14f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(0x22FFFFFF)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { hideTextEditor() }
        }

        val confirm = TextView(context).apply {
            text = context.getString(android.R.string.ok)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(18f), dp(8f), dp(18f), dp(8f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(0xFF3897F0.toInt())
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmText() }
        }

        row.addView(cancel)
        row.addView(
            confirm,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = dp(10f)
            },
        )
        container.addView(row)
        textInput = input
        return container
    }

    private fun showTextEditor(x: Float, y: Float, initial: String = "", onDone: (String) -> Unit) {
        textCallback = onDone
        val editor = textEditor ?: return
        textInput?.setText(initial)
        if (initial.isNotEmpty()) {
            textInput?.setSelection(initial.length)
        }
        // 隐藏工具栏，避让软键盘
        toolbar?.visibility = View.GONE
        editor.visibility = View.VISIBLE
        textInput?.requestFocus()
        val input = textInput ?: return
        val imm = hostContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        input.post { runCatching { imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) } }
    }

    private fun hideTextEditor() {
        textEditor?.visibility = View.GONE
        // 恢复工具栏悬浮窗
        toolbar?.visibility = View.VISIBLE
        val input = textInput
        val imm = hostContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (input != null) {
            runCatching { imm?.hideSoftInputFromWindow(input.windowToken, 0) }
        }
        canvas?.requestFocus()
    }

    private fun confirmText() {
        val text = textInput?.text?.toString().orEmpty()
        textCallback?.invoke(text)
        textCallback = null
        hideTextEditor()
    }

    private fun handleFinishAction(action: ScreenDrawFinishAction) {
        if (saving) return
        val drawCanvas = canvas ?: return
        val host = hostContext ?: return

        saving = true
        hideTextEditor()
        hideOptionsPopup()
        toolbar?.visibility = View.GONE
        drawCanvas.visibility = View.INVISIBLE
        suppressOtherOverlays()

        mainHandler.postDelayed({
            val accepted = SlideIndexAccessibilityService.captureDisplayBitmap { outcome ->
                when (outcome) {
                    is ScreenshotCaptureOutcome.Success -> {
                        val composedResult = compose(drawCanvas, outcome.bitmap)
                        outcome.bitmap.recycle()
                        if (composedResult != null) {
                            dispatchFinishAction(host, action, composedResult.bitmap, composedResult.screenRect)
                        } else {
                            failSave()
                        }
                    }
                    else -> failSave()
                }
            }
            if (!accepted) failSave()
        }, CAPTURE_DELAY_MS)
    }

    private fun dispatchFinishAction(
        host: Context,
        action: ScreenDrawFinishAction,
        composed: Bitmap,
        screenRect: Rect?,
    ) {
        when (action) {
            ScreenDrawFinishAction.EDIT -> {
                val ok = FloatBallTextPick.openBuiltinImageEditor(host, composed, screenRect, null)
                composed.recycle()
                if (ok) {
                    restoreOtherOverlays()
                    dismiss()
                } else {
                    failSave()
                }
            }

            ScreenDrawFinishAction.OCR -> {
                SmartScreenshotOcr.pickTextFromBitmap(host, composed, screenRect, null)
                composed.recycle()
                restoreOtherOverlays()
                dismiss()
            }

            ScreenDrawFinishAction.COPY -> {
                val uri = ScreenshotStorage.createClipboardUri(host, composed)
                val ok = uri != null && ScreenshotStorage.copyToClipboard(host, uri)
                composed.recycle()
                if (ok) {
                    toast(host, R.string.screen_draw_copied)
                    restoreOtherOverlays()
                    dismiss()
                } else {
                    failSave()
                }
            }

            ScreenDrawFinishAction.SAVE -> {
                val uri = ScreenshotStorage.saveToGallery(host, composed)
                composed.recycle()
                if (uri != null) {
                    toast(host, R.string.screen_draw_saved)
                    restoreOtherOverlays()
                    dismiss()
                } else {
                    failSave()
                }
            }

            ScreenDrawFinishAction.SAVE_TEMP -> {
                val uri = FloatBallTextPick.saveScreenshotReturningUri(host, composed, ingestToHistory = false)
                composed.recycle()
                if (uri != null) {
                    ImageEditorSavedImageDeleteScheduler.scheduleDeleteAfterMinutes(host, uri, 5L)
                    toast(host, R.string.inspire_image_edit_save_auto_delete)
                    restoreOtherOverlays()
                    dismiss()
                } else {
                    failSave()
                }
            }

            ScreenDrawFinishAction.SHARE -> {
                val uri = ScreenshotStorage.createShareUri(host, composed)
                val ok = uri != null && ScreenshotStorage.share(host, uri)
                composed.recycle()
                if (ok) {
                    toast(host, R.string.screen_draw_shared)
                    restoreOtherOverlays()
                    dismiss()
                } else {
                    failSave()
                }
            }

            ScreenDrawFinishAction.PIN -> {
                ScreenPinManager.pinImage(host, composed, screenRect, null)
                composed.recycle()
                restoreOtherOverlays()
                dismiss()
            }
        }
    }

    private fun compose(canvasView: ScreenDrawCanvasView, screenshot: Bitmap): ComposedScreenDraw? {
        val strokes = canvasView.snapshotBitmap() ?: return null
        val result = screenshot.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val target = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        target.drawBitmap(
            strokes,
            null,
            Rect(0, 0, result.width, result.height),
            paint,
        )
        strokes.recycle()

        // 若用户框选了区域，则截取该局部区域截图输出，并返回对应的 screenRect
        val boxRect = canvasView.currentBoxSelectRect()
        if (boxRect != null && boxRect.width() > 10f && boxRect.height() > 10f) {
            val left = boxRect.left.toInt().coerceIn(0, result.width - 1)
            val top = boxRect.top.toInt().coerceIn(0, result.height - 1)
            val right = boxRect.right.toInt().coerceIn(left + 1, result.width)
            val bottom = boxRect.bottom.toInt().coerceIn(top + 1, result.height)
            val cropW = right - left
            val cropH = bottom - top
            if (cropW > 0 && cropH > 0) {
                val cropped = Bitmap.createBitmap(result, left, top, cropW, cropH)
                result.recycle()
                return ComposedScreenDraw(cropped, Rect(left, top, right, bottom))
            }
        }
        return ComposedScreenDraw(result, Rect(0, 0, result.width, result.height))
    }

    private fun startDragAndDropShare(cropRect: RectF?): Boolean {
        val drawCanvas = canvas ?: return false
        val host = hostContext ?: return false
        val screenshot = drawCanvas.backgroundSnapshot
        val strokes = drawCanvas.snapshotBitmap() ?: return false
        val base = screenshot?.copy(Bitmap.Config.ARGB_8888, true)
            ?: Bitmap.createBitmap(drawCanvas.width, drawCanvas.height, Bitmap.Config.ARGB_8888)
        val cvs = Canvas(base)
        cvs.drawBitmap(strokes, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        strokes.recycle()

        val finalBitmap: Bitmap
        if (cropRect != null && cropRect.width() > 10f && cropRect.height() > 10f) {
            val l = cropRect.left.toInt().coerceIn(0, base.width - 1)
            val t = cropRect.top.toInt().coerceIn(0, base.height - 1)
            val r = cropRect.right.toInt().coerceIn(l + 1, base.width)
            val b = cropRect.bottom.toInt().coerceIn(t + 1, base.height)
            val w = r - l
            val h = b - t
            if (w <= 0 || h <= 0) {
                base.recycle()
                return false
            }
            finalBitmap = Bitmap.createBitmap(base, l, t, w, h)
            base.recycle()
        } else {
            finalBitmap = base
        }

        val shareUri = ScreenshotStorage.createShareUri(host, finalBitmap)
        if (shareUri == null) {
            finalBitmap.recycle()
            return false
        }

        val clipData = ClipData.newUri(host.contentResolver, "Screenshot", shareUri)
        val shadowBuilder = object : View.DragShadowBuilder(drawCanvas) {
            override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
                val maxW = (screenWidth * 0.40f).toInt().coerceAtLeast(120)
                val maxH = (screenHeight * 0.40f).toInt().coerceAtLeast(120)
                val scale = minOf(maxW.toFloat() / finalBitmap.width, maxH.toFloat() / finalBitmap.height, 1f)
                val sw = (finalBitmap.width * scale).toInt().coerceAtLeast(1)
                val sh = (finalBitmap.height * scale).toInt().coerceAtLeast(1)
                outShadowSize.set(sw, sh)
                outShadowTouchPoint.set(sw / 2, sh / 2)
            }
            override fun onDrawShadow(canvas: Canvas) {
                val maxW = (screenWidth * 0.40f).toInt().coerceAtLeast(120)
                val maxH = (screenHeight * 0.40f).toInt().coerceAtLeast(120)
                val scale = minOf(maxW.toFloat() / finalBitmap.width, maxH.toFloat() / finalBitmap.height, 1f)
                val sw = (finalBitmap.width * scale).toInt().coerceAtLeast(1)
                val sh = (finalBitmap.height * scale).toInt().coerceAtLeast(1)
                val dest = Rect(0, 0, sw, sh)
                canvas.drawBitmap(finalBitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = 230
                })
            }
        }

        val flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
        // 起拖前登记：流转栏按落点决定"把画布放回来"（恢复）还是"退出绘制"（关掉）。
        val dragFlowSource = DragFlowSource(
            onRestore = { restoreFromDragFlow() },
            onDiscard = { dismiss() },
        )
        DragFlowSession.begin(dragFlowSource)
        val success = drawCanvas.startDragAndDrop(clipData, shadowBuilder, null, flags)
        if (success) {
            tempHideForDragFlow()
            // 兜底：若流转栏没开/没收到这次拖拽，别让会话一直挂着被下一次拖拽误认领。
            // 只清会话，不动画布（拖拽可能还没结束）。
            mainHandler.postDelayed({ DragFlowSession.end(dragFlowSource) }, 30_000L)
            toast(host, "已抓起，可拖拽流转至操作栏")
        } else {
            DragFlowSession.end(dragFlowSource)
        }
        return success
    }

    fun tempHideForDragFlow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { tempHideForDragFlow() }
            return
        }
        if (canvasHost == null) return
        hideOptionsPopup()
        canvasHost?.visibility = View.GONE
        toolbar?.visibility = View.GONE
    }

    fun restoreFromDragFlow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreFromDragFlow() }
            return
        }
        if (canvasHost == null) return
        canvasHost?.visibility = View.VISIBLE
        toolbar?.visibility = View.VISIBLE
        canvas?.requestFocus()
    }

    private fun failSave() {
        saving = false
        toolbar?.visibility = View.VISIBLE
        canvas?.visibility = View.VISIBLE
        restoreOtherOverlays()
        hostContext?.let { toast(it, R.string.screen_draw_save_failed) }
    }

    private fun suppressOtherOverlays() {
        if (overlaysSuppressed) return
        overlaysSuppressed = true
        runCatching { GlobalOverlayDismissHelper.dismissAllPanels() }
        runCatching { FloatingPointerOverlayWindow.suppressForScreenshotCapture() }
        runCatching { FloatBallOverlay.suppressForScreenshotCapture() }
        runCatching { FloatBallPickResultPanel.suppressForScreenshotCapture() }
    }

    private fun restoreOtherOverlays() {
        if (!overlaysSuppressed) return
        overlaysSuppressed = false
        runCatching { FloatingPointerOverlayWindow.restoreAfterScreenshotCapture() }
        runCatching { FloatBallOverlay.restoreAfterScreenshotCapture() }
        runCatching { FloatBallPickResultPanel.restoreAfterScreenshotCapture() }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        restoreOtherOverlays()
        val wm = windowManager
        val bView = ballView
        val bOwner = ballOwner
        val bCompose = (bView as? FrameLayout)?.getChildAt(0) as? ComposeView ?: bView as? ComposeView
        bView?.let { view ->
            runCatching { wm?.removeView(view) }
            OverlayCompose.clearViewTreeOwners(view)
        }
        OverlayCompose.teardownOverlayCompose(bCompose, bOwner)
        ballView = null
        ballParams = null
        ballOwner = null
        ballLastX = null
        ballLastY = null
        minimized = false
        val toolbarView = toolbar
        toolbarView?.let { view -> runCatching { wm?.removeView(view) } }
        OverlayCompose.teardownOverlayCompose(toolbarView as? ComposeView, toolbarOwner)
        toolbar = null
        toolbarParams = null
        toolbarOwner = null
        val hostView = canvasHost
        hostView?.let { view -> runCatching { wm?.removeView(view) } }
        hideOptionsPopup()
        OverlayCompose.teardownOverlayCompose(optionsPopupView as? ComposeView, optionsOwner)
        hostView?.let { OverlayCompose.clearViewTreeOwners(it) }
        optionsPopupView = null
        optionsOwner = null
        canvasHost = null
        canvas = null
        textEditor = null
        textInput = null
        canvasParams = null

        screenOffReceiver?.unregister()
        screenOffReceiver = null
        rotationMonitor?.stop()
        rotationMonitor = null

        windowManager = null
        hostContext = null
        saving = false
        Log.i(TAG, "dismissed")
        onShowingChanged?.invoke(false)
    }

    private fun toast(context: Context, resId: Int) {
        runCatching {
            Toast.makeText(context.applicationContext, context.getString(resId), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toast(context: Context, text: CharSequence) {
        runCatching {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
