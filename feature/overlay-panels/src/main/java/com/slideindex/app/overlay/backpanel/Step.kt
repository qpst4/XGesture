package com.slideindex.app.overlay.backpanel

/**
 * Ported from AOSP SystemUI `Step` (Apache-2.0).
 *
 * In addition to a typical step function which returns one of two values based on a threshold,
 * `Step` also gracefully handles quick changes in input near the threshold value that would
 * typically result in the output rapidly changing.
 */
class Step<T>(
    private val threshold: Float,
    private val factor: Float = 1.1f,
    private val postThreshold: T,
    private val preThreshold: T,
) {
    data class Value<T>(val value: T, val isNewState: Boolean)

    private val lowerFactor = 2 - factor

    private lateinit var startValue: Value<T>
    private lateinit var previousValue: Value<T>
    private var hasCrossedUpperBoundAtLeastOnce = false
    private var progress: Float = 0f

    init {
        reset()
    }

    fun reset() {
        hasCrossedUpperBoundAtLeastOnce = false
        progress = 0f
        startValue = Value(preThreshold, false)
        previousValue = startValue
    }

    fun get(progress: Float): Value<T> {
        this.progress = progress
        val hasCrossedUpperBound = progress > threshold * factor
        val hasCrossedLowerBound = progress > threshold * lowerFactor
        return when {
            hasCrossedUpperBound && !hasCrossedUpperBoundAtLeastOnce -> {
                hasCrossedUpperBoundAtLeastOnce = true
                Value(postThreshold, true)
            }
            hasCrossedLowerBound -> previousValue.copy(isNewState = false)
            hasCrossedUpperBoundAtLeastOnce -> {
                hasCrossedUpperBoundAtLeastOnce = false
                Value(preThreshold, true)
            }
            else -> startValue
        }.also { previousValue = it }
    }
}
