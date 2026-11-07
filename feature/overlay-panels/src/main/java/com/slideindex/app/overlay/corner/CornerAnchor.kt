package com.slideindex.app.overlay.corner

import com.slideindex.app.overlay.PanelSide

enum class CornerAnchor {
    LEFT,
    RIGHT,
}

fun CornerAnchor.toPanelSide(): PanelSide = when (this) {
    CornerAnchor.LEFT -> PanelSide.LEFT
    CornerAnchor.RIGHT -> PanelSide.RIGHT
}
