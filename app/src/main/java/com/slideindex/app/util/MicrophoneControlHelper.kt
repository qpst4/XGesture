package com.slideindex.app.util

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.shell.ShellExecutionMode

object MicrophoneControlHelper {
    private const val TAG = "MicrophoneControlHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isMuted(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.isMicrophoneMute
    }

    fun setMute(context: Context, mute: Boolean, showToast: Boolean = true): Boolean {
        val am = context.getSystemService(AudioManager::class.java)
        if (am != null) {
            runCatching { am.isMicrophoneMute = mute }
        }

        // Android 12+ 若有特权，联动传感器隐私硬件开关
        if (TaskManagerUtil.hasPrivilegedAccess()) {
            val mode = if (PrivilegeGateway.isRootMode()) ShellExecutionMode.ROOT else ShellExecutionMode.SHIZUKU
            val flag = if (mute) "true" else "false"
            runCatching {
                TaskManagerUtil.runShellCommandLineExplicit(
                    command = "cmd sensor_privacy set-sensor-state 1 $flag",
                    mode = mode,
                )
            }
        }

        performHaptic(context)
        if (showToast) {
            val text = if (mute) {
                context.getString(R.string.mic_toast_muted)
            } else {
                context.getString(R.string.mic_toast_unmuted)
            }
            showToast(context, text)
        }
        Log.i(TAG, "Microphone mute state changed to: $mute")
        return true
    }

    fun toggle(context: Context, showToast: Boolean = true): Boolean {
        val current = isMuted(context)
        return setMute(context, !current, showToast)
    }

    private fun showToast(context: Context, text: String) {
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
}
