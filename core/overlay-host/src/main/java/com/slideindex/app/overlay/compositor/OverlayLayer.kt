package com.slideindex.app.overlay.compositor

/**
 * Visual stacking order inside [OverlayCompositor] and relative WM band ordering.
 * Higher [zIndex] draws on top within the compositor; [wmBand] orders compositor vs other WM windows.
 */
enum class OverlayLayer(
    val zIndex: Float,
    val wmBand: Int,
) {
    /** Edge presentation content (migrating gradually). */
    EdgePresentation(zIndex = 10f, wmBand = 10),

    /** Pick result, stash, search, and other content panels. */
    ContentPanel(zIndex = 20f, wmBand = 20),

    /** Edge gesture hint animations — must stay above content panels. */
    GestureAnimation(zIndex = 40f, wmBand = 35),

    /** Float-ball line/ball display and edge trigger visuals. */
    FloatBallChrome(zIndex = 50f, wmBand = 40),

    /** Edge / float-ball touch capture (separate WM windows; not hosted in compositor). */
    TouchCapture(zIndex = 100f, wmBand = 100),
}
