package com.slideindex.app.overlay

import android.view.View

/**
 * Shared overlay panel lifecycle contract.
 *
 * - [isAttached]: window is on [android.view.WindowManager], including warm-up / attachHidden shells.
 * - [isUserVisible]: panel is shown to the user and may consume Back or focus.
 */
interface OverlayPanelVisibility {
    val isAttached: Boolean
    val isUserVisible: Boolean
}

fun OverlayFullScreenPanelHost.isViewVisible(): Boolean =
    composeView?.visibility == View.VISIBLE
