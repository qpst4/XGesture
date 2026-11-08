package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.overlay.OverlayWindowTypes
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/** Full-screen tap-to-pick paste target (FV multi-field red frames). */
object ClipboardPasteTargetOverlay {

    private const val AUTO_DISMISS_MS = 30_000L
    private const val STROKE_COLOR = 0xFFE53935.toInt()
    private const val FILL_COLOR = 0x22E53935

    private val handler = Handler(Looper.getMainLooper())
    private var overlayRef: WeakReference<View>? = null
    private var dismissRunnable: Runnable? = null
    private var pendingCallback: ((PasteResult?) -> Unit)? = null

    val isShowing: Boolean get() = overlayRef?.get() != null

    fun show(
        context: Context,
        service: AccessibilityService,
        targets: List<Rect>,
        entry: ClipboardEntry,
        onFinished: (PasteResult?) -> Unit,
    ) {
        handler.post {
            dismiss()
            pendingCallback = onFinished
            val hostContext = com.slideindex.app.overlay.panels.PanelRuntimeHooks.overlayHostContext
            ?.invoke() ?: context.applicationContext
            val density = hostContext.resources.displayMetrics.density
            val strokePx = (2.5f * density).coerceAtLeast(1f)
            val screenTargets = targets.map { Rect(it) }
            val drawRects = screenTargets.map { RectF(it) }
            val root = object : View(hostContext) {
                private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = STROKE_COLOR
                    strokeWidth = strokePx
                }
                private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = FILL_COLOR
                }
                private val locationOnScreen = IntArray(2)
                private var viewOffsetX = 0
                private var viewOffsetY = 0
                private val tmpRect = RectF()

                override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                    super.onLayout(changed, left, top, right, bottom)
                    getLocationOnScreen(locationOnScreen)
                    viewOffsetX = locationOnScreen[0]
                    viewOffsetY = locationOnScreen[1]
                }

                override fun onDraw(canvas: Canvas) {
                    for (rect in drawRects) {
                        tmpRect.set(
                            rect.left - viewOffsetX,
                            rect.top - viewOffsetY,
                            rect.right - viewOffsetX,
                            rect.bottom - viewOffsetY,
                        )
                        canvas.drawRect(tmpRect, fillPaint)
                        canvas.drawRect(tmpRect, strokePaint)
                    }
                }

                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        dismissCancelled()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }

                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.action != MotionEvent.ACTION_UP) return true
                    val x = event.rawX.roundToInt()
                    val y = event.rawY.roundToInt()
                    val hit = screenTargets.firstOrNull { it.contains(x, y) }
                    if (hit == null) {
                        dismissCancelled()
                        return true
                    }
                    val hitRect = Rect(hit)
                    removeOverlayViewOnly()
                    ClipboardPasteCoordinator.pasteEntryAtScreenRect(
                        service = service,
                        context = hostContext,
                        entry = entry,
                        rect = hitRect,
                        // 红框选择器只可能在 fvStyle 路径下弹出，这里必须显式传 true，
                        // 否则协调器会走 pasteEntryToFocusedField，点红框等于没点。
                        fvStyle = true,
                        onFinished = { result -> completePending(result) },
                    )
                    return true
                }
            }.apply {
                isFocusableInTouchMode = true
                isFocusable = true
                contentDescription = hostContext.getString(R.string.clipboard_paste_pick_content_desc)
            }
            val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                OverlayWindowTypes.overlayWindowType(hostContext),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            OverlayWindowTypes.applyFullScreen(params)
            wm.addView(root, params)
            overlayRef = WeakReference(root)
            val runnable = Runnable { dismissCancelled() }
            dismissRunnable = runnable
            handler.postDelayed(runnable, AUTO_DISMISS_MS)
        }
    }

    fun dismiss() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        val overlay = overlayRef?.get()
        overlayRef = null
        if (overlay != null) {
            runCatching {
                val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(overlay)
            }
        }
        pendingCallback = null
    }

    private fun dismissCancelled() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        val overlay = overlayRef?.get()
        overlayRef = null
        if (overlay != null) {
            runCatching {
                val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(overlay)
            }
        }
        pendingCallback?.invoke(null)
        pendingCallback = null
    }

    private fun removeOverlayViewOnly() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        val overlay = overlayRef?.get()
        overlayRef = null
        if (overlay != null) {
            runCatching {
                val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(overlay)
            }
        }
    }

    private fun completePending(result: PasteResult) {
        handler.post {
            pendingCallback?.invoke(result)
            pendingCallback = null
        }
    }
}
