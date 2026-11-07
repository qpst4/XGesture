package com.slideindex.app.voice

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.util.ForegroundNotificationChannels
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * 麦克风前台服务：**同一个服务同时承担"语音识别"与"录音"两件事**（§0.16.21 起）。
 *
 * **为什么必须是前台服务**：面板/输入槽都是 overlay 窗，App 进程对系统来说不在前台，
 * 直连麦克风会被判成后台录音而拒绝 —— 前台服务 + `microphone` 类型是唯一的正路
 * （overlay 权限让 App 可以在后台启动这个前台服务）。
 *
 * **为什么录音复用这个服务、而不是新建一个**：
 * ① manifest 里 `.voice.StashVoiceInputService` 已经声明了
 *    `android:foregroundServiceType="microphone"`（以及 `RECORD_AUDIO` /
 *    `FOREGROUND_SERVICE_MICROPHONE` 权限），复用 = **本次完全不用改 manifest**，
 *    少一处 Android 14+ 前台服务类型的合规风险；
 * ② 麦克风只有一支：两件事放在一个 Service 实例里，"识别与录音互斥"是**天然**的
 *    （见 [startListening] / [startRecording] 开头的互相收尾），不用跨服务协调。
 *
 * 识别用系统 [SpeechRecognizer]：优先**端上识别**（`createOnDeviceSpeechRecognizer`，离线、
 * 不把语音送云端），设备不支持时退回标准识别器。
 *
 * 录音用 [MediaRecorder]：AAC / M4A、单声道 44.1kHz、64kbps（≈8KB/s），
 * 单段上限 [StashAudioMaxDurationMs]（5 分钟 ≈ 2.4MB）。文件落在
 * `cacheDir/stash_composer_audio/`（与选图的 `stash_composer_images` 同一套"暂存目录"做法），
 * 存条目时才由 `StashRepository.persistAudio` 复制进闪念自己的音频目录。
 */
class StashVoiceInputService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false
    private var started = false
    /** 正在主动收尾：此期间的 `onError(ERROR_CLIENT)` 是 `cancel()` 的回声，不要当成失败报给用户。 */
    private var stopping = false
    /** 前台通知是否已经挂上（录音与识别共用这一个标志，收尾时统一摘掉）。 */
    private var foregroundPosted = false

    /* ---------------- 录音（§0.16.21） ---------------- */

    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordStartedAtMs = 0L
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 5 分钟到点自动停（**保留**已录部分，与手动停同一条路）。 */
    private val autoStopRecording = Runnable {
        Log.i(TAG, "达到单段上限 ${StashAudioMaxDurationMs}ms，自动停止录音")
        stopRecording(cancelled = false)
        stopSelf()
    }

    /**
     * 被电话 / 其它应用抢麦。
     *
     * `AUDIOFOCUS_LOSS*` 都要停：**保留已录部分**（不是取消）—— 用户按下录音键之后，
     * "被别的应用打断"不该等于"刚才那 20 秒白录了"。
     */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Log.i(TAG, "audio focus lost($change) → 停止录音并保留已录部分")
            stopRecording(cancelled = false)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening(cancelled = true)
                // 「取消语音输入」= 这一轮什么都不要：录音也一并丢弃。
                stopRecording(cancelled = true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECORD_START -> {
                startRecording()
                return START_NOT_STICKY
            }
            ACTION_RECORD_STOP -> {
                stopRecording(cancelled = false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECORD_CANCEL -> {
                stopRecording(cancelled = true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startListening()
        return START_NOT_STICKY
    }

    /* ==================== 语音识别 ==================== */

    private fun startListening() {
        // 与录音互斥（同一支麦克风）。这里**保留**已录部分：用户长按切去转文字时，
        // 刚录的那一段应当照常落成块，而不是被静默丢掉。
        stopRecording(cancelled = false)
        if (started) return
        if (!SpeechRecognizer.isRecognitionAvailable(this) && !isOnDeviceAvailable()) {
            Log.w(TAG, "no recognition service")
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            stopSelf()
            return
        }
        started = true
        if (!promoteToForeground(recording = false)) {
            // 渠道不可用时不前台化：这时录音大概率也拿不到，直接给失败反馈，别让用户对着空气说话。
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            stopSelf()
            return
        }
        StashVoiceSession.beginSession()
        createRecognizer()?.let { engine ->
            runCatching { engine.startListening(buildIntent()) }
                .onFailure { error ->
                    Log.e(TAG, "startListening failed", error)
                    StashVoiceSession.finishWithError(R.string.stash_voice_error_generic)
                    stopSelf()
                }
        } ?: run {
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            stopSelf()
        }
    }

    private fun isOnDeviceAvailable(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(this) }.getOrDefault(false)

    private fun createRecognizer(): SpeechRecognizer? {
        if (recognizer != null) return recognizer
        val engine = runCatching {
            if (isOnDeviceAvailable()) {
                onDevice = true
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                onDevice = false
                SpeechRecognizer.createSpeechRecognizer(this)
            }
        }.getOrNull()
        if (engine == null) {
            StashVoiceSession.finishWithError(R.string.stash_voice_error_unavailable)
            return null
        }
        engine.setRecognitionListener(listener)
        recognizer = engine
        return engine
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 跟随系统语言；识别器不认识时会自己回退。
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            if (onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (stopping) return
            val text = partialResults?.bestResult() ?: return
            if (text.isNotBlank()) StashVoiceSession.updatePartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (stopping) return
            val text = results?.bestResult().orEmpty()
            if (text.isBlank()) {
                StashVoiceSession.finishWithError(R.string.stash_voice_error_no_match)
            } else {
                StashVoiceSession.finishWithResult(text)
            }
            stopSelf()
        }

        override fun onError(error: Int) {
            if (stopping) return
            Log.w(TAG, "recognition error $error")
            StashVoiceSession.finishWithError(errorMessageRes(error))
            stopSelf()
        }
    }

    private fun Bundle.bestResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    private fun errorMessageRes(error: Int): Int = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> R.string.stash_voice_error_no_match

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        -> R.string.stash_voice_error_network

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.stash_voice_error_permission

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_CLIENT,
        -> R.string.stash_voice_error_generic

        else -> R.string.stash_voice_error_generic
    }

    /* ==================== 录音 ==================== */

    /**
     * 开始录一段。
     *
     * 顺序很讲究：**先前台化**（`startForegroundService` 起 5 秒内必须 `startForeground`，
     * 否则系统会判非法通知并杀进程），**再**建 MediaRecorder —— 建不出来时下面会主动摘掉前台状态。
     */
    private fun startRecording() {
        if (recorder != null) return
        // 与识别互斥：识别会话在这里收掉（不留结果）。
        stopListening(cancelled = true)
        if (!promoteToForeground(recording = true)) {
            StashAudioRecordSession.beginSession(System.currentTimeMillis())
            StashAudioRecordSession.finishWithError(R.string.stash_audio_record_failed)
            stopSelf()
            return
        }
        val dir = File(cacheDir, RECORD_CACHE_DIR_NAME).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.m4a")
        val engine = runCatching { MediaRecorder(this) }.getOrNull()
        val prepared = engine != null && runCatching {
            engine.setAudioSource(MediaRecorder.AudioSource.MIC)
            engine.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            engine.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            engine.setAudioChannels(1)
            engine.setAudioSamplingRate(RECORD_SAMPLE_RATE_HZ)
            engine.setAudioEncodingBitRate(RECORD_BIT_RATE_BPS)
            engine.setOutputFile(file.absolutePath)
            engine.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaRecorder error what=$what extra=$extra")
                // 回到主线程收尾：不要在 MediaRecorder 自己的回调里释放它。
                // ⚠️ `MediaRecorder.OnErrorListener.onError` 返回的是 **void**（不像 `MediaPlayer`
                // 那个监听器要返回 Boolean），所以这里没有"吞掉错误"这种返回值语义。
                val posted = mainHandler.post {
                    stopRecording(cancelled = false)
                    stopSelf()
                }
                if (!posted) {
                    // 队列都排不进去（极不可能）：当场收尾，绝不能让麦克风一直被占着。
                    Log.w(TAG, "MediaRecorder error handler not posted; stopping inline")
                    stopRecording(cancelled = false)
                    stopSelf()
                }
            }
            engine.prepare()
            engine.start()
        }.onFailure { Log.e(TAG, "startRecording failed", it) }.isSuccess
        if (!prepared) {
            runCatching { engine?.reset() }
            runCatching { engine?.release() }
            runCatching { file.delete() }
            StashAudioRecordSession.beginSession(System.currentTimeMillis())
            StashAudioRecordSession.finishWithError(R.string.stash_audio_record_failed)
            dropForeground()
            stopSelf()
            return
        }
        recorder = engine
        recordFile = file
        recordStartedAtMs = System.currentTimeMillis()
        StashAudioRecordSession.beginSession(recordStartedAtMs)
        requestAudioFocus()
        mainHandler.postDelayed(autoStopRecording, StashAudioMaxDurationMs)
    }

    /**
     * 结束这一段。
     *
     * @param cancelled true = 用户主动取消 → 删文件、不留结果；
     *   false = 正常停 / 被抢麦 / 到上限 → **保留已录部分**并交给 [StashAudioRecordSession]。
     *
     * 「保留」的边界：`MediaRecorder.stop()` 失败（抢麦时常见）会让 MPEG_4 缺 moov atom，
     * 那种文件播不出来。所以判据是"文件里**真的**有字节"，有就留下（播放侧会给"无法播放"提示），
     * 一个字节都没有才报错 —— 宁可让用户看见一段播不了的录音，也不要静默吞掉他刚说的话。
     */
    private fun stopRecording(cancelled: Boolean) {
        mainHandler.removeCallbacks(autoStopRecording)
        val engine = recorder
        if (engine == null) {
            abandonAudioFocus()
            if (cancelled) {
                recordFile?.let { runCatching { it.delete() } }
                recordFile = null
                StashAudioRecordSession.cancelSession()
            }
            return
        }
        recorder = null
        val file = recordFile
        recordFile = null
        val durationMs = (System.currentTimeMillis() - recordStartedAtMs).coerceAtLeast(0L)
        val stopped = runCatching { engine.stop() }
            .onFailure { Log.w(TAG, "MediaRecorder.stop failed（文件可能不完整）", it) }
            .isSuccess
        runCatching { engine.reset() }
        runCatching { engine.release() }
        abandonAudioFocus()
        dropForeground()
        val usable = file != null && file.exists() && file.length() > 0L
        if (cancelled) {
            runCatching { file?.delete() }
            StashAudioRecordSession.cancelSession()
            return
        }
        if (!usable) {
            // ⚠️ 这里 `file` **还是可空的**（`!usable` 不能推出 `file == null`，文件可能只是 0 字节）。
            file?.delete()
            StashAudioRecordSession.finishWithError(R.string.stash_audio_record_failed)
            return
        }
        // 走到这里编译器已经知道 `file != null`（`usable == true` 蕴含它），所以下面**不加** `!!`。
        if (!stopped) Log.w(TAG, "保留可能不完整的录音：${file.absolutePath}")
        StashAudioRecordSession.finishWithResult(file.absolutePath, durationMs)
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(audioFocusListener, mainHandler)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        }.onFailure { Log.w(TAG, "requestAudioFocus failed", it) }
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        audioFocusRequest = null
        runCatching { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) }
    }

    /* ==================== 前台通知 ==================== */

    /**
     * 起前台通知。返回 false 只代表**渠道真的不存在**（这时不能 startForeground）。
     *
     * 注意判据用 [ForegroundNotificationChannels.Result.isUsable]，不能用 `canPromote`：
     * 用户关掉通知渠道时 startForeground() 依然会成功（通知不显示），而不调它会被 AMS
     * 以前台服务超时为由杀掉整个进程。
     */
    private fun promoteToForeground(recording: Boolean): Boolean {
        val channelId = if (recording) CHANNEL_ID_RECORD else CHANNEL_ID
        val channel = ForegroundNotificationChannels.ensureUsable(
            context = this,
            id = channelId,
            name = getString(
                if (recording) R.string.stash_audio_channel_name else R.string.stash_voice_channel_name,
            ),
            importance = NotificationManager.IMPORTANCE_LOW,
            tag = TAG,
        )
        if (!channel.isUsable) {
            Log.e(TAG, if (recording) "录音通知渠道不可用" else "语音输入通知渠道不可用")
            return false
        }
        if (channel.blocked) {
            Log.w(TAG, "渠道存在但被用户关闭：通知不可见，仍照常前台化以免前台服务超时杀进程")
        }
        val cancelIntent = PendingIntent.getService(
            this,
            if (recording) 1 else 0,
            Intent(this, StashVoiceInputService::class.java)
                .setAction(if (recording) ACTION_RECORD_CANCEL else ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                getString(
                    if (recording) {
                        R.string.stash_audio_notification_title
                    } else {
                        R.string.stash_voice_notification_title
                    },
                ),
            )
            .setContentText(
                getString(
                    if (recording) {
                        R.string.stash_audio_notification_body
                    } else {
                        R.string.stash_voice_notification_body
                    },
                ),
            )
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.stash_voice_cancel), cancelIntent)
            .build()
        runCatching {
            startForeground(if (recording) NOTIFY_ID_RECORD else NOTIFY_ID, notification)
        }.onFailure { error ->
            Log.e(TAG, "startForeground failed", error)
            return false
        }
        foregroundPosted = true
        return true
    }

    private fun dropForeground() {
        if (!foregroundPosted) return
        foregroundPosted = false
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    private fun stopListening(cancelled: Boolean) {
        stopping = true
        val engine = recognizer
        recognizer = null
        runCatching { engine?.stopListening() }
        runCatching { engine?.cancel() }
        runCatching { engine?.destroy() }
        if (cancelled) StashVoiceSession.cancelSession()
        if (started) {
            started = false
            dropForeground()
        }
    }

    override fun onDestroy() {
        stopListening(cancelled = true)
        // 服务被别处 stopService / 系统回收时，录到一半的内容**保留**（用户按了录音键，
        // 不该因为服务生命周期而丢掉）；真正的"取消"只走 ACTION_RECORD_CANCEL。
        stopRecording(cancelled = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.slideindex.app.action.STASH_VOICE_START"
        const val ACTION_STOP = "com.slideindex.app.action.STASH_VOICE_STOP"

        /** 开始录音（§0.16.21）。 */
        const val ACTION_RECORD_START = "com.slideindex.app.action.STASH_AUDIO_RECORD_START"

        /** 结束录音并保留已录部分。 */
        const val ACTION_RECORD_STOP = "com.slideindex.app.action.STASH_AUDIO_RECORD_STOP"

        /** 放弃这一段（通知上的「取消」）。 */
        const val ACTION_RECORD_CANCEL = "com.slideindex.app.action.STASH_AUDIO_RECORD_CANCEL"

        private const val TAG = "StashVoiceInput"
        private const val CHANNEL_ID = "stash_voice_input"
        private const val CHANNEL_ID_RECORD = "stash_audio_record"
        private const val NOTIFY_ID = 24_200
        private const val NOTIFY_ID_RECORD = 24_201

        /** 录音的暂存目录（与选图的 `stash_composer_images` 同一套做法：先落 cache，存条目时才搬进仓库）。 */
        private const val RECORD_CACHE_DIR_NAME = "stash_composer_audio"

        /** 单声道 44.1kHz / 64kbps：语音够用，5 分钟约 2.4MB。 */
        private const val RECORD_SAMPLE_RATE_HZ = 44_100
        private const val RECORD_BIT_RATE_BPS = 64_000

        /** 语音输入的麦克风权限是否已给（给 UI 判断要不要提示）。 */
        fun hasPermission(context: Context): Boolean =
            StashVoiceController.hasRecordAudioPermission(context)
    }
}
