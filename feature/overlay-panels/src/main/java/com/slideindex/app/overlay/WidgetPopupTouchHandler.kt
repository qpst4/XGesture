package com.slideindex.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun WidgetPopupTouchHandler(
    blockingTouches: Boolean,
    visible: Boolean,
    editMode: Boolean,
    progress: Float,
    onDismissOutside: () -> Unit,
    onExitEditMode: () -> Unit
) {
    if (!blockingTouches) return
    Box(
        Modifier
            .fillMaxSize()
            .alpha(OverlayPanelEnterAnimation.alpha(progress) * 0.35f)
            .background(Color.Black)
            .pointerInput(blockingTouches, editMode) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .then(
                if (visible) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (editMode) onExitEditMode() else onDismissOutside()
                        }
                    )
                } else {
                    Modifier
                }
            )
    )
}
