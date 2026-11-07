package com.slideindex.app.overlay

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

/**
 * Listens for default-display rotation changes and debounces overlay relayout.
 * Camera and other apps may rotate the compositor without updating Configuration bounds.
 */
class OverlayDisplayRotationMonitor(
    context: Context,
    private val onRotationSettled: () -> Unit,
) : DisplayManager.DisplayListener {
    private val appContext = context.applicationContext
    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastRotation = OverlayScreenMetrics.displayRotation(appContext)
    private val settleRunnable = Runnable {
        val current = OverlayScreenMetrics.displayRotation(appContext)
        if (current == lastRotation) return@Runnable
        lastRotation = current
        OverlayDisplayRotationGate.markRotationSettling()
        OverlayCompose.clearWindowContextCache()
        onRotationSettled()
    }

    fun start() {
        lastRotation = OverlayScreenMetrics.displayRotation(appContext)
        displayManager.registerDisplayListener(this, handler)
    }

    fun stop() {
        handler.removeCallbacks(settleRunnable)
        displayManager.unregisterDisplayListener(this)
        OverlayDisplayRotationGate.clear()
    }

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayRemoved(displayId: Int) = Unit

    override fun onDisplayChanged(displayId: Int) {
        if (displayId != Display.DEFAULT_DISPLAY) return
        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, SETTLE_DEBOUNCE_MS)
    }

    companion object {
        private const val SETTLE_DEBOUNCE_MS = 200L
    }
}

/** Suppresses capture-window remove+add while the display rotation is settling. */
object OverlayDisplayRotationGate {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    var suppressCaptureWindowReAdd: Boolean = false
        private set

    private val clearRunnable = Runnable { suppressCaptureWindowReAdd = false }

    fun markRotationSettling() {
        suppressCaptureWindowReAdd = true
        handler.removeCallbacks(clearRunnable)
        handler.postDelayed(clearRunnable, SETTLING_WINDOW_MS)
    }

    fun clear() {
        handler.removeCallbacks(clearRunnable)
        suppressCaptureWindowReAdd = false
    }

    private const val SETTLING_WINDOW_MS = 450L
}
