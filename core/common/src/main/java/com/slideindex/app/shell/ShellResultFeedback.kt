package com.slideindex.app.shell

/** 命令跑完后实际要给的反馈形态：由命令自己的 [ShellResultDisplay] + 退出码决定。 */
enum class ShellResultFeedback {
    DIALOG,
    TOAST,
    SILENT,
}

/**
 * [ShellResultDisplay] → [ShellResultFeedback] 的唯一判定处。
 *
 * 以前这段判定在每个入口各写一份：设置页认三档，面板 Activity 和边缘浮层面板总是弹窗，
 * 快速启动器 / 圆环 / 应用内直达这条「动作」链路则完全不读设置。统一到这里后，
 * 各入口只负责「怎么显示」，不再各自解释设置的含义。
 */
object ShellResultFeedbackPolicy {

    fun forResult(display: ShellResultDisplay, exitCode: Int): ShellResultFeedback =
        when (display) {
            ShellResultDisplay.DIALOG -> ShellResultFeedback.DIALOG
            ShellResultDisplay.TOAST -> ShellResultFeedback.TOAST
            // 「不显示」只静默成功的情况：失败必须提示，否则用户完全不知道命令没跑成。
            ShellResultDisplay.NONE ->
                if (exitCode != 0) ShellResultFeedback.TOAST else ShellResultFeedback.SILENT
        }
}
