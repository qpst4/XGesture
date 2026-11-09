package com.slideindex.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.overlay.AccessibilityLink
import com.slideindex.app.overlay.OverlayStatePort
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 100% 纯本地离线异常捕获与诊断报告生成器。
 * 绝不使用任何第三方遥测 SDK，保护用户隐私。
 */
object LocalCrashHandler {
    private const val TAG = "LocalCrashHandler"
    private const val LEGACY_CRASH_FILE_NAME = "crash_log.txt"
    private const val CRASH_DIR_NAME = "crashes"
    private const val CRASH_FILE_PREFIX = "crash_"
    private const val CRASH_FILE_SUFFIX = ".txt"
    private const val MAX_CRASH_FILES = 20
    private const val MAX_CRASH_SAVE_CHARS = 64 * 1024
    const val MAX_CRASH_DISPLAY_BYTES = 256 * 1024
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    data class CrashReportEntry(
        val fileName: String,
        val timestampMs: Long,
        val previewLine: String,
    )

    data class CrashReportContent(
        val text: String,
        val truncated: Boolean,
        val totalBytes: Long,
    )

    fun install(context: Context) {
        val appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashReport(appContext, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        migrateLegacyCrashReport(appContext)
    }

    private fun crashDir(context: Context): File =
        File(context.filesDir, CRASH_DIR_NAME).apply { mkdirs() }

    private fun migrateLegacyCrashReport(context: Context) {
        val legacy = File(context.filesDir, LEGACY_CRASH_FILE_NAME)
        if (!legacy.exists() || !legacy.isFile) return
        runCatching {
            val content = legacy.readText()
            if (content.isNotBlank()) {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(legacy.lastModified()))
                val target = File(crashDir(context), "$CRASH_FILE_PREFIX$stamp$CRASH_FILE_SUFFIX")
                if (!target.exists()) {
                    target.writeText(content)
                }
            }
            legacy.delete()
        }.onFailure { Log.w(TAG, "Failed to migrate legacy crash report", it) }
    }

    private fun saveCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = compactStackTrace(sw.toString())

            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val info = buildString {
                appendLine("================ CRASH REPORT ================")
                appendLine("Time: $time")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                @Suppress("DEPRECATION")
                val threadId = thread.id
                appendLine("Thread: ${thread.name} (id: $threadId)")
                appendLine("Exception: ${throwable::class.java.name}: ${throwable.message}")
                appendLine("----------------- STACK TRACE -----------------")
                appendLine(stackTrace)
                appendLine("==============================================")
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(crashDir(context), "$CRASH_FILE_PREFIX$stamp$CRASH_FILE_SUFFIX")
            file.writeText(info)
            pruneOldCrashReports(context)
        }.onFailure { Log.e(TAG, "Failed to save crash report", it) }
    }

    private fun pruneOldCrashReports(context: Context) {
        val files = listCrashReportFiles(context)
        if (files.size <= MAX_CRASH_FILES) return
        files.drop(MAX_CRASH_FILES).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun listCrashReportFiles(context: Context): List<File> =
        crashDir(context)
            .listFiles { file ->
                file.isFile &&
                    file.name.startsWith(CRASH_FILE_PREFIX) &&
                    file.name.endsWith(CRASH_FILE_SUFFIX)
            }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun listCrashReports(context: Context): List<CrashReportEntry> =
        listCrashReportFiles(context).map { file ->
            val preview = runCatching { file.readLines().firstOrNull { it.isNotBlank() }.orEmpty() }
                .getOrDefault("")
            CrashReportEntry(
                fileName = file.name,
                timestampMs = file.lastModified(),
                previewLine = preview,
            )
        }

    fun readCrashReport(context: Context, fileName: String): String? =
        readCrashReportForDisplay(context, fileName)?.text

    fun crashReportFile(context: Context, fileName: String): File? {
        val file = File(crashDir(context), fileName)
        if (!file.exists() || !file.isFile) return null
        return file
    }

    fun readCrashReportForDisplay(context: Context, fileName: String): CrashReportContent? {
        val file = File(crashDir(context), fileName)
        if (!file.exists() || !file.isFile) return null
        val totalBytes = file.length()
        return runCatching {
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(MAX_CRASH_DISPLAY_BYTES.coerceAtLeast(1))
                var read = 0
                while (read < buffer.size) {
                    val count = input.read(buffer, read, buffer.size - read)
                    if (count <= 0) break
                    read += count
                }
                val truncated = totalBytes > read
                val text = buffer.decodeToString(0, read)
                CrashReportContent(
                    text = text,
                    truncated = truncated,
                    totalBytes = totalBytes,
                )
            }
        }.getOrNull()
    }

    fun readLastCrashReport(context: Context): String? {
        val latest = listCrashReportFiles(context).firstOrNull() ?: return null
        return readCrashReport(context, latest.name)
    }

    private fun compactStackTrace(raw: String): String {
        val deduped = dedupeConsecutiveStackLines(raw)
        if (deduped.length <= MAX_CRASH_SAVE_CHARS) return deduped
        return buildString {
            append(deduped, 0, MAX_CRASH_SAVE_CHARS.coerceAtMost(deduped.length))
            appendLine()
            append("... stack trace truncated ...")
        }
    }

    private fun dedupeConsecutiveStackLines(raw: String): String {
        val lines = raw.lineSequence().toList()
        if (lines.isEmpty()) return raw
        return buildString {
            var previous: String? = null
            var duplicateCount = 0
            fun flushDuplicates() {
                when {
                    duplicateCount >= 3 -> appendLine("\t... ${duplicateCount + 1} identical lines omitted ...")
                    duplicateCount > 0 -> repeat(duplicateCount) { appendLine(previous) }
                }
                duplicateCount = 0
            }
            for (line in lines) {
                if (line == previous) {
                    duplicateCount++
                    continue
                }
                flushDuplicates()
                appendLine(line)
                previous = line
            }
            flushDuplicates()
        }
    }

    fun clearCrashReports(context: Context) {
        listCrashReportFiles(context).forEach { file ->
            runCatching { file.delete() }
        }
        File(context.filesDir, LEGACY_CRASH_FILE_NAME).delete()
    }

    fun clearCrashReport(context: Context) {
        clearCrashReports(context)
    }

    /**
     * 生成完整的系统状态与诊断文本（用于一键复制排错）。
     */
    fun generateDiagnosticReport(context: Context): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

        val versionName = packageInfo?.versionName ?: "Unknown"
        val versionCode = packageInfo?.longVersionCode ?: -1L

        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        val accessibilityGranted = accessibilityServices.contains(context.packageName)

        val notificationListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        val notificationListenerGranted = notificationListeners.contains(context.packageName)

        val crashReports = listCrashReports(context)

        return buildString {
            appendLine("### XGesture 系统诊断与排错报告")
            appendLine("- **生成时间**: $time")
            appendLine("- **应用版本**: $versionName ($versionCode)")
            appendLine("- **设备型号**: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("- **系统版本**: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / Build ${Build.DISPLAY}")
            appendLine()
            appendLine("#### 权限与服务状态")
            appendLine("- 悬浮窗权限 (Overlay): ${if (overlayGranted) "✅ 已开启" else "❌ 未开启"}")
            appendLine("- 无障碍服务 (Accessibility): ${if (accessibilityGranted) "✅ 已开启" else "❌ 未开启"}")
            appendLine("- 通知监听服务 (Notification Listener): ${if (notificationListenerGranted) "✅ 已开启" else "❌ 未开启"}")
            appendLine("- 读取日志 (READ_LOGS): ${if (ClipboardPermissionHelper.hasReadLogsPermission(context)) "✅ 已授予" else "❌ 未授予"}")
            appendLine()

            // 无障碍链路健康度：ColorOS 这类 ROM 上「开关开着但连不上」是最高频的故障，
            // 这里把链路状态、掉线次数、恢复结果、设置回流与自愈计数一次性带出来，
            // 用户一键复制就能定位是“服务死了”还是“设置没应用上”。
            appendLine("#### 无障碍链路")
            val linkUsable = runCatching { AccessibilityLink.isUsable() }.getOrDefault(true)
            val linkBrokenReason = runCatching { AccessibilityLink.brokenReason() }.getOrNull()
            val lastAliveAgeMs = runCatching {
                val last = AccessibilityLink.lastAliveAtMs()
                if (last <= 0L) -1L
                else android.os.SystemClock.elapsedRealtime() - last
            }.getOrDefault(-1L)
            appendLine(
                "- 链路状态: " + if (linkUsable) "✅ 可用" else "❌ 不可用" +
                    (linkBrokenReason?.let { "（原因: $it）" } ?: ""),
            )
            appendLine(
                "- 上次成功调用: " +
                    if (lastAliveAgeMs >= 0) "${lastAliveAgeMs / 1000}s 前" else "本次进程内尚无记录",
            )
            appendLine(
                "- 掉线次数: ${runCatching { AccessibilityLink.brokenCount() }.getOrDefault(0)} / " +
                    "恢复尝试: ${runCatching { AccessibilityLink.recoveryAttempts() }.getOrDefault(0)} / " +
                    "最近结果: ${runCatching { AccessibilityLink.lastRecoveryResult() }.getOrDefault("-")}",
            )
            appendLine(
                "- 设置应用: ${runCatching { OverlayStatePort.settingsApplyCount() }.getOrDefault(0)} 次" +
                    "（最近 " + runCatching {
                        val at = OverlayStatePort.lastSettingsApplyAtMs()
                        if (at <= 0L) "无" else "${(android.os.SystemClock.elapsedRealtime() - at) / 1000}s 前"
                    }.getOrDefault("无") + "）",
            )
            appendLine(
                "- 触钮自愈: 巡检 ${runCatching { OverlayStatePort.healTickCount() }.getOrDefault(0)} 次 / " +
                    "跳过 ${runCatching { OverlayStatePort.healSkipCount() }.getOrDefault(0)} 次",
            )
            appendLine()
            if (crashReports.isEmpty()) {
                appendLine("#### 崩溃日志记录")
                appendLine("✅ 暂无未捕获异常崩溃记录（运行稳定）")
            } else {
                appendLine("#### 崩溃历史 (${crashReports.size} 份)")
                crashReports.forEachIndexed { index, entry ->
                    appendLine()
                    appendLine("##### #${index + 1} ${entry.fileName}")
                    val body = readCrashReport(context, entry.fileName).orEmpty().trim()
                    appendLine("```text")
                    appendLine(body)
                    appendLine("```")
                }
            }
        }
    }
}
