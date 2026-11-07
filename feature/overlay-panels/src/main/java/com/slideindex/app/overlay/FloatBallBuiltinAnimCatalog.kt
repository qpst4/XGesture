package com.slideindex.app.overlay

import com.slideindex.app.settings.FloatBallStyleType

object FloatBallBuiltinAnimCatalog {

    fun isBuiltinAnimated(type: FloatBallStyleType): Boolean = when (type) {
        FloatBallStyleType.ANIMATED_PLANE,
        FloatBallStyleType.ANIMATED_PULSE,
        FloatBallStyleType.ANIMATED_ORBIT,
        -> true
        else -> false
    }
}
