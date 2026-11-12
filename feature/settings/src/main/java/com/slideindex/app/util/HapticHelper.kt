package com.slideindex.app.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HapticStrength
import com.slideindex.app.settings.resolvedHapticStrength

object HapticHelper {
    private const val FLAGS = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING

    /** 两次震动的最小间隔：马达来不及停，更密的两声会叠成一片“糊”感。 */
    private const val MIN_PULSE_GAP_MS = 60L

    private var lastPulseAtMs = 0L

    /**
     * 一次手势里可能发声的震动事件。
     *
     * 每个事件对应设置页一个独立开关（[AppSettings] 里 `haptic*Enabled`）。
     * 「长滑」与「长按」曾经共用一个入口，导致关掉长滑会连长按开场声一起掐掉（issue #57）。
     */
    enum class Event {
        SHORT_THRESHOLD,
        COMPOUND_TURN,
        RETURN_SWIPE,
        HOVER,
        LONG_PRESS,
        SWIPE_LONG,
        SLOT_LETTER,
        SLOT_APP,
        CONFIRM,
        ACTION_CONFIRM,
    }

    private fun AppSettings.isEventEnabled(event: Event): Boolean = when (event) {
        Event.SHORT_THRESHOLD -> hapticShortThresholdEnabled
        Event.COMPOUND_TURN -> hapticCompoundTurnEnabled
        Event.RETURN_SWIPE -> hapticReturnSwipeEnabled
        Event.HOVER -> hapticHoverEnabled
        Event.LONG_PRESS -> hapticLongPressEnabled
        Event.SWIPE_LONG -> hapticLongThresholdEnabled
        Event.SLOT_LETTER -> hapticSlotLetterEnabled
        Event.SLOT_APP -> hapticSlotAppEnabled
        Event.CONFIRM -> hapticConfirmLaunchEnabled
        Event.ACTION_CONFIRM -> hapticActionConfirmEnabled
    }

    private fun Event.toPulseKind(): PulseKind = when (this) {
        Event.SLOT_LETTER -> PulseKind.LETTER
        Event.SLOT_APP -> PulseKind.APP
        Event.CONFIRM, Event.ACTION_CONFIRM -> PulseKind.CONFIRM
        Event.LONG_PRESS, Event.SWIPE_LONG -> PulseKind.LONG_THRESHOLD
        Event.SHORT_THRESHOLD, Event.COMPOUND_TURN, Event.RETURN_SWIPE, Event.HOVER -> PulseKind.GESTURE
    }

    /** 唯一发声入口：先查总开关、再查该事件开关，最后按事件选脉冲形态。 */
    fun play(view: View, settings: AppSettings, event: Event) {
        playWithKind(view, settings, event, event.toPulseKind())
    }

    private fun playWithKind(view: View, settings: AppSettings, event: Event, kind: PulseKind) {
        if (!settings.hapticEnabled) return
        if (!settings.isEventEnabled(event)) return
        pulse(view, settings, kind)
    }

    fun gestureStart(view: View, settings: AppSettings) = play(view, settings, Event.SHORT_THRESHOLD)

    fun compoundTurn(view: View, settings: AppSettings) = play(view, settings, Event.COMPOUND_TURN)

    fun returnSwipe(view: View, settings: AppSettings) = play(view, settings, Event.RETURN_SWIPE)

    fun hover(view: View, settings: AppSettings) = play(view, settings, Event.HOVER)

    fun longPress(view: View, settings: AppSettings) = play(view, settings, Event.LONG_PRESS)

    fun longThreshold(view: View, settings: AppSettings) = play(view, settings, Event.SWIPE_LONG)

    fun letterTick(view: View, settings: AppSettings) = play(view, settings, Event.SLOT_LETTER)

    fun appTick(view: View, settings: AppSettings) = play(view, settings, Event.SLOT_APP)

    fun confirmLaunch(view: View, settings: AppSettings) = play(view, settings, Event.CONFIRM)

    /** 悬浮球手势判定成立：额外受悬浮球专属开关 [AppSettings.floatBallGestureHapticEnabled] 约束。 */
    fun floatBallGestureStart(view: View, settings: AppSettings) {
        if (!settings.floatBallGestureHapticEnabled) return
        play(view, settings, Event.SHORT_THRESHOLD)
    }

    /** 悬浮球松手执行动作：额外受悬浮球专属开关 [AppSettings.floatBallGestureHapticEnabled] 约束。 */
    fun floatBallConfirmLaunch(view: View, settings: AppSettings) {
        if (!settings.floatBallGestureHapticEnabled) return
        play(view, settings, Event.CONFIRM)
    }

    /**
     * 通用「确认」反馈（存下 / 复制 / 删除 / 完成 / 星标）：
     * **只受总开关与强度控制** —— 不像 [confirmLaunch] 那样再叠一层"启动确认"开关。
     */
    fun actionConfirm(view: View, settings: AppSettings) {
        playWithKind(view, settings, Event.ACTION_CONFIRM, PulseKind.CONFIRM)
    }

    /** 通用「轻点」反馈（菜单项 / 标签 chip）：归在「通用确认」开关下。 */
    fun actionTick(view: View, settings: AppSettings) {
        playWithKind(view, settings, Event.ACTION_CONFIRM, PulseKind.LETTER)
    }

    fun preview(view: View, settings: AppSettings) {
        playWithKind(view, settings, Event.ACTION_CONFIRM, PulseKind.CONFIRM)
    }

    fun pickPanelSaveLongClick(view: View, settings: AppSettings) {
        emit(view, feedbackConstant(PulseKind.CONFIRM, settings.resolvedHapticStrength()))
    }

    private enum class PulseKind {
        GESTURE,
        LONG_THRESHOLD,
        LETTER,
        APP,
        CONFIRM,
    }

    private fun pulse(view: View, settings: AppSettings, kind: PulseKind) {
        if (!settings.hapticEnabled) return
        emit(view, feedbackConstant(kind, settings.resolvedHapticStrength()))
    }

    /**
     * 真正发声：系统语义常量优先；拿不到就用预定义波形兜底。
     *
     * [MIN_PULSE_GAP_MS] 内只发一次——马达来不及停，两声叠在一起听起来就是“糊”的
     * （快速长滑时“开始 + 过长距”两声尤其明显）。
     */
    private fun emit(view: View, constant: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPulseAtMs < MIN_PULSE_GAP_MS) return
        lastPulseAtMs = now
        val success = if (view.isAttachedToWindow) {
            view.performHapticFeedback(constant, FLAGS)
        } else false
        if (!success) {
            vibrateFallback(view.context, constant)
        }
    }

    private fun vibrateFallback(context: Context, feedbackConstant: Int) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        // 预定义波形比自拼 createOneShot 更接近系统手感（小米/OPPO 上差异尤其明显）。
        val effect = VibrationEffect.createPredefined(effectFor(feedbackConstant))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 标成“触摸反馈”通道，别让系统按通知/铃声震动去处理。
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
            )
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun effectFor(feedbackConstant: Int): Int = when (feedbackConstant) {
        HapticFeedbackConstants.GESTURE_START,
        HapticFeedbackConstants.CONFIRM,
        HapticFeedbackConstants.CONTEXT_CLICK,
        -> VibrationEffect.EFFECT_CLICK
        else -> VibrationEffect.EFFECT_TICK
    }

    private fun vibrator(context: Context): Vibrator? {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        return manager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    private fun feedbackConstant(kind: PulseKind, strength: HapticStrength): Int =
        when (strength) {
            HapticStrength.LIGHT -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.CLOCK_TICK
                PulseKind.LONG_THRESHOLD -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.LETTER -> HapticFeedbackConstants.CLOCK_TICK
                PulseKind.APP -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            HapticStrength.MEDIUM -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.GESTURE_START
                PulseKind.LONG_THRESHOLD -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.LETTER -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.APP -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            }
            HapticStrength.STRONG -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.GESTURE_START
                PulseKind.LONG_THRESHOLD -> HapticFeedbackConstants.CONFIRM
                PulseKind.LETTER -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.APP -> HapticFeedbackConstants.CONFIRM
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            }
        }
}
