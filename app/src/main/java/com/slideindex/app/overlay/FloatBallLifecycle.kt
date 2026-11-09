package com.slideindex.app.overlay

import android.os.Looper

/**
 * 悬浮球的生命周期/布局触发（从 `FloatBallOverlay` 拆出的第 4 类）。
 *
 * 覆盖：语言切换重建、布局重算、输入法高度变化。
 */
internal object FloatBallLifecycle {

    fun recreateForLocaleChange() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { recreateForLocaleChange() }
            return
        }
        if (!FloatBallOverlay.isShowing) return
        val hostContext = overlayHostContext() ?: return
        val settings = FloatBallOverlay.settingsState?.value ?: return
        val persistPosition = FloatBallOverlay.onPositionPersisted ?: return
        val persistSide = FloatBallOverlay.onActiveSidePersisted ?: {}
        FloatBallOverlay.dismiss()
        FloatBallOverlay.showOrUpdate(hostContext, settings, persistPosition, persistSide)
    }

    fun relayout() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FloatBallOverlay.mainHandler.post { relayout() }
            return
        }
        if (FloatBallOverlay.captureSuppressed) return
        FloatBallOverlay.settingsState?.value ?: return
        val view = FloatBallOverlay.displayView
        if (view != null && view.isAttachedToWindow) {
            view.post { FloatBallOverlay.relayoutNow() }
        } else {
            FloatBallOverlay.relayoutNow()
        }
    }

    fun onKeyboardImeChanged() {
        if (!FloatBallOverlay.isShowing || FloatBallOverlay.isDraggingNow()) return
        relayout()
    }
}
