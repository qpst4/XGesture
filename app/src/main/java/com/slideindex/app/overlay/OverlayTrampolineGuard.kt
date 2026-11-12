package com.slideindex.app.overlay

import com.slideindex.app.service.ShellCommandEditorTrampoline
import com.slideindex.app.service.ShellCommandResultTrampoline

/**
 * 需要把**边缘浮层整体**让位的 trampoline。
 *
 * 快速启动器「添加条目」以前在这里：那时它用浮层 sheet，必须挡住浮层触摸。
 * 现在它是整页 Activity：进页前正常结束会话、退出后按原页重开，边缘触钮/悬浮球
 * 照常可用（与圆环/边角一致），所以不再需要这条守卫。
 * Shell 命令面板浮窗同样刻意保留侧边触钮（回桌面时 Activity 可能不 destroy）。
 */
internal object OverlayTrampolineGuard {
    /** 目前没有任何 trampoline 需要整扇浮层停止接触摸。 */
    fun blocksOverlayPresentationTouch(): Boolean = false

    fun blocksOverlayResume(): Boolean =
        ShellCommandEditorTrampoline.isActive() ||
            ShellCommandResultTrampoline.isActive()
}
