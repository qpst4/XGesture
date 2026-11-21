package com.slideindex.app.dragpress

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 执行系统命令，支持设定超时与最大缓冲读取，管道异常时自动回收。
 */
internal object ProcessCommandRunner {
    fun run(command: List<String>, timeoutMs: Long = 4000): Pair<Int, String> {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "process-command-timeout").apply { isDaemon = true }
        }
        val timedOut = AtomicBoolean()
        val timer = watchdog.schedule({
            timedOut.set(true)
            process.destroy()
        }, timeoutMs, TimeUnit.MILLISECONDS)
        return try {
            process.outputStream.close()
            val output = StringBuilder()
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(2048)
                while (true) {
                    val size = reader.read(buffer)
                    if (size < 0) break
                    if (output.length < 48 * 1024) {
                        output.append(buffer, 0, size.coerceAtMost(48 * 1024 - output.length))
                    }
                }
            }
            val code = process.waitFor()
            (if (timedOut.get()) -1 else code) to output.toString()
        } finally {
            timer.cancel(false)
            watchdog.shutdownNow()
            process.destroy()
            runCatching { process.inputStream.close() }
        }
    }
}
