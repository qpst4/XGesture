package com.slideindex.app.overlay

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/**
 * Reserves bottom (navigation) and optionally left-edge (back) strips for system gesture
 * indicators on full-screen overlay panel windows.
 *
 * When [excludeLeftBackEdge] is false, the overlay can receive back gestures to dismiss itself.
 */
object OverlayPanelSystemGestureExclusion {
    private const val BACK_EDGE_DP = 48f
    private val configTagKey: Int = "overlay_panel_system_gesture_exclusion".hashCode()

    fun attach(
        view: View,
        excludeLeftBackEdge: Boolean = true
    ) {
        view.setTag(configTagKey, excludeLeftBackEdge)
        val layoutListener = View.OnLayoutChangeListener { target, _, _, _, _, _, _, _, _ ->
            updateExclusionRects(target)
        }
        view.addOnLayoutChangeListener(layoutListener)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    updateExclusionRects(v)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    v.removeOnLayoutChangeListener(layoutListener)
                    v.setTag(configTagKey, null)
                    ViewCompat.setSystemGestureExclusionRects(v, emptyList())
                }
            }
        )
        if (view.isAttachedToWindow) {
            updateExclusionRects(view)
        }
    }

    private fun updateExclusionRects(view: View) {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) {
            ViewCompat.setSystemGestureExclusionRects(view, emptyList())
            return
        }
        val excludeLeft = view.getTag(configTagKey) as? Boolean ?: true
        val density = view.resources.displayMetrics.density
        val backEdgePx = if (excludeLeft) {
            (BACK_EDGE_DP * density).roundToInt().coerceIn(0, width)
        } else {
            0
        }
        val navBarPx = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemGestures())
            ?.bottom
            ?: 0
        val rects = buildList {
            if (backEdgePx > 0) {
                add(Rect(0, 0, backEdgePx, height))
            }
            if (navBarPx > 0) {
                add(Rect(0, height - navBarPx, width, height))
            }
        }
        ViewCompat.setSystemGestureExclusionRects(view, rects)
    }
}
