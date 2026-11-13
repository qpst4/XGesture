package com.slideindex.app.shell

import android.content.Context
import android.widget.Toast
import com.slideindex.app.service.ShellCommandResultTrampoline
import com.slideindex.app.util.ShellCommandRunOutcome
import com.slideindex.app.util.ShellResultFormatter

/**
 * 「动作」类入口共用的 Shell 结果投递。
 *
 * 快速启动器 / 圆环 / 快捷轮盘 / 悬浮球 / 应用内直达都没有自己的结果 UI，
 * 所以 DIALOG 复用命令面板那条透明 Activity 渲染管线（[ShellCommandResultTrampoline]）。
 * 命令面板自己的两套 UI 仍在各自宿主里就地渲染，只共用
 * [ShellResultFeedbackPolicy] 的判定，避免「设置页认三档、别的入口各认一套」。
 */
object ShellCommandResultDelivery {

    fun deliver(
        context: Context,
        label: String,
        display: ShellResultDisplay,
        outcome: ShellCommandRunOutcome,
    ) {
        when (ShellResultFeedbackPolicy.forResult(display, outcome.exitCode)) {
            ShellResultFeedback.DIALOG -> {
                val launched = ShellCommandResultTrampoline.launch(
                    context = context,
                    result = ShellCommandResultTrampoline.Payload(
                        label = label,
                        command = outcome.expandedCommand,
                        exitCode = outcome.exitCode,
                        output = outcome.output,
                    ),
                    onPrepare = {},
                    onDismiss = {},
                )
                // 后台启动 Activity 被系统 / OEM 拦掉时不能静默：至少把结果用 Toast 交出去。
                if (!launched) toast(context, outcome)
            }
            ShellResultFeedback.TOAST -> toast(context, outcome)
            ShellResultFeedback.SILENT -> Unit
        }
    }

    fun toast(context: Context, outcome: ShellCommandRunOutcome) {
        Toast.makeText(
            context,
            ShellResultFormatter.toast(context, outcome.exitCode, outcome.output),
            Toast.LENGTH_LONG,
        ).show()
    }

    /** 结果弹窗标题：优先用户给命令起的名字；没有就交给 [defaultLabel]。 */
    fun labelFor(saved: ShellCommand?, fallback: String): String =
        saved?.label?.trim()?.takeIf { it.isNotBlank() } ?: fallback

    /** 动作里手填的命令没有命令面板条目可查，用命令首行当标题。 */
    fun defaultLabel(commandLine: String): String {
        val firstLine = commandLine.lineSequence().firstOrNull().orEmpty().trim()
        return if (firstLine.length <= MAX_LABEL_CHARS) {
            firstLine
        } else {
            firstLine.take(MAX_LABEL_CHARS) + "…"
        }
    }

    private const val MAX_LABEL_CHARS = 40
}
