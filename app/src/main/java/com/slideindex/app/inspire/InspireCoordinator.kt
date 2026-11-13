package com.slideindex.app.inspire

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.Choreographer
import com.slideindex.app.barcode.ZxingBarcodeScanner
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.nativeengine.NativeEngineRuntime
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.RegionalScreenshotCrop
import com.slideindex.app.overlay.history.HistoryHandleCaptureVisibility
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.AccessibilityTextExtractor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GestureEVO Inspire pick pipeline: parallel accessibility + screenshot, then result UI.
 */
object InspireCoordinator {
    private const val CAPTURE_HIDE_DELAY_MS = 50L
    /** [awaitCaptureFrame] 的兜底上限：Choreographer 回调丢了也不能把取词卡住。 */
    private const val CAPTURE_FRAME_TIMEOUT_MS = 150L
    private const val TRANSITION_MAX_WAIT_MS = 250L
    private const val SCREENSHOT_TIMEOUT_MS = 2_000L
    /** Regional / preview a11y: abandon waiting after this (work may continue in background). */
    private const val A11Y_TIMEOUT_MS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Dedicated pick thread; avoids [Dispatchers.Default] starvation from a11y/OCR/app work. */
    private val pickDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PickPipeline").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val ocrDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PickOcr").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val barcodeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PickBarcode").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    /** Isolated from [Dispatchers.Default] so abandoned a11y work cannot starve picks. */
    private val a11yScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pickInFlight = AtomicBoolean(false)

    /**
     * 取词会话号：每接受一次取词自增。
     *
     * [scheduleDeferredPreviewScreenshot] 是独立协程，可能比发起它的那次取词活得久：
     * 它落地前必须确认"这张图还是这一轮要的"，否则迟到的图会被塞进下一轮的面板
     * （图里还带着上一轮的面板 —— 面板那边也会有 `panel_screenshot_dropped` 兜底）。
     */
    private val pickSessionSeq = AtomicInteger(0)

    fun pickInRect(
        service: AccessibilityService,
        context: Context,
        rect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        previewBoundsPick: Boolean = false,
        onResult: (FloatBallPickResult) -> Unit
    ) {
        if (!pickInFlight.compareAndSet(false, true)) {
            PickPerf.mark("pick_rejected", "inFlight=true")
            return
        }
        val pickSession = pickSessionSeq.incrementAndGet()
        PickPerf.mark("pick_enqueued", "preview=$previewBoundsPick session=$pickSession")
        scope.launch(pickDispatcher) {
            try {
                PickPerf.mark("pick_dispatch_start", "preview=$previewBoundsPick")
                val screenSizeStart = SystemClock.elapsedRealtime()
                val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
                PickPerf.markStepDuration("pick_screen_size", screenSizeStart)
                val safeRect = FloatBallOcrRegions.clampToScreen(rect, screenWidth, screenHeight)
                val ocrReadyStart = SystemClock.elapsedRealtime()
                val ocrReady = isOcrReady(context, ocrFallbackEnabled, ocrModelId)
                PickPerf.markStepDuration("ocr_ready_check", ocrReadyStart, "ready=$ocrReady")
                val deferOcr = ocrReady
                PickPerf.mark("pick_rect_ready", "preview=$previewBoundsPick deferOcr=$deferOcr")
                val result = processScreenContent(
                    service = service,
                    context = context,
                    dragSelectRect = safeRect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    previewBoundsPick = previewBoundsPick,
                    presentPickPanel = true,
                    deferOcr = deferOcr,
                    ocrReady = ocrReady,
                    pickSession = pickSession
                )
                PickPerf.mark("pick_onResult_post")
                // 取词面板一显示就记下这次展示的令牌：识码是异步的，回来时要确认面板还是这一次。
                val panelToken = withContext(Dispatchers.Main.immediate) {
                    onResult(result)
                    FloatBallPickResultPanel.showToken
                }
                PickPerf.mark("pick_onResult_done")
                launchDeferredBarcodeScan(result, panelToken)
                if (deferOcr) {
                    launchDeferredOcr(
                        context = context,
                        ocrModelId = ocrModelId,
                        result = result,
                        switchToOcrOnComplete = false
                    )
                }
            } finally {
                pickInFlight.set(false)
            }
        }
    }

    fun pickOnRelease(
        service: AccessibilityService,
        context: Context,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        regionalRect: Boolean,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        onResult: (FloatBallPickResult) -> Unit
    ) {
        if (!pickInFlight.compareAndSet(false, true)) {
            PickPerf.mark("pick_rejected", "inFlight=true")
            return
        }
        val pickSession = pickSessionSeq.incrementAndGet()
        PickPerf.mark("pick_enqueued", "regional=$regionalRect session=$pickSession")
        scope.launch(pickDispatcher) {
            try {
                PickPerf.mark("pick_dispatch_start", "regional=$regionalRect")
                PickPerf.mark("pickOnRelease_start", "regionalRect=$regionalRect ocr=$ocrFallbackEnabled")
                val metrics = context.resources.displayMetrics
                val screenSizeStart = SystemClock.elapsedRealtime()
                val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
                PickPerf.markStepDuration("pick_screen_size", screenSizeStart)
                val rect = if (regionalRect) {
                    rectBetween(startX, startY, endX, endY).let {
                        FloatBallOcrRegions.clampToScreen(it, screenWidth, screenHeight)
                    }
                } else {
                    FloatBallOcrRegions.expandPoint(metrics, startX, startY, screenWidth, screenHeight)
                }
                val ocrReadyStart = SystemClock.elapsedRealtime()
                val ocrReady = isOcrReady(context, ocrFallbackEnabled, ocrModelId)
                PickPerf.markStepDuration("ocr_ready_check", ocrReadyStart, "ready=$ocrReady")
                val deferOcr = regionalRect && ocrReady
                PickPerf.mark("pick_rect_ready", "regional=$regionalRect deferOcr=$deferOcr")
                val result = processScreenContent(
                    service = service,
                    context = context,
                    dragSelectRect = rect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    presentPickPanel = true,
                    regionalRectPick = regionalRect,
                    deferOcr = deferOcr,
                    ocrReady = ocrReady,
                    pickSession = pickSession
                )
                PickPerf.mark("pick_onResult_post")
                // 取词面板一显示就记下这次展示的令牌：识码是异步的，回来时要确认面板还是这一次。
                val panelToken = withContext(Dispatchers.Main.immediate) {
                    onResult(result)
                    FloatBallPickResultPanel.showToken
                }
                PickPerf.mark("pick_onResult_done")
                launchDeferredBarcodeScan(result, panelToken)
                if (deferOcr) {
                    launchDeferredOcr(
                        context = context,
                        ocrModelId = ocrModelId,
                        result = result,
                        switchToOcrOnComplete = result.ocrPreferSwitchOnComplete
                    )
                }
            } finally {
                pickInFlight.set(false)
            }
        }
    }

    suspend fun processScreenContent(
        service: AccessibilityService,
        context: Context,
        dragSelectRect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        previewBoundsPick: Boolean = false,
        presentPickPanel: Boolean = false,
        regionalRectPick: Boolean = false,
        deferOcr: Boolean = false,
        ocrReady: Boolean? = null,
        // 发起本次取词的会话号；0 表示不追踪（无延后截图的入口）。
        pickSession: Int = 0
    ): FloatBallPickResult {
        PickPerf.mark(
            "processScreenContent_start",
            "rect=$dragSelectRect preview=$previewBoundsPick regional=$regionalRectPick deferOcr=$deferOcr ocr=$ocrFallbackEnabled"
        )
        InspireDataHolder.clear()
        InspireDataHolder.setDragRect(Rect(dragSelectRect))

        val startUptimeMs = SystemClock.uptimeMillis()
        var a11yTimedOut = false
        var a11yPrefetchHit = false
        val a11yDeferred = if (previewBoundsPick) {
            val prefetched = PickPrefetchCache.consumePreviewA11y(dragSelectRect)
            if (prefetched != null) {
                a11yPrefetchHit = true
                a11yScope.async { prefetched }
            } else {
                PickPrefetchCache.invalidate()
                launchA11yCollect(
                    service = service,
                    dragSelectRect = dragSelectRect,
                    previewBoundsPick = true,
                    regionalRectPick = regionalRectPick
                )
            }
        } else {
            PickPrefetchCache.invalidate()
            launchA11yCollect(
                service = service,
                dragSelectRect = dragSelectRect,
                previewBoundsPick = previewBoundsPick,
                regionalRectPick = regionalRectPick
            )
        }

        var prefetchedA11y: List<String>? = null
        if (previewBoundsPick && a11yPrefetchHit) {
            val a11yWaitStart = SystemClock.elapsedRealtime()
            prefetchedA11y = withTimeoutOrNull(A11Y_TIMEOUT_MS) { a11yDeferred.await() }.orEmpty()
            if (prefetchedA11y.isNotEmpty()) {
                PickPerf.markStepDuration(
                    "a11y_wait_done",
                    a11yWaitStart,
                    "words=${prefetchedA11y.size}"
                )
                InspireDataHolder.setAccessibilityContent(prefetchedA11y)
                val elapsedFast = SystemClock.uptimeMillis() - startUptimeMs
                PickPerf.mark(
                    "preview_fast_path",
                    "screenshot=deferred elapsed=${elapsedFast}ms"
                )
                PickPerf.mark(
                    "transition_delay_skipped",
                    "reason=prefetch_hit_fast_path elapsed=${elapsedFast}ms"
                )
                PickPerf.mark("buildPickResult_start")
                val fastResult = buildPickResult(
                    context = context,
                    dragSelectRect = dragSelectRect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    ocrOnly = false,
                    deferOcr = deferOcr,
                    previewBoundsPick = previewBoundsPick,
                    regionalRectPick = regionalRectPick,
                    ocrReady = ocrReady,
                    deferredScreenshot = true
                )
                PickPerf.mark("buildPickResult_end", "source=${fastResult.activeSource}")
                scheduleDeferredPreviewScreenshot(
                    service = service,
                    context = context,
                    dragSelectRect = dragSelectRect,
                    ocrFallbackEnabled = ocrFallbackEnabled,
                    ocrModelId = ocrModelId,
                    deferOcr = deferOcr,
                    ocrReady = ocrReady,
                    pickSession = pickSession
                )
                return fastResult
            }
        }

        captureScreenshotIntoHolder(service, dragSelectRect, deferred = false)

        val a11yWaitStart = SystemClock.elapsedRealtime()
        val collected = prefetchedA11y
            ?: withTimeoutOrNull(A11Y_TIMEOUT_MS) { a11yDeferred.await() }.orEmpty()
        if (collected.isEmpty()) {
            a11yTimedOut = true
            PickPerf.markStepDuration(
                "a11y_wait_timeout",
                a11yWaitStart,
                "limit=${A11Y_TIMEOUT_MS}ms"
            )
        } else if (prefetchedA11y == null) {
            PickPerf.markStepDuration("a11y_wait_done", a11yWaitStart, "words=${collected.size}")
        }
        InspireDataHolder.setAccessibilityContent(collected)

        val elapsed = SystemClock.uptimeMillis() - startUptimeMs
        val skipTransitionReason = when {
            a11yTimedOut -> null
            previewBoundsPick && a11yPrefetchHit -> "prefetch_hit"
            regionalRectPick -> "regional_rect"
            else -> null
        }
        if (skipTransitionReason != null) {
            PickPerf.mark("transition_delay_skipped", "reason=$skipTransitionReason elapsed=${elapsed}ms")
        } else if (!a11yTimedOut && elapsed < TRANSITION_MAX_WAIT_MS) {
            val delayMs = TRANSITION_MAX_WAIT_MS - elapsed
            PickPerf.mark("transition_delay", "delayMs=$delayMs")
            delay(delayMs)
        }

        PickPerf.mark("buildPickResult_start")
        val result = buildPickResult(
            context = context,
            dragSelectRect = dragSelectRect,
            ocrFallbackEnabled = ocrFallbackEnabled,
            ocrModelId = ocrModelId,
            ocrOnly = a11yTimedOut,
            deferOcr = deferOcr,
            previewBoundsPick = previewBoundsPick,
            regionalRectPick = regionalRectPick,
            ocrReady = ocrReady
        )
        PickPerf.mark("buildPickResult_end", "source=${result.activeSource}")
        return result
    }

    private fun launchA11yCollect(
        service: AccessibilityService,
        dragSelectRect: Rect,
        previewBoundsPick: Boolean,
        regionalRectPick: Boolean
    ): Deferred<List<String>> = a11yScope.async {
        val a11yStart = SystemClock.elapsedRealtime()
        val path = when {
            previewBoundsPick -> "collectTextForPreviewRect"
            regionalRectPick -> "collectTextInRect"
            else -> "collectTextForPreviewRect"
        }
        PickPerf.mark("a11y_start", "path=$path")
        val words = when {
            previewBoundsPick -> {
                runInterruptible {
                    AccessibilityTextExtractor.collectTextForPreviewRect(service, dragSelectRect)
                }.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { listOf(it) }
                    .orEmpty()
            }
            regionalRectPick -> {
                runInterruptible {
                    AccessibilityTextExtractor.collectTextInRect(service, dragSelectRect)
                }.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { listOf(it) }
                    .orEmpty()
            }
            else -> emptyList()
        }
        PickPerf.markStepDuration("a11y_end", a11yStart, "words=${words.size}")
        words
    }

    private fun isOcrReady(
        context: Context,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String
    ): Boolean {
        if (!ocrFallbackEnabled || ocrModelId.isBlank()) return false
        val repository = OcrDependencyAccess.modelRepository(context) ?: return false
        if (!repository.isInstalled(ocrModelId)) return false
        val engine = OcrDependencyAccess.catalogProvider(context)
            ?.findModel(ocrModelId)
            ?.engine
        if (engine == OcrEngines.PPOCR) {
            val coordinator = NativeEngineRuntime.coordinator
            if (coordinator?.isPackInstalled(NativeEnginePackIds.OCR) != true) return false
        }
        return true
    }

    fun scheduleDeferredBarcodeScan(result: FloatBallPickResult) {
        launchDeferredBarcodeScan(result, FloatBallPickResultPanel.showToken)
    }

    private fun launchDeferredBarcodeScan(result: FloatBallPickResult, panelToken: Int) {
        val bitmap = result.screenshot
        if (bitmap == null || bitmap.isRecycled) return
        val scanCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        scope.launch(barcodeDispatcher) {
            try {
                val scanStart = SystemClock.elapsedRealtime()
                PickPerf.mark("barcode_async_start")
                // 预算 + 取消：面板关了就停，低端机上也不至于把 CPU 拖几秒。
                val barcodeResults = ZxingBarcodeScanner.scanBitmap(
                    scanCopy,
                    pickFastPath = true,
                    budgetMs = ZxingBarcodeScanner.PANEL_SCAN_BUDGET_MS,
                    shouldCancel = { !FloatBallPickResultPanel.isStillShowing(panelToken) },
                )
                PickPerf.markStepDuration(
                    "barcode_async_end",
                    scanStart,
                    "count=${barcodeResults.size}"
                )
                if (barcodeResults.isEmpty()) return@launch
                withContext(Dispatchers.Main.immediate) {
                    FloatBallPickResultPanel.updateBarcodeResults(barcodeResults, panelToken)
                }
            } finally {
                if (!scanCopy.isRecycled) {
                    scanCopy.recycle()
                }
            }
        }
    }

    private fun launchDeferredOcr(
        context: Context,
        ocrModelId: String,
        result: FloatBallPickResult,
        switchToOcrOnComplete: Boolean
    ) {
        val bitmap = result.screenshot
        if (bitmap == null || bitmap.isRecycled) return
        val ocrCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        scope.launch(ocrDispatcher) {
            try {
                val ocrStart = SystemClock.elapsedRealtime()
                PickPerf.mark("ocr_async_start", "model=$ocrModelId")
                val ocrResult = RegionalScreenshotOcr.recognizeBitmapPublic(
                    context,
                    ocrModelId,
                    ocrCopy
                )
                PickPerf.markStepDuration("ocr_async_end", ocrStart, "len=${ocrResult.textOrNull()?.length ?: 0}")
                withContext(Dispatchers.Main.immediate) {
                    when (ocrResult) {
                        is com.slideindex.app.ocr.OcrRecognizeResult.Success -> {
                            FloatBallPickResultPanel.updateOcrText(ocrResult.text, switchToOcrOnComplete)
                        }
                        is com.slideindex.app.ocr.OcrRecognizeResult.Failure -> {
                            FloatBallPickResultPanel.finishOcrPending()
                            FloatBallPickResultPanel.showOcrError(context, ocrResult.reason)
                        }
                    }
                }
            } finally {
                if (!ocrCopy.isRecycled) {
                    ocrCopy.recycle()
                }
            }
        }
    }

    private suspend fun buildPickResult(
        context: Context,
        dragSelectRect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        ocrOnly: Boolean = false,
        deferOcr: Boolean = false,
        previewBoundsPick: Boolean = false,
        regionalRectPick: Boolean = false,
        ocrReady: Boolean? = null,
        /** 快路径：图还没拍，延后截图在路上（见 [scheduleDeferredPreviewScreenshot]）。 */
        deferredScreenshot: Boolean = false
    ): FloatBallPickResult {
        val rawAccessibility = InspireDataHolder.accessibilityContent.orEmpty()
        val a11yText = rawAccessibility.joinToString(separator = "").trim().takeIf { it.isNotEmpty() }

        val resolvedOcrReady = ocrReady ?: run {
            val ocrReadyStart = SystemClock.elapsedRealtime()
            val ready = isOcrReady(context, ocrFallbackEnabled, ocrModelId)
            PickPerf.markStepDuration("buildPickResult_ocrReady", ocrReadyStart, "ready=$ready")
            ready
        }

        val screenshotStart = SystemClock.elapsedRealtime()
        val screenshotHandle = InspireDataHolder.acquireScreenshotBitmap()
        PickPerf.markStepDuration(
            "buildPickResult_screenshot_acquire",
            screenshotStart,
            "has=${screenshotHandle != null}"
        )
        val ocrText = if (resolvedOcrReady && !deferOcr) {
            val ocrStart = SystemClock.elapsedRealtime()
            PickPerf.mark("ocr_start", "model=$ocrModelId")
            val recognized = screenshotHandle?.requireBitmap()?.let { bitmap ->
                withContext(ocrDispatcher) {
                    RegionalScreenshotOcr.recognizeBitmapPublic(context, ocrModelId, bitmap).textOrNull()
                }
            }
            PickPerf.markStepDuration("ocr_end", ocrStart, "len=${recognized?.length ?: 0}")
            recognized
        } else {
            PickPerf.mark(
                "ocr_skipped",
                "ocrFallback=$ocrFallbackEnabled model=$ocrModelId ocrOnly=$ocrOnly deferOcr=$deferOcr"
            )
            null
        }

        val screenshotCopy = screenshotHandle?.let { handle ->
            try {
                val bitmap = handle.requireBitmap()
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } finally {
                handle.close()
            }
        }

        val activeSource = when {
            deferOcr && previewBoundsPick && !a11yText.isNullOrBlank() -> PickResultTextSource.A11Y
            deferOcr && regionalRectPick -> PickResultTextSource.OCR
            deferOcr -> PickResultTextSource.OCR
            ocrOnly && !ocrText.isNullOrBlank() -> PickResultTextSource.OCR
            ocrText.isNullOrBlank() -> PickResultTextSource.A11Y
            a11yText.isNullOrBlank() -> PickResultTextSource.OCR
            else -> {
                val a11yLongest = a11yText.lines().maxOfOrNull { it.trim().length } ?: 0
                val ocrLongest = ocrText.lines().maxOfOrNull { it.trim().length } ?: 0
                if (ocrLongest > a11yLongest) PickResultTextSource.OCR else PickResultTextSource.A11Y
            }
        }

        val preferOcrOnDeferredComplete = deferOcr && regionalRectPick

        return FloatBallPickResult(
            a11yText = a11yText,
            ocrText = ocrText,
            screenshot = screenshotCopy,
            screenRect = Rect(dragSelectRect),
            layoutMeta = InspireDataHolder.screenshotLayoutMeta,
            activeSource = activeSource,
            ocrAvailable = resolvedOcrReady,
            ocrPending = deferOcr && resolvedOcrReady,
            ocrPreferSwitchOnComplete = preferOcrOnDeferredComplete,
            barcodeResults = emptyList(),
            awaitingDeferredScreenshot = deferredScreenshot
        )
    }

    /**
     * 预览框命中的「先开面板、延后截图」分支：快门在这里才按。
     *
     * ⚠️ 两点约束（都靠面板侧配合，见 `FloatBallPickResultPanel`）：
     * 1. 面板此时已经在屏幕上，所以它按「延后截图」分支把 reveal 挂起，等这张图落地才滑上来；
     *    这里不能反过来假设面板是不可见的。
     * 2. 本协程可能比发起它的那次取词活得久，落地前要用 [pickSession] 确认还是同一轮，
     *    否则迟到的图会落到下一轮面板上（图里带着上一轮的面板）。
     */
    private fun scheduleDeferredPreviewScreenshot(
        service: AccessibilityService,
        context: Context,
        dragSelectRect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        deferOcr: Boolean,
        ocrReady: Boolean? = null,
        pickSession: Int = 0
    ) {
        scope.launch(pickDispatcher) {
            // 排到队尾时可能已经开了新一轮取词：这一枪打下去只会白耗一次系统截图配额
            // （takeScreenshot 有最小间隔，白打一次会把新一轮那张图限流掉）。
            if (isStalePickSession(pickSession)) {
                PickPerf.mark("screenshot_deferred_dropped", "reason=stale_before_capture session=$pickSession")
                return@launch
            }
            val resolvedOcrReady = ocrReady ?: isOcrReady(context, ocrFallbackEnabled, ocrModelId)
            val crop = captureCroppedScreenshotCopy(service, dragSelectRect, deferred = true)
            if (isStalePickSession(pickSession)) {
                PickPerf.mark("screenshot_deferred_dropped", "reason=stale_after_capture session=$pickSession")
                crop?.bitmap?.recycle()
                return@launch
            }
            if (crop == null) {
                PickPerf.mark("screenshot_deferred_end", "bitmap=false")
                withContext(Dispatchers.Main.immediate) {
                    if (deferOcr && resolvedOcrReady) {
                        FloatBallPickResultPanel.finishOcrPending()
                    }
                    // 图没回来也得把面板放上来：它正按「延后截图」等着这张图，不能停在屏幕外。
                    FloatBallPickResultPanel.finishDeferredScreenshot()
                    FloatBallOverlay.onPickPanelDeferredScreenshotSkipped()
                }
                return@launch
            }
            val screenshot = crop.bitmap
            val layoutMeta = crop.layoutMeta
            val panelToken = withContext(Dispatchers.Main.immediate) {
                FloatBallPickResultPanel.updatePickScreenshot(
                    screenshot,
                    Rect(dragSelectRect),
                    layoutMeta
                )
                FloatBallPickResultPanel.showToken
            }
            PickPerf.mark("screenshot_deferred_delivered", "bitmap=true")
            val enriched = FloatBallPickResult(
                a11yText = null,
                ocrText = null,
                screenshot = screenshot,
                screenRect = Rect(dragSelectRect),
                layoutMeta = layoutMeta,
                ocrAvailable = resolvedOcrReady,
                ocrPending = deferOcr && resolvedOcrReady,
                ownsImages = false
            )
            launchDeferredBarcodeScan(enriched, panelToken)
            if (deferOcr) {
                launchDeferredOcr(
                    context = context,
                    ocrModelId = ocrModelId,
                    result = enriched,
                    switchToOcrOnComplete = false
                )
            }
        }
    }

    /** [pickSession] 为 0 表示该入口不追踪会话（没有延后截图，如 [InspireFloating]）。 */
    private fun isStalePickSession(pickSession: Int): Boolean =
        pickSession != 0 && pickSession != pickSessionSeq.get()

    private suspend fun captureScreenshotIntoHolder(
        service: AccessibilityService,
        dragSelectRect: Rect,
        deferred: Boolean
    ) {
        PickPerf.mark("overlays_hide_start")
        withOverlaysHiddenForCapture {
            PickPerf.mark("overlays_hide_end")
            val shotStart = SystemClock.elapsedRealtime()
            val stepPrefix = if (deferred) "screenshot_deferred" else "screenshot"
            PickPerf.mark("${stepPrefix}_start")
            val cropStart = SystemClock.elapsedRealtime()
            val crop = RegionalScreenshotOcr.captureRectBitmap(
                service = service,
                screenRect = dragSelectRect,
                edgePaddingPx = 0
            )
            PickPerf.markStepDuration(
                "${stepPrefix}_crop_done",
                cropStart,
                "cropped=${crop != null}"
            )
            if (crop != null) {
                InspireDataHolder.replaceScreenshotBitmap(
                    ManagedBitmap.from(crop.bitmap),
                    crop.layoutMeta
                )
                PickPerf.markStepDuration("${stepPrefix}_end", shotStart)
            } else {
                PickPerf.markStepDuration("${stepPrefix}_end", shotStart, "no_bitmap")
            }
        }
    }

    private suspend fun captureCroppedScreenshotCopy(
        service: AccessibilityService,
        dragSelectRect: Rect,
        deferred: Boolean
    ): RegionalScreenshotCrop? {
        var crop: RegionalScreenshotCrop? = null
        PickPerf.mark("overlays_hide_start")
        withOverlaysHiddenForCapture(deferOverlayRestore = deferred) {
            PickPerf.mark("overlays_hide_end")
            val shotStart = SystemClock.elapsedRealtime()
            val stepPrefix = if (deferred) "screenshot_deferred" else "screenshot"
            PickPerf.mark("${stepPrefix}_start")
            val cropStart = SystemClock.elapsedRealtime()
            val captured = RegionalScreenshotOcr.captureRectBitmap(
                service = service,
                screenRect = dragSelectRect,
                edgePaddingPx = 0
            )
            PickPerf.markStepDuration(
                "${stepPrefix}_crop_done",
                cropStart,
                "cropped=${captured != null}"
            )
            if (captured != null) {
                val copy = captured.bitmap.copy(captured.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                captured.bitmap.recycle()
                if (copy != null) {
                    crop = RegionalScreenshotCrop(copy, captured.layoutMeta)
                }
                PickPerf.markStepDuration("${stepPrefix}_end", shotStart)
            } else {
                PickPerf.markStepDuration("${stepPrefix}_end", shotStart, "no_bitmap")
            }
        }
        return crop
    }

    private suspend fun <T> withOverlaysHiddenForCapture(
        deferOverlayRestore: Boolean = false,
        block: suspend () -> T
    ): T {
        withContext(Dispatchers.Main.immediate) {
            FloatingPointerOverlayWindow.suppressForScreenshotCapture()
            FloatBallOverlay.suppressForScreenshotCapture()
            InspireFloating.hide()
            // 边缘触钮画在触钮窗上，不隐藏同样会被 takeScreenshot 拍进全屏截图。
            SlideIndexAccessibilityService.suppressEdgeChromeForCapture()
            // 贴边收纳把手是另一个常驻窗（HistoryFloatService），也会被拍进去。
            HistoryHandleCaptureVisibility.suppress()
        }
        // 上面全是 View/窗口操作：要等它们真的合成过一帧再按快门。
        // 只 delay 一个固定值时，主线程一卡顿就会拍在"浮层还没撤下去"的那一帧上（偶发遮挡）。
        awaitCaptureFrame()
        delay(CAPTURE_HIDE_DELAY_MS)
        return try {
            block()
        } finally {
            if (deferOverlayRestore) {
                scope.launch(Dispatchers.Main.immediate) {
                    FloatingPointerOverlayWindow.restoreAfterScreenshotCapture()
                    FloatBallOverlay.restoreAfterScreenshotCapture()
                    SlideIndexAccessibilityService.restoreEdgeChromeAfterCapture()
                    HistoryHandleCaptureVisibility.restore()
                }
            } else {
                withContext(Dispatchers.Main.immediate) {
                    FloatingPointerOverlayWindow.restoreAfterScreenshotCapture()
                    FloatBallOverlay.restoreAfterScreenshotCapture()
                    SlideIndexAccessibilityService.restoreEdgeChromeAfterCapture()
                    HistoryHandleCaptureVisibility.restore()
                }
            }
        }
    }

    /** 等一次合成帧（对齐 [SlideIndexAccessibilityService.performSmartScreenshot] 的写法）；回调丢失时最多等 150ms。 */
    private suspend fun awaitCaptureFrame() {
        withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(CAPTURE_FRAME_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    Choreographer.getInstance().postFrameCallback {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }
    }

    private fun rectBetween(startX: Float, startY: Float, endX: Float, endY: Float): Rect {
        val left = minOf(startX, endX).toInt()
        val top = minOf(startY, endY).toInt()
        val right = maxOf(startX, endX).toInt()
        val bottom = maxOf(startY, endY).toInt()
        return Rect(left, top, right, bottom)
    }
}
