package com.slideindex.app.diagnostic

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 边缘手势 / 浮层面板的轻量内存埋点（**仅 debug 构建生效**）。
 *
 * 为什么需要它：松手触发（`GestureTriggerMode.ON_RELEASE`）打不开快速启动器这类问题，
 * 决定性事实只有四个——「谁把会话 reset 了」「enter 动画到底有没有跑」
 * 「presentation 窗口什么时候被摘」「有没有第二条 UP/CANCEL 进来」——
 * 而这条路径在 release 上**一行日志都没有**，通用 logcat 抓不到任何有用信息
 * （用户交上来的录制常只剩 WindowManager/HWUI 之类框架 tag）。
 *
 * 这里把这些事实留在进程内环形缓冲里，由「扩展 → 诊断日志 → 分享」
 * 一键导出成 `xgesture_diagnostic_*.log` 发给开发者。
 *
 * 约束：
 * - [enabled] 为 false（release）时所有方法都是空操作，零日志零开销；
 * - 只占内存（[MAX_LINES] 行封顶），不写盘、不上报、不含用户内容；
 * - 任何埋点都不得抛异常，不得阻塞主线程。
 */
object EdgeDiag {

    private const val MAX_LINES = 4000
    private const val LOG_TAG = "EdgeDiag"
    private const val STACK_FRAMES = 6

    /**
     * 诊断包开关。
     *
     * 默认 false：只有 debug 构建记录埋点，正式包零开销（R8 会把 [enabled] 常量折叠成 false）。
     * 需要给用户发一个「正式签名、可覆盖安装、带埋点」的排错包时，把这里改成 true 再出 release
     * ——**排错结束必须改回 false**，否则发布包会一直记录这些日志。
     */
    private const val DIAGNOSTIC_BUILD_FORCE_ENABLE = false

    /** release 且未开诊断包时恒为 false：所有埋点退化为空操作。 */
    val enabled: Boolean = DIAGNOSTIC_BUILD_FORCE_ENABLE || com.slideindex.app.perf.PerfBuildFlags.debugBuild

    private val lock = Any()
    private val buffer = ArrayDeque<String>(MAX_LINES)

    /** 连续重复行的折叠状态：同一 tag+内容连着来只留一行并标注「×N」。 */
    private var lastRepeatKey: String? = null
    private var lastRepeatBase: String = ""
    private var lastRepeatCount = 0

    /** [DateTimeFormatter] 线程安全（埋点会从 binder/工作线程进来）。 */
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun log(tag: String, message: String) {
        if (!enabled) return
        val line = format(tag, message)
        val repeatKey = "$tag\u0000$message"
        synchronized(lock) {
            if (repeatKey == lastRepeatKey && buffer.isNotEmpty()) {
                // 每个手势都会走的 idle 摘窗之类会刷屏，折叠成一行「×N」，
                // 否则 1500 行缓冲会被噪声冲干净，真实现场反而留不下来。
                lastRepeatCount++
                buffer[buffer.lastIndex] = "$lastRepeatBase（×${lastRepeatCount + 1}）"
            } else {
                lastRepeatKey = repeatKey
                lastRepeatBase = line
                lastRepeatCount = 0
                buffer.addLast(line)
                while (buffer.size > MAX_LINES) buffer.removeFirst()
            }
        }
        runCatching { Log.i(LOG_TAG, line) }
    }

    /**
     * 记录「是谁调的」：把调用栈前几帧拼在消息后面。
     *
     * 用在 `forceReset` / `cancelForwardedTouch` / `endQuickLauncherSessionAnimated`
     * 这类「面板被谁关掉」的地方——一次复现就能指名道姓，不用猜。
     */
    fun logStack(tag: String, message: String) {
        if (!enabled) return
        log(tag, "$message ← ${callerFrames()}")
    }

    /** 会话分隔线：方便在导出文件里定位某一次复现。 */
    fun marker(title: String) {
        if (!enabled) return
        log("====", "──────── $title ────────")
    }

    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) {
        buffer.clear()
        lastRepeatKey = null
        lastRepeatBase = ""
        lastRepeatCount = 0
    }

    fun lineCount(): Int = synchronized(lock) { buffer.size }

    /**
     * 生成可直接贴进诊断报告的文本段：环境头 + 全部埋点。
     *
     * 环境头里放「哪一类根因」要用到的开关状态：接管桥是否连接、无障碍/宿主是否就绪、
     * 前台包名、进程名与版本——这些决定了报告能不能自解释。
     */
    fun exportText(context: Context): String {
        val lines = snapshot()
        return buildString {
            appendLine()
            appendLine("#### 边缘手势埋点 (EdgeDiag)")
            if (!enabled) {
                appendLine("⚠️ 本次为 release 构建，埋点关闭（需要用 debug 构建复现才能拿到现场）")
                return@buildString
            }
            appendLine("- 应用版本: ${versionText()}")
            appendLine("- 埋点构建: ${buildKindText()}")
            appendLine("- 进程: ${processText(context)} (pid ${Process.myPid()})")
            appendLine("- 设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val diag = com.slideindex.app.overlay.OverlayHostRuntime.diagnosticsInfo?.invoke()
            appendLine("- 无障碍服务: ${yesNo(diag?.serviceConnected == true)}")
            appendLine("- 浮层宿主就绪: ${yesNo(diag?.overlayReady == true)}")
            appendLine("- LSPosed 接管状态: ${moduleBridgeText(context)}")
            appendLine("- 前台包名: ${diag?.foregroundPackage ?: "-"}")
            appendLine("- 埋点行数: ${lines.size}")
            appendLine()
            if (lines.isEmpty()) {
                appendLine("（没有记录到埋点：要么是 release 构建，要么复现前进程刚重启）")
            } else {
                appendLine("```text")
                lines.forEach { appendLine(it) }
                appendLine("```")
            }
        }
    }

    private fun format(tag: String, message: String): String =
        "${LocalTime.now().format(timeFormat)} [$tag] $message"

    private fun callerFrames(): String = runCatching {
        val frames = Throwable().stackTrace
        frames.asSequence()
            .filterNot { frame ->
                val name = frame.className
                name == EdgeDiag::class.java.name ||
                    name.startsWith("com.slideindex.app.diagnostic.EdgeDiag")
            }
            .take(STACK_FRAMES)
            .joinToString(" < ") { frame ->
                val file = frame.fileName ?: "?"
                "${frame.className.substringAfterLast('.')}.${frame.methodName}($file:${frame.lineNumber})"
            }
    }.getOrDefault("-")

    private fun versionText(): String {
        val flags = com.slideindex.app.perf.PerfBuildFlags
        return "${flags.appVersionName} (${flags.appVersionCode})"
    }

    /** 让收到的报告能自证是「诊断包」还是 debug 包，避免和正式包的报告混淆。 */
    private fun buildKindText(): String = when {
        DIAGNOSTIC_BUILD_FORCE_ENABLE -> "诊断包（正式签名，埋点强制开启）"
        com.slideindex.app.perf.PerfBuildFlags.debugBuild -> "debug 构建"
        else -> "关闭（正式包）"
    }

    private fun processText(context: Context): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            context.packageName
        }
    }.getOrDefault(context.packageName)

    /**
     * 模块回执状态：`active/state/detail` 由模块自身写回，
     * 能直接看出「接管是否真的在生效」——这正是①/②类根因的分界线。
     */
    private fun moduleBridgeText(context: Context): String =
        com.slideindex.app.overlay.OverlayHostRuntime.moduleBridgeText?.invoke(context) ?: "-"

    private fun yesNo(value: Boolean): String = if (value) "是" else "否"
}
