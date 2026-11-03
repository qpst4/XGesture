package com.slideindex.app.overlay.corner

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.data.AppRepository
import com.slideindex.app.overlay.ModuleForwardedTouchGate
import com.slideindex.app.overlay.OverlayBlurGate
import com.slideindex.app.overlay.OverlayPassthrough
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.CornerGestureSlotPickTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.OverlaySuppression
import com.slideindex.app.util.OverlaySuppressionScope
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

internal class CornerGestureController(
    private val context: Context,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val zoneLayout = CornerZoneLayout()
    private var settings = AppSettings()
    private var density = context.resources.displayMetrics.density
    private var suspendedForExternalActivity = false
    private var savedOverlayFlags: Int? = null
    private var suspendedCaptureAnchor: CornerAnchor? = null
    private var suspendedCaptureStrip: CornerZoneStrip? = null

    private data class CaptureSlot(
        val anchor: CornerAnchor,
        val strip: CornerZoneStrip,
        val host: FrameLayout,
        val params: WindowManager.LayoutParams,
    )

    private val captureSlots = mutableListOf<CaptureSlot>()
    private var overlayRoot: FrameLayout? = null
    private var overlayView: CornerGestureOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayAttached = false
    private var capturesAttached = false
    private var expandedCaptureAnchor: CornerAnchor? = null
    private var expandedCaptureStrip: CornerZoneStrip? = null
    /**
     * 输入层接管（system_server 模块）用的命中快照。
     *
     * 由主线程在 [applySettings] 里刷新，binder 线程只读；不做 UI 状态读取，保证跨线程安全。
     * 未启用 / 被抑制 / 当前方向不生效时为空列表，模块据此完全放行。
     */
    @Volatile
    private var forwardedZones: List<ForwardedZone> = emptyList()

    /** 单个角落触发带的屏幕坐标（不可变，供 binder 线程读取）。 */
    private data class ForwardedZone(
        val anchor: CornerAnchor,
        val strip: CornerZoneStrip,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )
    private var zonePreviewActive = false
    private var previewRoot: FrameLayout? = null
    private var previewView: CornerZonePreviewView? = null
    private var previewParams: WindowManager.LayoutParams? = null
    private var previewAttached = false

    fun setZonePreviewActive(active: Boolean) {
        zonePreviewActive = active
        if (active) {
            ensurePreviewView()
            syncPreviewWindow()
        } else {
            detachPreview()
        }
    }

    fun applyZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) {
        if (!zonePreviewActive) return
        val corner = settings.cornerGestureSettings.copy(
            verticalEdgeWidthDp = CornerGestureSettings.clampVerticalEdgeWidthDp(verticalEdgeWidthDp),
            verticalEdgeHeightDp = CornerGestureSettings.clampVerticalEdgeHeightDp(verticalEdgeHeightDp),
            horizontalEdgeWidthDp = CornerGestureSettings.clampHorizontalEdgeWidthDp(horizontalEdgeWidthDp),
            horizontalEdgeHeightDp = CornerGestureSettings.clampHorizontalEdgeHeightDp(horizontalEdgeHeightDp),
        )
        syncZoneLayout(corner)
        previewView?.update(zoneLayout, corner, density)
    }

    fun applySettings(settings: AppSettings) {
        this.settings = settings
        this.density = context.resources.displayMetrics.density
        val corner = settings.cornerGestureSettings
        syncZoneLayout(corner)
        refreshForwardedZones()
        if (zonePreviewActive) {
            ensurePreviewView()
            previewView?.update(zoneLayout, corner, density)
            syncPreviewWindow()
        }
        if (!settings.serviceEnabled || !corner.enabled) {
            detachPreview()
            detachGestureLayers()
            return
        }
        if (isCornerWheelSuppressed(settings)) {
            detachPreview()
            detachGestureLayers()
            return
        }
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!corner.isActiveInCurrentOrientation(landscape)) {
            detachCaptures()
            detachOverlay()
            return
        }
        ensureViews()
        syncCaptureWindows()
        if (!overlayView!!.isOverlayVisible()) {
            detachOverlay()
        }
    }

    fun onConfigurationChanged() {
        applySettings(settings)
    }

    fun refreshSuppression() {
        applySettings(settings)
    }

    fun openSlotPicker(anchor: CornerAnchor, slotIndex: Int) {
        val view = overlayView ?: return
        if (!view.isOverlayVisible()) return
        suspendOverlayForExternalActivity()
        val corner = when (anchor) {
            CornerAnchor.LEFT -> CornerGestureSlotPickTrampolineActivity.CORNER_LEFT
            CornerAnchor.RIGHT -> CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT
        }
        val intent = CornerGestureSlotPickTrampolineActivity.createIntent(context, corner, slotIndex).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun resumeAfterSlotPicker() {
        if (!suspendedForExternalActivity) return
        resumeOverlayAfterExternalActivity()
    }

    /** 是否仍处于"为外部编辑页让位"的挂起态（供 overlay 侧前台变化时判断要不要自愈）。 */
    fun isSuspendedForExternalActivity(): Boolean = suspendedForExternalActivity

    private fun suspendOverlayForExternalActivity() {
        if (!overlayAttached || suspendedForExternalActivity) return
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        val view = overlayView ?: return
        if (!view.isOverlayVisible()) return
        suspendedForExternalActivity = true
        savedOverlayFlags = params.flags
        suspendedCaptureAnchor = expandedCaptureAnchor
        suspendedCaptureStrip = expandedCaptureStrip
        restoreCaptureSize()
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        root.visibility = View.GONE
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun resumeOverlayAfterExternalActivity() {
        if (!suspendedForExternalActivity) return
        suspendedForExternalActivity = false
        if (!overlayAttached) {
            savedOverlayFlags = null
            suspendedCaptureAnchor = null
            suspendedCaptureStrip = null
            return
        }
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        savedOverlayFlags?.let { params.flags = it }
        savedOverlayFlags = null
        val anchor = suspendedCaptureAnchor
        val strip = suspendedCaptureStrip
        suspendedCaptureAnchor = null
        suspendedCaptureStrip = null
        if (anchor != null && strip != null) {
            expandCaptureForSession(anchor, strip)
        }
        root.visibility = View.VISIBLE
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
        runCatching { windowManager.updateViewLayout(root, params) }
        overlayView?.invalidate()
    }

    private fun isCornerWheelSuppressed(settings: AppSettings): Boolean =
        OverlaySuppression.shouldSuppress(
            settings = settings,
            context = context,
            foregroundPackage = OverlayService.foregroundPackage,
            scope = OverlaySuppressionScope.CORNER_WHEEL,
        )

    fun destroy() {
        detachPreview()
        detachGestureLayers()
        overlayView = null
        overlayRoot = null
        captureSlots.clear()
        previewView = null
        previewRoot = null
    }

    private fun detachGestureLayers() {
        detachOverlay()
        detachCaptures()
    }

    private fun ensurePreviewView() {
        if (previewView == null) {
            previewView = CornerZonePreviewView(context)
            previewRoot = FrameLayout(context).apply {
                addView(
                    previewView,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            previewParams = OverlayWindowTypes.createPresentationParams(context).apply {
                OverlayWindowTypes.applyFullScreen(this)
            }
        }
        previewView?.update(zoneLayout, settings.cornerGestureSettings, density)
    }

    private fun syncPreviewWindow() {
        if (!zonePreviewActive) {
            detachPreview()
            return
        }
        val corner = settings.cornerGestureSettings
        if (!settings.serviceEnabled || !corner.enabled) {
            detachPreview()
            return
        }
        val root = previewRoot ?: return
        val params = previewParams ?: return
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.applyPresentationPassthroughFlags(params)
        if (!previewAttached) {
            OverlayWindowTypes.ensureNoBrightnessOverride(params)
            runCatching { windowManager.addView(root, params) }
                .onSuccess { previewAttached = true }
                .onFailure { Log.e(TAG, "Failed to attach corner zone preview", it) }
        } else {
            previewView?.update(zoneLayout, corner, density)
        }
    }

    private fun detachPreview() {
        zonePreviewActive = false
        if (!previewAttached) return
        previewRoot?.let { runCatching { windowManager.removeView(it) } }
        previewAttached = false
    }

    private fun ensureViews() {
        if (overlayView == null) {
            overlayView = CornerGestureOverlayView(
                context = context,
                appRepository = appRepository,
                onSessionEnd = {
                    restoreCaptureSize()
                    syncOverlayBackgroundBlur(active = false)
                    detachOverlay()
                },
                onReleaseCapture = ::restoreCaptureSize,
                onOpenSlotPicker = ::openSlotPicker,
                onShellCommandsPersist = onShellCommandsPersist,
                onMenuVisualActiveChange = ::syncOverlayBackgroundBlur,
            )
            overlayRoot = FrameLayout(context).apply {
                addView(
                    overlayView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            overlayParams = OverlayWindowTypes.createPresentationParams(context).apply {
                OverlayWindowTypes.applyFullScreen(this)
            }
        }
        ensureCaptureSlots()
        overlayView?.applySettings(settings, density)
    }

    private fun ensureCaptureSlots() {
        CornerAnchor.entries.forEach { anchor ->
            CornerZoneStrip.entries.forEach { strip ->
                if (captureSlots.none { it.anchor == anchor && it.strip == strip }) {
                    val host = FrameLayout(context).apply {
                        addView(
                            CornerTouchCaptureView(context) { event ->
                                handleCaptureTouch(anchor, strip, event)
                            },
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                    captureSlots += CaptureSlot(
                        anchor = anchor,
                        strip = strip,
                        host = host,
                        params = OverlayWindowTypes.createCaptureParams(
                            context,
                            "XG:corner-${anchor.name.lowercase()}-${strip.name.lowercase()}",
                        ),
                    )
                }
            }
        }
    }

    private fun syncCaptureWindows() {
        val corner = settings.cornerGestureSettings
        attachCapturesIfNeeded()
        captureSlots.forEach { slot ->
            val anchorEnabled = when (slot.anchor) {
                CornerAnchor.LEFT -> corner.leftEnabled
                CornerAnchor.RIGHT -> corner.rightEnabled
            }
            val enabled = anchorEnabled &&
                corner.hasActiveTriggerZone() &&
                stripEnabled(corner, slot.strip)
            syncCaptureSlot(slot, enabled)
        }
        syncExclusionWindows(corner)
    }

    private fun syncCaptureSlot(slot: CaptureSlot, enabled: Boolean) {
        if (!enabled) {
            if (slot.host.parent != null) {
                runCatching { windowManager.removeView(slot.host) }
            }
            return
        }
        val params = slot.params
        if (expandedCaptureAnchor == slot.anchor) {
            if (expandedCaptureStrip == slot.strip) {
                applyFullScreenCapture(params)
            } else {
                if (slot.host.parent != null) {
                    runCatching { windowManager.removeView(slot.host) }
                }
                return
            }
        } else {
            val rect = zoneLayout.stripRect(slot.anchor, slot.strip) ?: run {
                if (slot.host.parent != null) {
                    runCatching { windowManager.removeView(slot.host) }
                }
                return
            }
            applyStripCaptureLayout(params, rect)
        }
        if (slot.host.parent == null) {
            OverlayWindowTypes.ensureNoBrightnessOverride(params)
            runCatching { windowManager.addView(slot.host, params) }
                .onFailure { Log.e(TAG, "Failed to attach corner capture", it) }
        } else {
            OverlayWindowTypes.ensureNoBrightnessOverride(params)
            runCatching { windowManager.updateViewLayout(slot.host, params) }
                .onFailure { Log.e(TAG, "Failed to update corner capture", it) }
        }
    }

    private fun expandCaptureForSession(anchor: CornerAnchor, strip: CornerZoneStrip) {
        expandedCaptureAnchor = anchor
        expandedCaptureStrip = strip
        syncCaptureWindows()
    }

    private fun restoreCaptureSize() {
        if (expandedCaptureAnchor == null) return
        expandedCaptureAnchor = null
        expandedCaptureStrip = null
        syncCaptureWindows()
    }

    private fun applyStripCaptureLayout(params: WindowManager.LayoutParams, rect: RectF) {
        params.width = rect.width().toInt().coerceAtLeast(1)
        params.height = rect.height().toInt().coerceAtLeast(1)
        params.x = rect.left.toInt()
        params.y = rect.top.toInt()
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun applyFullScreenCapture(params: WindowManager.LayoutParams) {
        val (screenW, screenH) = OverlayScreenMetrics.sizePx(context)
        params.width = screenW
        params.height = screenH
        params.x = 0
        params.y = 0
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun syncZoneLayout(corner: CornerGestureSettings) {
        val (screenW, screenH) = OverlayScreenMetrics.sizePx(context)
        zoneLayout.update(screenW, screenH, density, corner)
    }

    /** 刷新 [forwardedZones]：只在真正可用时给出矩形，其余情况一律空（模块放行）。 */
    private fun refreshForwardedZones() {
        val corner = settings.cornerGestureSettings
        val landscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val usable = settings.serviceEnabled &&
            corner.enabled &&
            corner.hasActiveTriggerZone() &&
            corner.isActiveInCurrentOrientation(landscape) &&
            !isCornerWheelSuppressed(settings)
        if (!usable) {
            forwardedZones = emptyList()
            return
        }
        forwardedZones = FORWARD_ANCHORS.flatMap { anchor ->
            val anchorEnabled = when (anchor) {
                CornerAnchor.LEFT -> corner.leftEnabled
                CornerAnchor.RIGHT -> corner.rightEnabled
            }
            if (!anchorEnabled) {
                emptyList()
            } else {
                FORWARD_STRIPS.mapNotNull { strip ->
                    val rect = zoneLayout.stripRect(anchor, strip) ?: return@mapNotNull null
                    if (rect.width() <= 0f || rect.height() <= 0f) {
                        null
                    } else {
                        ForwardedZone(
                            anchor = anchor,
                            strip = strip,
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                        )
                    }
                }
            }
        }
    }

    /** binder 线程调用：这一点是否落在当前可用的角轮盘触发带上。 */
    fun canAcceptForwardedTouchAt(anchor: CornerAnchor, x: Float, y: Float): Boolean =
        forwardedZones.any { zone ->
            zone.anchor == anchor &&
                x >= zone.left && x <= zone.right && y >= zone.top && y <= zone.bottom
        }

    /**
     * 输入层接管转发来的触摸：直接复用既有会话路径（与窗口触摸完全同一条流程），
     * 因而捕获窗扩展、松开后的 passthrough、会话结束回收都自动一致。
     */
    fun handleForwardedTouch(anchor: CornerAnchor, event: android.view.MotionEvent) {
        val strip = stripForPoint(anchor, event.rawX, event.rawY)
        handleCaptureTouch(anchor, strip, event)
    }

    /** 输入层会话结束：结束轮盘会话并把捕获窗收回空闲尺寸。 */
    fun cancelForwardedTouch(anchor: CornerAnchor) {
        overlayView?.cancelSession()
        expandedCaptureAnchor = null
        expandedCaptureStrip = null
        if (capturesAttached) syncCaptureWindows()
    }

    private fun stripForPoint(anchor: CornerAnchor, x: Float, y: Float): CornerZoneStrip =
        FORWARD_STRIPS.firstOrNull { strip ->
            val rect = zoneLayout.stripRect(anchor, strip) ?: return@firstOrNull false
            rect.width() > 0f && rect.height() > 0f && rect.contains(x, y)
        } ?: CornerZoneStrip.VERTICAL

    private fun stripEnabled(corner: CornerGestureSettings, strip: CornerZoneStrip): Boolean =
        when (strip) {
            CornerZoneStrip.VERTICAL ->
                corner.verticalEdgeWidthDp > 0f && corner.verticalEdgeHeightDp > 0f
            CornerZoneStrip.HORIZONTAL ->
                corner.horizontalEdgeWidthDp > 0f && corner.horizontalEdgeHeightDp > 0f
        }

    private fun attachCapturesIfNeeded() {
        capturesAttached = true
    }

    private fun detachCaptures() {
        captureSlots.forEach { slot ->
            runCatching { windowManager.removeView(slot.host) }
        }
        capturesAttached = false
    }

    private fun syncExclusionWindows(corner: CornerGestureSettings) {
        // 系统手势排除直接设在 capture 窗口上，不再单独开“exclusion 窗口”：
        // 每个 corner 原来是 capture + exclusion 两层，现在只剩一层。
        captureSlots.forEach { slot ->
            val anchorEnabled = when (slot.anchor) {
                CornerAnchor.LEFT -> corner.leftEnabled
                CornerAnchor.RIGHT -> corner.rightEnabled
            }
            val enabled = corner.overrideSystemNav &&
                anchorEnabled &&
                corner.hasActiveTriggerZone() &&
                stripEnabled(corner, slot.strip)
            applyStripExclusionRects(slot, enabled)
        }
    }

    private fun applyStripExclusionRects(slot: CaptureSlot, enabled: Boolean) {
        val rect = if (enabled) zoneLayout.stripRect(slot.anchor, slot.strip) else null
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) {
            slot.host.systemGestureExclusionRects = emptyList()
            return
        }
        slot.host.systemGestureExclusionRects = listOf(
            Rect(0, 0, rect.width().toInt(), rect.height().toInt()),
        )
    }

    private fun ensureOverlayAttached() {
        if (overlayAttached) return
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        val view = overlayView ?: return
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        applyBackgroundBlurFlags(params, active = false)
        runCatching { windowManager.addView(root, params) }
            .onSuccess {
                overlayAttached = true
                view.applySettings(settings, density)
            }
            .onFailure { Log.e(TAG, "Failed to attach corner overlay", it) }
    }

    private fun detachOverlay() {
        if (!overlayAttached) return
        suspendedForExternalActivity = false
        savedOverlayFlags = null
        suspendedCaptureAnchor = null
        suspendedCaptureStrip = null
        overlayView?.cancelSession()
        syncOverlayBackgroundBlur(active = false)
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayAttached = false
    }

    private fun syncOverlayBackgroundBlur(active: Boolean) {
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        if (!overlayAttached || !root.isAttachedToWindow) return
        applyBackgroundBlurFlags(params, active = active)
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "Failed to update corner overlay blur", it) }
    }

    private fun applyBackgroundBlurFlags(
        params: WindowManager.LayoutParams,
        active: Boolean,
    ) {
        val corner = settings.cornerGestureSettings
        val wantsBlur = active &&
            corner.backgroundStyle == CornerGestureSettings.BACKGROUND_BLUR &&
            corner.blurDp > 0
        val canNativeBlur = wantsBlur &&
            OverlayBlurGate.canBlur(windowManager, userEnabled = settings.overlayBlurEnabled)
        if (canNativeBlur) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val rawBlurPx = (corner.blurDp * density).roundToInt()
            params.setBlurBehindRadius(rawBlurPx.coerceIn(1, 80))
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.setBlurBehindRadius(0)
        }
    }

    private fun handleCaptureTouch(
        anchor: CornerAnchor,
        strip: CornerZoneStrip,
        event: MotionEvent,
    ): Boolean {
        if (!settings.cornerGestureSettings.enabled) return false
        val view = overlayView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 从外部编辑页（槽位编辑等）返回后可能仍处于"可视层已暂停"状态：
                // 旧实现靠进程内静态回调恢复，拆进程后那个回调落在主进程、这里是空操作，
                // 结果就是"轮盘不显示但槽位震动还在"（捕获窗是独立窗口，仍可触摸）。
                // 新会话开始时自愈一次，保证下一次触发一定能看到轮盘。
                if (suspendedForExternalActivity) {
                    resumeOverlayAfterExternalActivity()
                }
                if (view.isWheelPinned()) {
                    ensureOverlayAttached()
                    return view.handlePinnedTouchEvent(event)
                }
                if (!zoneLayout.contains(anchor, event.rawX, event.rawY)) return false
                ensureOverlayAttached()
                expandCaptureForSession(anchor, strip)
                view.beginSession(anchor, event)
                return view.handleTouch(event)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (view.isWheelPinned()) {
                    ensureOverlayAttached()
                    return view.handlePinnedTouchEvent(event)
                }
                if (!view.isSessionActive()) return false
                val passthrough = event.actionMasked == MotionEvent.ACTION_UP &&
                    view.shouldPassthroughTap()
                val consumed = view.handleTouch(event)
                if (passthrough) {
                    performTapPassthrough(event.rawX, event.rawY)
                }
                return consumed
            }
        }
        return false
    }

    private fun performTapPassthrough(rawX: Float, rawY: Float) {
        // 只拦注入回声，不拦用户新点击（旧实现 1500ms 全拦 → 接管区里单击必失效）。
        com.slideindex.app.diagnostic.EdgeDiag.log(
            "passthrough",
            "角轮盘放行 at=($rawX,$rawY) pending=${ModuleForwardedTouchGate.isInjectionPending()} " +
                "echo=${ModuleForwardedTouchGate.isInjectedEcho(rawX, rawY)}"
        )
        OverlayPassthrough.run(
            hideTriggers = {
                restoreCaptureSize()
                detachOverlay()
                detachCaptures()
            },
            showTriggers = { syncCaptureWindows() },
            rawX = rawX,
            rawY = rawY,
            onComplete = {},
        )
    }

    companion object {
        private const val TAG = "CornerGestureController"
        private val FORWARD_ANCHORS = listOf(CornerAnchor.LEFT, CornerAnchor.RIGHT)
        private val FORWARD_STRIPS = listOf(CornerZoneStrip.VERTICAL, CornerZoneStrip.HORIZONTAL)
    }
}
