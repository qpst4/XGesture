package com.slideindex.app.overlay

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * View-overlay float motion using Compose animation core (SideGesture-style spring).
 * Replaces [android.animation.ValueAnimator] on panel paging / overscroll snap paths.
 */
class OverlayFloatSpringMotion(
    private val scope: CoroutineScope,
    private val animationSpec: AnimationSpec<Float> = OverlayPanelSpringMotion.snapSpec
) {
    private var job: Job? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun animateTo(
        start: Float,
        target: Float,
        epsilon: Float,
        onValue: (Float) -> Unit,
        onComplete: () -> Unit = {}
    ) {
        cancel()
        if (abs(start - target) < epsilon) {
            onValue(target)
            onComplete()
            return
        }
        job = scope.launch {
            try {
                // View overlay scopes lack Compose's MonotonicFrameClock; AndroidUiDispatcher supplies it.
                withContext(AndroidUiDispatcher.Main) {
                    AnimationState(initialValue = start).animateTo(
                        targetValue = target,
                        animationSpec = animationSpec
                    ) {
                        onValue(value)
                    }
                }
                onValue(target)
                onComplete()
            } finally {
                job = null
            }
        }
    }
}

object OverlayPanelSpringMotion {
    const val SNAP_STIFFNESS = 3000f

    val snapSpec = spring<Float>(stiffness = SNAP_STIFFNESS)
}
