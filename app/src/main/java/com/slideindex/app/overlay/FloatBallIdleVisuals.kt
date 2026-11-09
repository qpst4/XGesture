package com.slideindex.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.slideindex.app.settings.AppSettings
import kotlin.math.abs

/**
 * 悬浮球的空闲显隐与 alpha 动画（从 `FloatBallOverlay` 拆出的第 7 类）。
 *
 * 状态仍由 [FloatBallOverlay] 持有（`internal` 可见）。
 */
internal object FloatBallIdleVisuals {

    // —— 闲置隐身 ——

    /**
     * 按当前设置把球的渲染 alpha 对齐到「显示 / 闲置」。
     *
     * [animate] 只用于"触摸显形 / 回到闲置"两个瞬间；设置回流、开关切换一律立即生效。
     */
    internal fun syncBallIdleAlpha(settings: AppSettings, animate: Boolean) {
        if (FloatBallOverlay.ballIconView == null) return
        val shown = FloatBallOverlay.ballRevealed || FloatBallOverlay.ballPressed || FloatBallOverlay.isBallForceRevealed() || FloatBallOverlay.isDraggingNow()
        publishBallIdleTarget(settings, shown)
        applyBallGifFreeze(settings, shown)
        if (!idleVisualsEnabled(settings)) {
            cancelBallAlphaAnimation()
            cancelBallIdleHide()
            FloatBallOverlay.ballRevealed = true
            applyBallAlpha(null)
            return
        }
        val target = if (shown || !settings.floatBallHideWhenIdle) null else settings.floatBallIdleOpacity
        if (animate) {
            animateBallAlphaTo(target, settings)
        } else {
            cancelBallAlphaAnimation()
            applyBallAlpha(target)
        }
        if (shown && !FloatBallOverlay.ballPressed && !FloatBallOverlay.isBallForceRevealed() && !FloatBallOverlay.isDraggingNow()) {
            ensureBallIdleHideScheduled(settings)
        }
    }

    /** null = 跟随设置不透明度；非 null = 闲置隐身直接指定的 alpha。 */
    private fun applyBallAlpha(value: Float?) {
        FloatBallOverlay.ballAlphaOverrideValue = value
        FloatBallOverlay.ballIconView?.setAlphaOverride(value)
    }

    /** 闲置相关的视觉开关：隐身和「闲置定格动图」任意一个开着，都继续跑闲置计时。 */
    private fun idleVisualsEnabled(settings: AppSettings): Boolean =
        settings.floatBallHideWhenIdle || settings.floatBallIdleFreezeGif

    /** 闲置定格：球仍可见，但停止 GIF 出帧；触摸/拖动由 shown 恢复。 */
    private fun applyBallGifFreeze(settings: AppSettings, shown: Boolean) {
                FloatBallOverlay.ballIconView?.setGifFrozen(!shown && settings.floatBallIdleFreezeGif)
    }

    /** 把「当前该不该隐身」发布给窗内那份球（BOTH_EDGES 模式下球画在触摸窗里）。 */
    private fun publishBallIdleTarget(settings: AppSettings, shown: Boolean) {
        val state = FloatBallOverlay.sceneState ?: return
        state.ballIdleHidden.value = settings.floatBallHideWhenIdle && !shown
        state.ballIdleOpacity.value = settings.floatBallIdleOpacity.coerceIn(0f, 1f)
    }

    private fun animateBallAlphaTo(target: Float?, settings: AppSettings) {
        val end = target ?: settings.floatBallOpacity
        val start = FloatBallOverlay.ballAlphaOverrideValue ?: settings.floatBallOpacity
        if (abs(start - end) < 0.01f) {
            cancelBallAlphaAnimation()
            applyBallAlpha(target)
            return
        }
        cancelBallAlphaAnimation()
        val animator = ValueAnimator.ofFloat(start, end)
        animator.duration = if (end < start) FloatBallOverlay.BALL_HIDE_FADE_MS else FloatBallOverlay.BALL_REVEAL_FADE_MS
        animator.addUpdateListener { animation ->
            applyBallAlpha(animation.animatedValue as Float)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) applyBallAlpha(target)
            }
        })
        FloatBallOverlay.ballAlphaAnimator = animator
        animator.start()
    }

    internal fun cancelBallAlphaAnimation() {
        val animator = FloatBallOverlay.ballAlphaAnimator
        FloatBallOverlay.ballAlphaAnimator = null
        animator?.cancel()
    }

    /** 触摸按下、命中球区：立即显形并取消待隐身。 */
    fun onFloatBallPressed() {
        // 新一轮按下（按在球上）：清掉上一轮可能残留的侧向覆盖。
        // 残留会让下一次拖动把红十字的起点算到对面去（"像从另一侧拖过来"）。
        FloatBallOverlay.dragActiveSideOverride = null
        FloatBallOverlay.activeSideAtDragStart = null
        FloatBallOverlay.ballPressed = true
        cancelBallIdleHide()
        if (FloatBallOverlay.ballRevealed) return
        FloatBallOverlay.ballRevealed = true
        FloatBallOverlay.settingsState?.value?.let { syncBallIdleAlpha(it, animate = true) }
    }

    /** 抬手/取消/手势收尾：按闲置判定时长安排隐身。 */
    fun onFloatBallReleased() {
        FloatBallOverlay.ballPressed = false
        FloatBallOverlay.settingsState?.value?.let { scheduleBallIdleHide(it) }
    }

    /** 设置预览等场景强制显示：期间不做闲置隐身。 */
    internal fun setBallForceRevealed(active: Boolean) {
        if (FloatBallOverlay.isBallForceRevealed() == active) return
        FloatBallOverlay.setBallForceRevealedFlag(active)
        val settings = FloatBallOverlay.settingsState?.value ?: return
        if (active) {
            cancelBallIdleHide()
            if (!FloatBallOverlay.ballRevealed) {
                FloatBallOverlay.ballRevealed = true
                syncBallIdleAlpha(settings, animate = false)
            }
        } else if (FloatBallOverlay.ballRevealed) {
            scheduleBallIdleHide(settings)
        }
    }

    private fun ensureBallIdleHideScheduled(settings: AppSettings) {
        val delayMs = settings.floatBallIdleDelayMs.coerceIn(0, FloatBallOverlay.BALL_IDLE_DELAY_MAX_MS)
        if (FloatBallOverlay.ballIdleHideRunnable != null && FloatBallOverlay.ballIdleHideDelayMs == delayMs) return
        scheduleBallIdleHide(settings)
    }

    private fun scheduleBallIdleHide(settings: AppSettings) {
        cancelBallIdleHide()
        if (!idleVisualsEnabled(settings) || FloatBallOverlay.ballPressed || FloatBallOverlay.isBallForceRevealed() || FloatBallOverlay.isDraggingNow()) return
        val delayMs = settings.floatBallIdleDelayMs.coerceIn(0, FloatBallOverlay.BALL_IDLE_DELAY_MAX_MS)
        val runnable = Runnable {
            FloatBallOverlay.ballIdleHideRunnable = null
            FloatBallOverlay.ballIdleHideDelayMs = -1
            val current = FloatBallOverlay.settingsState?.value ?: return@Runnable
            if (!idleVisualsEnabled(current) || FloatBallOverlay.ballPressed || FloatBallOverlay.isBallForceRevealed() || FloatBallOverlay.isDraggingNow()) {
                return@Runnable
            }
            FloatBallOverlay.ballRevealed = false
            syncBallIdleAlpha(current, animate = true)
        }
        FloatBallOverlay.ballIdleHideRunnable = runnable
        FloatBallOverlay.ballIdleHideDelayMs = delayMs
        FloatBallOverlay.mainHandler.postDelayed(runnable, delayMs.toLong())
    }

    internal fun cancelBallIdleHide() {
        FloatBallOverlay.ballIdleHideRunnable?.let { FloatBallOverlay.mainHandler.removeCallbacks(it) }
        FloatBallOverlay.ballIdleHideRunnable = null
        FloatBallOverlay.ballIdleHideDelayMs = -1
    }
}
