package com.slideindex.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.PowerManager
import android.view.Choreographer
import com.slideindex.app.overlay.EdgeOverlayHost
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.GlobalOverlayDismissHelper
import com.slideindex.app.overlay.history.HistoryHandleCaptureVisibility
import com.slideindex.app.util.LockScreenState
import com.slideindex.app.util.TriggerEnvironmentState
import java.util.concurrent.atomic.AtomicInteger

internal class SlideIndexAccessibilityWatchdog(
    private val service: SlideIndexAccessibilityService,
    private val overlayHost: () -> EdgeOverlayHost?
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLockReceiverRegistered = false

    /** 截屏请求序号：每次 [takeScreenshotDelayed] 递增；旧请求的截图与恢复都会被丢弃。 */
    private val screenshotRequestSeq = AtomicInteger(0)

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    TriggerEnvironmentState.lockScreenActive = true
                    com.slideindex.app.overlay.OverlayStatePort.publish(service, "screenOff")
                    GlobalOverlayDismissHelper.dismissAllPanels(lockScreenBoundary = true)
                    // 熄屏是硬边界：无条件收掉侧边会话/全屏直触，避免丢 UP 后整屏被浮层吃掉。
                    runCatching { overlayHost()?.forceRecoverInteractionState() }
                    // 触钮 + 悬浮球/边角轮盘一并按锁屏抑制策略刷新，避免解锁后只恢复触钮。
                    overlayHost()?.refreshOverlaySuppression()
                }
                Intent.ACTION_SCREEN_ON -> {
                    syncLockScreenState()
                    overlayHost()?.refreshOverlaySuppression()
                }
                Intent.ACTION_USER_PRESENT -> {
                    TriggerEnvironmentState.lockScreenActive = false
                    com.slideindex.app.overlay.OverlayStatePort.publish(service, "userPresent")
                    // 开启「解锁后继续显示悬浮球提醒」时，补显锁屏期间保留下来的悬浮球提醒。
                    com.slideindex.app.overlay.FloatIconOverlayWindow.replayAfterUnlock()
                    // 贴边卡同理：锁屏时按设置挂起，解锁补显。
                    com.slideindex.app.overlay.EdgeCardOverlayWindow.replayAfterUnlock(service)
                    overlayHost()?.refreshOverlaySuppression()
                }
            }
        }
    }

    fun syncLockScreenState() {
        val accessibilityWindows = service.windows
        val isLocked = LockScreenState.detectActive(service, accessibilityWindows)
        TriggerEnvironmentState.lockScreenActive = isLocked
        com.slideindex.app.overlay.OverlayStatePort.publish(service, "syncLockScreenState")
        if (isLocked) {
            GlobalOverlayDismissHelper.dismissAllPanels(lockScreenBoundary = true)
        }
    }

    fun registerScreenLockReceiver() {
        if (screenLockReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        service.registerReceiver(screenLockReceiver, filter)
        screenLockReceiverRegistered = true
    }

    fun unregisterScreenLockReceiver() {
        if (!screenLockReceiverRegistered) return
        runCatching { service.unregisterReceiver(screenLockReceiver) }
        screenLockReceiverRegistered = false
        TriggerEnvironmentState.lockScreenActive = false
        com.slideindex.app.overlay.OverlayStatePort.publish(service, "unregisterScreenLockReceiver")
    }

    fun toggleKeepScreenOn(): Boolean {
        if (wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
            return true
        }
        val powerManager = service.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "SlideIndex:KeepScreenOn"
        )
        return runCatching {
            wakeLock?.acquire(10 * 60 * 1000L)
            true
        }.getOrDefault(false)
    }

    fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    fun takeScreenshotDelayed(mainHandler: Handler) {
        // 连点 / 多入口并发时，只有最后一次请求有权恢复浮层：否则前一次的到点恢复会把浮层放出来，
        // 正好被后一次的系统截图拍到 —— 本函数修的就是"拍到自家浮层"，不能在这里再留一个口子。
        val requestId = screenshotRequestSeq.incrementAndGet()
        val hideOverlays = Runnable {
            FloatingPointerOverlayWindow.suppressForScreenshotCapture()
            FloatBallOverlay.suppressForScreenshotCapture()
            FloatBallPickResultPanel.suppressForScreenshotCapture()
            // 边缘触钮与贴边收纳把手是另外两个常驻窗：只藏球和指针不够，它们照样会被拍进去。
            SlideIndexAccessibilityService.suppressEdgeChromeForCapture()
            HistoryHandleCaptureVisibility.suppress()
        }
        val restoreOverlays = Runnable {
            FloatingPointerOverlayWindow.restoreAfterScreenshotCapture()
            FloatBallOverlay.restoreAfterScreenshotCapture()
            FloatBallPickResultPanel.restoreAfterScreenshotCapture()
            SlideIndexAccessibilityService.restoreEdgeChromeAfterCapture()
            HistoryHandleCaptureVisibility.restore()
        }
        val scheduleCapture = Runnable {
            mainHandler.postDelayed({
                if (requestId != screenshotRequestSeq.get()) return@postDelayed
                service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                )
                mainHandler.postDelayed({
                    if (requestId != screenshotRequestSeq.get()) return@postDelayed
                    restoreOverlays.run()
                }, SCREENSHOT_RESTORE_DELAY_MS)
            }, SCREENSHOT_DELAY_MS)
        }
        val hideThenCapture = Runnable {
            hideOverlays.run()
            // Wait one compositor frame so WM detach is visible before system capture.
            Choreographer.getInstance().postFrameCallback { scheduleCapture.run() }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            hideThenCapture.run()
        } else {
            mainHandler.post(hideThenCapture)
        }
    }

    companion object {
        private const val SCREENSHOT_DELAY_MS = 400L
        /** Keep overlays hidden until the system capture finishes. */
        private const val SCREENSHOT_RESTORE_DELAY_MS = 800L
    }
}
