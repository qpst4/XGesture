package com.slideindex.app.overlay

import android.os.Looper
import android.util.Log
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.util.PermissionHelper

/**
 * 悬浮球的创建/更新入口（从 `FloatBallOverlay` 拆出的窗口生命周期第二半）。
 */
internal object FloatBallShow {

    fun showOrUpdate(
        context: android.content.Context,
        settings: AppSettings,
        onPositionPersisted: (xFraction: Float, yFraction: Float) -> Unit,
        onActiveSidePersisted: (FloatBallSide) -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post {
                showOrUpdate(context, settings, onPositionPersisted, onActiveSidePersisted)
            }
            return
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            FloatBallOverlay.dismiss()
            return
        }
        if (RegionalPickOverlay.isActive) {
            FloatBallOverlay.settingsState?.value = settings
            return
        }
        val hostContext = overlayHostContext()
            ?: run {
                Log.w(FloatBallOverlay.TAG, "accessibility service not connected")
                return
            }

        FloatBallOverlay.onPositionPersisted = onPositionPersisted
        FloatBallOverlay.onActiveSidePersisted = onActiveSidePersisted

        // 服务代次变了（无障碍被系统摘掉后重连）：上一代窗口已被系统删除，
        // 但 View.isAttachedToWindow 可能仍为 true。必须先清掉再按新代次重建，
        // 否则 isShowing 恒真 → 永远走不到 ensureWindows（“重开无障碍后球消失”）。
        val currentGeneration =
            com.slideindex.app.service.SlideIndexAccessibilityService.overlayHostGeneration()
        if (FloatBallOverlay.isShowing && FloatBallOverlay.windowGeneration != currentGeneration) {
            Log.i(
                FloatBallOverlay.TAG,
                "rebuild float ball: service generation changed " +
                    "${FloatBallOverlay.windowGeneration} → $currentGeneration",
            )
            FloatBallOverlay.dismiss()
        }

        if (FloatBallOverlay.isShowing && !FloatBallOverlay.areChromeWindowsAttached()) {
            // 熄屏/锁屏后系统可能摘掉 TYPE_ACCESSIBILITY_OVERLAY，本地引用仍在。
            // 先清理再重建，避免 isShowing=true 却永远不 ensureWindows。
            //
            // 真机修复：窗口过渡瞬间（例如面板出现 / 前台包名变化）isAttachedToWindow 会短暂为 false，
            // 但窗口其实还在 WM 里。此处若立刻 dismiss()+ensureWindows()，就是把悬浮球拆掉又装回 ——
            // 用户看到"每次触发面板，悬浮球闪一下"。logcat 实测调用栈：
            //   EdgeOverlayHost.refreshOverlaySuppression ← onForegroundPackageChanged
            //     ← updateForegroundPackage ← SlideIndexAccessibilityForegroundTracker
            // 因此先延后确认，仍然没挂上才真正重建。
            if (!FloatBallOverlay.pendingChromeRebuild) {
                FloatBallOverlay.pendingChromeRebuild = true
                FloatBallOverlay.mainHandler.postDelayed({
                    FloatBallOverlay.pendingChromeRebuild = false
                    if (FloatBallOverlay.isShowing && !FloatBallOverlay.areChromeWindowsAttached()) {
                        val persistPosition = FloatBallOverlay.onPositionPersisted
                        val persistSide = FloatBallOverlay.onActiveSidePersisted
                        FloatBallOverlay.dismiss()
                        FloatBallOverlay.onPositionPersisted = persistPosition
                        FloatBallOverlay.onActiveSidePersisted = persistSide
                        FloatBallOverlay.ensureWindows(hostContext, settings)
                    }
                }, FloatBallOverlay.CHROME_REBUILD_CONFIRM_MS)
            }
        } else if (!FloatBallOverlay.isShowing) {
            FloatBallOverlay.ensureWindows(hostContext, settings)
        } else {
            val incoming = settings
            val pendingSide = FloatBallOverlay.committedActiveSideUntilPersist
            if (pendingSide != null && incoming.floatBallActiveSide != pendingSide) {
                FloatBallOverlay.settingsState?.value =
                    incoming.copy(floatBall = incoming.floatBall.copy(floatBallActiveSide = pendingSide))
                return
            }
            if (pendingSide != null && incoming.floatBallActiveSide == pendingSide) {
                FloatBallOverlay.committedActiveSideUntilPersist = null
            }
            val current = FloatBallOverlay.settingsState?.value
            val merged = when {
                FloatBallOverlay.appearancePreviewRestore != null && current != null -> incoming.copy(
                    floatBall = incoming.floatBall.copy(
                        floatBallSizeDp = current.floatBallSizeDp,
                        floatBallOpacity = current.floatBallOpacity,
                        floatBallVisibleFraction = current.floatBallVisibleFraction,
                        floatBallLineHeightFraction = current.floatBallLineHeightFraction,
                        floatBallLineWidthDp = current.floatBallLineWidthDp,
                        floatBallLineOpacity = current.floatBallLineOpacity
                    )
                )
                // 拖动期：任何回流（别的设置写入、本次提交前后的中间态）都不许把 Y 拉回旧值。
                FloatBallOverlay.positionYPreviewRestore != null && current != null -> incoming.copy(
                    floatBall = incoming.floatBall.copy(
                        floatBallPositionYFraction = current.floatBallPositionYFraction
                    )
                )
                // 提交后、落盘回流前：继续顶住预览值，等 incoming 追上同一个值就放手。
                FloatBallOverlay.pendingPositionYFraction != null && current != null -> {
                    val pending = FloatBallOverlay.pendingPositionYFraction!!
                    if (kotlin.math.abs(incoming.floatBallPositionYFraction - pending) <=
                        FloatBallOverlay.POSITION_Y_EPSILON
                    ) {
                        FloatBallOverlay.cancelPendingPositionY()
                        incoming
                    } else {
                        incoming.copy(
                            floatBall = incoming.floatBall.copy(
                                floatBallPositionYFraction = current.floatBallPositionYFraction
                            )
                        )
                    }
                }
                FloatBallOverlay.isDraggingNow() &&
                    current != null &&
                    incoming.floatBallActiveSide != current.floatBallActiveSide -> incoming.copy(
                    floatBall = incoming.floatBall.copy(floatBallActiveSide = current.floatBallActiveSide)
                )
                else -> incoming
            }
            FloatBallOverlay.settingsState?.value = merged
            FloatBallOverlay.syncBallIdleAlpha(merged, animate = false)
            if (FloatBallOverlay.floatBallStyleSignature(current) !=
                FloatBallOverlay.floatBallStyleSignature(merged)
            ) {
                FloatBallOverlay.refreshStyleVisual()
            }
            FloatBallOverlay.touchHost?.updateSettings(merged)
            FloatBallOverlay.lineTouchHost?.updateSettings(merged)
            if (!FloatBallOverlay.isDraggingNow()) {
                FloatBallOverlay.recoverIdleTouchCaptureLayouts(merged)
                FloatBallOverlay.restorePassiveOverlayLayout(merged)
            }
        }
    }
}
