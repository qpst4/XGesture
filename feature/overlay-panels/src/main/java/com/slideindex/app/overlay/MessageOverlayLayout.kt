package com.slideindex.app.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.SideBubbleHorizontalEdge
import kotlin.math.roundToInt

object MessageOverlayLayout {
    const val FLOAT_ICON_EDGE_MARGIN_DP = 16f
    const val SIDE_BUBBLE_EDGE_MARGIN_DP = 12f

    /**
     * 悬浮球为投影预留的四周空间（dp）。
     *
     * 投影是自绘在球节点外的（见 [MessageBallShadow]），悬浮窗按内容 WRAP_CONTENT，
     * 没有这段 padding 时窗口矩形会把投影裁成直边。
     */
    const val FLOAT_ICON_SHADOW_PAD_DP = 8f
    fun cNoticeEdgeMarginDp(settings: MessageSettings): Float =
        settings.cNoticeEdgeMarginDp.coerceIn(0f, 32f)

    fun floatIconTopLeft(
        settings: MessageSettings,
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float
    ): Pair<Int, Int> {
        val iconPx = (settings.floatIconSizeDp.coerceIn(32f, 64f) * density).roundToInt()
        val marginPx = (FLOAT_ICON_EDGE_MARGIN_DP * density).roundToInt()
        val centerY = (MessagePlacementFractions.coerceY(settings.floatIconYFraction) * screenHeightPx).roundToInt()
        val centerX = when (settings.floatIconCorner.horizontalEdge()) {
            SideBubbleHorizontalEdge.Left -> marginPx + iconPx / 2
            SideBubbleHorizontalEdge.Right -> screenWidthPx - marginPx - iconPx / 2
        }
        val left = (centerX - iconPx / 2).coerceIn(marginPx, screenWidthPx - iconPx - marginPx)
        val top = (centerY - iconPx / 2).coerceIn(marginPx, screenHeightPx - iconPx - marginPx)
        return left to top
    }

    fun buildFloatIconLayoutParams(
        context: android.content.Context,
        settings: MessageSettings
    ): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val (left, top) = floatIconTopLeft(
            settings = settings,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density
        )
        // 窗口内容 = 球 + 四周投影 padding，窗口左上角要往回退 pad，
        // 球在屏幕上的位置才和改投影前一致（[floatIconTopLeft] 仍表示球的左上角）。
        val shadowPadPx = (FLOAT_ICON_SHADOW_PAD_DP * metrics.density).roundToInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left - shadowPadPx
            y = top - shadowPadPx
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun buildSideBubbleLayoutParams(
        context: android.content.Context,
        settings: MessageSettings
    ): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val edgeMarginPx = (SIDE_BUBBLE_EDGE_MARGIN_DP * density).roundToInt()
        val horizontalGravity = when (settings.sideBubbleHorizontalEdge) {
            SideBubbleHorizontalEdge.Left -> Gravity.START
            SideBubbleHorizontalEdge.Right -> Gravity.END
        }
        val yOffsetPx = (MessagePlacementFractions.coerceY(settings.sideBubbleYFraction) * metrics.heightPixels)
            .roundToInt()
            .coerceIn(edgeMarginPx, metrics.heightPixels - edgeMarginPx)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or horizontalGravity
            x = edgeMarginPx
            y = yOffsetPx
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun cNoticeAnchorY(
        settings: MessageSettings,
        screenHeightPx: Int,
        density: Float,
        listHeightPx: Int,
        yFraction: Float = settings.cNoticeYFraction,
    ): Int {
        val marginPx = (cNoticeEdgeMarginDp(settings) * density).roundToInt()
        val anchorTop = (MessagePlacementFractions.coerceY(yFraction) * screenHeightPx).roundToInt()
        val maxTop = maxOf(marginPx, screenHeightPx - listHeightPx - marginPx)
        return anchorTop.coerceIn(marginPx, maxTop)
    }

    fun cNoticeTopLeft(
        settings: MessageSettings,
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float,
        listWidthPx: Int,
        listHeightPx: Int,
        horizontalEdge: SideBubbleHorizontalEdge = settings.cNoticeHorizontalEdge,
        yFraction: Float = settings.cNoticeYFraction,
    ): Pair<Int, Int> {
        val marginPx = (cNoticeEdgeMarginDp(settings) * density).roundToInt()
        val top = cNoticeAnchorY(settings, screenHeightPx, density, listHeightPx, yFraction)
        val left = when (horizontalEdge) {
            SideBubbleHorizontalEdge.Left -> marginPx
            SideBubbleHorizontalEdge.Right -> screenWidthPx - marginPx - listWidthPx
        }
        return left to top
    }

    const val C_NOTICE_LANDSCAPE_SHEET_WIDTH_FRACTION = 0.65f

    fun isLandscapeDisplay(context: android.content.Context): Boolean {
        val (widthPx, heightPx) = OverlayScreenMetrics.sizePx(context)
        return widthPx > heightPx
    }

    fun cNoticeLandscapeSheetWidthPx(context: android.content.Context): Int {
        val (widthPx, heightPx) = OverlayScreenMetrics.sizePx(context)
        val landscapeWidthPx = maxOf(widthPx, heightPx)
        return (landscapeWidthPx * C_NOTICE_LANDSCAPE_SHEET_WIDTH_FRACTION).roundToInt()
    }

    fun cNoticeLandscapeSheetWidthDp(
        context: android.content.Context,
        density: Float = context.resources.displayMetrics.density,
    ): Float = cNoticeLandscapeSheetWidthPx(context) / density

    fun buildCNoticePanelLayoutParams(
        context: android.content.Context,
        horizontalEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right,
        edgeMarginDp: Float = 8f,
        landscape: Boolean = isLandscapeDisplay(context),
    ): WindowManager.LayoutParams {
        if (!landscape) {
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                OverlayWindowTypes.contentPanelWindowType(context),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                @Suppress("DEPRECATION")
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val metrics = context.resources.displayMetrics
        val marginPx = (edgeMarginDp.coerceIn(0f, 32f) * metrics.density).roundToInt()
        val topMarginPx = (20f * metrics.density).roundToInt()
        val sheetWidthPx = cNoticeLandscapeSheetWidthPx(context)
        val horizontalGravity = when (horizontalEdge) {
            SideBubbleHorizontalEdge.Left -> Gravity.START
            SideBubbleHorizontalEdge.Right -> Gravity.END
        }
        return WindowManager.LayoutParams(
            sheetWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.contentPanelWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or horizontalGravity
            x = marginPx
            y = topMarginPx
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun buildCNoticeLayoutParams(
        context: android.content.Context,
        settings: MessageSettings,
        listHeightPx: Int = 0,
        horizontalEdge: SideBubbleHorizontalEdge = settings.cNoticeHorizontalEdge,
        yFraction: Float = settings.cNoticeYFraction,
        horizontalDragOffsetPx: Int = 0,
        verticalDragOffsetPx: Int = 0,
    ): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val marginPx = (cNoticeEdgeMarginDp(settings) * metrics.density).roundToInt()
        val top = cNoticeAnchorY(
            settings = settings,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density,
            listHeightPx = listHeightPx,
            yFraction = yFraction,
        )
        val horizontalGravity = when (horizontalEdge) {
            SideBubbleHorizontalEdge.Left -> Gravity.START
            SideBubbleHorizontalEdge.Right -> Gravity.END
        }
        val edgeX = when (horizontalEdge) {
            SideBubbleHorizontalEdge.Left -> marginPx + horizontalDragOffsetPx
            SideBubbleHorizontalEdge.Right -> marginPx - horizontalDragOffsetPx
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.fullscreenLauncherWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or horizontalGravity
            x = edgeX.coerceAtLeast(0)
            y = top + verticalDragOffsetPx
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
