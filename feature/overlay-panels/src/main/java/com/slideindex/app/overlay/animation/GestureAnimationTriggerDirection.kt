package com.slideindex.app.overlay.animation

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import com.slideindex.app.gesture.SwipeDirection

enum class GestureAnimationTriggerDirection {
    Center,
    Up,
    Down,
    Center2,
    Up2,
    Down2,
    Click,
    ;

    val isLong: Boolean
        get() = this == Center2 || this == Up2 || this == Down2
}

fun SwipeDirection?.toGestureTriggerDirection(longDistance: Boolean): GestureAnimationTriggerDirection {
    if (this == null) return GestureAnimationTriggerDirection.Center
    return when (this) {
        SwipeDirection.IN -> if (longDistance) GestureAnimationTriggerDirection.Center2 else GestureAnimationTriggerDirection.Center
        SwipeDirection.UP, SwipeDirection.UP_RIGHT ->
            if (longDistance) GestureAnimationTriggerDirection.Up2 else GestureAnimationTriggerDirection.Up
        SwipeDirection.DOWN, SwipeDirection.DOWN_RIGHT ->
            if (longDistance) GestureAnimationTriggerDirection.Down2 else GestureAnimationTriggerDirection.Down
    }
}
