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
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.shell.ShellExecutionMode

object CycleKeyboardHelper {
    private const val TAG = "CycleKeyboardHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cycleKeyboard(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        val enabledImes: List<InputMethodInfo> = imm.enabledInputMethodList.orEmpty()
        if (enabledImes.isEmpty()) {
            Log.w(TAG, "No enabled input methods found")
            return false
        }
        if (enabledImes.size == 1) {
            val label = enabledImes[0].loadLabel(context.packageManager).toString()
            showFeedback(context, context.getString(R.string.cycle_keyboard_single_ime, label))
            performHaptic(context)
            return true
        }

        val currentImeId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ).orEmpty()

        val currentIndex = enabledImes.indexOfFirst { it.id == currentImeId }
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % enabledImes.size
        } else {
            0
        }
        val targetIme = enabledImes[nextIndex]
        val targetLabel = targetIme.loadLabel(context.packageManager).toString()

        val success = setInputMethod(context, targetIme.id)
        if (success) {
            showFeedback(context, context.getString(R.string.cycle_keyboard_switched_toast, targetLabel))
            performHaptic(context)
        }
        return success
    }

    private fun setInputMethod(context: Context, imeId: String): Boolean {
        // 路径 1：若具备 WRITE_SECURE_SETTINGS，直接写 Settings.Secure
        try {
            val ok = Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                imeId,
            )
            if (ok) {
                Log.i(TAG, "Switched IME via Settings.Secure: $imeId")
                return true
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
            if (result.exitCode == 0) return true
        }

        // 路径 3：降级回退到调出输入法选择器
        Log.i(TAG, "Privilege unavailable, fallback to InputMethodPicker")
        InputMethodHelper.switchInputMethod(context)
        return false
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
