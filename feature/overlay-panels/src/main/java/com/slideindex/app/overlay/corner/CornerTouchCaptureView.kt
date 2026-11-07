package com.slideindex.app.overlay.corner

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View

@SuppressLint("ViewConstructor")
class CornerTouchCaptureView(
    context: Context,
    private val onTouch: (MotionEvent) -> Boolean,
) : View(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = onTouch(event)
}
