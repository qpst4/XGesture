package com.slideindex.app.overlay.compositor

import com.slideindex.app.overlay.OverlayZOrderHooks

/**
 * Applies [OverlayScene] to WM window ordering across compositor, panels, and chrome.
 */
internal object OverlayZOrderCoordinator {
    fun sync() {
        when (OverlaySceneController.scene) {
            OverlayScene.EdgeGestureActive -> {
                OverlayCompositor.bringCompositorToFront()
                OverlayZOrderHooks.scheduleChromeAbovePanels?.invoke()
                OverlayZOrderHooks.bringFloatingPointerToFront?.invoke()
            }
            OverlayScene.ContentPanelVisible -> {
                OverlayCompositor.bringAboveContentPanels()
                OverlayZOrderHooks.scheduleChromeAbovePanels?.invoke()
                OverlayZOrderHooks.bringFloatingPointerToFront?.invoke()
            }
            OverlayScene.Idle -> Unit
        }
    }
}
