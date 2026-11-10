package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.accessibility.AccessibilityEvent
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.copy.UniversalCopyOverlay
import com.slideindex.app.backtap.BackTapGestureHost
import com.slideindex.app.translate.overlay.ScreenTranslationController
import com.slideindex.app.screensearch.ScreenSearchFloating
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStartup
import com.slideindex.app.clipboardfloat.ClipboardFloatImeCoordinator
import com.slideindex.app.overlay.KeyboardTriggerImeCoordinator
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.message.MessageReminderOrchestrator
import com.slideindex.app.overlay.AccessibilityLink
import com.slideindex.app.overlay.EdgeOverlayHost
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatBallTextPickCoordinator
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.GlobalOverlayDismissHelper
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.overlay.backpanel.BackPanelOverlayRegistry
import com.slideindex.app.overlay.corner.CornerAnchor
import com.slideindex.app.overlay.corner.CornerGestureHost
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 一次性整屏截图（无障碍截图）的结果。
 *
 * 之所以把失败原因带出来：无障碍截图在 Android 14+ 有**最小调用间隔**，太频繁会被系统拒绝。
 * 调用方必须能把这种情况讲给用户听，而不是静默什么都不做。
 *
 * 放在文件顶层而不是伴生对象里：Kotlin 只允许通过 `外层类.成员` 访问伴生对象里的**函数/属性**，
 * 伴生对象里的**类型**必须写成 `外层类.Companion.类型`，很别扭。
 */

@dagger.hilt.android.AndroidEntryPoint
class SlideIndexAccessibilityService : AccessibilityService() {

    @javax.inject.Inject lateinit var deps: AppDependencies
    @javax.inject.Inject lateinit var messageReminderOrchestrator: MessageReminderOrchestrator
    @javax.inject.Inject lateinit var backTapGestureHost: BackTapGestureHost

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var edgeOverlayHost: EdgeOverlayHost? = null

    /**
     * 浮层租约心跳：服务活着就每 2s 续租一次。
     *
     * 停掉心跳意味着"本服务随时可能消失"，OverlayHostLease 会在租约过期后
     * 把本进程残留的浮层窗口（含全屏直触的 presentation）强制摘掉，
     * 避免服务已经死了、窗口却继续吞整屏触摸。
     */
    private val overlayHostLeaseRunnable = object : Runnable {
        override fun run() {
            com.slideindex.app.overlay.OverlayHostLease.renew()
            mainHandler.postDelayed(this, OVERLAY_HOST_LEASE_RENEW_MS)
        }
    }

    private fun startOverlayHostLease() {
        com.slideindex.app.overlay.OverlayHostLease.renew()
        mainHandler.removeCallbacks(overlayHostLeaseRunnable)
        mainHandler.postDelayed(overlayHostLeaseRunnable, OVERLAY_HOST_LEASE_RENEW_MS)
    }

    private fun stopOverlayHostLease() {
        mainHandler.removeCallbacks(overlayHostLeaseRunnable)
    }

    /** 当前由输入层接管并转发过来的目标（SIDE_* / TARGET_*），用于会话结束时精确取消。 */
    private var activeForwardedTarget: Int = NO_FORWARDED_TARGET

    private lateinit var foregroundTracker: SlideIndexAccessibilityForegroundTracker
    private lateinit var watchdog: SlideIndexAccessibilityWatchdog
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    private var lastImeWindowsChangedWorkAtMs = 0L
    private var imeWindowsChangedWorkScheduled = false
    private val coalescedImeWindowsChangedWork = Runnable {
        imeWindowsChangedWorkScheduled = false
        lastImeWindowsChangedWorkAtMs = android.os.SystemClock.uptimeMillis()
        ClipboardFloatImeCoordinator.onWindowsChanged(this)
        KeyboardTriggerImeCoordinator.onWindowsChanged(this)
    }

    private fun handleImeWindowsChanged() {
        val now = android.os.SystemClock.uptimeMillis()
        val elapsed = now - lastImeWindowsChangedWorkAtMs
        if (elapsed >= IME_WINDOWS_CHANGED_COALESCE_MS) {
            lastImeWindowsChangedWorkAtMs = now
            if (imeWindowsChangedWorkScheduled) {
                mainHandler.removeCallbacks(coalescedImeWindowsChangedWork)
                imeWindowsChangedWorkScheduled = false
            }
            ClipboardFloatImeCoordinator.onWindowsChanged(this)
            KeyboardTriggerImeCoordinator.onWindowsChanged(this)
            return
        }
        if (imeWindowsChangedWorkScheduled) return
        imeWindowsChangedWorkScheduled = true
        mainHandler.postDelayed(coalescedImeWindowsChangedWork, IME_WINDOWS_CHANGED_COALESCE_MS - elapsed)
    }

    @SuppressLint("SwitchIntDef") // Only handle the event types this service cares about
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 真实前台切换（Activity 变化）才丢弃前台包名缓存。
                // 实测 resolveHostPackage 被调用 26.5 次/秒、累计占整段交互 26% 时间（PerfProbe），
                // 这里是它唯一必须立刻看到新值的入口。
                com.slideindex.app.util.AccessibilityForegroundResolver.invalidate()
                foregroundTracker.handleWindowStateChanged(event)
                ClipboardFloatImeCoordinator.onWindowsChanged(this)
                KeyboardTriggerImeCoordinator.onWindowsChanged(this)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // 注意：这里**不**失效前台包名缓存。WINDOWS_CHANGED 由输入法/弹窗/Toast 高频触发，
                // 用它失效会让缓存失去意义；前台正确性由 WINDOW_STATE_CHANGED + TTL 双重保证。
                foregroundTracker.handleWindowsChanged()
                handleImeWindowsChanged()
            }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: SlideIndexAccessibilityService? = null

        /**
         * 无障碍实例代次：每绑定 / 重绑一个新实例就 +1。
         *
         * 为什么需要它：无障碍实例的窗口在实例销毁时会被系统**整片摘掉**，而客户端**不保证**收到
         * `onViewDetachedFromWindow`（真机实测：`dumpsys window windows` 里已经没有那扇窗，
         * `View.isAttachedToWindow` 仍是 true）。浮层宿主只能靠"代次变没变"来判断
         * "我建窗时依赖的那个实例还在不在" —— 见 `OverlaySidePanelHost.attachedGeneration`。
         */
        @Volatile
        private var instanceGeneration: Int = 0

        /** 当前无障碍实例代次（浮层宿主用来丢弃"挂在旧实例窗口上的壳子"）。 */
        fun overlayHostGeneration(): Int = instanceGeneration

        private val mainHandler = Handler(Looper.getMainLooper())
        private const val IME_WINDOWS_CHANGED_COALESCE_MS = 200L

        fun dispatchExternalGestureAction(
            action: GestureAction,
            anchorRawY: Float,
            panelSide: com.slideindex.app.overlay.PanelSide? = null
        ): Boolean {
            val service = instance ?: return false
            return service.dispatchExternalGestureAction(action, anchorRawY, panelSide)
        }

        /**
         * 服务是否**真的**可用。
         *
         * 不能只看 `instance != null`：系统可以在不回调 `onUnbind` / `onDestroy` 的情况下
         * 把连接摘掉（服务收到 `init(NO_ID, null)`），此后实例还在、旧窗口还在，
         * 但 `performGlobalAction` 恒 false、新建无障碍窗口恒 `token null`。
         * 真实失败会由 [com.slideindex.app.overlay.AccessibilityLink] 记下来，这里一并判断。
         */
        fun isConnected(): Boolean = instance != null && AccessibilityLink.isUsable()

        /** overlay 宿主是否就绪；未就绪时模块不应在输入层吞掉事件。 */
        fun isOverlayReady(): Boolean = instance?.edgeOverlayHost != null

        /**
         * 该边当前是否真的能处理输入层转发的触摸。
         *
         * 只读 OverlayManager 维护的可能力掩码（volatile），可在 binder 线程安全调用。
         */
        fun canHandleForwardedSide(sideId: Int): Boolean {
            // 注入窗口内一律"不接"：让模块放行我们注入的那一击，直达目标应用。
            // （模块对该查询有 1s 缓存，这只是尽力而为；真正的兜底是 handleModuleGestureTouch 里的回声丢弃。）
            if (com.slideindex.app.overlay.ModuleForwardedTouchGate.isInjectionPending()) return false
            val host = instance?.edgeOverlayHost ?: return false
            val side = sideId.toPanelSideOrNull() ?: return false
            return host.isForwardingCapable(side)
        }

        /**
         * 触钮之外的接管目标（角轮盘；悬浮球线条待下一步）的实时命中复核。
         *
         * 由 system_server 的 binder 线程调用，因此只读各宿主维护的 volatile 快照，
         * 不触碰 Compose/UI 状态；命中区随设置或旋转变化时，宿主会刷新该快照。
         */
        fun canHandleForwardedTargetAt(target: Int, x: Float, y: Float): Boolean {
            instance ?: return false
            if (com.slideindex.app.overlay.ModuleForwardedTouchGate.isInjectionPending()) return false
            return when (target) {
                ModuleHookBridgeContract.TARGET_FLOAT_BALL,
                ModuleHookBridgeContract.TARGET_FLOAT_LINE,
                -> com.slideindex.app.overlay.FloatBallOverlay.canAcceptForwardedTouchAt(target, x, y)
                ModuleHookBridgeContract.TARGET_CORNER_LEFT ->
                    CornerGestureHost.canAcceptForwardedTouchAt(CornerAnchor.LEFT, x, y)
                ModuleHookBridgeContract.TARGET_CORNER_RIGHT ->
                    CornerGestureHost.canAcceptForwardedTouchAt(CornerAnchor.RIGHT, x, y)
                else -> canHandleForwardedSide(target)
            }
        }

        /**
         * 接收 system_server 模块在输入层接管后转发来的触摸事件。
         *
         * 返回 false 表示当前 app 无法处理（服务未就绪/边不可用），模块据此放行事件。
         */
        fun handleModuleGestureTouch(
            sessionId: Long,
            sideId: Int,
            action: Int,
            x: Float,
            y: Float,
            eventTime: Long,
            downTime: Long,
            metaState: Int,
        ): Boolean {
            val service = instance ?: return false
            val side = sideId.toPanelSideOrNull()
            val cornerAnchor = if (side == null) sideId.toCornerAnchorOrNull() else null
            val cornerHost = if (cornerAnchor != null) CornerGestureHost.instanceOrNull() else null
            val floatBallTarget = sideId.isFloatBallTarget()
            if (side != null) {
                service.edgeOverlayHost ?: return false
            } else if (cornerHost == null && !floatBallTarget) {
                return false
            }
            // 注入回声：我们自己刚注入的那一击被模块又转发回来。丢掉，不能进第二次手势解析，
            // 否则会再触发同一个动作（老实现用"最近 1500ms 有转发就禁止放行"来防这个，
            // 代价是接管区里所有单击都穿透不了）。
            if (com.slideindex.app.overlay.ModuleForwardedTouchGate.isInjectedEcho(x, y)) {
                EdgeDiag.log(
                    "module",
                    "丢弃注入回声 sessionId=$sessionId target=$sideId " +
                        "action=${android.view.MotionEvent.actionToString(action)} at=($x,$y)"
                )
                return true
            }
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                service.activeForwardedTarget = sideId
            }
            EdgeDiag.log(
                "module",
                "模块转发触摸 sessionId=$sessionId side=$sideId " +
                    "action=${android.view.MotionEvent.actionToString(action)} at=($x,$y)"
            )
            mainHandler.post {
                val event = android.view.MotionEvent.obtain(downTime, eventTime, action, x, y, metaState)
                try {
                    if (side != null) {
                        service.edgeOverlayHost?.handleForwardedTouch(side, event)
                    } else if (cornerAnchor != null) {
                        cornerHost?.handleForwardedTouch(cornerAnchor, event)
                    } else if (floatBallTarget) {
                        com.slideindex.app.overlay.FloatBallOverlay.handleForwardedTouch(sideId, event)
                    }
                } finally {
                    event.recycle()
                }
            }
            return true
        }

        fun handleModuleGestureSessionEnd(sessionId: Long, reason: Int) {
            val service = instance ?: return
            val target = service.activeForwardedTarget
            service.activeForwardedTarget = NO_FORWARDED_TARGET
            // 「正常终止」与「流被打断」必须分开处理：
            //
            // 模块只有在手指抬起/系统取消（ACTION_UP / ACTION_CANCEL）时才会发 REASON_UP / REASON_CANCEL，
            // 而这两条事件本身也已经转发给 app 了，手势引擎已经自己收过尾（松手触发更是在这一拍里
            // 才刚把面板打开）。此时再走 cancelForwardedTouch 的强制复位，会把刚打开的驻留面板
            // （快速启动器 / 任务切换器 / Shell）连同全屏窗口一起拆掉——真机上表现为
            // 「松手触发弹不出来」：面板 2ms 后被拆，180ms 的进场动画根本没机会露脸。
            //
            // 真正需要强制复位的是流被中途打断的情况（多指、桥断、本地复位）：那时 app 可能压根
            // 没收到终止事件，全屏可触摸的 presentation 会一直吞触摸。真的连 UP/CANCEL 也丢了时，
            // 由 EdgeGestureOverlayView 的 10s StuckGestureWatchdog 兜底。
            val gracefulEnd = reason == com.slideindex.app.xposed.takeover.TakeoverSessionPolicy.REASON_UP ||
                reason == com.slideindex.app.xposed.takeover.TakeoverSessionPolicy.REASON_CANCEL
            EdgeDiag.logStack(
                "module",
                "模块会话结束 sessionId=$sessionId reason=${moduleEndReasonName(reason)} target=$target → " +
                    if (gracefulEnd) {
                        "正常终止：边缘触钮不再强制复位（驻留面板留在屏幕上；悬浮球/角轮盘照旧收窗）"
                    } else {
                        "异常终止：强制复位，收回可能卡住的全屏直触"
                    }
            )
            if (target == NO_FORWARDED_TARGET) return
            val side = target.toPanelSideOrNull()
            val cornerAnchor = if (side == null) target.toCornerAnchorOrNull() else null
            // 只有边缘触钮吃这个豁免：
            // 悬浮球 / 角轮盘的 cancelForwardedTouch 还负责把展开成全屏的触摸窗收回来
            // （collapseBallTouchHostFromFullscreen / overlayView.cancelSession），
            // 一起跳过会让整屏触摸被窗吃掉。
            if (side != null && gracefulEnd) return
            mainHandler.post {
                if (side != null) {
                    service.edgeOverlayHost?.cancelForwardedTouch(side)
                } else if (cornerAnchor != null) {
                    CornerGestureHost.instanceOrNull()?.cancelForwardedTouch(cornerAnchor)
                } else if (target.isFloatBallTarget()) {
                    // 多指/桥断等非正常结束：先清选中态再收手势，避免线条侧“cancel 变提交”。
                    com.slideindex.app.overlay.FloatBallOverlay.cancelForwardedTouch(
                        target,
                        selectionCancel = !gracefulEnd,
                    )
                }
            }
        }

        /** 模块侧会话结束原因名，便于在诊断日志里一眼区分「UP 结束」与「多指/桥断」。 */
        private fun moduleEndReasonName(reason: Int): String = when (reason) {
            com.slideindex.app.xposed.takeover.TakeoverSessionPolicy.REASON_UP -> "REASON_UP"
            com.slideindex.app.xposed.takeover.TakeoverSessionPolicy.REASON_CANCEL -> "REASON_CANCEL"
            com.slideindex.app.xposed.takeover.TakeoverSessionPolicy.REASON_MULTI_TOUCH -> "REASON_MULTI_TOUCH"
            com.slideindex.app.xposed.takeover.TakeoverSessionPolicy.REASON_BRIDGE_LOST -> "REASON_BRIDGE_LOST"
            com.slideindex.app.xposed.takeover.TakeoverSessionPolicy.REASON_RESET -> "REASON_RESET"
            else -> "REASON_NONE($reason)"
        }

        private fun Int.toPanelSideOrNull(): PanelSide? = when (this) {
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_LEFT -> PanelSide.LEFT
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_RIGHT -> PanelSide.RIGHT
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_BOTTOM -> PanelSide.BOTTOM
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_TOP -> PanelSide.TOP
            else -> null
        }

        private fun Int.toCornerAnchorOrNull(): CornerAnchor? = when (this) {
            ModuleHookBridgeContract.TARGET_CORNER_LEFT -> CornerAnchor.LEFT
            ModuleHookBridgeContract.TARGET_CORNER_RIGHT -> CornerAnchor.RIGHT
            else -> null
        }

        private fun Int.isFloatBallTarget(): Boolean =
            this == ModuleHookBridgeContract.TARGET_FLOAT_BALL ||
                this == ModuleHookBridgeContract.TARGET_FLOAT_LINE

        private const val NO_FORWARDED_TARGET = -1

        fun applyServiceEnabledImmediate(enabled: Boolean) {
            val service = instance
            if (service == null) {
                Log.w(TAG, "applyServiceEnabledImmediate: a11y not connected, enabled=$enabled")
                return
            }
            val hostReady = service.edgeOverlayHost != null
            Log.i(TAG, "applyServiceEnabledImmediate: enabled=$enabled hostReady=$hostReady")
            if (Looper.myLooper() == Looper.getMainLooper()) {
                service.edgeOverlayHost?.applyServiceEnabledImmediate(enabled)
            } else {
                mainHandler.post {
                    instance?.edgeOverlayHost?.applyServiceEnabledImmediate(enabled)
                }
            }
        }

        fun accessibilityInstance(): SlideIndexAccessibilityService? = instance

        fun currentForegroundPackageName(): String? =
            instance?.foregroundTracker?.currPackageName

        fun currentForegroundClassName(): String? =
            instance?.foregroundTracker?.currClassName

        fun perform(action: GestureAction): Boolean =
            SlideIndexAccessibilityGestureInjector.perform(action) { instance }

        fun performUniversalCopy(): Boolean {
            val service = instance ?: return false
            if (UniversalCopyOverlay.isShowing) {
                UniversalCopyOverlay.dismiss()
                return true
            }
            UniversalCopyOverlay.collectAndShow(service)
            return true
        }

        fun performScreenTranslate(): Boolean {
            val service = instance ?: return false
            ScreenTranslationController.toggle(service)
            return true
        }

        fun performScreenSearch(): Boolean {
            val service = instance ?: return false
            val run: () -> Unit = {
                runCatching {
                    ScreenSearchFloating.get(service).togglePanel()
                }.onFailure { error ->
                    Log.e(TAG, "performScreenSearch failed", error)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                run()
            } else {
                mainHandler.post(run)
            }
            return true
        }

        /**
         * 一次性整屏截图（**无障碍截图**，不弹 MediaProjection 授权）。
         *
         * 与 [performSmartScreenshot] 的分工：那条路会把截图交给 `SmartScreenshotOverlay` 做标注/分享；
         * 这里只把像素交回调用方（快捷菜单「截屏识别」要的是 Bitmap，不是标注面板）。
         *
         * ⚠️ 调用方**必须自行收起浮层**再调本方法，否则浮层会被一起截进去。
         *
         * @return false 表示压根没能发起（服务未连接 / 系统版本不够），此时**不会**回调。
         */
        fun captureDisplayBitmap(onResult: (ScreenshotCaptureOutcome) -> Unit): Boolean {
            val service = instance ?: return false
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        val bitmap = try {
                            // 系统给的是硬件缓冲，必须拷成软件位图后再 close，照 performSmartScreenshot 的写法。
                            val wrapped = android.graphics.Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace,
                            )
                            val software = wrapped?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            wrapped?.recycle()
                            software
                        } catch (error: Throwable) {
                            Log.w(TAG, "captureDisplayBitmap decode failed", error)
                            null
                        } finally {
                            runCatching { screenshotResult.hardwareBuffer.close() }
                        }
                        onResult(
                            if (bitmap != null) {
                                ScreenshotCaptureOutcome.Success(bitmap)
                            } else {
                                ScreenshotCaptureOutcome.Failed(0)
                            },
                        )
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "captureDisplayBitmap takeScreenshot failed: $errorCode")
                        onResult(
                            if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                                ScreenshotCaptureOutcome.Throttled
                            } else {
                                ScreenshotCaptureOutcome.Failed(errorCode)
                            },
                        )
                    }
                },
            )
            return true
        }

        fun performSmartScreenshot(): Boolean {
            val service = instance ?: return false
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                return false
            }
            mainHandler.post {
                GlobalOverlayDismissHelper.dismissAllPanels()
                PanelSide.entries.forEach { side ->
                    GestureAnimationOverlayRegistry.controller(side).hide()
                    BackPanelOverlayRegistry.controller(side).hide()
                }
                FloatingPointerOverlayWindow.suppressForScreenshotCapture()
                FloatBallOverlay.suppressForScreenshotCapture()
                FloatBallPickResultPanel.suppressForScreenshotCapture()

                Choreographer.getInstance().postFrameCallback {
                    mainHandler.postDelayed({
                        service.takeScreenshot(
                            android.view.Display.DEFAULT_DISPLAY,
                            service.mainExecutor,
                            object : AccessibilityService.TakeScreenshotCallback {
                                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                                    try {
                                        val wrapped = android.graphics.Bitmap.wrapHardwareBuffer(
                                            screenshotResult.hardwareBuffer,
                                            screenshotResult.colorSpace
                                        )
                                        val software = wrapped?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                        wrapped?.recycle()
                                        if (software != null) {
                                            mainHandler.post {
                                                com.slideindex.app.overlay.screenshot.SmartScreenshotOverlay.show(service, software)
                                            }
                                        }
                                    } finally {
                                        screenshotResult.hardwareBuffer.close()
                                        mainHandler.post {
                                            FloatingPointerOverlayWindow.restoreAfterScreenshotCapture()
                                            FloatBallOverlay.restoreAfterScreenshotCapture()
                                            FloatBallPickResultPanel.restoreAfterScreenshotCapture()
                                        }
                                    }
                                }

                                override fun onFailure(errorCode: Int) {
                                    Log.w(TAG, "performSmartScreenshot takeScreenshot failed: $errorCode")
                                    mainHandler.post {
                                        FloatingPointerOverlayWindow.restoreAfterScreenshotCapture()
                                        FloatBallOverlay.restoreAfterScreenshotCapture()
                                        FloatBallPickResultPanel.restoreAfterScreenshotCapture()
                                    }
                                }
                            }
                        )
                    }, 120L)
                }
            }
            return true
        }

        fun dispatchPointerTap(
            rawX: Float,
            rawY: Float,
            onFinished: (Boolean) -> Unit,
            preferNodeClick: Boolean = false
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerTap(
            instance,
            rawX,
            rawY,
            onFinished,
            preferNodeClick
        )

        fun dispatchTap(
            rawX: Float,
            rawY: Float,
            onFinished: (Boolean) -> Unit,
            durationMs: Long = TAP_DURATION_MS
        ) = SlideIndexAccessibilityGestureInjector.dispatchTap(instance, rawX, rawY, onFinished, durationMs)

        fun dispatchPointerSwipe(
            startX: Float,
            startY: Float,
            config: PointerSwipeConfig,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerSwipe(instance, startX, startY, config, onFinished)

        fun dispatchPointerSwipePath(
            startX: Float,
            startY: Float,
            path: Path,
            durationMs: Long,
            maxDurationMs: Long = SlideIndexAccessibilityGestureInjector.DEFAULT_SWIPE_MAX_DURATION_MS,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerSwipePath(
            instance,
            startX,
            startY,
            path,
            durationMs,
            maxDurationMs,
            onFinished
        )

        fun dispatchPointerDragNoFling(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            dragDurationMs: Long = 420L,
            holdDurationMs: Long = 140L,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerDragNoFling(
            instance,
            startX,
            startY,
            endX,
            endY,
            dragDurationMs,
            holdDurationMs,
            onFinished
        )

        fun dispatchPointerHold(
            rawX: Float,
            rawY: Float,
            durationMs: Long,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerHold(instance, rawX, rawY, durationMs, onFinished)


        fun dispatchTapSync(rawX: Float, rawY: Float): Boolean =
            SlideIndexAccessibilityGestureInjector.dispatchTapSync(instance, rawX, rawY)

        const val TAP_DURATION_MS = SlideIndexAccessibilityGestureInjector.TAP_DURATION_MS
        const val POINTER_TAP_DURATION_MS = SlideIndexAccessibilityGestureInjector.POINTER_TAP_DURATION_MS
        const val POINTER_TAP_CHAIN_GAP_MS = SlideIndexAccessibilityGestureInjector.POINTER_TAP_CHAIN_GAP_MS

        fun reloadApps() {
            instance?.edgeOverlayHost?.reloadApps()
        }

        /**
         * 预览/浮层指令的可见失败出口。
         *
         * 这些指令只能在 `:overlay` 进程生效（浮层宿主在那儿）。历史上它们在别的进程
         * 会命中 `instance == null` 而静默丢弃，用户只觉得"功能坏了"；这里统一留日志，
         * 让"错进程调用/服务离线"必然可见。
         */
        private fun requireOverlayProcess(scope: String) {
            if (instance == null) {
                Log.e(TAG, "浮层指令被丢弃（本进程没有无障碍服务实例，多半是错进程调用）: $scope")
            }
        }

        private fun overlayHostOrNull(scope: String): com.slideindex.app.overlay.EdgeOverlayHost? {
            val host = instance?.edgeOverlayHost
            if (host == null) {
                Log.e(TAG, "浮层指令被丢弃（服务未连接）: $scope")
            }
            return host
        }

        fun setFloatBallStripZonePreview(active: Boolean) {
            requireOverlayProcess("setFloatBallStripZonePreview")
            com.slideindex.app.overlay.FloatBallOverlay.setStripZonePreviewActive(active)
        }

        fun previewFloatBallPositionYFraction(fraction: Float) {
            requireOverlayProcess("previewFloatBallPositionYFraction")
            com.slideindex.app.overlay.FloatBallOverlay.previewPositionYFraction(fraction)
        }

        fun endFloatBallPositionYPreview(restoreIfNeeded: Boolean) {
            requireOverlayProcess("endFloatBallPositionYPreview")
            com.slideindex.app.overlay.FloatBallOverlay.endPositionYPreview(restoreIfNeeded)
        }

        fun clearFloatBallPositionYPreviewRestore() {
            requireOverlayProcess("clearFloatBallPositionYPreviewRestore")
            com.slideindex.app.overlay.FloatBallOverlay.clearPositionYPreviewRestore()
        }

        fun previewFloatBallAppearance(
            sizeDp: Float? = null,
            opacity: Float? = null,
            visibleFraction: Float? = null,
            lineHeightFraction: Float? = null,
            lineWidthDp: Float? = null,
            lineOpacity: Float? = null
        ) {
            requireOverlayProcess("previewFloatBallAppearance")
            com.slideindex.app.overlay.FloatBallOverlay.previewAppearance(
                sizeDp = sizeDp,
                opacity = opacity,
                visibleFraction = visibleFraction,
                lineHeightFraction = lineHeightFraction,
                lineWidthDp = lineWidthDp,
                lineOpacity = lineOpacity
            )
        }

        fun endFloatBallAppearancePreview(restoreIfNeeded: Boolean) {
            requireOverlayProcess("endFloatBallAppearancePreview")
            com.slideindex.app.overlay.FloatBallOverlay.endAppearancePreview(restoreIfNeeded)
        }

        fun clearFloatBallAppearancePreviewRestore() {
            requireOverlayProcess("clearFloatBallAppearancePreviewRestore")
            com.slideindex.app.overlay.FloatBallOverlay.clearAppearancePreviewRestore()
        }

        fun setCornerZonePreviewActive(active: Boolean) {
            overlayHostOrNull("setCornerZonePreviewActive")?.setCornerZonePreviewActive(active)
        }

        fun applyCornerZonePreviewDimensions(
            verticalEdgeWidthDp: Float,
            verticalEdgeHeightDp: Float,
            horizontalEdgeWidthDp: Float,
            horizontalEdgeHeightDp: Float
        ) {
            overlayHostOrNull("applyCornerZonePreviewDimensions")?.applyCornerZonePreviewDimensions(
                verticalEdgeWidthDp,
                verticalEdgeHeightDp,
                horizontalEdgeWidthDp,
                horizontalEdgeHeightDp
            )
        }

        fun previewIndexHeightFraction(fraction: Float) {
            overlayHostOrNull("previewIndexHeightFraction")?.previewIndexHeightFraction(fraction)
        }

        fun clearIndexHeightPreview() {
            overlayHostOrNull("clearIndexHeightPreview")?.clearIndexHeightPreview()
        }

        fun commitIndexHeightPreview() {
            overlayHostOrNull("commitIndexHeightPreview")?.commitIndexHeightPreview()
        }

        fun previewIndexVerticalPositionFraction(fraction: Float) {
            overlayHostOrNull("previewIndexVerticalPositionFraction")
                ?.previewIndexVerticalPositionFraction(fraction)
        }

        fun clearIndexVerticalPositionPreview() {
            overlayHostOrNull("clearIndexVerticalPositionPreview")?.clearIndexVerticalPositionPreview()
        }

        fun commitIndexVerticalPositionPreview() {
            overlayHostOrNull("commitIndexVerticalPositionPreview")?.commitIndexVerticalPositionPreview()
        }

        fun commitIndexSliderPreviews() {
            overlayHostOrNull("commitIndexSliderPreviews")?.commitIndexSliderPreviews()
        }

        fun mergeTriggerHandleLayoutPreview(
            side: com.slideindex.app.overlay.PanelSide,
            handleId: String,
            edgeWidthDp: Float? = null,
            topFraction: Float? = null,
            bottomFraction: Float? = null,
            shortSwipeDistanceDp: Float? = null,
            longSwipeDistanceDp: Float? = null,
            design: com.slideindex.app.gesture.TriggerHandleDesign? = null
        ) {
            overlayHostOrNull("mergeTriggerHandleLayoutPreview")?.mergeTriggerHandleLayoutPreview(
                side = side,
                handleId = handleId,
                edgeWidthDp = edgeWidthDp,
                topFraction = topFraction,
                bottomFraction = bottomFraction,
                shortSwipeDistanceDp = shortSwipeDistanceDp,
                longSwipeDistanceDp = longSwipeDistanceDp,
                design = design
            )
        }

        fun clearTriggerHandleLayoutPreview() {
            overlayHostOrNull("clearTriggerHandleLayoutPreview")?.clearTriggerHandleLayoutPreview()
        }

        fun commitTriggerHandleLayoutPreview() {
            overlayHostOrNull("commitTriggerHandleLayoutPreview")
                ?.commitTriggerHandleLayoutPreview()
        }

        fun clearOverlayLayoutPreview() {
            overlayHostOrNull("clearOverlayLayoutPreview")?.clearOverlayLayoutPreview()
        }

        fun setPreviewMode(
            enabled: Boolean,
            content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
            focus: LayoutPreviewFocus? = null
        ) {
            overlayHostOrNull("setPreviewMode")?.setPreviewMode(enabled, content, focus)
        }

        fun setGestureAnglesPreview(angles: com.slideindex.app.gesture.GestureAngles?) {
            overlayHostOrNull("setGestureAnglesPreview")?.setGestureAnglesPreview(angles)
        }

        fun recoverOverlaysIfIdle() {
            instance?.edgeOverlayHost?.recoverOverlaysIfIdle()
        }

        fun refreshOverlaySuppression() {
            instance?.edgeOverlayHost?.refreshOverlaySuppression()
        }

        fun recoverTriggerInteraction(forceReAddChrome: Boolean = false) {
            instance?.edgeOverlayHost?.recoverTriggerInteraction(forceReAddChrome)
        }

        fun onKeyboardImeChanged(visibilityChanged: Boolean = false) {
            instance?.edgeOverlayHost?.onKeyboardImeChanged(visibilityChanged)
        }

        fun refreshTriggerVisuals() {
            instance?.edgeOverlayHost?.refreshTriggerVisuals()
        }

        fun bringEdgeChromeAbovePanels(forceReAdd: Boolean = true, sides: Set<PanelSide>? = null) {
            instance?.edgeOverlayHost?.bringEdgeChromeAbovePanels(forceReAdd, sides)
        }

        fun edgePresentationNeedsChromeRaise(): Boolean =
            instance?.edgeOverlayHost?.edgePresentationNeedsChromeRaise() == true

        /**
         * 音量/亮度条是否正在边缘 presentation 上显示。
         *
         * 条显示期间它会切成可触摸的全屏窗，而悬浮球和它是同类型窗口（只能靠 remove/add 抢 z 序）。
         * 悬浮球据此让出触摸、并停止抬升，见 `FloatBallOverlay.isAdjustBarCoveringBall`。
         */
        fun edgePresentationHasAdjustPanel(): Boolean =
            instance?.edgeOverlayHost?.edgePresentationHasAdjustPanel() == true

        fun notifyEdgeChromeBelowPanel() {
            instance?.edgeOverlayHost?.notifyEdgeChromeBelowPanel()
        }

        fun suspendAllEdgeOverlays() {
            instance?.edgeOverlayHost?.suspendAllEdgeOverlays()
        }

        fun resumeAllEdgeOverlays() {
            instance?.edgeOverlayHost?.resumeAllEdgeOverlays()
        }

        /**
         * 取词/截图前隐藏边缘触钮：触钮是画在触钮窗上的，不隐藏就会被 takeScreenshot 拍进全屏截图。
         * 只隐藏绘制、不摘窗口，用完必须 [restoreEdgeChromeAfterCapture]。
         */
        fun suppressEdgeChromeForCapture() {
            instance?.edgeOverlayHost?.suppressCaptureVisuals()
        }

        fun restoreEdgeChromeAfterCapture() {
            instance?.edgeOverlayHost?.resumeCaptureVisuals()
        }

        fun suspendEdgeCapturesForPassthrough() {
            instance?.edgeOverlayHost?.suspendEdgeCapturesForPassthrough()
        }

        fun resumeEdgeCapturesAfterPassthrough() {
            instance?.edgeOverlayHost?.resumeEdgeCapturesAfterPassthrough()
        }

        fun overlayHostContext(): Context? = instance

        fun collectTextAt(rawX: Float, rawY: Float): String? {
            val service = instance ?: return null
            return AccessibilityTextExtractor.collectTextAt(service, rawX, rawY)
        }

        fun findControlBoundsAt(
            rawX: Float,
            rawY: Float,
            activeWindowOnly: Boolean = false,
            maxNodes: Int = AccessibilityTextExtractor.DEFAULT_MAX_TRAVERSAL_NODES
        ): Rect? {
            val service = instance ?: return null
            return AccessibilityTextExtractor.findControlBoundsAt(
                service,
                rawX,
                rawY,
                activeWindowOnly,
                maxNodes
            )
        }

        fun collectTextInRect(rect: Rect): String {
            val service = instance ?: return ""
            return AccessibilityTextExtractor.collectTextInRect(service, rect)
        }

        fun pickFloatBallTextInRect(
            context: Context,
            rect: Rect,
            ocrFallbackEnabled: Boolean,
            ocrModelId: String,
            previewBoundsPick: Boolean = false,
            onResult: (FloatBallPickResult) -> Unit
        ) {
            val service = instance ?: run {
                onResult(
                    FloatBallPickResult(
                        a11yText = null,
                        ocrText = null,
                        screenshot = null,
                        screenRect = null
                    )
                )
                return
            }
            FloatBallTextPickCoordinator.pickInRect(
                service,
                context,
                rect,
                ocrFallbackEnabled,
                ocrModelId,
                previewBoundsPick,
                onResult
            )
        }

        fun pickFullscreen(
            context: Context,
            ocrFallbackEnabled: Boolean,
            ocrModelId: String
        ): Boolean {
            val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
            if (screenWidth <= 0 || screenHeight <= 0) return false
            val panelAnchorX = screenWidth / 2f
            val panelAnchorY = screenHeight.toFloat()
            FloatBallPickResultPanel.showLoading(
                context,
                panelAnchorX,
                panelAnchorY,
                PickResultTextSource.OCR
            )
            pickFloatBallOnRelease(
                context = context,
                startX = 0f,
                startY = 0f,
                endX = screenWidth.toFloat(),
                endY = screenHeight.toFloat(),
                regionalRect = true,
                ocrFallbackEnabled = ocrFallbackEnabled,
                ocrModelId = ocrModelId
            ) { result ->
                FloatBallPickResultPanel.showResult(context, panelAnchorX, panelAnchorY, result)
            }
            return true
        }

        fun pickFloatBallOnRelease(
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
            val service = instance ?: run {
                onResult(
                    FloatBallPickResult(
                        a11yText = null,
                        ocrText = null,
                        screenshot = null,
                        screenRect = null
                    )
                )
                return
            }
            FloatBallTextPickCoordinator.pickOnRelease(
                service,
                context,
                startX,
                startY,
                endX,
                endY,
                regionalRect,
                ocrFallbackEnabled,
                ocrModelId,
                onResult
            )
        }

        fun currentForegroundPackage(): String? = instance?.foregroundPackageName()

        /**
         * 编排在注入失败/被关闭后调用：同步做一次无障碍填充。
         *
         * 返回 null 表示无障碍服务没连着；返回结果里的 success/strategy/reason 直接用于记账。
         * 必须由主线程调用（编排的 handler 就是主线程）。
         */
        fun fillOtpNow(code: String, settings: AppSettings): OtpAutoFillController.FillOutcome? {
            val service = instance ?: return null
            return OtpAutoFillController.fillNow(service, settings, code)
        }

        private const val TAG = "SlideIndexA11y"
        private const val CONFIG_CHANGE_SUPPRESSION_RETRY_MS = 400L
        private const val SCROLL_BOTTOM_STROKE_COUNT = 10
        /** 浮层租约续租间隔，需明显小于 OverlayHostLease 的超时时间。 */
        private const val OVERLAY_HOST_LEASE_RENEW_MS = 2_000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        instanceGeneration++
        AccessibilityLink.noteAlive("onServiceConnected")
        // 同一服务实例可能被系统重复回调 onServiceConnected（重绑 / 抖动重绑）。
        // 旧看门狗注册的熄屏广播与唤醒锁必须先解绑：字段被覆盖后旧对象再没人引用，
        // 它注册的接收者会一直留着，每重连一次就多一份。
        if (::watchdog.isInitialized) {
            runCatching { watchdog.unregisterScreenLockReceiver() }
            runCatching { watchdog.releaseWakeLock() }
        }
        // 上一代手势动画的 Compose 组合属于已被系统摘掉的窗口：不丢就会出现
        // “状态机在跑（onDrag/snapTo 都有）但屏幕上一个像素都没有”。重连即重建。
        runCatching { GestureAnimationOverlayRegistry.detachAll() }
        startOverlayHostLease()
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "onServiceConnected")
        watchdog = SlideIndexAccessibilityWatchdog(this) { edgeOverlayHost }
        foregroundTracker = SlideIndexAccessibilityForegroundTracker(
            service = this,
            overlayHost = { edgeOverlayHost },
            onMaybeOtp = {},
            onSyncLockScreen = { watchdog.syncLockScreenState() },
            excludedPackageProvider = {
                deps.settingsRepository.readSnapshot().previousAppExcludedPackages
            }
        )
        watchdog.syncLockScreenState()
        // 重复连接时旧宿主必须显式 stop：只覆盖引用会让它的触钮窗口与协程继续活着，
        // 和新宿主叠成两套完全重合的窗口——点击穿透注入的那一下会被旧层吞掉，
        // 真机表现就是「触钮消失底下还有一层」。
        runCatching { edgeOverlayHost?.stop() }
        edgeOverlayHost = null
        edgeOverlayHost = EdgeOverlayHost(this, serviceScope, deps).also { it.start() }
        watchdog.registerScreenLockReceiver()
        lastOrientation = resources.configuration.orientation
        syncMonitoring()
        backTapGestureHost.start(serviceScope)
        GestureToggleTileWarmup.requestListening(this, "a11yConnected")
        // 预热分享面板的目标列表（文本分享最常见）：延迟几秒，避开服务连接高峰期；命中缓存时是空操作。
        com.slideindex.app.overlay.share.ShareSheetTargetCache.warmUpAsync(this)
        notifyModuleHostState(ready = true)
        logDiagnosticEnvironment()
        Log.i(TAG, "onServiceConnected: edge overlays attached")
    }

    /**
     * 诊断包的环境头：把「这条机器上到底能不能按预期写/接管」一次性写进埋点。
     *
     * 排查时最费时间的三件事——音量能不能绝对设置、亮度能不能在滑动中实时写、模块有没有真的接管——
     * 全都在这里给出当场值，不用再靠猜。
     */
    private fun logDiagnosticEnvironment() {
        runCatching {
            val settings = deps.settingsRepository.readSnapshot()
            val volumeScale = runCatching {
                val manager = getSystemService(android.media.AudioManager::class.java)
                if (manager == null) "audio=null"
                else com.slideindex.app.util.VolumeControlHelper.describeScale(
                    manager,
                    android.media.AudioManager.STREAM_MUSIC,
                )
            }.getOrElse { "audio=err:${it.javaClass.simpleName}" }
            EdgeDiag.marker("环境快照")
            EdgeDiag.log("env", "音量量程/步长: $volumeScale")
            EdgeDiag.log("env", "亮度: ${com.slideindex.app.util.BrightnessControlHelper.describeScale(this)}")
            EdgeDiag.log(
                "env",
                "权限: 通知策略=${diagYesNo(com.slideindex.app.util.PermissionHelper.hasNotificationPolicyAccess(this))} " +
                    "悬浮窗=${diagYesNo(com.slideindex.app.util.PermissionHelper.canDrawOverlays(this))} " +
                    "写系统设置=${diagYesNo(com.slideindex.app.util.PermissionHelper.canWriteSettings(this))} " +
                    "Shizuku=${diagYesNo(com.slideindex.app.util.TaskManagerUtil.hasPermission())}",
            )
            EdgeDiag.log(
                "env",
                "接管设置: 侧边=${diagYesNo(settings.systemGestureTakeoverSides)} " +
                    "顶部=${diagYesNo(settings.systemGestureTakeoverTop)} " +
                    "底部=${diagYesNo(settings.systemGestureTakeoverBottom)} " +
                    "拦截系统返回=${diagYesNo(settings.interceptSystemBackGesture)}",
            )
            val bridge = com.slideindex.app.xposed.bridge.ModuleBridgeStatusStore.read(this)
            EdgeDiag.log(
                "env",
                "模块回执: active=${bridge.active} state=${bridge.state} detail=${bridge.detail}",
            )
        }.onFailure {
            EdgeDiag.log("env", "环境快照失败 ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun diagYesNo(value: Boolean): String = if (value) "是" else "否"

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newOrientation = newConfig.orientation
        if (lastOrientation != Configuration.ORIENTATION_UNDEFINED &&
            newOrientation != lastOrientation &&
            FloatingPointerOverlayWindow.isShowing
        ) {
            FloatingPointerOverlayWindow.dismiss()
        }
        lastOrientation = newOrientation
        messageReminderOrchestrator.onConfigurationChanged(this, newConfig)
        syncForegroundPackageForOverlaySuppression()
        edgeOverlayHost?.onConfigurationChanged()
        scheduleOverlaySuppressionAfterConfigurationChange()
    }

    private val configChangeSuppressionRunnable = Runnable {
        syncForegroundPackageForOverlaySuppression()
        edgeOverlayHost?.recoverTriggerInteraction(forceReAddChrome = false)
        edgeOverlayHost?.refreshOverlaySuppression()
    }

    private val configChangeFinalSettleRunnable = Runnable {
        syncForegroundPackageForOverlaySuppression()
        edgeOverlayHost?.recoverTriggerInteraction(forceReAddChrome = false)
        edgeOverlayHost?.refreshOverlaySuppression()
    }

    private fun scheduleOverlaySuppressionAfterConfigurationChange() {
        mainHandler.removeCallbacks(configChangeSuppressionRunnable)
        mainHandler.removeCallbacks(configChangeFinalSettleRunnable)
        mainHandler.postDelayed(configChangeSuppressionRunnable, CONFIG_CHANGE_SUPPRESSION_RETRY_MS)
        mainHandler.postDelayed(configChangeFinalSettleRunnable, CONFIG_CHANGE_SUPPRESSION_RETRY_MS * 2)
    }

    private fun syncForegroundPackageForOverlaySuppression() {
        val resolved = com.slideindex.app.util.AccessibilityForegroundResolver.resolveHostPackage(this)
        if (resolved != null) {
            edgeOverlayHost?.updateForegroundPackage(resolved)
            return
        }
        val selfPackage = applicationContext.packageName
        val activePkg = if (::foregroundTracker.isInitialized) {
            foregroundTracker.currPackageName?.takeIf { it.isNotBlank() && it != selfPackage }
        } else {
            null
        }
        activePkg?.let { edgeOverlayHost?.updateForegroundPackage(it) }
            ?: edgeOverlayHost?.refreshOverlaySuppression()
    }

    fun dispatchExternalGestureAction(
        action: GestureAction,
        anchorRawY: Float,
        panelSide: com.slideindex.app.overlay.PanelSide? = null
    ): Boolean =
        edgeOverlayHost?.dispatchExternalGestureAction(action, anchorRawY, panelSide) == true

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "onUnbind: accessibility service unbound by system")
        // 心跳先停：之后如果还有窗口没拆掉，OverlayHostLease 会在租约过期时兜底摘除。
        stopOverlayHostLease()
        // 每一段都独立兜异常：任何一步抛异常都不能让后面的窗口留在 WindowManager 里
        // （残留的全屏直触窗会吞掉整屏触摸，这是最危险的故障形态）。
        runCatching { edgeOverlayHost?.stop() }
        edgeOverlayHost = null
        if (::watchdog.isInitialized) {
            runCatching { watchdog.unregisterScreenLockReceiver() }
            runCatching { watchdog.releaseWakeLock() }
        }
        runCatching { ScreenSearchFloating.destroy() }
        if (::backTapGestureHost.isInitialized) runCatching { backTapGestureHost.stop() }
        instance = null
        AccessibilityLink.reset()
        runCatching { com.slideindex.app.overlay.OverlayStatePort.publish(this, "onUnbind") }
        runCatching { notifyModuleHostState(ready = false) }
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.i(TAG, "onRebind: accessibility service rebound by system")
        instance = this
        instanceGeneration++
        startOverlayHostLease()
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "onRebind")
        if (edgeOverlayHost == null) {
            edgeOverlayHost = EdgeOverlayHost(this, serviceScope, deps).also { it.start() }
        }
        if (::watchdog.isInitialized) {
            watchdog.registerScreenLockReceiver()
            watchdog.syncLockScreenState()
        }
        if (::backTapGestureHost.isInitialized) backTapGestureHost.start(serviceScope)
        syncMonitoring()
        GestureToggleTileWarmup.requestListening(this, "a11yRebound")
        notifyModuleHostState(ready = true)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(coalescedImeWindowsChangedWork)
        mainHandler.removeCallbacks(configChangeSuppressionRunnable)
        mainHandler.removeCallbacks(configChangeFinalSettleRunnable)
        stopOverlayHostLease()
        runCatching { ClipboardAccess.repository?.stopListening() }
        if (::watchdog.isInitialized) {
            runCatching { watchdog.unregisterScreenLockReceiver() }
            runCatching { watchdog.releaseWakeLock() }
        }
        runCatching { edgeOverlayHost?.stop() }
        edgeOverlayHost = null
        runCatching { ScreenSearchFloating.destroy() }
        if (::backTapGestureHost.isInitialized) runCatching { backTapGestureHost.stop() }
        runCatching { serviceScope.cancel() }
        instance = null
        AccessibilityLink.reset()
        runCatching { com.slideindex.app.overlay.OverlayStatePort.publish(this, "onDestroy") }
        runCatching { notifyModuleHostState(ready = false) }
        runCatching { super.onDestroy() }
    }

    /**
     * 通知 system_server 里的 LSPosed 模块：app 侧边缘 overlay 宿主是否就绪。
     *
     * 模块收到 ready 会立刻重绑事件桥（不必等 2.5s 冷却 + 下一次触摸/状态探测），
     * 收到失活会立刻收掉可能存在的吞流会话。广播内容只有一个布尔值，
     * 不携带任何用户数据（与配置下发一样是自定义 action 的普通广播）。
     */
    private fun notifyModuleHostState(ready: Boolean) {
        runCatching {
            sendBroadcast(
                Intent(ModuleHookBridgeContract.ACTION_HOST_STATE_CHANGED).apply {
                    putExtra(ModuleHookBridgeContract.EXTRA_HOST_READY, ready)
                },
            )
        }.onFailure { Log.w(TAG, "notifyModuleHostState($ready) failed: ${it.message}") }
    }

    internal fun launchPreviousApp(): Boolean = foregroundTracker.launchPreviousApp()

    internal fun foregroundPackageName(): String? =
        if (::foregroundTracker.isInitialized) foregroundTracker.currPackageName else null

    internal fun toggleKeepScreenOn(): Boolean = watchdog.toggleKeepScreenOn()

    internal fun syncMonitoring() {
        syncClipboardMonitoring()
        syncScreenshotMonitoring()
    }

    internal fun syncClipboardMonitoring() {
        val repository = ClipboardAccess.repository ?: return
        ClipboardMonitorStartup.runOnMainWhenReady {
            repository.syncClipboardMonitoringFromSettings()
        }
    }

    internal fun syncScreenshotMonitoring() {
        val repository = ClipboardAccess.repository ?: return
        val settings = deps.settingsRepository.readSnapshot()
        if (settings.clipboardScreenshotMonitoring &&
            ClipboardPermissionHelper.hasMediaReadPermission(this)
        ) {
            repository.startScreenshotMonitoring()
        } else {
            repository.stopScreenshotMonitoring()
        }
    }

    internal fun takeScreenshotDelayed() = watchdog.takeScreenshotDelayed(mainHandler)

    internal fun fastVerticalScroll(toTop: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        val builder = GestureDescription.Builder()
        if (toTop) {
            val path = Path().apply {
                moveTo(centerX, centerY)
                lineTo(centerX, centerY + Int.MAX_VALUE)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 120))
        } else {
            val strokeCount = SCROLL_BOTTOM_STROKE_COUNT
                .coerceAtMost(GestureDescription.getMaxStrokeCount())
            repeat(strokeCount) { index ->
                val path = Path().apply {
                    moveTo(centerX, centerY)
                    lineTo(centerX, 0f)
                }
                builder.addStroke(
                    GestureDescription.StrokeDescription(path, index * 80L, 12)
                )
            }
        }
        val accepted = dispatchGesture(builder.build(), null, null)
        if (!accepted) {
            Log.w(TAG, "fastVerticalScroll(toTop=$toTop) rejected")
        }
        return accepted
    }
}
