package com.slideindex.app.util

import android.content.Context
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
        val strength = settings.resolvedHapticStrength()
        val constant = feedbackConstant(PulseKind.CONFIRM, strength)
        val success = if (view.isAttachedToWindow) {
            view.performHapticFeedback(constant, FLAGS)
        } else false
        if (!success) {
            vibrateFallback(view.context, constant)
        }
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
        val constant = feedbackConstant(kind, settings.resolvedHapticStrength())
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
        val durationMs = when (feedbackConstant) {
            HapticFeedbackConstants.GESTURE_START,
            HapticFeedbackConstants.CONFIRM,
            -> 24L
            HapticFeedbackConstants.CONTEXT_CLICK -> 16L
            else -> 10L
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
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
