package com.slideindex.app.shizuku

import android.os.SystemClock
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * shell 身份的**常驻 sh 会话**（运行在 Shizuku UserService 进程里）。
 *
 * 为什么需要：UserService 本来就是一个常驻进程，但之前每条命令都走
 * `sh -c "..."` 起一个新进程 —— 真机实测单次 ~100ms。亮度连续调节一秒只能写 10 次，
 * 用户看到的就是"一格一格的阶梯"。
 *
 * 这里把一个 `sh` 留在进程里，命令写进它的 stdin、用起止标记读回输出与退出码：
 * 单次降到几毫秒，同样一秒能写 30 次以上。协议与 [RootShellSession] 完全一致
 * （那一份已经在 Root 模式跑了很久，坑都趟过了）。
 *
 * 资源约束：
 * - 空闲 [IDLE_TIMEOUT_MS] 后自动关闭会话（下次用到再拉起），不常驻占内存；
 * - 会话异常（超时/进程死掉）时退回一次性执行，绝不把命令丢掉。
 */
internal object PrivilegedShellSession {

    private const val TAG = "PrivilegedShellSession"

    /** 空闲这么久就关掉 sh（不占资源；下次用到自动重开）。 */
    private const val IDLE_TIMEOUT_MS = 5 * 60_000L

    private val lock = Any()

    private var process: java.lang.Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var lastUsedAtMs = 0L

    private var watchdog: ScheduledExecutorService? = null
    private var pendingIdleClose: ScheduledFuture<*>? = null

    fun run(command: String, timeoutMs: Long): TaskManagerShellExecutor.ShellExecResult {
        synchronized(lock) {
            if (!ensureOpen()) {
                return runOneShot(command, timeoutMs)
            }
            lastUsedAtMs = SystemClock.elapsedRealtime()
            val result = execOnSession(command, timeoutMs)
            if (result.exitCode == -1 &&
                (result.output.contains("timed out") || result.output.contains("session dead"))
            ) {
                Log.w(TAG, "session unhealthy, fallback one-shot: ${result.output.take(60)}")
                destroySession()
                return runOneShot(command, timeoutMs)
            }
            scheduleIdleClose()
            return result
        }
    }

    fun close() {
        synchronized(lock) {
            pendingIdleClose?.cancel(false)
            pendingIdleClose = null
            destroySession()
        }
    }

    private fun runOneShot(command: String, timeoutMs: Long): TaskManagerShellExecutor.ShellExecResult =
        TaskManagerShellExecutor.shellCommandWithOutput(
            timeoutMs,
            *TaskManagerShellExecutor.buildPlainShellArgs(command),
        )

    private fun ensureOpen(): Boolean {
        val alive = process?.isAlive == true && writer != null && reader != null
        if (alive) return true
        destroySession()
        return openSession()
    }

    private fun openSession(): Boolean = runCatching {
        val sh = TaskManagerShellExecutor.resolveShPath()
        val proc = ProcessBuilder(listOf(sh))
            .redirectErrorStream(true)
            .directory(File("/"))
            .apply {
                environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/product/bin"
            }
            .start()
        val outputWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
        val inputReader = proc.inputStream.bufferedReader()
        process = proc
        writer = outputWriter
        reader = inputReader
        // 探活：能跑通一条最简单的命令才算会话可用（避免把坏会话当成好的用）。
        val probe = execOnSession("echo ok", 2_500L)
        if (probe.exitCode != 0 || !probe.output.contains("ok")) {
            Log.w(TAG, "shell session probe failed exit=${probe.exitCode} out=${probe.output.take(60)}")
            destroySession()
            return false
        }
        lastUsedAtMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "shell session opened")
        true
    }.getOrElse { error ->
        Log.e(TAG, "openSession failed", error)
        destroySession()
        false
    }

    private fun execOnSession(
        command: String,
        timeoutMs: Long,
    ): TaskManagerShellExecutor.ShellExecResult {
        val sessionWriter = writer
            ?: return TaskManagerShellExecutor.ShellExecResult(-1, "session dead")
        val sessionReader = reader
            ?: return TaskManagerShellExecutor.ShellExecResult(-1, "session dead")
        val token = UUID.randomUUID().toString().replace("-", "")
        val startMarker = "SI_S_$token"
        val endMarker = "SI_E_$token"
        return runCatching {
            sessionWriter.write("echo $startMarker\n")
            sessionWriter.write("$command\n")
            sessionWriter.write("echo $endMarker:\$?\n")
            sessionWriter.flush()

            val output = StringBuilder()
            var exitCode = -1
            var started = false
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!sessionReader.ready()) {
                    if (process?.isAlive != true) {
                        return TaskManagerShellExecutor.ShellExecResult(-1, "session dead")
                    }
                    Thread.sleep(2L)
                    continue
                }
                val line = sessionReader.readLine() ?: break
                when {
                    line == startMarker -> started = true
                    line.startsWith(endMarker) -> {
                        exitCode = line.removePrefix("$endMarker:").toIntOrNull() ?: -1
                        break
                    }
                    started -> {
                        if (output.isNotEmpty()) output.append('\n')
                        output.append(line)
                    }
                }
            }
            if (!started || (exitCode == -1 && SystemClock.elapsedRealtime() >= deadline)) {
                return TaskManagerShellExecutor.ShellExecResult(-1, "session command timed out")
            }
            TaskManagerShellExecutor.ShellExecResult(exitCode, output.toString())
        }.getOrElse { error ->
            Log.w(TAG, "execOnSession failed", error)
            TaskManagerShellExecutor.ShellExecResult(-1, error.message ?: "session exec failed")
        }
    }

    /** 每次用完续一次：空闲到点就关，避免常驻进程白占内存。 */
    private fun scheduleIdleClose() {
        val executor = watchdog ?: Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "shell-session-idle").apply { isDaemon = true }
        }.also { watchdog = it }
        pendingIdleClose?.cancel(false)
        pendingIdleClose = executor.schedule(
            {
                synchronized(lock) {
                    val idleMs = SystemClock.elapsedRealtime() - lastUsedAtMs
                    if (idleMs >= IDLE_TIMEOUT_MS && process != null) {
                        Log.i(TAG, "shell session idle ${idleMs}ms, closing")
                        destroySession()
                    }
                }
            },
            IDLE_TIMEOUT_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun destroySession() {
        runCatching { writer?.write("exit\n") }
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { process?.destroyForcibly() }
        runCatching { process?.waitFor(200, TimeUnit.MILLISECONDS) }
        writer = null
        reader = null
        process = null
    }
}
