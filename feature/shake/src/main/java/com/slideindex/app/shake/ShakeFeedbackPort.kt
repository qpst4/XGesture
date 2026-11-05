package com.slideindex.app.shake

import android.content.Context
import com.slideindex.app.gesture.GestureAction

/** Haptic and visual feedback after a shake gesture is recognized. */
interface ShakeFeedbackPort {
    fun vibrate(context: Context)
    /**
     * @param forceAudible 扣桌反馈：系统已静音时仍播放提示音。
     * @param volume App 内反馈音强度（0–100）。
     */
    fun playActionSound(
        context: Context,
        forceAudible: Boolean = false,
        volume: Int = FaceDownGestureSettings.DEFAULT_AUDIO_FEEDBACK_VOLUME,
    )
    fun showGestureFeedback(
        context: Context,
        gestureType: ShakeGestureType,
        action: GestureAction,
        colorArgb: Int,
    )
    fun detachFeedbackOverlay()

    /** 显示「晃动菜单」浮层：按方向列出该菜单里绑定的动作，未配置的方向显示为「无」。 */
    fun showMenuOverlay(context: Context, menu: ShakeMenu)

    /** 关掉菜单浮层；执行动作、会话超时或服务停止时调用。 */
    fun detachMenuOverlay()
}
