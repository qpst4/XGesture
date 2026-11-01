package com.slideindex.app.copy

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.PathParser
import androidx.core.graphics.toColorInt
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatBallTranslateCoordinator
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.dragdrop.DragFlowSession
import com.slideindex.app.overlay.dragdrop.DragFlowSource
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelQueryBridge
import com.slideindex.app.overlay.setupBackgroundBlur
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.SlideIndexAccessibilityService
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UniversalCopyOverlay {
    private const val AUTO_DISMISS_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentOverlay: WeakReference<View>? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null
    private var currentWindowManager: WindowManager? = null

    /**
     * 本次长按拖拽的源会话。起拖时把浮层"藏起来但保留状态"，
     * 由流转栏决定结束后恢复还是真正 [dismiss]（见 DragFlowSession）。
     */
    private var currentDragSource: DragFlowSource? = null
    private var dismissRunnable: Runnable? = null
    private var currentOcrJob: Job? = null

    private val currentBlocks = mutableListOf<SelectableBlock>()
    private val cachedA11yBlocks = mutableListOf<SelectableBlock>()
    private val cachedOcrBlocks = mutableListOf<SelectableBlock>()
    private enum class ExtractMode { A11Y, OCR }
    private var currentMode: ExtractMode = ExtractMode.A11Y
    private var isRecognizing: Boolean = false
    private var isDismissing: Boolean = false

    private var statusPillRef: WeakReference<View>? = null
    private var statusTextRef: WeakReference<TextView>? = null
    private var hideStatusRunnable: Runnable? = null

    private var selectAllButton: WeakReference<ImageView>? = null
    private var screenshotButton: WeakReference<ImageView>? = null
    private var pickButton: WeakReference<ImageView>? = null
    private var ocrButton: WeakReference<ImageView>? = null
    private var translateButton: WeakReference<ImageView>? = null
    private var searchButton: WeakReference<ImageView>? = null
    private var copyButton: WeakReference<ImageView>? = null
    private var closeButton: WeakReference<ImageView>? = null
    private var textBlocksViewRef: WeakReference<TextBlocksView>? = null

    val isShowing: Boolean get() = currentOverlay?.get()?.visibility == View.VISIBLE

    fun show(context: Context, blocks: List<UniversalCopyBlock>) {
        handler.post {
            dismiss(animated = false)
            val selectable = blocks.map { SelectableBlock(it.text, Rect(it.bounds)) }
            cachedA11yBlocks.clear()
            cachedA11yBlocks.addAll(selectable)
            cachedOcrBlocks.clear()
            currentMode = ExtractMode.A11Y
            addOverlay(context, selectable)
        }
    }

    fun collectAndShow(service: SlideIndexAccessibilityService) {
        val blocks = UniversalCopyCollector.collectAllFromService(service)
        show(service, blocks)
    }

    fun dismiss(animated: Boolean = true) {
        // 拖拽会话到这里作废：无论它是正常结束还是被"取消"，
        // 都不能把恢复动作留给下一次拖拽。
        currentDragSource?.let(DragFlowSession::end)
        currentDragSource = null
        textBlocksViewRef?.get()?.endDrag()
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        hideStatusRunnable?.let(handler::removeCallbacks)
        hideStatusRunnable = null
        currentOcrJob?.cancel()
        currentOcrJob = null
        isRecognizing = false
        statusPillRef = null
        statusTextRef = null
        copyButton = null
        searchButton = null
        translateButton = null
        pickButton = null
        selectAllButton = null
        screenshotButton = null
        ocrButton = null
        closeButton = null
        textBlocksViewRef = null
        currentBlocks.clear()
        cachedA11yBlocks.clear()
        cachedOcrBlocks.clear()
        currentMode = ExtractMode.A11Y

        val overlay = currentOverlay?.get() ?: return
        if (isDismissing) return

        if (!animated || !overlay.isAttachedToWindow) {
            removeOverlayImmediate(overlay)
            return
        }

        isDismissing = true
        overlay.animate()
            .alpha(0f)
            .setDuration(130)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                removeOverlayImmediate(overlay)
                isDismissing = false
            }
            .start()
    }

    /**
     * 长按拖拽起拖：把浮层藏起来但**不清状态**（选区、块缓存、模式都留着），
     * 这样"恢复"能原样回来；同时挂上 FLAG_NOT_TOUCHABLE，让手指下面的应用
     * 正常接收这次投放，而不是被铺满全屏的选区窗吃掉。
     */
    private fun hideForDragFlow() {
        val overlay = currentOverlay?.get() ?: return
        val params = currentLayoutParams ?: return
        overlay.visibility = View.INVISIBLE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { currentWindowManager?.updateViewLayout(overlay, params) }
    }

    /** 恢复药丸：把浮层连同选区原样放回来。 */
    private fun restoreFromDragFlow() {
        currentDragSource?.let(DragFlowSession::end)
        currentDragSource = null
        val overlay = currentOverlay?.get() ?: return
        val params = currentLayoutParams ?: return
        overlay.visibility = View.VISIBLE
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { currentWindowManager?.updateViewLayout(overlay, params) }
        overlay.requestFocus()
        textBlocksViewRef?.get()?.endDrag()
        // 恢复后重新给满一条自动关闭时间：拖拽期间那条计时不该继续吃掉用户的停留时间。
        dismissRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, AUTO_DISMISS_MS)
    }

    private fun removeOverlayImmediate(overlay: View) {
        runCatching {
            val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(overlay)
            currentOverlay = null
            currentLayoutParams = null
            currentWindowManager = null
        }
    }

    private class SelectableBlock(val text: String, val bounds: Rect, var selected: Boolean = false)

    private fun addOverlay(context: Context, initialBlocks: List<SelectableBlock>) {
        currentBlocks.clear()
        currentBlocks.addAll(initialBlocks)

        val density = context.resources.displayMetrics.density
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val accentColor = "#326D32".toColorInt()
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusableInTouchMode = true
            isFocusable = true
        }

        val blocksView = TextBlocksView(context, currentBlocks, density, accentColor,
            onEmptyTap = { dismiss() },
            onSelectionChanged = { updateToolbarState() }
        )
        textBlocksViewRef = WeakReference(blocksView)
        root.addView(blocksView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 1. 顶部解耦的悬浮微胶囊（Status Island）
        val statusPill = createStatusPill(context, density)
        root.addView(statusPill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(56)
        })

        // 2. 底部纯图标浮动 Dock 胶囊（Floating Action Dock）
        val dock = createFloatingDock(context, density, accentColor)
        root.addView(dock, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(40)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        root.alpha = 0f
        statusPill.alpha = 0f
        statusPill.translationY = -dp(14).toFloat()
        dock.alpha = 0f
        dock.translationY = dp(32).toFloat()

        wm.addView(root, params)
        currentOverlay = WeakReference(root)
        currentLayoutParams = params
        currentWindowManager = wm
        isDismissing = false

        root.animate()
            .alpha(1f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .start()

        statusPill.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        dock.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(0.7f))
            .start()

        updateToolbarState()

        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, AUTO_DISMISS_MS)
    }

    private fun createStatusPill(context: Context, density: Float): View {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            elevation = dp(8).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                outlineAmbientShadowColor = 0x1A000000.toInt()
                outlineSpotShadowColor = 0x2A000000.toInt()
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF5FAFAFC.toInt(), 0xEEF1F2F6.toInt())
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke((0.75f * density + 0.5f).toInt(), 0x80FFFFFF.toInt())
            }
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                if (currentBlocks.isEmpty() && !isRecognizing) {
                    performScreenOcr(context)
                }
            }
        }
        val text = TextView(context).apply {
            setTextColor(0xDE1F2024.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        pill.addView(text)
        statusPillRef = WeakReference(pill)
        statusTextRef = WeakReference(text)
        return pill
    }

    private fun showStatus(text: String, autoDismissMs: Long = 0) {
        val pill = statusPillRef?.get() ?: return
        val textView = statusTextRef?.get() ?: return
        hideStatusRunnable?.let(handler::removeCallbacks)
        hideStatusRunnable = null

        textView.text = text
        pill.visibility = View.VISIBLE
        pill.animate().cancel()
        pill.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .start()

        if (autoDismissMs > 0) {
            val hideRunnable = Runnable {
                pill.animate()
                    .alpha(0f)
                    .translationY(-12f * pill.resources.displayMetrics.density)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            hideStatusRunnable = hideRunnable
            handler.postDelayed(hideRunnable, autoDismissMs)
        }
    }

    private fun createFloatingDock(
        context: Context,
        density: Float,
        accentColor: Int
    ): View {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val cornerRadiusPx = dp(24).toFloat()
        val strokeWidthPx = (0.75f * density + 0.5f).toInt()
        val primaryIconColor = 0xDE1F2024.toInt()

        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(14).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                outlineAmbientShadowColor = 0x22000000.toInt()
                outlineSpotShadowColor = 0x38000000.toInt()
            }
            // 坚实透亮白玉微晶底色（在深黑电影/短视频场景下始终保持饱满白色实体，绝不塌陷成灰黑）
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF4F7F8FA.toInt(), 0xEEEDEFF3.toInt())
            ).apply {
                cornerRadius = cornerRadiusPx
                setStroke(strokeWidthPx, 0xA0FFFFFF.toInt())
            }
            // 挂载硬件级跨应用真·毛玻璃 BackgroundBlurDrawable
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    setupBackgroundBlur(this@apply, cornerRadiusPx, dp(28), 0x80FFFFFF.toInt())
                    foreground = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        cornerRadius = cornerRadiusPx
                        setStroke(strokeWidthPx, 0xB0FFFFFF.toInt())
                    }
                    postInvalidateOnAnimation()
                    return true
                }
            })
            setPadding(dp(6), dp(5), dp(6), dp(5))
            isClickable = true
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    view.performClick()
                }
                true
            }
        }

        // 1. 全选
        val selectAll = createDockIconButton(
            context, density,
            icon = CopyActionIconDrawable(CopyActionIconDrawable.IconType.SELECT_ALL, density, tintColor = primaryIconColor),
            tooltip = context.getString(R.string.universal_copy_select_all)
        ) {
            val allSelected = currentBlocks.isNotEmpty() && currentBlocks.all { it.selected }
            currentBlocks.forEach { it.selected = !allSelected }
            textBlocksViewRef?.get()?.invalidate()
            updateToolbarState()
        }
        selectAllButton = WeakReference(selectAll)
        dock.addView(selectAll)

        // 2. 智能截图（一键无缝唤起系统智能截图，支持全屏/选区/贴图/编辑）
        val screenshot = createDockIconButton(
            context, density,
            icon = CopyActionIconDrawable(CopyActionIconDrawable.IconType.SCREENSHOT, density, tintColor = primaryIconColor),
            tooltip = context.getString(R.string.universal_copy_screenshot)
        ) {
            dismiss(animated = false)
            SlideIndexAccessibilityService.performSmartScreenshot()
        }
        screenshotButton = WeakReference(screenshot)
        dock.addView(screenshot)

        // 3. OCR 识图 / 无障碍 双向切换
        val ocr = createDockIconButton(
            context, density,
            icon = CopyActionIconDrawable(CopyActionIconDrawable.IconType.OCR_SCAN, density, tintColor = primaryIconColor),
            tooltip = context.getString(R.string.universal_copy_ocr)
        ) {
            if (currentMode == ExtractMode.A11Y) {
                if (cachedOcrBlocks.isNotEmpty()) {
                    // 已有 OCR 缓存，直接秒切
                    currentMode = ExtractMode.OCR
                    currentBlocks.clear()
                    currentBlocks.addAll(cachedOcrBlocks.map { SelectableBlock(it.text, Rect(it.bounds), false) })
                    textBlocksViewRef?.get()?.setBlocks(currentBlocks)
                    showStatus(context.getString(R.string.universal_copy_switched_ocr), autoDismissMs = 2000)
                    updateToolbarState()
                } else {
                    performScreenOcr(context)
                }
            } else {
                // 当前是 OCR，切回无障碍
                currentMode = ExtractMode.A11Y
                currentBlocks.clear()
                currentBlocks.addAll(cachedA11yBlocks.map { SelectableBlock(it.text, Rect(it.bounds), false) })
                textBlocksViewRef?.get()?.setBlocks(currentBlocks)
                showStatus(context.getString(R.string.universal_copy_switched_a11y), autoDismissMs = 2000)
                updateToolbarState()
            }
        }
        ocrButton = WeakReference(ocr)
        dock.addView(ocr)

        // 4. 分词
        val pick = createDockIconButton(
            context, density,
            icon = CopyActionIconDrawable(CopyActionIconDrawable.IconType.SEGMENT, density, tintColor = primaryIconColor),
            tooltip = context.getString(R.string.universal_copy_pick)
        ) {
            val selected = sortBlocksNaturally(currentBlocks.filter { it.selected })
            if (selected.isEmpty()) {
                notifySelectionRequired(context)
                return@createDockIconButton
            }
            val text = selected.joinToString("\n") { it.text }
            openPickPanel(context, text)
            dismiss()
        }
        pickButton = WeakReference(pick)
        dock.addView(pick)

        // 5. 翻译（采用工整饱满等宽矢量，彻底告别发虚甩尾毛刺）
        val translate = createDockIconButton(
            context, density,
            icon = CopyActionIconDrawable(CopyActionIconDrawable.IconType.TRANSLATE, density, tintColor = primaryIconColor),
            tooltip = context.getString(R.string.universal_copy_translate)
        ) {
            val selected = sortBlocksNaturally(currentBlocks.filter { it.selected })
            if (selected.isEmpty()) {
                notifySelectionRequired(context)
                return@createDockIconButton
            }
            val text = selected.joinToString("\n") { it.text }
            performTranslate(context, text)
            dismiss()
        }
        translateButton = WeakReference(translate)
        dock.addView(translate)

        // 5. 搜索（直接无缝呼出自带搜索面板，不跳系统弹窗）
        val search = createDockIconButton(
            context, density,
            icon = CopyActionIconDrawable(CopyActionIconDrawable.IconType.SEARCH, density, tintColor = primaryIconColor),
            tooltip = context.getString(R.string.universal_copy_search)
        ) {
            val selected = sortBlocksNaturally(currentBlocks.filter { it.selected })
            if (selected.isEmpty()) {
                notifySelectionRequired(context)
                return@createDockIconButton
            }
            val text = selected.joinToString(" ") { it.text }
            val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context
            SearchPanelQueryBridge.rememberQuery(hostContext, text)
            SearchPanelOverlayWindow.show(hostContext)
            dismiss()
        }
        searchButton = WeakReference(search)
        dock.addView(search)

        // 6. 复制（核心主操作，选中后亮起主题色胶囊）
        val copyDrawable = androidx.core.content.ContextCompat.getDrawable(
            context, R.drawable.ic_baseline_content_copy_24
        )?.mutate()?.apply {
            setTint(primaryIconColor)
        }
        val copy = createDockIconButton(
            context, density,
            icon = copyDrawable,
            tooltip = context.getString(R.string.universal_copy_copy)
        ) {
            val selected = sortBlocksNaturally(currentBlocks.filter { it.selected })
            if (selected.isEmpty()) {
                notifySelectionRequired(context)
                return@createDockIconButton
            }
            val text = selected.joinToString("\n") { it.text }
            copyToClipboard(context, text)
            Toast.makeText(
                context,
                context.resources.getQuantityString(
                    R.plurals.universal_copy_copied,
                    selected.size,
                    selected.size,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            dismiss()
        }
        copyButton = WeakReference(copy)
        dock.addView(copy)

        // 分隔细微线
        val divider = View(context).apply {
            background = GradientDrawable().apply {
                setColor(0x18000000.toInt())
                cornerRadius = dp(1).toFloat()
            }
        }
        dock.addView(divider, LinearLayout.LayoutParams(dp(1), dp(16)).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        })

        // 7. 关闭
        val closeDrawable = androidx.core.content.ContextCompat.getDrawable(
            context, R.drawable.ic_clipboard_overlay_close
        )?.mutate()?.apply {
            setTint(0x991F2024.toInt())
        }
        val close = createDockIconButton(
            context, density,
            icon = closeDrawable,
            tooltip = context.getString(android.R.string.cancel)
        ) {
            dismiss()
        }
        closeButton = WeakReference(close)
        dock.addView(close)

        return dock
    }

    private fun createDockIconButton(
        context: Context,
        density: Float,
        icon: android.graphics.drawable.Drawable?,
        tooltip: String,
        onClick: () -> Unit
    ): ImageView {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val size = dp(40)
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = tooltip
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(20).toFloat()
            }
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                }
                false
            }
            setOnLongClickListener {
                runCatching { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show()
                true
            }
            setOnClickListener {
                runCatching { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                onClick()
            }
        }
    }

    private fun updateToolbarState() {
        if (isRecognizing) return
        val count = currentBlocks.count { it.selected }
        val context = currentOverlay?.get()?.context ?: return
        val density = context.resources.displayMetrics.density
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val primaryIconColor = 0xDE1F2024.toInt()

        val isA11yMode = currentMode == ExtractMode.A11Y
        val ocrIconType = if (isA11yMode) CopyActionIconDrawable.IconType.OCR_SCAN else CopyActionIconDrawable.IconType.ACCESSIBILITY
        val ocrTooltip = if (isA11yMode) context.getString(R.string.universal_copy_ocr) else context.getString(R.string.universal_copy_switch_to_a11y)

        if (currentBlocks.isEmpty()) {
            showStatus(context.getString(R.string.universal_copy_empty_prompt), autoDismissMs = 0)
            selectAllButton?.get()?.visibility = View.GONE
            screenshotButton?.get()?.visibility = View.VISIBLE
            copyButton?.get()?.visibility = View.GONE
            searchButton?.get()?.visibility = View.GONE
            translateButton?.get()?.visibility = View.GONE
            pickButton?.get()?.visibility = View.GONE
            ocrButton?.get()?.apply {
                visibility = View.VISIBLE
                alpha = 1f
                isEnabled = true
                contentDescription = ocrTooltip
                background = GradientDrawable().apply {
                    setColor("#326D32".toColorInt())
                    cornerRadius = dp(20).toFloat()
                }
                setImageDrawable(CopyActionIconDrawable(ocrIconType, density, tintColor = Color.WHITE))
            }
            return
        }

        selectAllButton?.get()?.visibility = View.VISIBLE
        screenshotButton?.get()?.visibility = View.VISIBLE
        copyButton?.get()?.visibility = View.VISIBLE
        searchButton?.get()?.visibility = View.VISIBLE
        translateButton?.get()?.visibility = View.VISIBLE
        pickButton?.get()?.visibility = View.VISIBLE
        ocrButton?.get()?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            isEnabled = true
            contentDescription = ocrTooltip
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(20).toFloat()
            }
            setImageDrawable(CopyActionIconDrawable(ocrIconType, density, tintColor = primaryIconColor))
        }

        val hasSelection = count > 0
        if (hasSelection) {
            val countStr = context.resources.getQuantityString(
                R.plurals.universal_copy_selected,
                count,
                count,
            )
            showStatus(countStr, autoDismissMs = 2500)
        } else {
            showStatus(context.getString(R.string.universal_copy_tap_to_select), autoDismissMs = 2000)
        }

        val targetAlpha = if (hasSelection) 1f else 0.58f
        searchButton?.get()?.alpha = targetAlpha
        translateButton?.get()?.alpha = targetAlpha
        pickButton?.get()?.alpha = targetAlpha
        copyButton?.get()?.apply {
            alpha = targetAlpha
            if (hasSelection) {
                background = GradientDrawable().apply {
                    setColor("#326D32".toColorInt())
                    cornerRadius = dp(20).toFloat()
                }
                drawable?.mutate()?.setTint(Color.WHITE)
                animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
            } else {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(20).toFloat()
                }
                drawable?.mutate()?.setTint(primaryIconColor)
            }
        }
    }

    private fun notifySelectionRequired(context: Context) {
        showStatus(context.getString(R.string.universal_copy_tap_to_select), autoDismissMs = 2000)
        Toast.makeText(context, R.string.universal_copy_tap_to_select, Toast.LENGTH_SHORT).show()
        statusPillRef?.get()?.apply {
            animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
        }
    }

    private fun sortBlocksNaturally(blocks: List<SelectableBlock>): List<SelectableBlock> {
        return blocks.sortedWith { a, b ->
            val yDiff = a.bounds.top - b.bounds.top
            if (abs(yDiff) > 24) {
                yDiff
            } else {
                a.bounds.left - b.bounds.left
            }
        }
    }

    private fun performTranslate(context: Context, text: String) {
        // 引擎 / 目标语言 / 「即时翻译」全部交给统一翻译链路：与取词面板、剪贴板智能翻译完全一致。
        // 选「本地 App」或关掉即时翻译时不会打开面板（否则会留下一个只显示原文的空面板）。
        FloatBallTranslateCoordinator.translateFromExternalEntry(
            context = context,
            text = text,
            openTextPanel = { openPickPanel(context, it) },
        )
    }

    private fun resolvePpOcrModelId(context: Context): String? {
        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
        val configured = settings?.floatBallOcrModelId.orEmpty()
        val catalog = OcrDependencyAccess.catalogProvider(context) ?: return null
        val repo = OcrDependencyAccess.modelRepository(context) ?: return null

        if (configured.isNotBlank()) {
            val entry = catalog.findModel(configured)
            if (entry?.engine == OcrEngines.PPOCR && repo.isInstalled(configured)) {
                return configured
            }
        }
        val installed = repo.installedModelIds()
        return installed.firstOrNull { id ->
            catalog.findModel(id)?.engine == OcrEngines.PPOCR
        }
    }

    private fun performScreenOcr(context: Context) {
        if (isRecognizing) return
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (service == null) {
            Toast.makeText(context, R.string.universal_copy_no_text, Toast.LENGTH_SHORT).show()
            return
        }
        val modelId = resolvePpOcrModelId(context)
        if (modelId.isNullOrBlank()) {
            Toast.makeText(context, R.string.share_image_ocr_model_required, Toast.LENGTH_LONG).show()
            return
        }
        val root = currentOverlay?.get() ?: return
        isRecognizing = true
        updateOcrRecognizingUi(context, isRecognizing = true)

        root.visibility = View.INVISIBLE

        currentOcrJob?.cancel()
        currentOcrJob = scope.launch {
            var ocrBitmap: Bitmap? = null
            try {
                delay(60)
                ocrBitmap = RegionalScreenshotOcr.captureDisplayBitmapPublic(service)
                root.visibility = View.VISIBLE

                if (ocrBitmap == null) {
                    isRecognizing = false
                    updateOcrRecognizingUi(context, isRecognizing = false)
                    Toast.makeText(context, R.string.universal_copy_ocr_no_text, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val inference = OcrDependencyAccess.inferenceService(context)
                val lines = withContext(Dispatchers.Default) {
                    try {
                        inference?.recognizePpOcrLines(modelId, ocrBitmap)
                    } finally {
                        ocrBitmap.recycle()
                    }
                }

                isRecognizing = false
                updateOcrRecognizingUi(context, isRecognizing = false)

                if (lines.isNullOrEmpty()) {
                    Toast.makeText(context, R.string.universal_copy_ocr_no_text, Toast.LENGTH_SHORT).show()
                    if (currentBlocks.isEmpty()) {
                        showStatus(context.getString(R.string.universal_copy_empty_prompt), autoDismissMs = 0)
                    }
                } else {
                    val newBlocks = lines.map { SelectableBlock(it.text, Rect(it.bounds)) }
                    cachedOcrBlocks.clear()
                    cachedOcrBlocks.addAll(newBlocks)
                    currentMode = ExtractMode.OCR
                    applyRecognizedBlocks(newBlocks)
                    showStatus(context.getString(R.string.universal_copy_switched_ocr), autoDismissMs = 2000)
                }
            } catch (_: CancellationException) {
                root.visibility = View.VISIBLE
                isRecognizing = false
                updateOcrRecognizingUi(context, isRecognizing = false)
            } catch (_: Throwable) {
                root.visibility = View.VISIBLE
                isRecognizing = false
                updateOcrRecognizingUi(context, isRecognizing = false)
                Toast.makeText(context, R.string.universal_copy_ocr_no_text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOcrRecognizingUi(context: Context, isRecognizing: Boolean) {
        if (isRecognizing) {
            showStatus(context.getString(R.string.universal_copy_ocr_recognizing), autoDismissMs = 0)
            ocrButton?.get()?.apply {
                alpha = 0.4f
                isEnabled = false
            }
            selectAllButton?.get()?.isEnabled = false
            screenshotButton?.get()?.isEnabled = false
            copyButton?.get()?.isEnabled = false
            searchButton?.get()?.isEnabled = false
            translateButton?.get()?.isEnabled = false
            pickButton?.get()?.isEnabled = false
        } else {
            ocrButton?.get()?.apply {
                alpha = 1f
                isEnabled = true
            }
            selectAllButton?.get()?.isEnabled = true
            screenshotButton?.get()?.isEnabled = true
            copyButton?.get()?.isEnabled = true
            searchButton?.get()?.isEnabled = true
            translateButton?.get()?.isEnabled = true
            pickButton?.get()?.isEnabled = true
            updateToolbarState()
        }
    }

    private fun applyRecognizedBlocks(newBlocks: List<SelectableBlock>) {
        currentBlocks.clear()
        currentBlocks.addAll(newBlocks)
        textBlocksViewRef?.get()?.setBlocks(currentBlocks)

        dismissRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, AUTO_DISMISS_MS)

        updateToolbarState()
    }

    private fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("XGesture", text))
    }

    private fun openPickPanel(context: Context, text: String) {
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context
        FloatBallPickResultPanel.showResult(
            context = hostContext,
            result = FloatBallPickResult(
                a11yText = text,
                ocrText = null,
                screenshot = null,
                screenRect = null,
                activeSource = PickResultTextSource.A11Y,
                contentOrigin = PickResultContentOrigin.SCREEN_PICK
            ),
            initialTextMode = PickResultTextMode.WORD_TAP
        )
    }

    private class TextBlocksView(
        context: Context,
        private var blocks: List<SelectableBlock>,
        private val density: Float,
        accentColor: Int,
        private val onEmptyTap: () -> Unit,
        private val onSelectionChanged: () -> Unit
    ) : View(context) {
        private val cornerRadius = 6f * density
        private val tapPadding = (10 * density).toInt()
        private val tapSlopSq = (18 * density * 18 * density)
        private val scrimPaint = Paint().apply { color = "#33000000".toColorInt() }
        private val normalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#22FFFFFF".toColorInt() }
        private val normalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#5AFFFFFF".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or (0x48 shl 24)
        }
        private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or (0xF0.toInt() shl 24)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or (0x22 shl 24)
        }
        private val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or (0xEE.toInt() shl 24)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        private var isDraggingBox = false
        private var isDragAndDropping = false
        private var downX = 0f
        private var downY = 0f
        private var currentX = 0f
        private var currentY = 0f
        private var downBlock: SelectableBlock? = null
        private val tmpRect = RectF()
        private val dragBoxRect = RectF()
        private val dragBoxScreenRect = Rect()
        private val initialSelectedStates = mutableMapOf<SelectableBlock, Boolean>()

        private var viewOffsetX = 0
        private var viewOffsetY = 0
        private val locationOnScreen = IntArray(2)

        private val longPressRunnable = Runnable {
            val targetBlock = downBlock ?: return@Runnable
            if (isDraggingBox || isDragAndDropping) return@Runnable

            // 1. 若当前长按的块未选中，则先将其置为选中
            if (!targetBlock.selected) {
                targetBlock.selected = true
                invalidate()
                onSelectionChanged()
            }

            // 2. 收集将要拖出的文本（自上而下自然排序）
            val selected = sortBlocksNaturally(blocks.filter { it.selected })
            val dragText = if (selected.isNotEmpty()) {
                selected.joinToString("\n") { it.text }
            } else {
                targetBlock.text
            }

            if (dragText.isBlank()) return@Runnable

            // 3. 强触觉振动反馈
            runCatching { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

            // 4. 构造跨应用 ClipData 与 DragShadowBuilder
            val clipData = ClipData.newPlainText("UniversalCopy", dragText)
            val shadowBuilder = TextDragShadowBuilder(this, dragText, density, accentColor)
            val flags = View.DRAG_FLAG_GLOBAL

            // 5. 启动系统级跨应用拖拽。
            //    以前这里起拖即 dismiss，浮层和选区一起没了，流转栏点"恢复"也救不回来；
            //    现在只藏起来，由 DragFlowSession 让流转栏决定结束后恢复还是关掉。
            val source = DragFlowSource(
                onRestore = { restoreFromDragFlow() },
                onDiscard = { dismiss() },
            )
            currentDragSource = source
            DragFlowSession.begin(source)
            isDragAndDropping = true
            val started = runCatching {
                startDragAndDrop(clipData, shadowBuilder, null, flags)
            }.getOrDefault(false)

            if (started) {
                // 下一帧再藏：拖影已经起飞，浮层消失的时机与旧实现一致。
                // 若这一帧里流转栏已经恢复/丢弃了源，就别再把它藏回去了。
                post {
                    if (currentDragSource === source) {
                        hideForDragFlow()
                    }
                }
            } else {
                DragFlowSession.end(source)
                currentDragSource = null
                isDragAndDropping = false
            }
        }

        /** 拖拽会话结束（恢复或丢弃）后复位，否则下一次长按会被 `isDragAndDropping` 挡住。 */
        fun endDrag() {
            isDragAndDropping = false
        }

        fun setBlocks(newBlocks: List<SelectableBlock>) {
            this.blocks = newBlocks
            invalidate()
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            getLocationOnScreen(locationOnScreen)
            viewOffsetX = locationOnScreen[0]
            viewOffsetY = locationOnScreen[1]
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            for (block in blocks) {
                tmpRect.set(
                    block.bounds.left.toFloat() - viewOffsetX,
                    block.bounds.top.toFloat() - viewOffsetY,
                    block.bounds.right.toFloat() - viewOffsetX,
                    block.bounds.bottom.toFloat() - viewOffsetY
                )
                if (block.selected) {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selectedFillPaint)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selectedStrokePaint)
                } else {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalFillPaint)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalStrokePaint)
                }
            }

            if (isDraggingBox) {
                val l = minOf(downX, currentX) - viewOffsetX
                val t = minOf(downY, currentY) - viewOffsetY
                val r = maxOf(downX, currentX) - viewOffsetX
                val b = maxOf(downY, currentY) - viewOffsetY
                dragBoxRect.set(l, t, r, b)
                canvas.drawRoundRect(dragBoxRect, 8f * density, 8f * density, boxFillPaint)
                canvas.drawRoundRect(dragBoxRect, 8f * density, 8f * density, boxStrokePaint)
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    removeCallbacks(longPressRunnable)
                    isDragAndDropping = false
                    downX = event.rawX
                    downY = event.rawY
                    currentX = event.rawX
                    currentY = event.rawY
                    isDraggingBox = false
                    downBlock = findBlockAt(event.rawX, event.rawY)
                    initialSelectedStates.clear()
                    blocks.forEach { initialSelectedStates[it] = it.selected }

                    // 如果命中了某个文字块，排布长按检测计时器
                    if (downBlock != null) {
                        postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy >= tapSlopSq) {
                        removeCallbacks(longPressRunnable)
                        if (!isDraggingBox && !isDragAndDropping) {
                            isDraggingBox = true
                        }
                    }
                    if (isDraggingBox && !isDragAndDropping) {
                        currentX = event.rawX
                        currentY = event.rawY
                        val left = minOf(downX, currentX).toInt()
                        val top = minOf(downY, currentY).toInt()
                        val right = maxOf(downX, currentX).toInt()
                        val bottom = maxOf(downY, currentY).toInt()
                        dragBoxScreenRect.set(left, top, right, bottom)

                        var changed = false
                        var newlySelected = false
                        for (block in blocks) {
                            val inBox = Rect.intersects(block.bounds, dragBoxScreenRect)
                            val targetState = (initialSelectedStates[block] == true) || inBox
                            if (block.selected != targetState) {
                                block.selected = targetState
                                changed = true
                                if (targetState) newlySelected = true
                            }
                        }
                        if (changed) {
                            if (newlySelected) {
                                runCatching { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                            }
                            onSelectionChanged()
                        }
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPressRunnable)
                    if (isDragAndDropping) {
                        isDragAndDropping = false
                        downBlock = null
                        return true
                    }
                    if (isDraggingBox) {
                        isDraggingBox = false
                        invalidate()
                        onSelectionChanged()
                        performClick()
                    } else {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (dx * dx + dy * dy < tapSlopSq) {
                            val upBlock = findBlockAt(event.rawX, event.rawY)
                            if (upBlock != null && upBlock === downBlock) {
                                upBlock.selected = !upBlock.selected
                                runCatching { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                                invalidate()
                                onSelectionChanged()
                                performClick()
                            } else if (upBlock == null && downBlock == null) {
                                if (blocks.any { it.selected }) {
                                    blocks.forEach { it.selected = false }
                                    runCatching { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                                    invalidate()
                                    onSelectionChanged()
                                } else {
                                    onEmptyTap()
                                }
                                performClick()
                            }
                        }
                    }
                    downBlock = null
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPressRunnable)
                    if (isDraggingBox) {
                        isDraggingBox = false
                        invalidate()
                    }
                    isDragAndDropping = false
                    downBlock = null
                }
            }
            return true
        }

        private fun findBlockAt(x: Float, y: Float): SelectableBlock? {
            val ix = x.toInt()
            val iy = y.toInt()
            var best: SelectableBlock? = null
            var bestArea = Int.MAX_VALUE
            for (block in blocks) {
                val b = block.bounds
                if (ix >= b.left - tapPadding && ix <= b.right + tapPadding &&
                    iy >= b.top - tapPadding && iy <= b.bottom + tapPadding
                ) {
                    val area = b.width() * b.height()
                    if (area < bestArea) {
                        best = block
                        bestArea = area
                    }
                }
            }
            return best
        }
    }

    private class TextDragShadowBuilder(
        view: View,
        private val text: String,
        private val density: Float,
        accentColor: Int,
    ) : View.DragShadowBuilder(view) {
        private val shadowPad = (density * 12).toInt()
        private val cardCornerPx = density * 12f
        private val cardWidth = (density * 220).toInt()
        private val cardHeight = (density * 68).toInt()
        private val totalWidth = cardWidth + shadowPad * 2
        private val totalHeight = cardHeight + shadowPad * 2

        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density * 1.5f
            color = (accentColor and 0x00FFFFFF) or 0xCC000000.toInt()
        }
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xDE1F2024.toInt()
            textSize = density * 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        private val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8A1F2024.toInt()
            textSize = density * 11.5f
        }

        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(totalWidth, totalHeight)
            outShadowTouchPoint.set(totalWidth / 2, totalHeight / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            val left = shadowPad.toFloat()
            val top = shadowPad.toFloat()
            val right = left + cardWidth
            val bottom = top + cardHeight

            // 1. 卡片柔和外阴影
            cardPaint.setShadowLayer(density * 8f, 0f, density * 4f, 0x38000000.toInt())
            canvas.drawRoundRect(left, top, right, bottom, cardCornerPx, cardCornerPx, cardPaint)
            cardPaint.clearShadowLayer()

            // 2. 翡翠主题强调边框
            canvas.drawRoundRect(left, top, right, bottom, cardCornerPx, cardCornerPx, borderPaint)

            // 3. 卡片内文字预览绘制（前两行）
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val firstLine = (lines.firstOrNull() ?: "").take(22)
            val secondLine = (if (lines.size > 1) lines[1] else lines.firstOrNull()?.drop(22).orEmpty()).take(26)

            val textLeft = left + density * 14f
            val firstLineY = top + density * 26f
            canvas.drawText(firstLine, textLeft, firstLineY, textPaint)

            if (secondLine.isNotBlank()) {
                val secondLineY = firstLineY + density * 18f
                canvas.drawText(secondLine, textLeft, secondLineY, subTextPaint)
            }
        }
    }

    private class CopyActionIconDrawable(
        val iconType: IconType,
        private val density: Float,
        var tintColor: Int = Color.WHITE
    ) : Drawable() {
        enum class IconType {
            SELECT_ALL,
            SCREENSHOT,
            SEGMENT,
            OCR_SCAN,
            ACCESSIBILITY,
            SEARCH,
            TRANSLATE
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val path = Path()

        private val selectAllPath: Path? by lazy {
            runCatching {
                PathParser.createPathFromPathData("M3,5h2V3c-1.1,0 -2,0.9 -2,2zM3,13h2v-2H3v2zM7,21h2v-2H7v2zM3,9h2V7H3v2zM13,3h-2v2h2V3zM19,3v2h2c0,-1.1 -0.9,-2 -2,-2zM5,21v-2H3c0,1.1 0.9,2 2,2zM3,17h2v-2H3v2zM9,3H7v2h2V3zM11,21h2v-2h-2v2zM19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2zM19,9h2V7h-2v2zM19,17h2v-2h-2v2zM15,21h2v-2h-2v2zM15,5h2V3h-2v2zM7,17h10V7H7v10zM9,9h6v6H9V9z")
            }.getOrNull()
        }

        private val screenshotPath: Path? by lazy {
            runCatching {
                PathParser.createPathFromPathData("M17,15h2V7c0,-1.1 -0.9,-2 -2,-2H9v2h8v8zM7,17V1H5v4H1v2h4v10c0,1.1 0.9,2 2,2h10v4h2v-4h4v-2H7z")
            }.getOrNull()
        }

        private val accessibilityPath: Path? by lazy {
            runCatching {
                PathParser.createPathFromPathData("M20.5,6c-2.61,0.7 -5.67,1 -8.5,1s-5.89,-0.3 -8.5,-1L3,8c1.86,0.5 4,0.83 6,1v13h2v-6h2v6h2V9c2,-0.17 4.14,-0.5 6,-1l-0.5,-2zM12,6c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2z")
            }.getOrNull()
        }

        private val searchPath: Path? by lazy {
            runCatching {
                PathParser.createPathFromPathData("M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zm-6,0C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
            }.getOrNull()
        }

        private val translatePath: Path? by lazy {
            runCatching {
                PathParser.createPathFromPathData("M11.9,22L16.45,10L18.55,10L23.1,22L21,22L19.92,18.95L15.08,18.95L14,22L11.9,22ZM4,19L2.6,17.6L7.65,12.55Q6.78,11.68 6.06,10.55Q5.35,9.43 4.75,8L6.85,8Q7.35,8.98 7.85,9.7Q8.35,10.42 9.05,11.15Q9.88,10.32 10.76,8.84Q11.65,7.35 12.1,6L1,6L1,4L8,4L8,2L10,2L10,4L17,4L17,6L14.1,6Q13.58,7.8 12.52,9.7Q11.48,11.6 10.45,12.6L12.85,15.05L12.1,17.1L9.05,13.98L4,19ZM15.7,17.2L19.3,17.2L17.5,12.1L15.7,17.2Z")
            }.getOrNull()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val scale = (b.width().coerceAtMost(b.height()) / (24f * density))

            strokePaint.color = tintColor
            strokePaint.strokeWidth = 1.8f * density * scale
            fillPaint.color = tintColor

            when (iconType) {
                IconType.SELECT_ALL -> {
                    val p = selectAllPath
                    if (p != null) {
                        canvas.save()
                        canvas.translate(cx - 12f * density * scale, cy - 12f * density * scale)
                        canvas.scale(density * scale, density * scale)
                        fillPaint.alpha = alpha
                        canvas.drawPath(p, fillPaint)
                        canvas.restore()
                    }
                }
                IconType.SCREENSHOT -> {
                    val p = screenshotPath
                    if (p != null) {
                        canvas.save()
                        canvas.translate(cx - 12f * density * scale, cy - 12f * density * scale)
                        canvas.scale(density * scale, density * scale)
                        fillPaint.alpha = alpha
                        canvas.drawPath(p, fillPaint)
                        canvas.restore()
                    }
                }
                IconType.SEGMENT -> {
                    strokePaint.alpha = alpha
                    val h = 4.5f * density * scale
                    val r = 2.0f * density * scale
                    canvas.drawRoundRect(cx - 8.5f * density * scale, cy - h, cx - 1.8f * density * scale, cy + h, r, r, strokePaint)
                    canvas.drawRoundRect(cx + 1.8f * density * scale, cy - h, cx + 8.5f * density * scale, cy + h, r, r, strokePaint)
                    canvas.drawLine(cx, cy - 6.5f * density * scale, cx, cy + 6.5f * density * scale, strokePaint)
                }
                IconType.OCR_SCAN -> {
                    // 四角扫描取景框 + 中心清晰的文字标识 'T' (Text Scan)
                    strokePaint.alpha = alpha
                    val arm = 4.5f * density * scale
                    val left = cx - 7.5f * density * scale
                    val right = cx + 7.5f * density * scale
                    val top = cy - 7.5f * density * scale
                    val bottom = cy + 7.5f * density * scale

                    path.reset()
                    path.moveTo(left, top + arm)
                    path.lineTo(left, top)
                    path.lineTo(left + arm, top)

                    path.moveTo(right - arm, top)
                    path.lineTo(right, top)
                    path.lineTo(right, top + arm)

                    path.moveTo(right, bottom - arm)
                    path.lineTo(right, bottom)
                    path.lineTo(right - arm, bottom)

                    path.moveTo(left + arm, bottom)
                    path.lineTo(left, bottom)
                    path.lineTo(left, bottom - arm)
                    canvas.drawPath(path, strokePaint)

                    // 绘制中心的大写字母 'T'（代表 Text 扫描提取文本）
                    strokePaint.strokeWidth = 2.0f * density * scale
                    canvas.drawLine(cx - 3.2f * density * scale, cy - 3.0f * density * scale, cx + 3.2f * density * scale, cy - 3.0f * density * scale, strokePaint)
                    canvas.drawLine(cx, cy - 3.0f * density * scale, cx, cy + 4.2f * density * scale, strokePaint)
                    strokePaint.strokeWidth = 1.8f * density * scale
                }
                IconType.ACCESSIBILITY -> {
                    val p = accessibilityPath
                    if (p != null) {
                        canvas.save()
                        canvas.translate(cx - 12f * density * scale, cy - 12f * density * scale)
                        canvas.scale(density * scale, density * scale)
                        fillPaint.alpha = alpha
                        canvas.drawPath(p, fillPaint)
                        canvas.restore()
                    }
                }
                IconType.SEARCH -> {
                    val p = searchPath
                    if (p != null) {
                        canvas.save()
                        canvas.translate(cx - 12f * density * scale, cy - 12f * density * scale)
                        canvas.scale(density * scale, density * scale)
                        fillPaint.alpha = alpha
                        canvas.drawPath(p, fillPaint)
                        canvas.restore()
                    }
                }
                IconType.TRANSLATE -> {
                    val p = translatePath
                    if (p != null) {
                        canvas.save()
                        canvas.translate(cx - 12f * density * scale, cy - 12f * density * scale)
                        canvas.scale(density * scale, density * scale)
                        fillPaint.alpha = alpha
                        canvas.drawPath(p, fillPaint)
                        canvas.restore()
                    }
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}




