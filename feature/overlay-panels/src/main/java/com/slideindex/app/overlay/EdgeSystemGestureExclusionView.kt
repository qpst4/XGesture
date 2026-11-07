package com.slideindex.app.overlay

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat

/**
 * Non-touchable edge strip that opts out of the system back-gesture region.
 * Touches pass through to the app below; only [View.setSystemGestureExclusionRects] applies.
 */
class EdgeSystemGestureExclusionView(context: Context) : View(context) {

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateSystemGestureExclusion()
    }

    private fun updateSystemGestureExclusion() {
        if (width <= 0 || height <= 0) {
            ViewCompat.setSystemGestureExclusionRects(this, emptyList())
            return
        }
        ViewCompat.setSystemGestureExclusionRects(
            this,
            listOf(Rect(0, 0, width, height))
        )
    }
}
