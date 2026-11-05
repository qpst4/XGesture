package com.slideindex.app.shortcut.port

import android.util.Base64

/**
 * C path: one root shell command that reads every ShortcutService package XML
 * as raw bytes and returns them base64-encoded with per-file markers.
 */
object ShortcutRootBatchReader {

    private const val PACKAGES_DIR = "/data/system_ce/0/shortcut_service/packages"

    fun readAll(rootShell: RootShell): Map<String, ByteArray> {
        val command = """
            for f in $PACKAGES_DIR/*.xml; do
              [ -f "${'$'}f" ] || continue
              printf '\n@@SCFILE:%s@@\n' "$(basename "${'$'}f")"
              base64 "${'$'}f"
            done
        """.trimIndent()
        val output = rootShell.executeLarge(command, timeoutSec = 120L)
        return decode(output)
    }

    /** Read only one package; used by execution-time refresh. */
    fun readPackage(rootShell: RootShell, packageName: String): ByteArray? {
        if (packageName.isBlank() || packageName.contains('/')) return null
        val command = "base64 ${shellQuote("$PACKAGES_DIR/$packageName.xml")}"
        val output = rootShell.executeLarge(command, timeoutSec = 30L)
        if (output.isBlank()) return null
        val payload = output.filterNot { it.isWhitespace() }
        if (payload.isEmpty()) return null
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun decode(output: String): Map<String, ByteArray> {
        val marker = Regex("""@@SCFILE:([^@\r\n]+)@@""")
        val matches = marker.findAll(output).toList()
        if (matches.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, ByteArray>()
        for (index in matches.indices) {
            val match = matches[index]
            val fileName = match.groupValues[1].trim()
            if (fileName.isEmpty()) continue
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else output.length
            if (start >= end) continue
            val payload = output.substring(start, end).filterNot { it.isWhitespace() }
            if (payload.isEmpty()) continue
            val bytes = runCatching {
                Base64.decode(payload, Base64.DEFAULT)
            }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            result[fileName.removeSuffix(".xml")] = bytes
        }
        return result
    }
}
