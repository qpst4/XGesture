package com.slideindex.app.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.shell.ShellExecutionMode

object CycleKeyboardHelper {
    private const val TAG = "CycleKeyboardHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 「循环切换输入法」：
     * - 配置了目标输入法 → 在当前输入法与目标之间来回切（再按一下切回上一次那个）；
     * - 未配置 / 配置的目标已不可用 → 沿用旧行为，在已启用输入法之间依次轮换。
     */
    fun cycleKeyboard(context: Context): Boolean {
        val enabledImes = InputMethodCatalog.enabled(context)
        if (enabledImes.isEmpty()) {
            Log.w(TAG, "No enabled input methods found")
            return false
        }

        val decision = CycleInputMethodPlanner.decide(
            enabledImeIds = enabledImes.map { it.id },
            currentImeId = InputMethodCatalog.currentId(context),
            targetImeId = InputMethodActionPrefs.readCycleTarget(context),
            previousImeId = InputMethodActionPrefs.readCyclePrevious(context),
            excludedImeIds = InputMethodActionPrefs.readCycleExcluded(context),
        )

        decision.singleEnabledImeId?.let { singleId ->
            val label = enabledImes.firstOrNull { it.id == singleId }?.label ?: singleId
            showFeedback(context, context.getString(R.string.cycle_keyboard_single_ime, label))
            performHaptic(context)
            return true
        }

        val targetId = decision.switchToImeId ?: return true
        val targetLabel = enabledImes.firstOrNull { it.id == targetId }?.label ?: targetId
        val outcome = setInputMethod(context, targetId)
        if (outcome != SwitchOutcome.SWITCHED) return false

        decision.rememberPreviousImeId?.let { previousId ->
            InputMethodActionPrefs.writeCyclePrevious(context, previousId)
        }
        if (decision.clearPrevious) {
            InputMethodActionPrefs.writeCyclePrevious(context, "")
        }
        showFeedback(context, context.getString(R.string.cycle_keyboard_switched_toast, targetLabel))
        performHaptic(context)
        return true
    }

    /**
     * 「切换输入法」配置了具体输入法时直接切过去；目标已被停用/卸载则退回系统选择器，
     * 避免动作看起来完全没反应。
     */
    fun switchToInputMethod(context: Context, imeId: String): Boolean {
        if (!InputMethodCatalog.isEnabled(context, imeId)) {
            Log.w(TAG, "Configured input method is unavailable: $imeId")
            return InputMethodHelper.switchInputMethod(context)
        }
        // 没有切 IME 的能力时 setInputMethod 会弹选择器：这算「已执行」，不能报失败。
        return setInputMethod(context, imeId) != SwitchOutcome.FAILED
    }

    private enum class SwitchOutcome {
        /** 已经真正切过去了。 */
        SWITCHED,

        /** 没有特权，降级弹出了系统选择器，由用户自己选。 */
        PICKER_SHOWN,

        /** 既没切成功，选择器也没能弹出来。 */
        FAILED,
    }

    private fun setInputMethod(context: Context, imeId: String): SwitchOutcome {
        // 路径 1：若具备 WRITE_SECURE_SETTINGS，直接写 Settings.Secure
        try {
            val ok = Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                imeId,
            )
            if (ok) {
                Log.i(TAG, "Switched IME via Settings.Secure: $imeId")
                return SwitchOutcome.SWITCHED
            }
        } catch (_: SecurityException) {
            // 没有安全设置写入权限，尝试特权路径
        }

        // 路径 2：通过特权（Root / Shizuku）运行 cmd input_method set-input-method
        if (TaskManagerUtil.hasPrivilegedAccess()) {
            val mode = if (PrivilegeGateway.isRootMode()) ShellExecutionMode.ROOT else ShellExecutionMode.SHIZUKU
            val result = TaskManagerUtil.runShellCommandLineExplicit(
                command = "cmd input_method set-input-method ${shellQuote(imeId)}",
                mode = mode,
            )
            Log.i(TAG, "Switched IME via privileged shell: exitCode=${result.exitCode}")
            if (result.exitCode == 0) return SwitchOutcome.SWITCHED
        }

        // 路径 3：降级回退到调出输入法选择器
        Log.i(TAG, "Privilege unavailable, fallback to InputMethodPicker")
        return if (InputMethodHelper.switchInputMethod(context)) {
            SwitchOutcome.PICKER_SHOWN
        } else {
            SwitchOutcome.FAILED
        }
    }

    private fun showFeedback(context: Context, text: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun performHaptic(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
