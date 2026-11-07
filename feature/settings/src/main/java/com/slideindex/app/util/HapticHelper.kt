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

    fun gestureStart(view: View, settings: AppSettings) {
        if (!settings.hapticGestureTriggerEnabled) return
        pulse(view, settings, PulseKind.GESTURE)
    }

    fun longThreshold(view: View, settings: AppSettings) {
        if (!settings.hapticGestureTriggerEnabled) return
        pulse(view, settings, PulseKind.LONG_THRESHOLD)
    }

    fun letterTick(view: View, settings: AppSettings) {
        if (!settings.hapticSlotSelectionEnabled) return
        pulse(view, settings, PulseKind.LETTER)
    }

    fun appTick(view: View, settings: AppSettings) {
        if (!settings.hapticSlotSelectionEnabled) return
        pulse(view, settings, PulseKind.APP)
    }

    fun confirmLaunch(view: View, settings: AppSettings) {
        if (!settings.hapticConfirmLaunchEnabled) return
        pulse(view, settings, PulseKind.CONFIRM)
    }

    /** 悬浮球手势判定成立：额外受悬浮球专属开关 [AppSettings.floatBallGestureHapticEnabled] 约束。 */
    fun floatBallGestureStart(view: View, settings: AppSettings) {
        if (!settings.floatBallGestureHapticEnabled) return
        if (!settings.hapticGestureTriggerEnabled) return
        pulse(view, settings, PulseKind.GESTURE)
    }

    /** 悬浮球松手执行动作：额外受悬浮球专属开关 [AppSettings.floatBallGestureHapticEnabled] 约束。 */
    fun floatBallConfirmLaunch(view: View, settings: AppSettings) {
        if (!settings.floatBallGestureHapticEnabled) return
        if (!settings.hapticConfirmLaunchEnabled) return
        pulse(view, settings, PulseKind.CONFIRM)
    }

    /**
     * 通用「确认」反馈（存下 / 复制 / 删除 / 完成 / 星标）：
     * **只受总开关与强度控制** —— 不像 [confirmLaunch] 那样再叠一层"启动确认"开关。
     */
    fun actionConfirm(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.CONFIRM)
    }

    /** 通用「轻点」反馈（菜单项 / 标签 chip）：同样只受总开关与强度控制。 */
    fun actionTick(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.LETTER)
    }

    fun preview(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.CONFIRM)
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
