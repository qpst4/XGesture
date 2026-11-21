package com.slideindex.app.link

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮指针与悬浮球取词十字光标的「链接雷达」全屏 HUD 协调器（1:1 对齐 GestureEVO LinkRadarOverlay）：
 * - 会话启动时异步扫描当前屏幕所有可见应用窗口的链接（[LinkRadarScanner.scan]），并绘制柔和圆角识别线框；
 * - 当指针或悬浮球取词十字光标靠近链接区域（[onPointerPositionChanged]）时，将该链接线框加粗高亮，
 *   并在链接框正上方（或正下方）居中弹出 **「[目标App图标] 在 XXX 中打开」** 的动态莫奈胶囊横幅；
 * - 当指针点击（[onPointerTapRequested]）时，若当前正命中链接，则直接按分发规则拉起目标应用。
 */
@SuppressLint("StaticFieldLeak")
object LinkRadarOverlay {
    private const val TAG = "LinkRadarOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var activeService: AccessibilityService? = null
    private var windowManager: WindowManager? = null
    private var overlayView: LinkRadarView? = null
    private var isAttached = false

    private var sessionScope: CoroutineScope? = null
    private var previewJob: Job? = null
    private var dismissJob: Job? = null

    private var regions = emptyList<LinkRegion>()
    private var areas = emptyList<RadarHitArea>()
    private var targets = emptyList<Int>()

    private var latestX = Float.NaN
    private var latestY = Float.NaN
    private var latestRadiusPx = 0f
    private var targetSuppressed = false
    private var dispatched = false
    private var isResolvingShortLink = false
    private var radarHoverHoldMs = LinkRuleRepository.DEFAULT_RADAR_HOVER_HOLD_MS
    private var onTargetBannerReady: ((Int) -> Unit)? = null

    /**
     * 当链接雷达横幅准备就绪（对于短链为联网解析出最终目标应用后；对于普通链接为预览应用加载完成后）时回调，
     * 参数为当前配置的雷达横幅停留时长（毫秒，0 表示始终保持、不自动变黄）。
     */
    fun setOnTargetBannerReadyListener(listener: ((Int) -> Unit)?) {
        onTargetBannerReady = listener
    }

    /** 当前配置的雷达横幅停留时长（毫秒，0 = 始终保持）。 */
    fun currentRadarHoverHoldMs(): Int = radarHoverHoldMs

    /** 当前是否正在联网解析短链或加载目标应用预览（供悬浮球挂起红框变黄计时器）。 */
    fun isResolvingShortLinkNow(): Boolean = isResolvingShortLink

    fun startSession(
        service: AccessibilityService,
        initialX: Float = Float.NaN,
        initialY: Float = Float.NaN,
        initialRadiusPx: Float = 24f * service.resources.displayMetrics.density,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startSession(service, initialX, initialY, initialRadiusPx) }
            return
        }
        stopSession()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        sessionScope = scope
        activeService = service
        dispatched = false
        targetSuppressed = false
        latestX = initialX
        latestY = initialY
        latestRadiusPx = initialRadiusPx

        scope.launch {
            val enabled = LinkRuleRepository.isRadarPointerEnabled(service)
            if (!enabled || sessionScope !== scope) return@launch
            radarHoverHoldMs = runCatching { LinkRuleRepository.getRadarHoverHoldMs(service) }
                .getOrDefault(LinkRuleRepository.DEFAULT_RADAR_HOVER_HOLD_MS)

            val scanned = try {
                withContext(Dispatchers.Default) {
                    LinkRadarScanner.scan(service)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Screen link capture failed", e)
                emptyList()
            }

            if (sessionScope !== scope || scanned.isEmpty()) return@launch
            regions = scanned
            areas = hitAreas(scanned)
            ensureAttached(service)
            overlayView?.setLinks(scanned)
            if (!latestX.isNaN() && !latestY.isNaN()) {
                evaluatePointerTarget(latestX, latestY, latestRadiusPx)
            }
        }
    }

    fun stopSession() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopSession() }
            return
        }
        sessionScope?.cancel()
        sessionScope = null
        previewJob = null
        dismissJob = null
        regions = emptyList()
        areas = emptyList()
        targets = emptyList()
        latestX = Float.NaN
        latestY = Float.NaN
        targetSuppressed = false
        dispatched = false
        isResolvingShortLink = false
        onTargetBannerReady = null

        val view = overlayView
        val wm = windowManager
        if (view != null && wm != null && isAttached) {
            runCatching { wm.removeViewImmediate(view) }
        }
        view?.clear()
        overlayView = null
        windowManager = null
        activeService = null
        isAttached = false
    }

    /**
     * 当悬浮球进入区域框选（regionalPickActive）等需要临时隐藏雷达横幅的场景时调用。
     */
    fun setTargetSuppressed(suppressed: Boolean) {
        if (targetSuppressed == suppressed) return
        targetSuppressed = suppressed
        if (suppressed) {
            clearTarget()
            overlayView?.setLinks(emptyList())
        } else if (sessionScope != null && !dispatched) {
            overlayView?.setLinks(regions)
            if (!latestX.isNaN() && !latestY.isNaN()) {
                evaluatePointerTarget(latestX, latestY, latestRadiusPx)
            }
        }
    }

    /**
     * 由悬浮球取词十字光标或悬浮指针位置更新时实时驱动。
     */
    fun onPointerPositionChanged(x: Float, y: Float, radiusPx: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onPointerPositionChanged(x, y, radiusPx) }
            return
        }
        latestX = x
        latestY = y
        latestRadiusPx = radiusPx
        if (sessionScope == null || dispatched || targetSuppressed || regions.isEmpty()) return
        evaluatePointerTarget(x, y, radiusPx)
    }

    private fun evaluatePointerTarget(x: Float, y: Float, radiusPx: Float) {
        val service = activeService ?: return
        val view = overlayView ?: return
        view.setLinks(regions)

        val hysteresis = 5f * service.resources.displayMetrics.density
        val hits = hitTargets(x, y, radiusPx, hysteresis)
        if (hits.isEmpty()) {
            if (targets.isNotEmpty() && dismissJob?.isActive != true) {
                dismissJob = sessionScope?.launch {
                    delay(70)
                    clearTarget()
                }
            }
        } else {
            dismissJob?.cancel()
            dismissJob = null
            if (hits != targets) {
                targets = hits
                showTarget(hits.map { regions[it] })
            }
        }
    }

    private fun hitTargets(x: Float, y: Float, radiusPx: Float, hysteresis: Float = 0f): List<Int> =
        findRadarTargets(
            areas = areas,
            x = x,
            y = y,
            radius = radiusPx * 1.15f,
            previous = targets.firstOrNull(),
            hysteresis = hysteresis,
        )

    private fun clearTarget() {
        val hadTarget = targets.isNotEmpty() || previewJob != null
        dismissJob?.cancel()
        dismissJob = null
        previewJob?.cancel()
        previewJob = null
        isResolvingShortLink = false
        targets = emptyList()
        if (hadTarget) {
            overlayView?.clearTarget()
        }
    }

    private fun showTarget(links: List<LinkRegion>) {
        val service = activeService ?: return
        val view = overlayView ?: return
        val hits = links.distinctBy { it.url }
        previewJob?.cancel()
        if (hits.size > 1) {
            isResolvingShortLink = false
            view.show(hits, "释放以选择链接 (${hits.size})")
            onTargetBannerReady?.invoke(radarHoverHoldMs)
            return
        }
        val primary = hits.first()
        isResolvingShortLink = true
        view.show(hits, "打开链接")
        previewJob = sessionScope?.launch {
            val shortRuleName = runCatching {
                LinkDispatcher.pendingShortLinkRuleName(service, primary.url)
            }.getOrNull()
            if (shortRuleName != null && targets.isNotEmpty()) {
                view.show(hits, "正在解析短链 ($shortRuleName)…")
            }
            val preview = try {
                predict(service, primary)
            } catch (cancelled: CancellationException) {
                isResolvingShortLink = false
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Link target preview failed", e)
                "打开链接" to null
            }
            isResolvingShortLink = false
            if (targets.isNotEmpty()) {
                view.show(hits, preview.first, preview.second)
                onTargetBannerReady?.invoke(radarHoverHoldMs)
            }
        }
    }

    private suspend fun predict(service: AccessibilityService, link: LinkRegion): Pair<String, Drawable?> {
        val target = previewLinkTarget(service, link.url, link.sourcePackage, link.sourceText)
        return if (target != null) {
            "在 ${target.label} 中打开" to target.rawIcon
        } else {
            "打开链接" to null
        }
    }

    fun hasTarget(): Boolean {
        if (sessionScope == null || dispatched || targetSuppressed || regions.isEmpty()) return false
        if (latestX.isNaN() || latestY.isNaN()) return false
        return hitTargets(latestX, latestY, latestRadiusPx).isNotEmpty()
    }

    /**
     * 当前指针/十字光标命中的唯一或首选链接（供悬浮指针轻点或取词面板直达）。
     */
    fun currentTargetLinks(): List<LinkRegion> {
        if (sessionScope == null || dispatched || targetSuppressed || regions.isEmpty()) return emptyList()
        if (latestX.isNaN() || latestY.isNaN()) return emptyList()
        return hitTargets(latestX, latestY, latestRadiusPx)
            .mapNotNull { regions.getOrNull(it) }
            .distinctBy { it.url }
    }

    /**
     * 当悬浮指针在链接上触发点击时调用：若命中雷达目标，直接按分发规则打开链接并返回 true。
     */
    fun onPointerTapRequested(x: Float = latestX, y: Float = latestY, radiusPx: Float = latestRadiusPx): Boolean {
        val service = activeService ?: return false
        if (sessionScope == null || dispatched || targetSuppressed || regions.isEmpty()) return false
        if (x.isNaN() || y.isNaN()) return false
        val hits = hitTargets(x, y, radiusPx)
            .mapNotNull { regions.getOrNull(it) }
            .distinctBy { it.url }
        if (hits.isEmpty()) return false

        dispatched = true
        clearTarget()
        overlayView?.clear()
        val link = hits.first()
        dispatchScope.launch {
            LinkLauncher.openUrl(service, link.url, link.sourcePackage, link.sourceText)
        }
        return true
    }

    private fun hitAreas(links: List<LinkRegion>): List<RadarHitArea> =
        links.mapIndexed { index, link ->
            RadarHitArea(
                index = index,
                group = link.groupId,
                precise = link.isPrecise,
                boxes = link.rects.map {
                    RadarBox(
                        it.left.toFloat(),
                        it.top.toFloat(),
                        it.right.toFloat(),
                        it.bottom.toFloat(),
                    )
                },
            )
        }

    private fun ensureAttached(service: AccessibilityService) {
        if (isAttached && overlayView != null) return
        val wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as? WindowManager ?: return
        val view = LinkRadarView(service)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "SlideIndex:LinkRadarOverlay"
        }
        runCatching {
            wm.addView(view, params)
            windowManager = wm
            overlayView = view
            isAttached = true
        }.onFailure {
            Log.w(TAG, "Failed to attach LinkRadarOverlay", it)
        }
    }
}
