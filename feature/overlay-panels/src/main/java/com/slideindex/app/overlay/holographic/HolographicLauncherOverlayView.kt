package com.slideindex.app.overlay.holographic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import com.slideindex.app.settings.HolographicLauncherSettings

@SuppressLint("ViewConstructor")
class HolographicLauncherOverlayView(
    context: Context,
    private val mainHandler: Handler,
) : FrameLayout(context) {

    interface Listener {
        fun onLaunch(app: HolographicLauncherApp)
        fun onClosed()
    }

    private val ballView = Ball3DView(context)
    private val backgroundView = HolographicLauncherBackgroundView(context)
    private val closeButton = ImageView(context)
    private var listener: Listener? = null
    private var autoCloseRunnable: Runnable? = null
    private var timeoutSeconds = HolographicLauncherSettings.DEFAULT_TIMEOUT_SECONDS

    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        backgroundView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        addView(backgroundView)
        ballView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        addView(ballView)

        val closeSize = (40 * resources.displayMetrics.density).toInt()
        val margin = (16 * resources.displayMetrics.density).toInt()
        closeButton.layoutParams = FrameLayout.LayoutParams(closeSize, closeSize).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(margin, margin, margin, margin)
        }
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        closeButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(140, 0, 0, 0))
        }
        closeButton.scaleType = ImageView.ScaleType.CENTER
        closeButton.setColorFilter(Color.WHITE)
        closeButton.setOnClickListener { close() }
        addView(closeButton)

        ballView.onAppClick = { app ->
            cancelAutoClose()
            listener?.onLaunch(app)
        }
        ballView.onTouchAction = { resetAutoClose() }
        ballView.onBlankClick = { close() }
        ballView.onRotationStateChange = { rotating ->
            closeButton.visibility = if (rotating) INVISIBLE else VISIBLE
        }
    }

    fun bind(
        apps: List<HolographicLauncherApp>,
        settings: HolographicLauncherSettings,
        usesNativeWindowBlur: Boolean,
        listener: Listener,
    ) {
        this.listener = listener
        timeoutSeconds = settings.timeoutSeconds
        backgroundView.applySettings(settings, usesNativeWindowBlur)
        ballView.applySettings(settings)
        ballView.setApps(apps)
        resetAutoClose()
    }

    fun refreshBackgroundBlur() {
        backgroundView.refreshBlur()
    }

    /**
     * 后台补齐图标后重排球面。
     *
     * 只重放点、**不重置旋转与自动关闭计时**（`bind()` 会 resetAutoClose）：节点是按同一个列表
     * 顺序重建的，所以位置不变，用户只是看到原本空着的格子上出现了图标。
     */
    fun refreshAppIcons(apps: List<HolographicLauncherApp>) {
        ballView.setApps(apps)
    }

    fun resetAutoClose() {
        cancelAutoClose()
        val delayMs = timeoutSeconds.coerceIn(
            HolographicLauncherSettings.MIN_TIMEOUT_SECONDS,
            HolographicLauncherSettings.MAX_TIMEOUT_SECONDS,
        ) * 1000L
        val runnable = Runnable { close() }
        autoCloseRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelAutoClose() {
        autoCloseRunnable?.let { mainHandler.removeCallbacks(it) }
        autoCloseRunnable = null
    }

    fun close() {
        cancelAutoClose()
        listener?.onClosed()
        listener = null
    }

    fun release() {
        cancelAutoClose()
        listener = null
        backgroundView.release()
        ballView.onAppClick = null
        ballView.onTouchAction = null
        ballView.onBlankClick = null
        ballView.onRotationStateChange = null
    }
}
