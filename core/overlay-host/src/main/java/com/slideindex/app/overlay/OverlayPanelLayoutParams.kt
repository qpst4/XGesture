package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * Shared [WindowManager.LayoutParams] builders for full-screen overlay panels
 * (stash/clipboard side panel, translate, pick-result).
 */
object OverlayPanelLayoutParams {

    @Suppress("DEPRECATION")
    private val defaultSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

    fun fullScreenOverlay(
        context: Context,
        focusable: Boolean = false,
        touchable: Boolean = true,
        softInputMode: Int = defaultSoftInputMode,
        windowType: Int = OverlayWindowTypes.overlayWindowType(context),
        title: String? = null,
    ): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            flags or if (focusable) {
                0
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } or if (touchable) {
                0
            } else {
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            },
            PixelFormat.TRANSLUCENT
        ).apply {
            title?.let { this.title = it }
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            this.softInputMode = softInputMode
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /**
     * 暂存/剪贴板侧栏：固定 [TYPE_APPLICATION_OVERLAY]，与悬浮球/触钮 z-order 一致。
     *
     * ⚠️ **窗口满屏（MATCH_PARENT）**，面板自己只占右侧一条（比例见 `StashPanelWidthMode`，
     * 由 `HistoryPanelUi.panelWidthOf` 乘出来）。
     *
     * 曾经改成"窗口 = 屏宽 × 78% + 拖动移动窗口 + 系统模糊"（§0.16.3）来治"跟手拖出时只剩白 tint（雾）"，
     * 但**用户看了实机后不满意，整批回退**：现在回到"窗口满屏 + 面板内容自己平移 + App 自绘磨砂罩"。
     * 回退记录见 `docs/capsule-refactor-plan.md` §0.16.3。
     */
    fun stashClipboardSidePanel(
        context: Context,
        focusable: Boolean = false,
        touchable: Boolean = true,
        softInputMode: Int = defaultSoftInputMode
    ): WindowManager.LayoutParams = fullScreenOverlay(
        context = context,
        focusable = focusable,
        touchable = touchable,
        softInputMode = softInputMode,
        windowType = OverlayWindowTypes.contentPanelWindowType(context),
        title = "XG:stash-panel",
    ).apply {
        // 侧栏浏览时不抢焦点，尽量保持底层 App 输入法不收起（对齐 ClipShare 单窗 NOT_FOCUSABLE 策略）。
        flags = flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        // 窗口**满屏**：§0.16.3 那次改窄 + 移动窗口 + 系统模糊的改造已被用户打回，整批回退。
    }

    /**
     * ⚠️ **已与真实面板宽度脱钩**，别再拿它算宽度：面板宽度现在按设置走
     * （`StashPanelWidthMode` → `HistoryPanelUi.LocalStashPanelWidthFraction`）。
     * 它唯一的潜在使用点 `OverlaySidePanelHost.applyPanelX` 自 §0.16.3 整批回退之后就没人调用了
     * （见那里的注释），留着只为与旧代码对照。
     */
    const val SIDE_PANEL_WIDTH_FRACTION = 0.78f

    fun pickResultPanel(context: Context): WindowManager.LayoutParams =
        fullScreenOverlay(
            context = context,
            focusable = false,
            touchable = false,
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            windowType = OverlayWindowTypes.contentPanelWindowType(context),
            title = "XG:pick-result-panel",
        )
}
