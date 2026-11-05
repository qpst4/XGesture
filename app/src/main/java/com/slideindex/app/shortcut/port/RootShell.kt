package com.slideindex.app.shortcut.port

import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * Persistent root shell for reading and executing shortcut commands.
 */
class RootShell {

    private var process: Process? = null
    private var writer: BufferedWriter? = null

    fun openShell(): Boolean {
        return try {
            val builder = ProcessBuilder("su")
            builder.redirectErrorStream(true)
            process = builder.start()
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            true
        } catch (error: Exception) {
            error.printStackTrace()
            false
        }
    }

    fun execute(cmd: String, timeoutSec: Long = 10L): String {
        val current = process ?: return ""
        val currentWriter = writer ?: return ""
        val output = ByteArrayOutputStream()
        val input: InputStream = current.inputStream
        return try {
            val marker = "--EOF--" + System.currentTimeMillis()
            currentWriter.write(cmd + "\n")
            currentWriter.write("echo $marker\n")
            currentWriter.flush()

            val buffer = ByteArray(1024)
            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec)
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val read = input.read(buffer)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        if (output.toString().contains(marker)) break
                    }
                } else {
                    Thread.sleep(50L)
                }
            }
            output.toString().replaceAfter(marker, "").replace(marker, "").trim()
        } catch (error: Exception) {
            error.printStackTrace()
            ""
        }
    }

    /**
     * C path: large, binary-safe-ish read. The output is base64 text, but it can
     * be several MB, so the marker is checked against a rolling tail instead of
     * copying the whole accumulated output on every chunk.
     */
    fun executeLarge(cmd: String, timeoutSec: Long = 120L): String {
        val current = process ?: return ""
        val currentWriter = writer ?: return ""
        val output = ByteArrayOutputStream()
        val input: InputStream = current.inputStream
        return try {
            val marker = "--EOF-LARGE--" + System.nanoTime()
            val markerBytes = marker.toByteArray(Charsets.US_ASCII)
            currentWriter.write(cmd + "\n")
            currentWriter.write("echo $marker\n")
            currentWriter.flush()

            val buffer = ByteArray(8192)
            var tail = ByteArray(0)
            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec)
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val read = input.read(buffer)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        val chunk = buffer.copyOf(read)
                        val combined = tail + chunk
                        if (combined.containsSequence(markerBytes)) break
                        tail = if (combined.size > markerBytes.size - 1) {
                            combined.copyOfRange(combined.size - (markerBytes.size - 1), combined.size)
                        } else {
                            combined
                        }
                    }
                } else {
                    Thread.sleep(10L)
                }
            }
            output.toString().replaceAfter(marker, "").replace(marker, "").trim()
        } catch (error: Exception) {
            error.printStackTrace()
            ""
        }
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        for (i in 0..(size - needle.size)) {
            var matches = true
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    fun close(): Boolean {
        return try {
            writer?.write("exit\n")
            writer?.flush()
            process?.destroy()
            writer?.close()
            process = null
            writer = null
            true
        } catch (error: Exception) {
            false
        }
    }
}
