package com.slideindex.app.util

import android.content.Context
import android.os.SystemClock
import com.slideindex.app.diagnostic.EdgeDiag
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * 亮度 / 音量的连续调节控制器。
 *
 * 两条写入路径都必须是「主线程只更新 UI 状态、真正的系统写入丢到单线程 worker 上合并」：
 * - 音量：绝对设置被 ColorOS 拒时会退化成多次 `adjustStreamVolume`，一次 MOVE 十几轮 binder
 *   就能把主线程钉死（旧实现 253 次/秒，Choreographer 一次丢 459 帧）；
 * - 亮度：只走 Shizuku shell 时单次写入是百毫秒量级，写一次就卡一帧。
 *
 * 合并策略统一为「最新值优先」：手势期间中间值可以丢，最后一个值绝不能丢。
 */
class ContinuousAdjustController(
    private val context: Context,
    private val overlayBrightness: OverlayBrightnessControl?,
) {
    enum class Mode {
        VOLUME,
        BRIGHTNESS,
    }

    private data class BrightnessRequest(val fraction: Float, val commitAutoAdj: Boolean)

    private val appContext = context.applicationContext

    private var activeMode: Mode? = null
    private var anchorRawY = Float.NaN
    private var baselineFraction = 0f
    private var lastFraction = Float.NaN
    private var lastCommittedFraction = Float.NaN
    private var lastWriteWasDuringGesture = false

    /** 亮度写入：单线程 + 最新值优先。 */
    private val brightnessWriteExecutor = Executors.newSingleThreadExecutor()
    private val pendingBrightness = AtomicReference<BrightnessRequest?>(null)
    private val brightnessWriteRunning = AtomicBoolean(false)
    private var lastBrightnessWriteAtMs = 0L

    /** 上一次走 shell 旁路（兼容通道）的时刻：按 100ms 节奏下发，不给常驻会话塞满。 */
    private var lastShellPreviewAtMs = 0L

    /** 音量写入：单线程 + 最新值优先（这是把主线程从 binder 里救出来的关键）。 */
    private val volumeWriteExecutor = Executors.newSingleThreadExecutor()
    private val pendingVolumeFraction = AtomicReference<Float?>(null)
    private val volumeWriteRunning = AtomicBoolean(false)
    private var lastVolumeTraceAtMs = 0L

    private var gestureStartedAtMs = 0L

    fun begin(mode: Mode, rawY: Float): Boolean {
        if (!hasAccess(mode)) return false
        if (activeMode != mode) {
            activeMode = mode
            anchorRawY = rawY
            lastCommittedFraction = Float.NaN
            baselineFraction = readCurrentFraction(mode)
            lastFraction = baselineFraction
            gestureStartedAtMs = SystemClock.uptimeMillis()
            lastVolumeTraceAtMs = gestureStartedAtMs
            EdgeDiag.log(
                "adjust",
                "begin mode=$mode rawY=$rawY baseline=${"%.3f".format(baselineFraction)} " +
                    "scale=${describeScale(mode)}"
            )
        }
        return true
    }

    fun update(mode: Mode, rawY: Float) {
        if (activeMode != mode) return
        applyFraction(mode, fractionFor(rawY))
    }

    fun applyOnce(mode: Mode, anchorRawY: Float, targetRawY: Float): Float? {
        if (!begin(mode, anchorRawY)) return null
        update(mode, targetRawY)
        val fraction = currentFraction()
        end()
        return fraction
    }

    fun end() {
        val mode = activeMode ?: return
        val fraction = lastFraction
        activeMode = null
        anchorRawY = Float.NaN
        lastFraction = Float.NaN
        lastCommittedFraction = Float.NaN
        if (mode == Mode.BRIGHTNESS && !fraction.isNaN()) {
            flushBrightnessCommit(fraction)
        } else {
            flushVolumeTrace()
            clearBrightnessPreview()
        }
        EdgeDiag.log(
            "adjust",
            "end mode=$mode fraction=${if (fraction.isNaN()) "-" else "%.3f".format(fraction)} " +
                "duration=${SystemClock.uptimeMillis() - gestureStartedAtMs}ms"
        )
    }

    fun readCurrentFraction(mode: Mode): Float = when (mode) {
        Mode.VOLUME -> VolumeControlHelper.readFraction(appContext, VolumeControlHelper.Stream.MEDIA)
        Mode.BRIGHTNESS -> BrightnessControlHelper.readBrightnessFraction(appContext)
    }

    fun clearBrightnessPreview() {
        overlayBrightness?.apply(null)
    }

    fun currentMode(): Mode? = activeMode

    fun currentFraction(): Float =
        if (lastFraction.isNaN()) 0f else lastFraction.coerceIn(0f, 1f)

    fun setFraction(mode: Mode, fraction: Float, previewOnly: Boolean = false) {
        if (!hasAccess(mode)) return
        val clamped = fraction.coerceIn(0f, 1f)
        lastFraction = clamped
        when (mode) {
            Mode.VOLUME -> queueVolumeWrite(clamped)
            Mode.BRIGHTNESS -> {
                if (!BrightnessControlHelper.hasAccess(appContext)) return
                if (previewOnly) {
                    queueBrightnessWrite(BrightnessRequest(clamped, commitAutoAdj = false))
                } else {
                    flushBrightnessCommit(clamped)
                }
            }
        }
    }

    private fun hasAccess(mode: Mode): Boolean = when (mode) {
        Mode.VOLUME -> VolumeControlHelper.hasAccess(appContext)
        Mode.BRIGHTNESS -> BrightnessControlHelper.hasAccess(appContext)
    }

    private fun describeScale(mode: Mode): String = when (mode) {
        Mode.VOLUME -> runCatching {
            val manager = appContext.getSystemService(android.media.AudioManager::class.java)
            if (manager == null) "audio=null"
            else VolumeControlHelper.describeScale(manager, android.media.AudioManager.STREAM_MUSIC)
        }.getOrElse { "audio=err:${it.javaClass.simpleName}" }

        Mode.BRIGHTNESS -> BrightnessControlHelper.describeScale(appContext)
    }

    private fun fractionFor(rawY: Float): Float {
        if (anchorRawY.isNaN()) return baselineFraction
        val span = appContext.resources.displayMetrics.heightPixels
            .coerceAtLeast(1) * DRAG_SPAN_SCREEN_FRACTION
        return (baselineFraction + (anchorRawY - rawY) / span).coerceIn(0f, 1f)
    }

    private fun applyFraction(mode: Mode, fraction: Float) {
        lastFraction = fraction.coerceIn(0f, 1f)
        when (mode) {
            Mode.VOLUME -> queueVolumeWrite(lastFraction)
            Mode.BRIGHTNESS -> queueBrightnessWrite(
                BrightnessRequest(lastFraction, commitAutoAdj = false)
            )
        }
    }

    // ---------------------------------------------------------------- 音量

    private fun queueVolumeWrite(fraction: Float) {
        if (!VolumeControlHelper.hasAccess(appContext)) return
        pendingVolumeFraction.set(fraction)
        if (volumeWriteRunning.compareAndSet(false, true)) {
            runCatching { volumeWriteExecutor.execute(::drainVolumeWrites) }
                .onFailure { volumeWriteRunning.set(false) }
        }
    }

    private fun drainVolumeWrites() {
        try {
            while (true) {
                val fraction = pendingVolumeFraction.getAndSet(null) ?: break
                runCatching {
                    VolumeControlHelper.setFraction(
                        appContext,
                        VolumeControlHelper.Stream.MEDIA,
                        fraction,
                    )
                }.onFailure {
                    EdgeDiag.log("volume", "写入抛异常 ${it.javaClass.simpleName}: ${it.message}")
                }
                maybeFlushVolumeTrace()
                if (pendingVolumeFraction.get() == null) break
            }
        } finally {
            volumeWriteRunning.set(false)
            val leftover = pendingVolumeFraction.get()
            if (leftover != null && volumeWriteRunning.compareAndSet(false, true)) {
                runCatching { volumeWriteExecutor.execute(::drainVolumeWrites) }
                    .onFailure { volumeWriteRunning.set(false) }
            }
        }
    }

    private fun maybeFlushVolumeTrace() {
        val now = SystemClock.uptimeMillis()
        if (now - lastVolumeTraceAtMs < VOLUME_TRACE_INTERVAL_MS) return
        lastVolumeTraceAtMs = now
        VolumeStepStats.take()?.let { EdgeDiag.log("volume", it) }
    }

    private fun flushVolumeTrace() {
        VolumeStepStats.take()?.let { EdgeDiag.log("volume", "手势汇总 $it") }
    }

    // ---------------------------------------------------------------- 亮度

    private fun queueBrightnessWrite(request: BrightnessRequest) {
        if (!BrightnessControlHelper.hasAccess(appContext)) return
        pendingBrightness.set(request)
        if (brightnessWriteRunning.compareAndSet(false, true)) {
            runCatching { brightnessWriteExecutor.execute(::drainBrightnessWrites) }
                .onFailure { brightnessWriteRunning.set(false) }
        }
    }

    /** 松手提交：排在所有预览值之后，且不会被中间值覆盖。 */
    private fun flushBrightnessCommit(fraction: Float) {
        if (!BrightnessControlHelper.hasAccess(appContext)) {
            clearBrightnessPreview()
            return
        }
        queueBrightnessWrite(BrightnessRequest(fraction.coerceIn(0f, 1f), commitAutoAdj = true))
        android.os.Handler(android.os.Looper.getMainLooper()).post { clearBrightnessPreview() }
    }

    private fun drainBrightnessWrites() {
        try {
            while (true) {
                val request = pendingBrightness.getAndSet(null) ?: break
                // 合并：还有更新请求时直接用它（中间值可以丢，最后一个不能丢）
                val latest = pendingBrightness.getAndSet(null) ?: request
                // 手势中的预览值按最小间隔节流：shell 单次是百毫秒级，写太密只会把 worker 塞满，
                // 松手提交（commitAutoAdj=true）不受节流，必须立刻落地。
                if (!latest.commitAutoAdj) throttlePreviewWrite()
                writeSystemBrightness(latest.fraction, latest.commitAutoAdj)
                lastBrightnessWriteAtMs = SystemClock.uptimeMillis()
                if (pendingBrightness.get() == null) break
            }
        } finally {
            brightnessWriteRunning.set(false)
            val leftover = pendingBrightness.get()
            if (leftover != null && brightnessWriteRunning.compareAndSet(false, true)) {
                runCatching { brightnessWriteExecutor.execute(::drainBrightnessWrites) }
                    .onFailure { brightnessWriteRunning.set(false) }
            }
        }
    }

    /** 只在 worker 线程里 sleep，不碰主线程。 */
    private fun throttlePreviewWrite() {
        val now = SystemClock.uptimeMillis()
        val wait = PREVIEW_WRITE_MIN_INTERVAL_MS - (now - lastBrightnessWriteAtMs)
        if (wait in 1..PREVIEW_WRITE_MIN_INTERVAL_MS) {
            runCatching { Thread.sleep(wait) }
        }
    }

    private fun writeSystemBrightness(fraction: Float, commitAutoAdj: Boolean) {
        if (!BrightnessControlHelper.hasAccess(appContext)) return
        val clamped = fraction.coerceIn(0f, 1f)
        if (!commitAutoAdj && !lastCommittedFraction.isNaN() &&
            abs(lastCommittedFraction - clamped) < FRACTION_DEDUP_EPSILON &&
            lastWriteWasDuringGesture
        ) {
            return
        }
        val startedAt = SystemClock.uptimeMillis()
        val duringGesture = !commitAutoAdj
        // 双写：Settings 每帧都写；shell 按 100ms 节奏旁路写一次（ColorOS 只认 shell 身份）。
        val includeShell = commitAutoAdj ||
            (SystemClock.uptimeMillis() - lastShellPreviewAtMs >= SHELL_PREVIEW_INTERVAL_MS)
        val synced = BrightnessControlHelper.writeBrightnessFraction(
            appContext,
            clamped,
            duringGesture = duringGesture,
            commitAutoAdj = commitAutoAdj,
            includeShell = includeShell,
        )
        if (includeShell) lastShellPreviewAtMs = SystemClock.uptimeMillis()
        val cost = SystemClock.uptimeMillis() - startedAt
        if (synced) {
            lastCommittedFraction = clamped
            lastWriteWasDuringGesture = duringGesture
        }
        EdgeDiag.log(
            "brightness",
            "write fraction=${"%.3f".format(clamped)} synced=$synced " +
                "duringGesture=$duringGesture commitAdj=$commitAutoAdj shell=$includeShell " +
                "cost=${cost}ms path=${BrightnessControlHelper.lastWritePath}"
        )
    }

    private companion object {
        private const val DRAG_SPAN_SCREEN_FRACTION = 0.5f
        private const val VOLUME_TRACE_INTERVAL_MS = 500L
        private const val FRACTION_DEDUP_EPSILON = 0.004f

        /** 预览写入的最小间隔：Settings 快路 1~3ms，30Hz 足够跟手且不浪费。 */
        private const val PREVIEW_WRITE_MIN_INTERVAL_MS = 33L

        /** shell 旁路（兼容通道）的下发节奏：5~20ms 一次会话写，10Hz 已够 ColorOS 生效。 */
        private const val SHELL_PREVIEW_INTERVAL_MS = 100L
    }
}
