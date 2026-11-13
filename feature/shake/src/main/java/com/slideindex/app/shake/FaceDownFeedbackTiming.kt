package com.slideindex.app.shake

import android.content.Context
import android.media.AudioManager
import com.slideindex.app.gesture.GestureAction

/**
 * 会在执行后进入静音/勿扰响铃态的动作，需先播反馈再执行，否则提示音会被自己静音。
 */
internal fun GestureAction.requiresFaceDownFeedbackBeforeExecution(context: Context): Boolean =
    when (this) {
        GestureAction.LockScreenAndSilenceRing,
        GestureAction.LockScreenAndMuteAll,
        -> true
        is GestureAction.ToggleMute -> {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.ringerMode != AudioManager.RINGER_MODE_SILENT
        }
        else -> false
    }
