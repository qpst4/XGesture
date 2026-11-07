package com.slideindex.app.overlay

/** 无障碍进程内共享的 IME 顶缘；布局层读取，协调器写入。 */
object KeyboardTriggerImeState {
    @Volatile
    var imeVisible: Boolean = false

    @Volatile
    var imeTop: Int? = null

    fun imeTopOrNull(): Int? = if (imeVisible) imeTop else null

    fun update(visible: Boolean, top: Int?) {
        imeVisible = visible
        imeTop = if (visible) top else null
    }
}
