package com.slideindex.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.slideindex.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DiagnosticReportExporter {

    private const val MAX_CLIPBOARD_BYTES = 500 * 1024

    private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun copyOrShare(context: Context) {
        shareOrCopy(context)
    }

    fun shareOrCopy(context: Context) {
        val appContext = context.applicationContext
        val uiContext = context
        exportScope.launch {
            val outcome = runCatching { buildExportOutcome(appContext) }
                .getOrElse { ExportOutcome.Failed }
            withContext(Dispatchers.Main.immediate) {
                presentOutcome(uiContext, appContext, outcome)
            }
        }
    }

    fun shareCrashReport(context: Context, fileName: String) {
        val appContext = context.applicationContext
        val uiContext = context
        exportScope.launch {
            val shared = runCatching { shareCrashReportFile(uiContext, appContext, fileName) }
                .getOrDefault(false)
            withContext(Dispatchers.Main.immediate) {
                if (shared) {
                    Toast.makeText(appContext, R.string.diagnostic_report_shared, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appContext, R.string.diagnostic_report_export_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildExportOutcome(appContext: Context): ExportOutcome {
        val report = buildString {
            append(LocalCrashHandler.generateDiagnosticReport(appContext))
            append(com.slideindex.app.diagnostic.EdgeDiag.exportText(appContext))
            append(buildTraceSection(appContext, "拖拽诊断", com.slideindex.app.diagnostic.DragTrace.readTail(appContext)))
            append(buildTraceSection(appContext, "宏录制诊断", com.slideindex.app.diagnostic.MacroTrace.readTail(appContext)))
            append(buildBrightnessSection(appContext))
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "xgesture_diagnostic_$stamp.log"
        return ExportOutcome.Ready(
            fullReport = report,
            fileName = fileName,
        )
    }

    /**
     * 亮度诊断段。
     *
     * 前半段是导出瞬间的现状（量程 / 四个键 / 实际亮度 / 权限），
     * 后半段是复现期间落盘的追踪（我们每次写、系统每次改键、写完 2.5 秒内的屏幕亮度采样）。
     * 用户只需"拉一次亮度 → 这个分享按钮"，不用做任何额外操作。
     */
    /**
     * 拖拽 / 宏录制的专用 trace 段。
     *
     * 这两块原来只有 logcat，用户点「分享诊断报告」带不走；现在各自落一个 512KB 的滚动文件，
     * 导出时整段附上，避免被 EdgeDiag 的共享缓冲噪声挤掉。
     */
    private fun buildTraceSection(appContext: Context, title: String, body: String): String =
        buildString {
            append("\n==== $title ====\n")
            append("trace bytes=${body.length}\n")
            append(body.ifBlank { "(empty)\n" })
            append("\n==== ${title}结束 ====\n")
        }

    private fun buildBrightnessSection(appContext: Context): String {
        val state = runCatching {
            val resolver = appContext.contentResolver
            val int = android.provider.Settings.System.getInt(
                resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, -1,
            )
            val float = runCatching {
                android.provider.Settings.System.getFloat(resolver, "screen_brightness_float")
            }.getOrNull()
            val adj = runCatching {
                android.provider.Settings.System.getFloat(resolver, "screen_auto_brightness_adj")
            }.getOrNull()
            val mode = android.provider.Settings.System.getInt(
                resolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
            )
            val display = com.slideindex.app.diagnostic.BrightnessTrace
                .readDisplayBrightness(appContext)
            // adj 的真实量纲：AOSP 是 -1..1，部分 OEM（ColorOS）实测 0..255。
            // 量纲错了我们按 -1..1 写的值会被系统忽略/钳掉，必须在报告里直接标出来。
            val adjScale = when {
                adj == null -> "unknown"
                kotlin.math.abs(adj) > 1.5f -> "0..255(non-standard)"
                else -> "-1..1"
            }
            "state int=$int float=$float adj=$adj adjScale=$adjScale mode=$mode display=$display\n" +
                "scale ${BrightnessControlHelper.describeScale(appContext)}\n" +
                "lastWritePath=${BrightnessControlHelper.lastWritePath}\n"
        }.getOrDefault("state unavailable\n")
        val tail = com.slideindex.app.diagnostic.BrightnessTrace.readTail(appContext)
        return buildString {
            append("\n==== 亮度诊断 ====\n")
            append(state)
            append("trace bytes=${tail.length}\n")
            append("---- trace tail ----\n")
            append(tail)
            append("\n==== 亮度诊断结束 ====\n")
        }
    }

    private fun presentOutcome(
        uiContext: Context,
        appContext: Context,
        outcome: ExportOutcome,
    ) {
        when (outcome) {
            ExportOutcome.Failed -> {
                Toast.makeText(appContext, R.string.diagnostic_report_export_failed, Toast.LENGTH_LONG).show()
            }
            is ExportOutcome.Ready -> {
                if (shareReport(uiContext, outcome.fullReport, outcome.fileName)) {
                    Toast.makeText(appContext, R.string.diagnostic_report_shared, Toast.LENGTH_SHORT).show()
                    return
                }
                val truncationSuffix = appContext.getString(R.string.diagnostic_report_truncation_suffix)
                val clipboardText = truncateUtf8(outcome.fullReport, MAX_CLIPBOARD_BYTES, truncationSuffix)
                val truncated = outcome.fullReport.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_BYTES
                if (copyToClipboard(appContext, clipboardText)) {
                    val messageRes = if (truncated) {
                        R.string.diagnostic_report_copied_truncated
                    } else {
                        R.string.diagnostic_report_copied
                    }
                    Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appContext, R.string.diagnostic_report_export_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private sealed class ExportOutcome {
        data class Ready(
            val fullReport: String,
            val fileName: String,
        ) : ExportOutcome()

        data object Failed : ExportOutcome()
    }

    private fun copyToClipboard(context: Context, text: String): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("XGesture Diagnostic Report", text))
            true
        }.getOrDefault(false)
    }

    private fun shareCrashReportFile(
        uiContext: Context,
        appContext: Context,
        fileName: String,
    ): Boolean {
        val file = LocalCrashHandler.crashReportFile(appContext, fileName) ?: return false
        return shareFile(
            context = uiContext,
            file = file,
            subject = fileName,
        )
    }

    private fun shareFile(context: Context, file: File, subject: String): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                context.getString(R.string.diagnostic_report_share_chooser_title),
            )
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun shareReport(context: Context, report: String, fileName: String): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(report, Charsets.UTF_8)
            shareFile(
                context = context,
                file = file,
                subject = context.getString(R.string.diagnostic_report_share_subject),
            )
        }.getOrDefault(false)
    }

    private fun truncateUtf8(text: String, maxBytes: Int, suffix: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text

        val suffixBytes = suffix.toByteArray(Charsets.UTF_8)
        val budget = (maxBytes - suffixBytes.size).coerceAtLeast(0)
        if (budget <= 0) return suffix

        var cut = budget
        while (cut > 0 && (bytes[cut - 1].toInt() and 0xC0) == 0x80) {
            cut--
        }
        return String(bytes, 0, cut, Charsets.UTF_8) + suffix
    }
}
