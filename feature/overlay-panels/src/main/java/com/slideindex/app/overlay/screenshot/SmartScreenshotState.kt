package com.slideindex.app.overlay.screenshot

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.min

/** 自由形状的采样点上限：够画满一屏，又不至于让每帧的路径/遮罩运算失控。 */
private const val MAX_FREEFORM_POINTS = 4096

@Stable
class SmartScreenshotState {

    var visible: Boolean by mutableStateOf(false)
        private set

    var screenshot: Bitmap? by mutableStateOf(null)
        private set

    var shape: ScreenshotShape by mutableStateOf(ScreenshotShape.Rectangle)
        private set

    var selectionRect: Rect by mutableStateOf(Rect.Zero)
        private set

    var forceSquareCrop: Boolean by mutableStateOf(false)
        private set

    var isCapturing: Boolean by mutableStateOf(false)
        private set

    /** 自由形状按触点顺序保存的原始点（视图坐标，当前与位图 1:1）。 */
    var freeformPoints: List<Offset> by mutableStateOf(emptyList())
        private set

    /** 由 [freeformPoints] 平滑闭合出来的路径；点数不足或已清空时为 null。 */
    var freeformPath: Path? by mutableStateOf(null)
        private set

    /**
     * 是否处于「拖拽框选」阶段：true 时界面不显示任何预设框，由用户按下并拖动来定义选区；
     * 抬手且选区有效后自动切回 false，进入原有的编辑 / 导出流程。
     */
    var isFreeSelecting: Boolean by mutableStateOf(false)
        private set

    /** 是否已经到了可以保存/复制/分享的状态：框选未完成、自由形状不成面时都不能导出。 */
    val canExport: Boolean
        get() = !isFreeSelecting &&
            (shape != ScreenshotShape.Freeform ||
                freeformPoints.size >= FreeformGeometry.MIN_POINT_COUNT)

    private var freeformRollback: List<Offset>? = null

    /** 当前框选手势的起点（位图坐标）；null 表示没有进行中的笔画。 */
    private var freeSelectionAnchor: Offset? = null

    private var minSelectionSizePx by mutableIntStateOf(0)
    val imageBounds: Rect
        get() {
            val bitmap = screenshot ?: return Rect.Zero
            return Rect(Offset.Zero, Size(bitmap.width.toFloat(), bitmap.height.toFloat()))
        }
    val minSelectionSize: Float
        get() = minSelectionSizePx.toFloat()

    fun startCapture() {
        visible = false
        screenshot = null
        selectionRect = Rect.Zero
        resetFreeform()
        forceSquareCrop = false
        freeSelectionAnchor = null
        isFreeSelecting = false
        isCapturing = true
    }

    fun cancelCapture() {
        isCapturing = false
    }

    /**
     * @param freeSelection true（默认）：进入后不显示任何预设框，由用户拖动框选；
     *                      false：保留旧行为，显示居中的 60% x 35% 默认框。
     */
    fun show(
        screenshot: Bitmap,
        minSelectionSizePx: Int,
        freeSelection: Boolean = true
    ) {
        this.screenshot = screenshot
        this.minSelectionSizePx = minSelectionSizePx
        shape = ScreenshotShape.Rectangle
        resetFreeform()
        freeSelectionAnchor = null
        forceSquareCrop = false
        isFreeSelecting = freeSelection
        selectionRect = if (freeSelection) {
            Rect.Zero
        } else {
            defaultSelectionRect(screenshot.width.toFloat(), screenshot.height.toFloat())
        }
        visible = true
        isCapturing = false
    }

    fun dismiss() {
        visible = false
        screenshot = null
        selectionRect = Rect.Zero
        shape = ScreenshotShape.Rectangle
        resetFreeform()
        freeSelectionAnchor = null
        isFreeSelecting = false
        forceSquareCrop = false
        isCapturing = false
    }

    /** 起笔：记录框选起点，清空旧预览。只在框选阶段有效。 */
    fun beginFreeSelection(point: Offset) {
        if (!isFreeSelecting) return
        val anchor = clampPoint(point)
        freeSelectionAnchor = anchor
        selectionRect = Rect(anchor.x, anchor.y, anchor.x, anchor.y)
    }

    /** 拖动中：用起点与当前点生成归一化矩形，支持从右下往左上反向拖。 */
    fun updateFreeSelection(point: Offset) {
        if (!isFreeSelecting) return
        val anchor = freeSelectionAnchor ?: return
        selectionRect = FreeSelectionGeometry.rectFromDrag(anchor, point, imageBounds)
    }

    /**
     * 抬手：宽高都达到最小尺寸才提交并进入编辑态；太小则清空预览、留在框选阶段。
     * @return 是否成功进入编辑态；false 时 UI 应提示重新拖动。
     */
    fun commitFreeSelection(): Boolean {
        if (!isFreeSelecting) return false
        val rect = selectionRect
        if (!FreeSelectionGeometry.isUsable(rect, minSelectionSizePx.toFloat())) {
            freeSelectionAnchor = null
            selectionRect = Rect.Zero
            return false
        }
        selectionRect = clampRect(rect)
        freeSelectionAnchor = null
        isFreeSelecting = false
        return true
    }

    /** 编辑态点「重新框选」：丢弃当前选区，回到空白框选阶段。 */
    fun restartFreeSelection() {
        freeSelectionAnchor = null
        selectionRect = Rect.Zero
        shape = ScreenshotShape.Rectangle
        resetFreeform()
        forceSquareCrop = false
        isFreeSelecting = true
    }

    fun updateShape(shape: ScreenshotShape) {
        if (isFreeSelecting || this.shape == shape) {
            return
        }
        val previousShape = this.shape
        this.shape = shape
        when (shape) {
            ScreenshotShape.Freeform -> {
                // 进入自由形状：先清空，等用户画；1:1 对任意形状没有意义。
                resetFreeform()
                forceSquareCrop = false
            }

            else -> {
                // 离开自由形状：用包围盒延续选区位置，避免"画完一切回矩形就跳回默认位置"。
                if (previousShape == ScreenshotShape.Freeform) {
                    val points = freeformPoints
                    if (points.size >= FreeformGeometry.MIN_POINT_COUNT) {
                        selectionRect = clampRect(FreeformGeometry.boundsOf(points))
                    }
                    resetFreeform()
                }
            }
        }
    }

    fun updateSelection(rect: Rect) {
        if (shape == ScreenshotShape.Freeform || isFreeSelecting) {
            return
        }
        selectionRect = clampRect(rect)
    }

    fun updateForceSquareCrop(enabled: Boolean) {
        if (shape == ScreenshotShape.Freeform || isFreeSelecting) {
            return
        }
        if (forceSquareCrop == enabled) {
            return
        }
        forceSquareCrop = enabled
        if (enabled) {
            selectionRect = clampSquareRect(squareRectByCenter(selectionRect))
        }
    }

    /** 起笔：把上一笔留作回滚点，避免一次误触就把画好的形状抹掉。 */
    fun beginFreeformStroke(point: Offset) {
        if (shape != ScreenshotShape.Freeform) {
            return
        }
        freeformRollback = freeformPoints
        freeformPoints = listOf(clampPoint(point))
        rebuildFreeformPath()
    }

    fun appendFreeformPoint(point: Offset) {
        if (shape != ScreenshotShape.Freeform) {
            return
        }
        val points = freeformPoints
        if (points.size >= MAX_FREEFORM_POINTS) {
            return
        }
        freeformPoints = points + clampPoint(point)
        rebuildFreeformPath()
    }

    /** 收笔：不构成面（点太少/太小）就回滚到上一笔，并返回 false 供 UI 提示。 */
    fun commitFreeformStroke(): Boolean {
        if (shape != ScreenshotShape.Freeform) {
            return false
        }
        val points = freeformPoints
        val bounds = FreeformGeometry.boundsOf(points)
        val usable = points.size >= FreeformGeometry.MIN_POINT_COUNT &&
            bounds.width >= minSelectionSizePx &&
            bounds.height >= minSelectionSizePx
        if (!usable) {
            freeformPoints = freeformRollback ?: emptyList()
            freeformRollback = null
            rebuildFreeformPath()
            return false
        }
        selectionRect = bounds
        freeformRollback = null
        return true
    }

    /** 拖动整体移动：点跟着走，包围盒（selectionRect）跟着点走，别只改 rect。 */
    fun translateFreeform(delta: Offset) {
        if (shape != ScreenshotShape.Freeform) {
            return
        }
        val points = freeformPoints
        if (points.size < FreeformGeometry.MIN_POINT_COUNT) {
            return
        }
        val bounds = FreeformGeometry.boundsOf(points)
        val minDx = -bounds.left
        val maxDx = imageBounds.width - bounds.right
        val minDy = -bounds.top
        val maxDy = imageBounds.height - bounds.bottom
        val dx = if (minDx <= maxDx) delta.x.coerceIn(minDx, maxDx) else 0f
        val dy = if (minDy <= maxDy) delta.y.coerceIn(minDy, maxDy) else 0f
        if (dx == 0f && dy == 0f) {
            return
        }
        freeformPoints = points.map { Offset(it.x + dx, it.y + dy) }
        rebuildFreeformPath()
        selectionRect = FreeformGeometry.boundsOf(freeformPoints)
    }

    private fun resetFreeform() {
        freeformRollback = null
        freeformPoints = emptyList()
        freeformPath = null
    }

    private fun rebuildFreeformPath() {
        freeformPath = if (freeformPoints.size >= 2) buildClosedPath(freeformPoints) else null
    }

    /**
     * 中点二次贝塞尔平滑 + 收笔回起点。
     *
     * 与 `ScreenDrawCanvasView.buildSmoothedPath` 同一套路：手绘点直接折线会有明显棱角，
     * 用相邻点中点当锚点、原始点当控制点，既平滑又不会削掉笔锋。
     */
    private fun buildClosedPath(points: List<Offset>): Path {
        val path = Path()
        val first = points.first()
        path.moveTo(first.x, first.y)
        var previous = first
        for (index in 1 until points.size) {
            val current = points[index]
            val midX = (previous.x + current.x) / 2f
            val midY = (previous.y + current.y) / 2f
            if (index == 1) {
                path.lineTo(midX, midY)
            } else {
                path.quadraticTo(previous.x, previous.y, midX, midY)
            }
            previous = current
        }
        path.quadraticTo(previous.x, previous.y, first.x, first.y)
        path.close()
        return path
    }

    private fun clampPoint(point: Offset): Offset {
        val bounds = imageBounds
        if (bounds.width <= 0f || bounds.height <= 0f) {
            return point
        }
        return Offset(
            point.x.coerceIn(0f, bounds.width),
            point.y.coerceIn(0f, bounds.height)
        )
    }

    private fun defaultSelectionRect(width: Float, height: Float): Rect {
        val targetWidth = width * 0.6f
        val targetHeight = height * 0.35f
        val left = (width - targetWidth) / 2f
        val top = (height - targetHeight) / 2f
        return Rect(
            left = left,
            top = top,
            right = left + targetWidth,
            bottom = top + targetHeight
        )
    }

    private fun squareRectByCenter(rect: Rect): Rect {
        val side = min(rect.width, rect.height)
        val half = side / 2f
        val center = rect.center
        return Rect(
            left = center.x - half,
            top = center.y - half,
            right = center.x + half,
            bottom = center.y + half
        )
    }

    private fun clampSquareRect(rect: Rect): Rect {
        val bitmap = screenshot ?: return rect
        val maxWidth = bitmap.width.toFloat()
        val maxHeight = bitmap.height.toFloat()
        val side = rect.width
            .coerceAtLeast(minSelectionSizePx.toFloat())
            .coerceAtMost(min(maxWidth, maxHeight))
        val center = rect.center
        val left = (center.x - side / 2f).coerceIn(0f, maxWidth - side)
        val top = (center.y - side / 2f).coerceIn(0f, maxHeight - side)
        return Rect(
            left = left,
            top = top,
            right = left + side,
            bottom = top + side
        )
    }

    private fun clampRect(rect: Rect): Rect {
        val bitmap = screenshot ?: return rect
        val minSize = minSelectionSizePx.toFloat()
        val maxWidth = bitmap.width.toFloat()
        val maxHeight = bitmap.height.toFloat()

        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom

        if (right - left < minSize) {
            right = left + minSize
        }
        if (bottom - top < minSize) {
            bottom = top + minSize
        }

        if (left < 0f) {
            right -= left
            left = 0f
        }
        if (top < 0f) {
            bottom -= top
            top = 0f
        }
        if (right > maxWidth) {
            val overflow = right - maxWidth
            left -= overflow
            right = maxWidth
        }
        if (bottom > maxHeight) {
            val overflow = bottom - maxHeight
            top -= overflow
            bottom = maxHeight
        }

        left = left.coerceAtLeast(0f)
        top = top.coerceAtLeast(0f)
        right = right.coerceAtMost(maxWidth)
        bottom = bottom.coerceAtMost(maxHeight)

        if (right - left < minSize) {
            right = (left + minSize).coerceAtMost(maxWidth)
            left = (right - minSize).coerceAtLeast(0f)
        }
        if (bottom - top < minSize) {
            bottom = (top + minSize).coerceAtMost(maxHeight)
            top = (bottom - minSize).coerceAtLeast(0f)
        }

        return Rect(left, top, right, bottom)
    }
}
