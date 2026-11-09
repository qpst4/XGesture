package com.slideindex.app.overlay

import android.util.Log
import android.graphics.Rect
import android.widget.Toast
import com.slideindex.app.clipboard.ClipboardAccess
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.slideindex.app.clipboardfloat.ClipboardPasteCoordinator
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.clipboardfloat.PasteResult
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.perf.PickPerf
import kotlin.math.roundToInt
import com.slideindex.app.settings.FloatBallSide

/**
 * 拖拽的收尾与手势完成（从 `FloatBallOverlay` 拆出的第 10 类）。
 */
internal object FloatBallDragFinish {

    private const val REGIONAL_RECT_MIN_SIDE_DP = 3f

    fun prepareLineDrag(
        touchDownX: Float,
        touchDownY: Float,
        fingerX: Float,
        fingerY: Float
    ) {
        val settings = FloatBallOverlay.settingsState?.value ?: return
        val dockedSide = FloatBallLayout.resolvedActiveSide(settings)
        val bothEdges = FloatBallLayout.usesEdgeLines(settings)
        FloatBallOverlay.activeSideAtDragStart = if (bothEdges) dockedSide else null
        FloatBallOverlay.lineDragEndedWithGesture = false
        if (!FloatBallOverlay.slopPhaseBallFollowActive) {
            FloatBallOverlay.dragOriginatedFromLine = bothEdges
            FloatBallOverlay.dragActiveSideOverride = if (bothEdges) {
                FloatBallSide.opposite(dockedSide)
            } else {
                null
            }
        }
        FloatBallOverlay.expandLineTouchCapture()
        FloatBallOverlay.showCursorAtScreenTouch(
            touchDownX = touchDownX,
            touchDownY = touchDownY,
            fingerX = fingerX,
            fingerY = fingerY,
            deferBallWindowMutation = true
        )
        if (FloatBallOverlay.isDraggingNow() && FloatBallOverlay.dragOriginatedFromLine) {
            FloatBallOverlay.setBallTouchable(false)
        }
    }

    fun commitLineDragSideSwap() {
        if (!FloatBallOverlay.dragOriginatedFromLine) return
        val fromSide = FloatBallOverlay.activeSideAtDragStart ?: return
        val settings = FloatBallOverlay.settingsState?.value ?: return
        if (!FloatBallLayout.usesEdgeLines(settings)) return
        val targetSide = FloatBallSide.opposite(fromSide)
        if (FloatBallLayout.resolvedActiveSide(settings) != targetSide) {
            FloatBallOverlay.applyActiveSide(targetSide)
        }
    }

    fun revertLineDragSideSwapIfNeeded() {
        if (!FloatBallOverlay.dragOriginatedFromLine) return
        val revertSide = FloatBallOverlay.activeSideAtDragStart ?: return
        val settings = FloatBallOverlay.settingsState?.value ?: return
        if (!FloatBallLayout.usesEdgeLines(settings)) return
        if (FloatBallLayout.resolvedActiveSide(settings) != revertSide) {
            FloatBallOverlay.applyActiveSide(revertSide)
        }
    }

    fun cancelDragWithoutPick() {
        if (!FloatBallOverlay.isDraggingNow()) return
        if (FloatBallOverlay.moveModeActive) FloatBallOverlay.exitMoveMode()
        FloatBallOverlay.cancelDragChromeLayoutFrame()
        FloatBallOverlay.cancelCursorCommitFrame()
        FloatBallOverlay.activeSideAtDragStart = null
        val settings = FloatBallOverlay.settingsState?.value
        if (settings != null) {
            FloatBallOverlay.restoreAfterDragEnd(settings)
        } else {
            FloatBallOverlay.clearCursorUi(restoreLayout = false)
            FloatBallOverlay.setDragging(false)
        }
    }

    fun completeDragGesture() {
        if (!FloatBallOverlay.isDraggingNow() || FloatBallOverlay.finishDragRequested) return
        FloatBallOverlay.finishDragRequested = true
        val settings = FloatBallOverlay.settingsState?.value ?: run {
            FloatBallOverlay.finishDragRequested = false
            return
        }
        FloatBallOverlay.finishDrag(settings)
        FloatBallOverlay.finishDragRequested = false
    }

    fun applyActiveSide(targetSide: FloatBallSide) {
        val settings = FloatBallOverlay.settingsState?.value ?: return
        if (!FloatBallLayout.usesEdgeLines(settings)) return
        val updated = settings.copy(floatBall = settings.floatBall.copy(floatBallActiveSide = targetSide))
        FloatBallOverlay.settingsState?.value = updated
        FloatBallOverlay.committedActiveSideUntilPersist = targetSide
        FloatBallOverlay.onActiveSidePersisted?.invoke(targetSide)
        FloatBallOverlay.applyBallLayout(updated)
        if (!FloatBallOverlay.isDraggingNow()) {
            FloatBallOverlay.applyLineLayout(updated)
            FloatBallOverlay.updateChromeVisibility(updated)
        }
    }

    fun restoreDockPosition(settings: AppSettings) {
        FloatBallOverlay.applyAllLayouts(settings)
    }

    fun onFingerDrag(fingerX: Float, fingerY: Float) {
        if (!FloatBallOverlay.isDraggingNow()) return
        FloatBallOverlay.dragSession.updateFingerPosition(fingerX, fingerY)
        FloatBallOverlay.updatePickAndBallFromFinger(moveBallWindow = true)
    }

    fun finishDrag(settings: AppSettings) {
        if (!FloatBallOverlay.isDraggingNow()) return
        FloatBallOverlay.cancelDragChromeLayoutFrame()
        FloatBallOverlay.cancelCursorCommitFrame()
        FloatBallOverlay.flushDragChromeLayout(syncAnchorState = true)
        FloatBallOverlay.commitPickAnchor()
        // 圆环工具栏「移动」进入的移动模式：松手只做"选边 + 存竖直位置 + 吸附"，不取词。
        if (FloatBallOverlay.moveModeActive) {
            FloatBallOverlay.finishMoveModeRelease(settings)
            FloatBallOverlay.activeSideAtDragStart = null
            FloatBallOverlay.restoreAfterDragEnd(FloatBallOverlay.settingsState?.value ?: settings)
            return
        }
        val hadPauseIntent = FloatBallOverlay.cursorPausedState?.value == true || FloatBallOverlay.selectionStartState?.value != null
        if (hadPauseIntent) {
            if (!FloatBallOverlay.regionalPickActive) {
                FloatBallOverlay.ensurePreviewBoundsForPick()
            }
            FloatBallOverlay.handlePickOnRelease(settings)
        } else {
            FloatBallOverlay.commitLineDragSideSwap()
        }
        FloatBallOverlay.dragActiveSideOverride = null
        FloatBallOverlay.activeSideAtDragStart = null

        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            FloatBallOverlay.persistBallCenterFraction()
        }

        FloatBallOverlay.restoreAfterDragEnd(FloatBallOverlay.settingsState?.value ?: settings)
    }

    fun handlePickOnRelease(settings: AppSettings) {
        val end = FloatBallOverlay.currentPickAnchor() ?: return
        val start = FloatBallOverlay.selectionStartState?.value ?: end
        val host = FloatBallOverlay.appContext ?: return
        val view = FloatBallOverlay.displayView ?: return
        val dragRect = FloatBallOverlay.rectBetween(start, end)
        val isRegionalDrag = FloatBallOverlay.regionalPickActive
        val ocrFallbackEnabled = settings.floatBallOcrFallbackEnabled
        val ocrModelId = settings.floatBallOcrModelId

        if (isRegionalDrag) {
            val density = view.resources.displayMetrics.density
            val minSidePx = (REGIONAL_RECT_MIN_SIDE_DP * density).roundToInt()
            val pickRect = dragRect
            if (pickRect.width() < minSidePx || pickRect.height() < minSidePx) {
                return
            }
            val sessionTag = "regional_rect"
            PickPerf.beginSession(sessionTag)
            PickPerf.mark(
                "ACTION_UP",
                "regional=true ocr=$ocrFallbackEnabled"
            )
            val panelAnchorX = pickRect.centerX().toFloat()
            val panelAnchorY = pickRect.bottom.toFloat()
            FloatBallPickResultPanel.showLoading(
                host,
                panelAnchorX,
                panelAnchorY,
                PickResultTextSource.OCR
            )
            SlideIndexAccessibilityService.pickFloatBallOnRelease(
                context = host,
                startX = pickRect.left.toFloat(),
                startY = pickRect.top.toFloat(),
                endX = pickRect.right.toFloat(),
                endY = pickRect.bottom.toFloat(),
                regionalRect = true,
                ocrFallbackEnabled = ocrFallbackEnabled,
                ocrModelId = ocrModelId
            ) { result ->
                PickPerf.mark("showResultPanel_callback")
                FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                PickPerf.endSession("END", sessionTag)
            }
        } else {
            if (FloatBallOverlay.tryFloatBallDragPaste(settings, end)) return
            var previewBounds = FloatBallOverlay.selectionPreviewBoundsState?.value
            if (previewBounds == null) {
                FloatBallOverlay.ensurePreviewBoundsForPick()
                previewBounds = FloatBallOverlay.selectionPreviewBoundsState?.value
            }
            val bounds = previewBounds ?: return
            val panelAnchorX = bounds.centerX().toFloat()
            val panelAnchorY = bounds.bottom.toFloat()
            PickPerf.beginSession("preview_bounds")
            FloatBallPickResultPanel.showLoading(
                host,
                panelAnchorX,
                panelAnchorY,
                PickResultTextSource.A11Y
            )
            SlideIndexAccessibilityService.pickFloatBallTextInRect(
                context = host,
                rect = bounds,
                ocrFallbackEnabled = ocrFallbackEnabled,
                ocrModelId = ocrModelId,
                previewBoundsPick = true
            ) { result ->
                FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                PickPerf.endSession("END", "preview_bounds")
            }
        }
    }

    fun tryFloatBallDragPaste(settings: AppSettings, anchor: Offset): Boolean {
        if (!settings.floatBallDragPasteEnabled) return false
        if (FloatBallOverlay.dragPastePickUpgraded) return false
        val hadPasteIntent = FloatBallOverlay.lockedDragPasteEditableRect != null
        val editableRect = FloatBallOverlay.lockedDragPasteEditableRect?.let { Rect(it) }
            ?: FloatBallPreviewBoundsCache.hitTestEditableAt(anchor.x, anchor.y)?.let { Rect(it) }
        FloatBallOverlay.lockedDragPasteEditableRect = null
        if (editableRect == null) return false
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        val host = FloatBallOverlay.appContext
        val repo = ClipboardAccess.repository
        if (service == null || host == null || repo == null) {
            if (hadPasteIntent) {
                Toast.makeText(host ?: FloatBallOverlay.appContext, R.string.search_engine_accessibility_required, Toast.LENGTH_SHORT)
                    .show()
            }
            return hadPasteIntent
        }
        if (repo.entryCount.value <= 0) {
            Toast.makeText(host, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return true
        }
        val rect = editableRect
        val fvStyle = settings.clipboardPasteFvStyleEnabled
        FloatBallOverlay.overlayScope.launch {
            val entry = withContext(Dispatchers.IO) { repo.peekLatestEntry() }
            if (entry == null) return@launch
            ClipboardPasteCoordinator.pasteEntryAtScreenRect(
                service = service,
                context = host,
                entry = entry,
                rect = rect,
                fvStyle = fvStyle,
                onFinished = { result ->
                    if (result is PasteResult.Failure) {
                        ClipboardPasteCoordinator.toastPasteFailure(host, result.reason)
                    }
                },
            )
        }
        return true
    }

    fun snapBallToEdge(settings: AppSettings) {
        FloatBallOverlay.restoreDockPosition(settings)
    }

    /** 当前球心占屏幕宽/高的比例（自由位置模式用的就是这两个分数）。 */
    fun currentBallCenterFraction(): Pair<Float, Float>? {
        val view = FloatBallOverlay.displayView ?: return null
        val settings = FloatBallOverlay.settingsState?.value ?: return null
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val ballSize = FloatBallLayout.ballSize(settings, density)
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, FloatBallOverlay.windowManager)
        // ⚠ 拖动中必须以**手指带动的真实球心**为准：FloatBallOverlay.dragSession 记着"手指↔球心"的偏移并做了边界钳制。
        // 若这里去问 FloatBallLayout.ballCenterPx(settings)，自由位置模式下读到的还是**旧的自定义分数**，
        // 结果就是"拖到哪、松手又弹回哪"（自由位置模式的移动一直是坏的）。
        val center: Pair<Float, Float> = if (FloatBallOverlay.isDraggingNow()) {
            val clamped = FloatBallOverlay.dragSession.clampedBallCenter(
                ballSize = ballSize,
                marginPx = FloatBallLayout.marginPx(density),
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx
            )
            clamped.x to clamped.y
        } else {
            val activeSide = FloatBallLayout.resolvedActiveSide(settings)
            FloatBallLayout.ballCenterPx(settings, metrics, activeSide, screenWidthPx, screenHeightPx)
        }
        val customCenterXFraction = FloatBallLayout.coerceCustomCenterXFraction(center.first / screenWidthPx)
        val yFraction = FloatBallLayout.coercePositionYFraction(center.second / screenHeightPx)
        return customCenterXFraction to yFraction
    }

    fun persistBallCenterFraction() {
        val (xFraction, yFraction) = FloatBallOverlay.currentBallCenterFraction() ?: return
        FloatBallOverlay.onPositionPersisted?.invoke(xFraction, yFraction)
    }

    /**
     * 按下阶段**不再**显示半透明预览十字。
     *
     * 原因：预览用「球的停靠点」算位置（[FloatBallOverlay.computePassivePickAnchor]），拖动阶段用「屏幕边缘 + 位移×倍率」
     * 算位置（[FloatBallDragSession]），两者不是同一个点——切过去的那一帧红十字会瞬移，
     * 这就是用户反馈的"一触摸就从边上跳到屏内"。
     *
     * 现在改成：十字第一次出现发生在拖动阶段的第一帧，且锚点已在屏幕外
     * （[FloatingPointerBounds.PICK_ANCHOR_OUTSIDE_DP]），观感是"从边缘滑进来"，没有可比对的上一帧。
     */
    fun showCursorPickPreview(
        @Suppress("UNUSED_PARAMETER") screenX: Float,
        @Suppress("UNUSED_PARAMETER") screenY: Float,
    ) {
        // 有意留空。
    }

    fun prepareLineDragStateForSlop() {
        val settings = FloatBallOverlay.settingsState?.value ?: return
        val dockedSide = FloatBallLayout.resolvedActiveSide(settings)
        val bothEdges = FloatBallLayout.usesEdgeLines(settings)
        if (!bothEdges) return
        FloatBallOverlay.dragOriginatedFromLine = true
        FloatBallOverlay.slopPhaseFromLineStrip = true
        FloatBallOverlay.dragActiveSideOverride = FloatBallSide.opposite(dockedSide)
    }

}
