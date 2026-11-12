package com.slideindex.app.overlay.pickresult

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import com.slideindex.app.overlay.panels.R
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 取词面板的「朗读」：单实例 TTS，再点一次即停止（issue #50）。
 *
 * 生命周期跟着进程走：面板关闭不主动 shutdown（TTS 初始化要几百毫秒，反复开关面板会明显卡顿），
 * 只在 [stop] 时打断当前播报。
 */
object PickResultTts {
    private const val TAG = "PickResultTts"

    private var engine: TextToSpeech? = null
    private var initialized = false
    private var pendingText: String? = null

    /** 是否正在播报；面板上的图标据此在「朗读 / 停止」之间切换。 */
    var isSpeaking by mutableStateOf(false)
        private set

    fun toggle(context: Context, text: String) {
        if (isSpeaking) {
            stop()
            return
        }
        val body = text.trim()
        if (body.isEmpty()) return
        if (initialized) {
            speak(context, body)
        } else {
            pendingText = body
            ensureEngine(context)
        }
    }

    fun stop() {
        engine?.stop()
        isSpeaking = false
    }

    private fun ensureEngine(context: Context) {
        if (engine != null) return
        val appContext = context.applicationContext
        engine = TextToSpeech(appContext) { status ->
            initialized = status == TextToSpeech.SUCCESS
            if (!initialized) {
                Log.w(TAG, "TTS init failed: $status")
                pendingText = null
                return@TextToSpeech
            }
            engine?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                }
            )
            pendingText?.let { speak(appContext, it) }
            pendingText = null
        }
    }

    private fun speak(context: Context, text: String) {
        val tts = engine ?: return
        val locale = tts.defaultVoice?.locale ?: Locale.getDefault()
        val availability = tts.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            Toast.makeText(
                context.applicationContext,
                R.string.pick_result_tts_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
            isSpeaking = false
            return
        }
        tts.setLanguage(locale)
        isSpeaking = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pick-${System.nanoTime()}")
    }
}
