package com.slideindex.app.overlay.compositor

import android.os.Handler
import android.os.Looper
import com.slideindex.app.overlay.OverlayZOrderHooks

/**
 * Single source of truth for overlay scene transitions.
 */
object OverlaySceneController {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var scene: OverlayScene = OverlayScene.Idle
        private set

    fun isEdgeGestureActive(): Boolean = scene is OverlayScene.EdgeGestureActive

    fun onEdgeGestureStarted() {
        runOnMain {
            if (scene is OverlayScene.EdgeGestureActive) {
                OverlayCompositor.bringCompositorToFront()
                return@runOnMain
            }
            scene = OverlayScene.EdgeGestureActive
            OverlayZOrderCoordinator.sync()
        }
    }

    fun onEdgeGestureEnded() {
        runOnMain {
            val contentPanelVisible = scene is OverlayScene.ContentPanelVisible
            scene = when (scene) {
                is OverlayScene.EdgeGestureActive -> OverlayScene.Idle
                is OverlayScene.ContentPanelVisible -> OverlayScene.ContentPanelVisible
                else -> OverlayScene.Idle
            }
            OverlayCompositor.bringCompositorToFront()
            val needsRaise = OverlayZOrderHooks.edgePresentationNeedsChromeRaise?.invoke() ?: false
            if (contentPanelVisible || needsRaise) {
                OverlayZOrderHooks.scheduleChromeAbovePanels?.invoke()
            } else {
                OverlayZOrderHooks.restoreChromeZOrderIfIdle?.invoke()
            }
            OverlayZOrderHooks.bringFloatingPointerToFront?.invoke()
        }
    }

    fun onContentPanelShown() {
        runOnMain {
            scene = OverlayScene.ContentPanelVisible
            OverlayCompositor.bringAboveContentPanels()
            OverlayZOrderHooks.scheduleChromeAbovePanels?.invoke()
            OverlayZOrderHooks.bringFloatingPointerToFront?.invoke()
        }
    }

    fun onContentPanelHidden() {
        runOnMain {
            if (scene is OverlayScene.ContentPanelVisible) {
                scene = OverlayScene.Idle
            }
            OverlayCompositor.bringCompositorToFront()
            OverlayZOrderHooks.scheduleChromeAbovePanels?.invoke()
            OverlayZOrderHooks.bringFloatingPointerToFront?.invoke()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
