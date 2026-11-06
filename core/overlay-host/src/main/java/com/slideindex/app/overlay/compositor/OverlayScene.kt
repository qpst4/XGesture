package com.slideindex.app.overlay.compositor

/**
 * High-level overlay scene; drives WM z-order sync via [OverlaySceneController].
 */
sealed class OverlayScene {
    data object Idle : OverlayScene()

    /** Finger is down on an edge capture strip; gesture animation should paint above panels/chrome. */
    data object EdgeGestureActive : OverlayScene()

    /** A content panel (pick result, etc.) is visible; compositor stays above panel unless idle chrome sync runs. */
    data object ContentPanelVisible : OverlayScene()
}
