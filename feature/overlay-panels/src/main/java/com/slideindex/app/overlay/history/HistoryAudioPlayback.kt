package com.slideindex.app.overlay.history

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 语音块的**单例播放器**（§0.16.21）。
 *
 * 为什么是单例而不是每个块一个 `MediaPlayer`：
 * ① 「同一时刻只该有一条在响」是产品要求，也是用户预期 —— 多条一起响是纯 bug；
 * ② 播放器要能被"切条目 / 关面板 / 开始录音"这些**本组件之外**的事件统一停掉，
 *    分散在每块里的实例没法被外部叫停。
 * 所以这里持有唯一的 `MediaPlayer`，并对外暴露 [playingPath]（**哪一条在放**）。
 *
 * 用 `MediaPlayer` 而不是 ExoPlayer：本仓库不许为了这个功能引新依赖，而本地 m4a 的
 * `setDataSource(path)` + `prepare()` + `start()` 就是 `MediaPlayer` 最拿手的场景。
 *
 * ⚠️ 所有方法都必须在**主线程**调用（`MediaPlayer` 的既有约束，也是这些调用点的实际情况：
 * 全部来自 Compose 的点击回调）。
 */
object HistoryAudioPlayback {
    private const val TAG = "HistoryAudioPlayback"

    /** 进度轮询间隔：200ms 足够让"播放中"看起来是连续走动的，又不至于每帧写状态。 */
    private const val TickIntervalMs = 200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var tickJob: Job? = null

    /**
     * "第几次播放"的发号器（§0.16.22）。
     *
     * 为什么必须有它：MediaPlayer 的 OnCompletion / OnError 回调**不在主线程**上跑
     * （framework 在自己的 handler 线程投递），而本对象的状态又必须由主线程改。于是那两个
     * 回调都只做一件事 —— 把"播完了/出错了"**投回主线程**。投递是异步的，等它跑到时用户
     * 完全可能已经点了**另一条**音频：拿一个陈旧的收尾去停掉新播放，就是"刚点开就没了"。
     * 每次 [toggle] 递增它、收尾回调带上自己那一次的号，号对不上就直接丢弃。
     */
    private var playbackGeneration = 0

    /**
     * 本次播放的**失败提示**（调用方在一次 `toggle` 里给的 lambda）。
     *
     * 为什么要存下来而不是当一个参数用掉就算：出错回调是**异步**的（见
     * [finishPlayback]），那时 `toggle` 早就返回了；存着的这一份才是"该由谁提示"。
     * [stop] 会一并清掉（停了就不该再弹上次那次播放的错）。
     */
    private var onErrorCallback: (() -> Unit)? = null

    /** 正在播放的那份音频的**绝对路径**；null = 没在放。UI 靠它画"当前播放的是哪条"。 */
    var playingPath by mutableStateOf<String?>(null)
        private set

    /** 当前播放位置（毫秒），由 [TickIntervalMs] 的心跳推进。 */
    var positionMs by mutableStateOf(0L)
        private set

    fun isPlaying(path: String): Boolean = playingPath != null && playingPath == path

    /**
     * 点一下语音块：**在放就停，没在放就从头放**（切换 = 先停旧的，所以不会两条一起响）。
     *
     * [onError] 在"文件不在 / 解不开 / 播到一半出错"时回调（UI 自己决定怎么提示）。
     */
    fun toggle(path: String, onError: () -> Unit) {
        if (path.isBlank() || !File(path).exists()) {
            stop()
            onError()
            return
        }
        if (isPlaying(path)) {
            stop()
            return
        }
        stop()
        // 本轮的号码在**建播放器之前**取：两个回调都拿它做"我是不是已经过期了"的判据。
        val generation = ++playbackGeneration
        // ⚠️ 失败提示也要在建播放器**之前**挂上：`prepare()` / `start()` 失败会当场（同步）
        // 触发 OnError，晚一步赋值的话那次错误就没人提示了。
        onErrorCallback = onError
        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(path)
                // ⚠️ 播完**必须**回主线程收尾（§0.16.22）：这个回调由 framework 在自己的线程上
                // 投递，直接在回调里改 `playingPath` / `positionMs` 是"跨线程写 Compose 状态"
                // —— 状态可能写进去了但这一帧的重组通知丢了，用户看到的就是**播完了还显示"播放中"**
                // （按钮不回位、进度条停在末尾、再点一次不从头播）。所以回调只负责投递。
                setOnCompletionListener {
                    finishPlayback(generation, error = null)
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "playback error what=$what extra=$extra path=$path")
                    // 返回值 true = 这个错误我们认领了（不要再往上层抛）。收尾同样回主线程。
                    finishPlayback(generation, error = what)
                    true
                }
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "prepare/start failed: $path", it) }.getOrNull()
        if (created == null) {
            stop()
            onError()
            return
        }
        player = created
        playingPath = path
        positionMs = 0L
        tickJob = scope.launch {
            while (true) {
                delay(TickIntervalMs)
                val current = player ?: break
                positionMs = runCatching { current.currentPosition.toLong() }.getOrDefault(0L)
            }
        }
    }

    /**
     * 播放结束 / 出错的**统一收尾**（§0.16.22）：清 `playingPath` / 进度 / 心跳，
     * 让胶囊回到「▶ duration」；出错时再额外叫一次失败提示。
     *
     * 只在主线程跑（[MediaPlayer] 的两个回调都投递到这里），并且**号码对不上就丢弃**
     * —— 投递是异步的，等它跑到时用户可能已经点了另一条音频：[playbackGeneration] 那一层
     * 就是为这件事存在的（旧收尾绝不能停掉新播放）。
     *
     * @param error `what`（`MediaPlayer.MEDIA_ERROR_*`）；null = 正常播完。
     */
    private fun finishPlayback(generation: Int, error: Int?) {
        scope.launch {
            if (generation != playbackGeneration) return@launch
            val notifyError = onErrorCallback
            stop()
            if (error != null) {
                Log.w(TAG, "playback finished with error what=$error")
                notifyError?.invoke()
            }
        }
    }

    /** 停掉当前播放（没有在放时是空操作）。切条目 / 关面板 / 开始录音都调它。 */
    fun stop() {
        tickJob?.cancel()
        tickJob = null
        val current = player
        player = null
        playingPath = null
        positionMs = 0L
        onErrorCallback = null
        if (current != null) {
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
    }
}
