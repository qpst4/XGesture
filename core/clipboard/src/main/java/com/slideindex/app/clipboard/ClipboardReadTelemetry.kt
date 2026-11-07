package com.slideindex.app.clipboard

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 剪贴板读取的可观测性：内存计数 + 追加写入私有目录的滚动日志。
 *
 * 之所以落文件而不是只打 Log：部分 ROM（实测魅族 Flyme）几乎不把应用自身的 Log
 * 输出到 logcat，漏记排查只能看文件。位置：
 * `files/diagnostic/clipboard_read.log`（超过 [MAX_LOG_BYTES] 时轮转为 `.1`）。
 */
object ClipboardReadTelemetry {
    private const val TAG = "ClipboardReadTelemetry"
    private const val DIR_NAME = "diagnostic"
    private const val FILE_NAME = "clipboard_read.log"
    private const val MAX_LOG_BYTES = 256L * 1024L

    data class Stats(
        val readRequests: Long,
        val attempts: Long,
        val focusWins: Long,
        val successes: Long,
        val nullResults: Long,
        val probeUnavailable: Long,
    )

    private val readRequests = AtomicLong()
    private val attempts = AtomicLong()
    private val focusWins = AtomicLong()
    private val successes = AtomicLong()
    private val nullResults = AtomicLong()
    private val probeUnavailable = AtomicLong()

    private val lock = Any()

    @Volatile
    private var ioHandler: Handler? = null

    @Volatile
    private var logFile: File? = null

    fun onReadRequested() {
        readRequests.incrementAndGet()
    }

    /** 没有悬浮窗能力时的直接读取（不会走焦点探针）。 */
    fun onProbeUnavailable(success: Boolean) {
        probeUnavailable.incrementAndGet()
        recordResult(success)
    }

    /** 进应用 / 打开剪贴板面板时的兜底补读。 */
    fun onCatchUp(context: Context, source: String, success: Boolean) {
        recordResult(success)
        append(context, "catch-up source=$source result=${if (success) "ok" else "null"}")
    }

    fun onAttempt(
        context: Context,
        attempt: Int,
        maxAttempts: Int,
        reason: String,
        focusGained: Boolean,
        focusMs: Long,
        focusedAtRead: Boolean,
        success: Boolean,
        elapsedMs: Long,
    ) {
        attempts.incrementAndGet()
        if (focusGained) focusWins.incrementAndGet()
        recordResult(success)
        append(
            context,
            buildString {
                append("attempt=").append(attempt).append('/').append(maxAttempts)
                append(" reason=").append(reason)
                append(" focusedAtRead=").append(focusedAtRead)
                append(" focusGained=").append(focusGained)
                append(" focusMs=").append(focusMs)
                append(" result=").append(if (success) "ok" else "null")
                append(" elapsedMs=").append(elapsedMs)
            },
        )
    }

    fun snapshot(): Stats = Stats(
        readRequests = readRequests.get(),
        attempts = attempts.get(),
        focusWins = focusWins.get(),
        successes = successes.get(),
        nullResults = nullResults.get(),
        probeUnavailable = probeUnavailable.get(),
    )

    fun logFilePath(context: Context): String =
        File(File(context.filesDir, DIR_NAME), FILE_NAME).absolutePath

    private fun recordResult(success: Boolean) {
        if (success) successes.incrementAndGet() else nullResults.incrementAndGet()
    }

    private fun append(context: Context, line: String) {
        val appContext = context.applicationContext
        val handler = ensureHandler(appContext) ?: return
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        handler.post {
            runCatching {
                val file = logFile ?: return@runCatching
                if (file.length() > MAX_LOG_BYTES) {
                    val rotated = File(file.parentFile, "$FILE_NAME.1")
                    runCatching { rotated.delete() }
                    runCatching { file.renameTo(rotated) }
                }
                file.appendText("$stamp $line\n")
            }.onFailure { Log.w(TAG, "append clipboard read log failed: ${it.message}") }
        }
    }

    private fun ensureHandler(context: Context): Handler? = ioHandler ?: synchronized(lock) {
        ioHandler ?: runCatching {
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            logFile = File(dir, FILE_NAME)
            val thread = HandlerThread("ClipboardReadTelemetry").apply { start() }
            Handler(thread.looper).also { ioHandler = it }
        }.getOrNull()
    }
}
