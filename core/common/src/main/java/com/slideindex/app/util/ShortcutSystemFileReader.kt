package com.slideindex.app.util

import android.util.Base64
import android.util.Log
import java.io.File

/**
 * Reads ShortcutService persistence files (text XML or ABX) from disk or shell.
 */
object ShortcutSystemFileReader {
    private const val TAG = "ShortcutSystemFileReader"
    private const val ABX2XML_TMP_PREFIX = "slideindex_sc_"

    @Volatile
    private var abx2XmlProbeDone = false

    @Volatile
    private var abx2XmlShellAvailable = false

    fun isAbx2XmlShellAvailable(): Boolean = abx2XmlShellAvailable

    fun probeAbx2XmlShell(
        runner: (command: String, useRoot: Boolean, timeoutMs: Long) -> ShellReadResult,
        useRoot: Boolean,
    ): Boolean {
        if (abx2XmlProbeDone) return abx2XmlShellAvailable
        synchronized(this) {
            if (abx2XmlProbeDone) return abx2XmlShellAvailable
            val probes = listOf(
                "which abx2xml 2>/dev/null",
                "command -v abx2xml 2>/dev/null",
                "test -x /system/bin/abx2xml",
            )
            abx2XmlShellAvailable = probes.any { command ->
                val result = runner(command, useRoot, 3_000L)
                result.exitCode == 0 && result.output.isNotBlank()
            }
            abx2XmlProbeDone = true
            Log.i(TAG, "abx2xmlShellAvailable=$abx2XmlShellAvailable root=$useRoot")
        }
        return abx2XmlShellAvailable
    }

    /**
     * Reads ShortcutService package XML as parseable text bytes.
     * On API 30+ OEM ROMs, converts ABX via [abx2xml] when framework binary XML is unavailable.
     */
    fun readShortcutXmlBytes(
        path: String,
        runner: (command: String, useRoot: Boolean, timeoutMs: Long) -> ShellReadResult,
        useRoot: Boolean,
        timeoutMs: Long,
    ): ByteArray? {
        if (
            !AbxXmlParser.isBinaryXmlSupported() &&
            probeAbx2XmlShell(runner, useRoot)
        ) {
            readTextXmlViaAbx2XmlShell(path, runner, useRoot, timeoutMs)?.let { return it }
        }
        readBytesFromDisk(path)?.let { raw ->
            return normalizeShortcutXmlBytes(raw, path, runner, useRoot, timeoutMs)
        }
        readBytesViaShellBase64(path, runner, useRoot, timeoutMs)?.let { raw ->
            return normalizeShortcutXmlBytes(raw, path, runner, useRoot, timeoutMs)
        }
        if (probeAbx2XmlShell(runner, useRoot)) {
            return readTextXmlViaAbx2XmlShell(path, runner, useRoot, timeoutMs)
        }
        return null
    }

    private fun normalizeShortcutXmlBytes(
        raw: ByteArray,
        path: String,
        runner: (command: String, useRoot: Boolean, timeoutMs: Long) -> ShellReadResult,
        useRoot: Boolean,
        timeoutMs: Long,
    ): ByteArray? {
        if (!AbxXmlParser.isAbxMagic(raw)) {
            return raw.takeIf { AbxXmlParser.looksLikeTextXml(raw.decodeToString()) }
        }
        if (AbxXmlParser.isBinaryXmlSupported()) return raw
        if (probeAbx2XmlShell(runner, useRoot)) {
            return readTextXmlViaAbx2XmlShell(path, runner, useRoot, timeoutMs)
        }
        return null
    }

    private fun readTextXmlViaAbx2XmlShell(
        sourcePath: String,
        runner: (command: String, useRoot: Boolean, timeoutMs: Long) -> ShellReadResult,
        useRoot: Boolean,
        timeoutMs: Long,
    ): ByteArray? {
        val fileName = sourcePath.substringAfterLast('/')
        val tmpPath = "/data/local/tmp/$ABX2XML_TMP_PREFIX$fileName"
        val quotedSrc = shellQuote(sourcePath)
        val quotedTmp = shellQuote(tmpPath)
        val commands = listOf(
            "abx2xml $quotedSrc $quotedTmp && cat $quotedTmp && rm -f $quotedTmp",
            "/system/bin/abx2xml $quotedSrc $quotedTmp && cat $quotedTmp && rm -f $quotedTmp",
        )
        for (command in commands) {
            val result = runner(command, useRoot, timeoutMs)
            if (result.exitCode != 0 || result.output.isBlank()) continue
            val text = result.output.trim()
            if (AbxXmlParser.looksLikeTextXml(text)) {
                return text.toByteArray(Charsets.UTF_8)
            }
        }
        return null
    }

    fun readBytesFromDisk(path: String): ByteArray? {
        return runCatching {
            val file = File(path)
            if (!file.isFile || !file.canRead()) return null
            file.readBytes().takeIf { it.isNotEmpty() }
        }.onFailure { error ->
            Log.w(TAG, "readBytesFromDisk($path) failed", error)
        }.getOrNull()
    }

    fun readBytesViaShellBase64(
        path: String,
        runner: (command: String, useRoot: Boolean, timeoutMs: Long) -> ShellReadResult,
        useRoot: Boolean,
        timeoutMs: Long,
    ): ByteArray? {
        val quoted = shellQuote(path)
        val attempts = listOf(
            "base64 -w 0 $quoted",
            "base64 $quoted",
            "toybox base64 -w 0 $quoted",
            "cat $quoted | base64 -w 0",
        )
        for (command in attempts) {
            val result = runner(command, useRoot, timeoutMs)
            if (result.exitCode != 0 || result.output.isBlank()) continue
            val decoded = runCatching {
                Base64.decode(result.output.trim(), Base64.DEFAULT)
            }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                return decoded
            }
        }
        return null
    }

    fun listPackageXmlFilesFromDisk(): List<String> {
        val dir = File(ShortcutSystemXmlParser.SHORTCUT_PACKAGES_DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".xml", ignoreCase = true) }
            ?.map { it.absolutePath }
            .orEmpty()
            .sorted()
    }

    fun listPackageXmlFilesViaShell(
        runner: (command: String, useRoot: Boolean, timeoutMs: Long) -> ShellReadResult,
        useRoot: Boolean,
    ): List<String> {
        val dir = ShortcutSystemXmlParser.SHORTCUT_PACKAGES_DIR
        val result = runner("ls -1 $dir/*.xml 2>/dev/null", useRoot, 8_000L)
        if (result.exitCode != 0 || result.output.isBlank()) return emptyList()
        return result.output.lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(".xml", ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()
    }

    fun packageNameFromPath(path: String): String =
        path.substringAfterLast('/').removeSuffix(".xml")

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    data class ShellReadResult(
        val exitCode: Int,
        val output: String,
    )
}
