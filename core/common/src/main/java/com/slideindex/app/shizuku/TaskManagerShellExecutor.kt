package com.slideindex.app.shizuku

import android.content.Context
import android.os.Process
import androidx.annotation.StringRes
import com.slideindex.app.common.R
import com.slideindex.app.shell.ShellRuntimeHooks

object TaskManagerShellExecutor {

    @Deprecated("模块侧不再持有资源 id")
    val SHELL_DOWNGRADE_HINT_RES: Int = 0
    const val SHELL_COMMAND_TIMEOUT_MS = 2_500L

    private const val SYSTEM_SH = "/system/bin/sh"
    private const val SYSTEM_SU = "/system/bin/su"

    data class ShellExecResult(val exitCode: Int, val output: String)

    fun shellDowngradeHint(context: Context): String = ShellRuntimeHooks.stringOf(context, "shell_downgrade_hint") ?: "已降级为 adb/shell 身份"

    fun shellCommand(vararg cmd: String): Boolean =
        shellCommandWithOutput(*cmd).exitCode == 0

    fun shellOutput(vararg cmd: String): String =
        shellCommandWithOutput(*cmd).output

    fun shellCommandWithOutput(vararg cmd: String): ShellExecResult =
        shellCommandWithOutput(SHELL_COMMAND_TIMEOUT_MS, *cmd)

    fun shellCommandWithOutput(timeoutMs: Long, vararg cmd: String): ShellExecResult {
        return runCatching {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .directory(java.io.File("/"))
                .apply {
                    environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/product/bin"
                }
                .start()
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ShellExecResult(-1, "Command timed out after ${timeoutMs / 1000}s")
            }
            val text = buildString {
                process.inputStream.bufferedReader().use { reader ->
                    val content = reader.readText()
                    if (content.isNotEmpty()) append(content.trimEnd())
                }
                if (isEmpty()) {
                    append("(no output, exit=${process.exitValue()})")
                }
            }
            process.destroy()
            ShellExecResult(process.exitValue(), text)
        }.getOrElse { error ->
            ShellExecResult(-1, error.message ?: "Execution failed")
        }
    }

    fun buildPlainShellArgs(command: String): Array<String> =
        arrayOf(resolveShPath(), "-c", command)

    fun runAsRootUser(
        command: String,
        timeoutMs: Long = SHELL_COMMAND_TIMEOUT_MS
    ): ShellExecResult {
        if (Process.myUid() == 0) {
            return shellCommandWithOutput(timeoutMs, *buildPlainShellArgs(command))
        }
        return RootShellSession.run(command, timeoutMs)
    }

    fun runPrivilegedCommand(
        command: String,
        timeoutMs: Long = SHELL_COMMAND_TIMEOUT_MS
    ): ShellExecResult {
        if (Process.myUid() == Process.SHELL_UID) {
            return shellCommandWithOutput(timeoutMs, *buildPlainShellArgs(command))
        }
        val root = runAsRootUser(command, timeoutMs)
        if (hasUsableOutput(root) || root.exitCode == 0) return root
        if (ShellRuntimeHooks.isRootMode()) return root
        val su = resolveSuInvocation()
        val q = shellQuote(command)
        val sh = resolveShPath()
        val asShell = runFirstSuccessfulShellScript(
            listOf(
                "$su shell -c $q",
                "$su 2000 $sh -c $q"
            ),
            timeoutMs,
            context = null
        )
        return if (hasUsableOutput(asShell)) asShell else root
    }

    fun hasUsableOutput(result: ShellExecResult): Boolean {
        if (result.exitCode != 0) return false
        val output = result.output.trim()
        if (output.isEmpty()) return false
        if (output.startsWith("Command timed out")) return false
        if (output.startsWith("(no output")) return false
        return true
    }

    /** Run [command] with adb/shell identity when root/Magisk is available. */
    fun runInShellIdentity(
        command: String,
        timeoutMs: Long = SHELL_COMMAND_TIMEOUT_MS,
    ): ShellExecResult {
        val sh = resolveShPath()
        val wrapper = findShellDowngradeWrapper()
        if (wrapper != null) {
            return shellCommandWithOutput(timeoutMs, sh, "-c", wrapper(command))
        }
        val su = resolveSuInvocation()
        val q = shellQuote(command)
        return runFirstSuccessfulShellScript(
            listOf(
                "$su shell -c $q",
                "$su 2000 $sh -c $q",
            ),
            timeoutMs,
            context = null,
        )
    }

    fun runAsShellUser(command: String, context: Context? = null): ShellExecResult {
        if (Process.myUid() != 0) {
            return shellCommandWithOutput(*buildPlainShellArgs(command))
        }
        val wrapper = findShellDowngradeWrapper()
            ?: return ShellExecResult(
                exitCode = -1,
                output = ShellRuntimeHooks.stringOf(context, "shell_downgrade_failed")
                    ?: "Cannot downgrade to adb/shell identity."
            )
        val result = shellCommandWithOutput(resolveShPath(), "-c", wrapper(command))
        val hint = context?.let(::shellDowngradeHint).orEmpty()
        return ShellExecResult(
            exitCode = result.exitCode,
            output = hint + result.output
        )
    }

    fun probeRootAvailable(): Boolean {
        return when {
            Process.myUid() == 0 -> true
            else -> {
                val result = RootShellSession.run("id -u", SHELL_COMMAND_TIMEOUT_MS)
                result.exitCode == 0 && parseNumericUid(result.output) == 0
            }
        }
    }

    fun formatShellOutput(exitCode: Int, output: String): String =
        "$exitCode\n---\n$output"

    fun resolveShPath(): String =
        if (java.io.File(SYSTEM_SH).exists()) SYSTEM_SH else "/bin/sh"

    fun resolveSuInvocation(): String {
        val candidates = listOf(
            "/sbin/su",
            "/system/xbin/su",
            SYSTEM_SU,
            "/vendor/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/magisk/magisk"
        )
        return candidates.firstOrNull { java.io.File(it).exists() } ?: "su"
    }

    fun shellQuote(command: String): String =
        "'" + command.replace("'", "'\\''") + "'"

    private fun findShellDowngradeWrapper(): ((String) -> String)? {
        val sh = resolveShPath()
        val su = resolveSuInvocation()
        val candidates = listOf(
            { cmd: String -> "toybox setuidgid 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "toybox setuidgid shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "/system/bin/toybox setuidgid 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "/system/bin/toybox setuidgid shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "setuidgid 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "setuidgid shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su --user shell -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su shell -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su -c \"$sh -c ${shellQuote(cmd)}\" shell" },
            { cmd: String -> "$su 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "/data/adb/ap/bin/apd su shell -c ${shellQuote(cmd)}" },
            { cmd: String -> "/data/adb/ksud su shell -c ${shellQuote(cmd)}" }
        )
        for (wrap in candidates) {
            val probe = shellCommandWithOutput(sh, "-c", wrap("id -u"))
            val uid = parseNumericUid(probe.output)
            if (probe.exitCode == 0 && uid != null && uid != 0) {
                return wrap
            }
        }
        return null
    }

    private fun runFirstSuccessfulShellScript(
        scripts: List<String>,
        timeoutMs: Long = SHELL_COMMAND_TIMEOUT_MS,
        context: Context? = null
    ): ShellExecResult {
        val fallback = ShellRuntimeHooks.stringOf(context, "shell_su_exec_failed") ?: "su execution failed"
        var last = ShellExecResult(-1, fallback)
        for (script in scripts) {
            val result = shellCommandWithOutput(timeoutMs, resolveShPath(), "-c", script)
            last = result
            if (result.exitCode == 0) return result
        }
        return last
    }

    fun parseNumericUid(output: String): Int? {
        val trimmed = output.trim()
        trimmed.toIntOrNull()?.let { return it }
        return Regex("""uid=(\d+)""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
    }
}
