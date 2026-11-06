package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

@SuppressLint("ViewConstructor") // Programmatically created panel host
class PanelRootView(
    context: Context,
    private val side: PanelSide,
    private var panelWidthPx: Int,
    private val onScrimClick: () -> Unit
) : FrameLayout(context) {

    private val scrim = View(context).apply {
        setBackgroundColor(Color.argb(120, 0, 0, 0))
        alpha = 0f
        setOnClickListener { onScrimClick() }
    }
    private val panelContainer = FrameLayout(context)

    init {
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val panelParams = LayoutParams(panelWidthPx, LayoutParams.MATCH_PARENT).apply {
            gravity = if (side == PanelSide.LEFT) Gravity.START else Gravity.END
        }
        addView(panelContainer, panelParams)
        updateTransforms(0f)
    }

    fun setPanelContent(view: View) {
        panelContainer.removeAllViews()
        panelContainer.addView(
            view,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setPanelWidth(widthPx: Int) {
        panelWidthPx = widthPx
        (panelContainer.layoutParams as LayoutParams).width = widthPx
        requestLayout()
    }

    fun setProgress(progress: Float) {
        updateTransforms(progress)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    private fun updateTransforms(progress: Float) {
        scrim.alpha = progress * 0.55f
        val hiddenOffset = panelWidthPx.toFloat()
        panelContainer.translationX = if (side == PanelSide.LEFT) {
            -hiddenOffset * (1f - progress)
        } else {
            hiddenOffset * (1f - progress)
        }
    }
}
