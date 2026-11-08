package com.slideindex.app.shizuku

import android.os.SystemClock
import android.util.Log
import com.slideindex.app.privilege.root.RootNamespace
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Keeps one interactive root shell alive so Root mode does not spawn a new `su` per command.
 */
object RootShellSession {

    private const val TAG = "RootShellSession"

    private val lock = Any()

    private var process: java.lang.Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    fun run(command: String, timeoutMs: Long): TaskManagerShellExecutor.ShellExecResult {
        if (android.os.Process.myUid() == 0) {
            return TaskManagerShellExecutor.shellCommandWithOutput(
                timeoutMs,
                *TaskManagerShellExecutor.buildPlainShellArgs(command)
            )
        }
        synchronized(lock) {
            if (!ensureOpen()) {
                return runOneShot(command, timeoutMs)
            }
            val result = execOnSession(command, timeoutMs)
            if (result.exitCode == -1 &&
                (result.output.contains("timed out") || result.output.contains("session dead"))
            ) {
                destroySession()
                return runOneShot(command, timeoutMs)
            }
            return result
        }
    }

    fun close() {
        synchronized(lock) {
            destroySession()
        }
    }

    private fun ensureOpen(): Boolean {
        val alive = process?.isAlive == true && writer != null && reader != null
        if (alive) return true
        destroySession()
        return openSession()
    }

    private fun openSession(): Boolean {
        val sh = TaskManagerShellExecutor.resolveShPath()
        val globalLauncher = RootNamespace.shellLauncher(sh)
        if (tryOpenSession(globalLauncher)) return true
        // 没有 nsenter 的 ROM：尝试 su --mount-master（Magisk 系）。
        if (RootNamespace.mountMasterUsable() &&
            tryOpenSession(sh, suArgs = listOf("--mount-master"))
        ) {
            return true
        }
        // 最后退回普通 shell，保证 Root 模式本身仍可用。
        if (globalLauncher != sh) {
            return tryOpenSession(sh)
        }
        return false
    }

    private fun tryOpenSession(
        shellCommand: String,
        suArgs: List<String> = emptyList(),
    ): Boolean {
        return runCatching {
            val su = TaskManagerShellExecutor.resolveSuInvocation()
            val proc = ProcessBuilder(listOf(su) + suArgs)
                .redirectErrorStream(true)
                .directory(java.io.File("/"))
                .apply {
                    environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/product/bin"
                }
                .start()
            val outputWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
            val inputReader = proc.inputStream.bufferedReader()
            outputWriter.write("$shellCommand\n")
            outputWriter.flush()
            drainStartup(inputReader, 250L)
            process = proc
            writer = outputWriter
            reader = inputReader
            val probe = execOnSession("id -u", 2_500L)
            val uid = TaskManagerShellExecutor.parseNumericUid(probe.output)
            if (probe.exitCode != 0 || uid != 0) {
                Log.w(TAG, "root shell probe failed exit=${probe.exitCode} output=${probe.output.take(80)}")
                destroySession()
                return false
            }
            Log.i(TAG, "root shell session opened")
            true
        }.getOrElse { error ->
            Log.e(TAG, "openSession failed", error)
            destroySession()
            false
        }
    }

    private fun execOnSession(command: String, timeoutMs: Long): TaskManagerShellExecutor.ShellExecResult {
        val sessionWriter = writer ?: return TaskManagerShellExecutor.ShellExecResult(-1, "session dead")
        val sessionReader = reader ?: return TaskManagerShellExecutor.ShellExecResult(-1, "session dead")
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
                    Thread.sleep(5L)
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

    private fun runOneShot(command: String, timeoutMs: Long): TaskManagerShellExecutor.ShellExecResult {
        val su = TaskManagerShellExecutor.resolveSuInvocation()
        val sh = TaskManagerShellExecutor.resolveShPath()
        // 常驻 shell 没起来时也不能丢全局 namespace；用 nsenter 包一层再交给 su。
        val effective = if (RootNamespace.nsenterBinaryExists()) {
            "${RootNamespace.NSENTER_PATH} -t 1 -m -- $sh -c " +
                TaskManagerShellExecutor.shellQuote(command)
        } else {
            command
        }
        val q = TaskManagerShellExecutor.shellQuote(effective)
        val scripts = listOf(
            "$su -c $q",
            "$su 0 sh -c $q"
        )
        var last = TaskManagerShellExecutor.ShellExecResult(-1, "su 执行失败")
        for (script in scripts) {
            val result = TaskManagerShellExecutor.shellCommandWithOutput(timeoutMs, sh, "-c", script)
            last = result
            if (result.exitCode == 0) return result
        }
        return last
    }

    private fun drainStartup(reader: BufferedReader, maxWaitMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!reader.ready()) {
                Thread.sleep(5L)
                continue
            }
            reader.readLine() ?: break
        }
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
