package com.slideindex.app.overlay

internal fun canReuseRegionalPickWindow(
    isAttached: Boolean,
    windowGeneration: Int,
    hostGeneration: Int,
): Boolean = isAttached && windowGeneration == hostGeneration
