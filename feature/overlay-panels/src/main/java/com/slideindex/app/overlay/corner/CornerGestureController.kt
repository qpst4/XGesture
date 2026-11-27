package com.slideindex.app.overlay.corner

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.data.AppRepository
import com.slideindex.app.overlay.ModuleForwardedTouchGate
import com.slideindex.app.overlay.OverlayBlurGate
import com.slideindex.app.overlay.OverlayMaterialSpec
import com.slideindex.app.overlay.OverlayPassthrough
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.OverlayWindowGuard
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.shell.ShellCommand
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class CornerGestureController(
    private val context: Context,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit,
    private val gestureRunner: com.slideindex.app.overlay.panels.GestureActionRunner,
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
     * 全屏捕获窗的守卫。
     *
     * 角轮盘会话期间捕获窗会扩到整屏（手指要能拖到屏幕任何位置）。但整屏窗口只要没被收回，
     * 它就会把下面所有应用的触摸吃掉（窗口范围内的 DOWN 不会再转发给下层窗口）——历史故障：
     * 轮盘可视层已经被系统摘掉/隐藏，捕获窗还停在整屏，整屏只有状态栏/导航手势能动。
     * 因此这里只认“可视层真的挂着 + 会话真的活着”，否则定时收回。
     */
    private val captureGuardHandler = Handler(Looper.getMainLooper())
    private var captureGuardScheduled = false
    private var lastCaptureTouchAtMs = 0L
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
            CornerAnchor.LEFT -> "left"
            CornerAnchor.RIGHT -> "right"
        }
        val intent = com.slideindex.app.overlay.panels.PanelRuntimeHooks.cornerSlotPickIntent
            ?.invoke(context, corner, slotIndex) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 编辑模式长按拖动换位后写回（反馈 #9 / #4）。
     *
     * 采用**插入**语义（与快捷轮盘一致）：把 from 的动作取出来插到 to，
     * 区间内其它槽位整体平移一格，而不是两个位置直接交换。
     * 仓库从既有钩子拿，不新增静态入口；写完由 AppSettings 回流刷新。
     */
    private fun moveCornerSlot(anchor: CornerAnchor, from: Int, to: Int) {
        if (from == to) return
        val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsRepository
            ?.invoke(context) ?: return
        val cornerSettings = settings.cornerGestureSettings
        val isLeft = cornerSettings.unifiedSlots || anchor == CornerAnchor.LEFT
        val slots = (if (isLeft) cornerSettings.leftSlots else cornerSettings.rightSlots).toMutableList()
        if (from !in slots.indices || to !in slots.indices) return
        val moved = slots.removeAt(from)
        slots.add(to, moved)
        // 一次事务写完整组槽位：逐槽位写会让轮盘把中间顺序也画出来（图标一个一个挪）。
        scope.launch {
            runCatching { repository.setCornerGestureSlots(isLeft, slots) }
        }
    }
    fun resumeAfterSlotPicker() {        if (!suspendedForExternalActivity) return
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
        // 先把可视层恢复成“可见 + 可交互”，再放大捕获窗：
        // syncCaptureWindows() 只有在可视层真的可见时才允许全屏，顺序反了会被它立即收回。
        root.visibility = View.VISIBLE
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
        runCatching { windowManager.updateViewLayout(root, params) }
        if (anchor != null && strip != null) {
            expandCaptureForSession(anchor, strip)
        }
        overlayView?.invalidate()
    }

    private fun isCornerWheelSuppressed(settings: AppSettings): Boolean =
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.suppressTrigger
            ?.invoke(settings, context, true) == true

    fun destroy() {
        cancelCaptureGuard()
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
            runCatching {
                OverlayWindowGuard.addView(windowManager, root, params, "corner-zone-preview")
            }
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
                onMoveSlots = ::moveCornerSlot,
                onShellCommandsPersist = onShellCommandsPersist,
                gestureRunner = gestureRunner,
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
            // 系统自己摘窗（无障碍重连 / 进程回收）不会走 detachOverlay()：本地 overlayAttached
            // 会一直停在 true，之后既不会再挂回去，捕获窗也永远缩不回来。必须在这里兜住。
            overlayRoot?.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit

                    override fun onViewDetachedFromWindow(v: View) {
                        if (v !== overlayRoot || !overlayAttached) return
                        Log.w(TAG, "corner overlay detached by system → collapse capture")
                        overlayAttached = false
                        restoreCaptureSize()
                    }
                },
            )
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
        // 全屏捕获窗只在“轮盘可视层真的挂着、可见、可交互”时才允许存在。
        // 否则一旦会话收尾漏了（丢 UP / 系统摘窗 / 挂起恢复失败），整屏触摸会被这个
        // 全屏窗口吃掉：窗口范围内的 DOWN 不会转发给下层应用，表现就是整机点不动。
        if (expandedCaptureAnchor != null && !isOverlayShown()) {
            Log.w(TAG, "syncCaptureWindows: overlay not shown → collapse full-screen capture")
            expandedCaptureAnchor = null
            expandedCaptureStrip = null
            cancelCaptureGuard()
        }
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
            runCatching {
                OverlayWindowGuard.addView(windowManager, slot.host, params, "corner-capture")
            }
                .onFailure { Log.e(TAG, "Failed to attach corner capture", it) }
        } else {
            OverlayWindowTypes.ensureNoBrightnessOverride(params)
            runCatching { windowManager.updateViewLayout(slot.host, params) }
                .onFailure {
                    // 尺寸没改成功就绝不能留着一个可能还是整屏的窗口：先摘掉，下一次 sync 会重新挂小条。
                    Log.e(TAG, "Failed to update corner capture, drop window", it)
                    runCatching { windowManager.removeView(slot.host) }
                }
        }
    }

    private fun expandCaptureForSession(anchor: CornerAnchor, strip: CornerZoneStrip) {
        expandedCaptureAnchor = anchor
        expandedCaptureStrip = strip
        syncCaptureWindows()
        scheduleCaptureGuard()
    }

    /**
     * 收回全屏捕获窗。
     *
     * 这里**不能**再用 `expandedCaptureAnchor == null` 当早退条件：状态字段和窗口真实尺寸
     * 可能已经不同步（异常被吞、系统摘窗等），一旦字段先被清掉，这个函数就再也不会把
     * 整屏窗口改回小条，窗口就永久吃掉整屏触摸。所以一律按“小条尺寸”重设已挂载的窗口。
     */
    private fun restoreCaptureSize() {
        expandedCaptureAnchor = null
        expandedCaptureStrip = null
        cancelCaptureGuard()
        // 走完整 sync：会话期间为了放大，另一条触发带（如右侧横向小条）会被临时摘掉，
        // 这里要把它加回来，否则“轮盘用一次之后另一个角就哑了”，直到下次设置刷新才恢复。
        syncCaptureWindows()
    }

    /** 只重设已挂载捕获窗的尺寸，不新增窗口（拆卸路径不能顺手把窗口加回来）。 */
    private fun collapseCaptureLayout() {
        captureSlots.forEach { slot ->
            if (slot.host.parent == null) return@forEach
            val rect = zoneLayout.stripRect(slot.anchor, slot.strip) ?: return@forEach
            applyStripCaptureLayout(slot.params, rect)
            OverlayWindowTypes.ensureNoBrightnessOverride(slot.params)
            runCatching { windowManager.updateViewLayout(slot.host, slot.params) }
                .onFailure {
                    Log.e(TAG, "collapseCaptureLayout: update failed, drop window", it)
                    runCatching { windowManager.removeView(slot.host) }
                }
        }
    }

    private fun scheduleCaptureGuard(delayMs: Long = CAPTURE_GUARD_INTERVAL_MS) {
        if (captureGuardScheduled) return
        captureGuardScheduled = true
        captureGuardHandler.postDelayed(captureGuard, delayMs)
    }

    private fun cancelCaptureGuard() {
        captureGuardScheduled = false
        captureGuardHandler.removeCallbacks(captureGuard)
    }

    /** 定时校验“全屏捕获窗”的前提是否还成立，不成立就强制收尾（整屏被吃时的最后一道保险）。 */
    private val captureGuard = Runnable {
        captureGuardScheduled = false
        if (expandedCaptureAnchor == null) return@Runnable
        val view = overlayView
        if (!isOverlayShown()) {
            releaseStuckCapture("overlay not shown")
            return@Runnable
        }
        if (view == null || (!view.isSessionActive() && !view.isWheelPinned())) {
            releaseStuckCapture("no live session")
            return@Runnable
        }
        if (!view.isWheelPinned() &&
            SystemClock.uptimeMillis() - lastCaptureTouchAtMs > CAPTURE_STALL_MS
        ) {
            releaseStuckCapture("no touch for ${CAPTURE_STALL_MS}ms")
            return@Runnable
        }
        // 钉住的轮盘本来就可以长时间停在屏幕上，低频巡检即可。
        scheduleCaptureGuard(
            if (view.isWheelPinned()) CAPTURE_PINNED_GUARD_INTERVAL_MS else CAPTURE_GUARD_INTERVAL_MS,
        )
    }

    private fun releaseStuckCapture(reason: String) {
        Log.w(TAG, "capture guard: $reason → collapse full-screen capture")
        overlayView?.cancelSession()
        restoreCaptureSize()
        detachOverlay()
    }

    /** 全屏捕获窗的唯一合法前提：可视层已挂载、可见、且可交互。 */
    private fun isOverlayShown(): Boolean {
        if (!overlayAttached) return false
        val root = overlayRoot ?: return false
        if (!root.isShown) return false
        val flags = overlayParams?.flags ?: return false
        return flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
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

    /**
     * 输入层会话结束：结束轮盘会话并把捕获窗收回空闲尺寸。
     *
     * [keepPinned] 为 true（模块正常松手结束）时，如果轮盘已经钉住，这里**不动它**：
     * 钉住是设计上的常驻状态（"无动作"/编辑模式），模块发来的会话结束只代表"这一笔手指走了"，
     * 不代表要关轮盘。以前一刀切 `cancelSession()`，真机表现就是：滑到圆心或 edit 按钮松手，
     * 钉住的轮盘在 1ms 后被这里清掉（看起来像闪退）。
     *
     * 此时全屏捕获窗保持展开（轮盘还要接后续的点击/拖动），由 [captureGuard] 按钉住态低频巡检兜底；
     * 用户真正关掉轮盘时走 `onSessionEnd` → `restoreCaptureSize()` + `detachOverlay()`。
     */
    fun cancelForwardedTouch(anchor: CornerAnchor, keepPinned: Boolean = false) {
        val view = overlayView
        if (keepPinned && view?.isWheelPinned() == true) return
        view?.cancelSession()
        expandedCaptureAnchor = null
        expandedCaptureStrip = null
        cancelCaptureGuard()
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
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        val view = overlayView ?: return
        // 系统摘窗时我们的标志可能没跟上（回调丢失）：按未挂载处理，走下面的 addView 重建。
        if (overlayAttached && !root.isAttachedToWindow) {
            Log.w(TAG, "ensureOverlayAttached: root no longer attached → treat as detached")
            overlayAttached = false
        }
        if (overlayAttached) {
            // 挂着但不可见/不可交互（挂起恢复失败等）：补一次，避免“轮盘看不见却在吃触摸”。
            if (!suspendedForExternalActivity && !isOverlayShown()) {
                root.visibility = View.VISIBLE
                OverlayWindowTypes.applyPresentationInteractiveFlags(params)
                runCatching { windowManager.updateViewLayout(root, params) }
                    .onFailure { Log.w(TAG, "Failed to restore corner overlay visibility", it) }
            }
            return
        }
        root.visibility = View.VISIBLE
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        applyBackgroundBlurFlags(params, active = false)
        runCatching {
            OverlayWindowGuard.addView(windowManager, root, params, "corner-overlay")
        }
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
        // 先清标志再摘窗：摘窗会触发 OnAttachStateChange 回调，避免那里重复走一遍收尾。
        overlayAttached = false
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        cancelCaptureGuard()
        collapseCaptureLayout()
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
        val spec = OverlayMaterialSpec.fromContext(context)
        val wantsBlur = active &&
            corner.backgroundStyle == CornerGestureSettings.BACKGROUND_BLUR &&
            spec.backdropBlurEnabled
        val canNativeBlur = wantsBlur &&
            OverlayBlurGate.canBlur(windowManager, userEnabled = spec.backdropBlurEnabled)
        if (canNativeBlur) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val rawBlurPx = if (spec.backdropBlurRadiusDp > 0) {
                (spec.backdropBlurRadiusDp * density).roundToInt()
            } else if (corner.blurDp > 0) {
                (corner.blurDp * density).roundToInt()
            } else {
                spec.blurRadiusPx(density)
            }
            params.setBlurBehindRadius(rawBlurPx.coerceIn(1, 100))
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
        lastCaptureTouchAtMs = SystemClock.uptimeMillis()
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
                    if (!isOverlayShown()) {
                        // 钉住的轮盘不可见还在吃触摸：直接收掉，绝不把整屏交出去。
                        Log.w(TAG, "pinned wheel without visible overlay → dismiss")
                        view.dismissPinnedWheel()
                        restoreCaptureSize()
                        detachOverlay()
                        return false
                    }
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

        /** 全屏捕获窗的一致性巡检间隔（只在窗口已放大时运行）。 */
        private const val CAPTURE_GUARD_INTERVAL_MS = 2_000L

        /** 钉住轮盘期间的巡检间隔（钉住是合法长驻状态，低频即可）。 */
        private const val CAPTURE_PINNED_GUARD_INTERVAL_MS = 30_000L

        /** 会话还挂着、但这么久没有任何触摸：判定为丢了 UP，强制收尾。 */
        private const val CAPTURE_STALL_MS = 15_000L

        private val FORWARD_ANCHORS = listOf(CornerAnchor.LEFT, CornerAnchor.RIGHT)
        private val FORWARD_STRIPS = listOf(CornerZoneStrip.VERTICAL, CornerZoneStrip.HORIZONTAL)
    }
}
