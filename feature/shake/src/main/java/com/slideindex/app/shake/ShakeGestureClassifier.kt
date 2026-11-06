package com.slideindex.app.shake

internal object ShakeGestureClassifier {
    fun clampSensitivity(value: Float): Float = ShakeSensitivityScale.clampUi(value)

    fun effectiveThreshold(uiValue: Float): Float = ShakeSensitivityScale.effectiveThreshold(uiValue)

    fun detectDirection(
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        absX: Float,
        absY: Float,
        absZ: Float,
        globalSensitivity: Float,
        independentEnabled: Boolean,
        perDirectionSensitivity: Map<ShakeGestureType, Float>,
        axisScale: Float = 1f,
    ): ShakeGestureType? {
        if (independentEnabled) {
            return detectPerDirection(
                axisX = axisX,
                axisY = axisY,
                axisZ = axisZ,
                globalSensitivity = globalSensitivity,
                perDirectionSensitivity = perDirectionSensitivity,
                axisScale = axisScale,
            )
        }

        val sensitivity = effectiveThreshold(globalSensitivity) * axisScale
        // 翻转原本要多加 1.5 rad/s：在旧阈值区间（2..20）里那几乎可忽略，
        // 但阈值整体下移后它会占掉很大比例（UI 20 时接近 75%），低档位因此显得"晃不动"。
        val flipYMargin = 0.5f * axisScale
        val swingMargin = 1.0f * axisScale
        val tiltBackMargin = 0.5f * axisScale
        val swingSideMargin = 0.5f * axisScale

        if (absY > sensitivity) {
            return when {
                axisY > sensitivity + flipYMargin -> ShakeGestureType.RIGHT_FLIP
                axisY < -sensitivity - flipYMargin -> ShakeGestureType.LEFT_FLIP
                else -> null
            }
        }

        val swingThreshold = sensitivity - swingMargin
        if (absX > swingThreshold) {
            return when {
                axisX > swingThreshold -> ShakeGestureType.FORWARD_FLIP
                axisX < -sensitivity - tiltBackMargin -> ShakeGestureType.BACKWARD_FLIP
                else -> null
            }
        }

        if (absZ > swingThreshold) {
            return when {
                axisZ > sensitivity + swingSideMargin -> ShakeGestureType.LEFT_FLICK
                axisZ < -sensitivity - swingSideMargin -> ShakeGestureType.RIGHT_FLICK
                else -> null
            }
        }

        return null
    }

    private fun detectPerDirection(
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        globalSensitivity: Float,
        perDirectionSensitivity: Map<ShakeGestureType, Float>,
        axisScale: Float = 1f,
    ): ShakeGestureType? {
        val flipRight = sensitivityFor(ShakeGestureType.RIGHT_FLIP, globalSensitivity, perDirectionSensitivity) * axisScale
        val flipLeft = sensitivityFor(ShakeGestureType.LEFT_FLIP, globalSensitivity, perDirectionSensitivity) * axisScale
        val tiltFront = sensitivityFor(ShakeGestureType.FORWARD_FLIP, globalSensitivity, perDirectionSensitivity) * axisScale
        val tiltBack = sensitivityFor(ShakeGestureType.BACKWARD_FLIP, globalSensitivity, perDirectionSensitivity) * axisScale
        val swingLeft = sensitivityFor(ShakeGestureType.LEFT_FLICK, globalSensitivity, perDirectionSensitivity) * axisScale
        val swingRight = sensitivityFor(ShakeGestureType.RIGHT_FLICK, globalSensitivity, perDirectionSensitivity) * axisScale
        val swingMargin = 1.0f * axisScale

        if (axisY > flipRight || axisY < -flipLeft) {
            return when {
                axisY > flipRight -> ShakeGestureType.RIGHT_FLIP
                axisY < -flipLeft -> ShakeGestureType.LEFT_FLIP
                else -> null
            }
        }

        if (axisX > tiltFront - swingMargin || axisX < -tiltBack + swingMargin) {
            return when {
                axisX > tiltFront -> ShakeGestureType.FORWARD_FLIP
                axisX < -tiltBack -> ShakeGestureType.BACKWARD_FLIP
                else -> null
            }
        }

        if (axisZ > swingLeft - swingMargin || axisZ < -swingRight + swingMargin) {
            return when {
                axisZ > swingLeft -> ShakeGestureType.LEFT_FLICK
                axisZ < -swingRight -> ShakeGestureType.RIGHT_FLICK
                else -> null
            }
        }

        return null
    }

    private fun sensitivityFor(
        type: ShakeGestureType,
        globalSensitivity: Float,
        perDirectionSensitivity: Map<ShakeGestureType, Float>,
    ): Float =
        effectiveThreshold(perDirectionSensitivity[type] ?: globalSensitivity)
}
