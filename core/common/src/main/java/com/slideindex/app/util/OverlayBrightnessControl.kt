package com.slideindex.app.util

/** Applies brightness to the overlay window (preview when system write is unavailable). */
fun interface OverlayBrightnessControl {
    fun apply(fraction: Float?)
}
