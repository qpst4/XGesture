package com.slideindex.app.util

import android.os.SystemClock
import com.slideindex.app.overlay.PanelSide

/**
 * 「跟随触发位置」用的手势触发锚点。
 *
 * 手势 / 轮盘 / 蜂窝 / 悬浮球等入口在派发动作时把「从哪一侧、从多高触发」记下来，
 * 小窗启动时读一次，决定贴哪一侧、以及竖直居中到哪里（见 `FreeWindowLauncher.launchBounds`）。
 *
 * 这是一份**进程内瞬态**状态，不落盘：
 * - 只有「自由窗口」设置里的跟随开关打开时才会被读，开关关闭时完全没有副作用；
 * - 超过 [MAX_AGE_MS] 的锚点视为过期（典型场景：手势开了面板、很久之后才从面板里启动应用），
 *   此时回落到用户配置的位置，避免拿一次旧手势的方位去摆新窗口。
 */
object FreeWindowTriggerAnchor {
    /** 一次触发到真正启动之间通常远小于 1s；这里留足长按判定 + 快捷方式解析的余量。 */
    const val MAX_AGE_MS = 3_000L

    data class Anchor(
        /** 触发侧；TOP / BOTTOM 触发不参与「贴左还是贴右」的判断，只用于居中。 */
        val side: PanelSide?,
        /** 触发高度的屏幕 y 坐标；<= 0 表示未知。 */
        val rawY: Float,
    )

    @Volatile
    private var anchor: Anchor? = null

    @Volatile
    private var recordedAtMs: Long = 0L

    /** 记录一次触发；侧与高度都拿不到时保持原值不动。 */
    fun record(side: PanelSide?, rawY: Float?) {
        if (side == null && (rawY == null || rawY <= 0f)) return
        anchor = Anchor(side = side, rawY = rawY ?: 0f)
        recordedAtMs = SystemClock.uptimeMillis()
    }

    /** 取未过期的锚点；没有或已过期返回 null，调用方按「未跟随」处理。 */
    fun peek(nowMs: Long = SystemClock.uptimeMillis()): Anchor? {
        val current = anchor ?: return null
        if (nowMs - recordedAtMs > MAX_AGE_MS) return null
        return current
    }

    fun clear() {
        anchor = null
        recordedAtMs = 0L
    }
}
