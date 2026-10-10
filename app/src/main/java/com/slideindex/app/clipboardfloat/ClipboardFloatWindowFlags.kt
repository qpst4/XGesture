package com.slideindex.app.clipboardfloat

import android.view.WindowManager.LayoutParams

/**
 * 剪贴板浮窗 Window flags 的唯一拼装入口。
 *
 * 背景：`ClipboardFloatService` 的 `params.flags` 曾在建窗、几何刷新、搜索焦点切换、拖拽起拖/恢复
 * 这几条路径上各自整体赋值。任何一条漏写 [LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH]，窗口就会静默失去
 * 「点窗外关闭」（窗口依旧可见、可点，但系统不再投递 [android.view.MotionEvent.ACTION_OUTSIDE]）。
 * 这里统一拼装，并只对外暴露「整体基础 flags」与「单比特增删」两类操作。
 */
object ClipboardFloatWindowFlags {

    /** 与显示模式、焦点状态都无关的常驻位。 */
    private const val BASE =
        LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_HARDWARE_ACCELERATED

    /**
     * 基础 flags。
     *
     * @param expanded 大窗（Expanded）模式需要 [LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH] 才能收到窗外点击；
     *   chip 模式不监听窗外点击。
     * @param keyboardFocus true 时窗口可取焦，让输入法挂到浮窗上（搜索态）；否则窗口不可取焦。
     *
     * ### 为什么不可取焦时**只**加 [LayoutParams.FLAG_NOT_FOCUSABLE]，不再加 `FLAG_ALT_FOCUSABLE_IM`
     *
     * 平台文档（`WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM`）写得很明确：在已设
     * `FLAG_NOT_FOCUSABLE` 的前提下再加这个 flag，等于"**请求成为输入法目标**"，
     * 于是输入法会被排到本窗之上 —— `dumpsys window` 里的 `imeLayeringTarget` 会变成这扇浮窗。
     *
     * 2026-10-10 真机 A/B（魅族 21 / Android 16）证实这会把键盘搞掉：通知栏内联回复里胶囊一出现，
     * `imeLayeringTarget` 就从 `NotificationShade` 变成我们的胶囊窗，系统随后不再绘制键盘
     * （`dumpsys input_method` 仍报 `mInputShown=true`）；收掉胶囊键盘立刻回来。普通应用里键盘不受影响
     * （同一份 A/B 截图：胶囊在、键盘照常工作），但凡是**自己用 insets / WindowInsetsController
     * 管理输入法**的宿主（SystemUI 内联回复、AI 悬浮窗）都会中招。
     *
     * 去掉它之后：本窗依旧不可取焦（不会抢走输入框焦点），但不再"请求成为输入法目标"，
     * 输入法的分层/控制权留在原宿主手里，键盘不再被顶掉。
     */
    fun forMode(expanded: Boolean, keyboardFocus: Boolean): Int {
        var flags = BASE
        if (expanded) {
            flags = flags or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        if (!keyboardFocus) {
            flags = flags or LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return flags
    }

    /**
     * 只按需增删 [LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH]，保留其它位。
     *
     * 刷新几何时用它，避免把拖拽期间的 [LayoutParams.FLAG_NOT_TOUCHABLE] 一并抹掉。
     */
    fun withOutsideTouch(flags: Int, enabled: Boolean): Int = if (enabled) {
        flags or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    } else {
        flags and LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
    }

    /** 只按需增删 [LayoutParams.FLAG_NOT_TOUCHABLE]，保留其它位。 */
    fun withNotTouchable(flags: Int, notTouchable: Boolean): Int = if (notTouchable) {
        flags or LayoutParams.FLAG_NOT_TOUCHABLE
    } else {
        flags and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    }
}
