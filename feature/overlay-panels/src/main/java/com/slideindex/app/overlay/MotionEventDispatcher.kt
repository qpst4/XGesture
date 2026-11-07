package com.slideindex.app.overlay

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset

fun interface OnMotionEventListener {
    fun onDispatch(event: MotionEvent)
}

object MotionEventDispatcher {
    private val listeners = mutableMapOf<PanelSide, MutableList<OnMotionEventListener>>()

    fun addListener(side: PanelSide, listener: OnMotionEventListener) {
        listeners.getOrPut(side) { mutableListOf() }.add(listener)
    }

    fun removeListener(side: PanelSide, listener: OnMotionEventListener) {
        listeners[side]?.remove(listener)
    }

    fun dispatch(side: PanelSide, event: MotionEvent) {
        val sideListeners = listeners[side] ?: return
        for (index in sideListeners.indices) {
            sideListeners.getOrNull(index)?.onDispatch(event)
        }
    }
}

@Composable
fun DragGestureHandler(
    side: PanelSide,
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (dragAmount: Offset) -> Unit
) {
    val curOnDragStart by rememberUpdatedState(newValue = onDragStart)
    val curOnDragEnd by rememberUpdatedState(newValue = onDragEnd)
    val curOnDragCancel by rememberUpdatedState(newValue = onDragCancel)
    val curOnDrag by rememberUpdatedState(newValue = onDrag)

    DisposableEffect(side) {
        var x = -1f
        var y = -1f
        val listener = OnMotionEventListener { event ->
            val rawX = event.rawX
            val rawY = event.rawY
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x = rawX
                    y = rawY
                    curOnDragStart(Offset(x, y))
                }
                MotionEvent.ACTION_MOVE -> {
                    val offsetX = rawX - x
                    val offsetY = rawY - y
                    x = rawX
                    y = rawY
                    curOnDrag(Offset(offsetX, offsetY))
                }
                MotionEvent.ACTION_UP -> {
                    curOnDragEnd()
                    x = -1f
                    y = -1f
                }
                MotionEvent.ACTION_CANCEL -> {
                    curOnDragCancel()
                    x = -1f
                    y = -1f
                }
                else -> Unit
            }
        }
        MotionEventDispatcher.addListener(side, listener)
        onDispose {
            MotionEventDispatcher.removeListener(side, listener)
        }
    }
}
