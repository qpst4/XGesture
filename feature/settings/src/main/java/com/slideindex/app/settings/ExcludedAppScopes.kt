package com.slideindex.app.settings

data class ExcludedAppScopes(
    val suppressTriggers: Boolean = true,
    val suppressCornerWheel: Boolean = true,
    val suppressFloatBall: Boolean = true,
    val suppressMultiTouch: Boolean = true,
    val suppressPhysicalKey: Boolean = true,
) {
    fun hasAny(): Boolean =
        suppressTriggers || suppressCornerWheel || suppressFloatBall || suppressMultiTouch || suppressPhysicalKey

    companion object {
        val ALL = ExcludedAppScopes(
            suppressTriggers = true,
            suppressCornerWheel = true,
            suppressFloatBall = true,
            suppressMultiTouch = true,
            suppressPhysicalKey = true,
        )
    }
}
