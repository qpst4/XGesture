package com.slideindex.app.dragpress

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.slideindex.app.shizuku.ITouchInterruptionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局长按强行拖拽手势检测与调度器：
 * - 监听特权触摸事件流；
 * - 识别单指长按（移动限制在 36px 以内，达到时长阈值后 armed 并震动）；
 * - 用户移动手指后 fired，通过 Shizuku 注入 CANCEL 掐断源应用触摸；
 * - 挂载透明防触遮罩，抓取触摸点文字/内容，无缝唤起流转栏（DragDropFlowController）接管后续拖拽。
 */
class GlobalPressDragSource(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val getTimeoutMs: () -> Int,
    private val isVibrateEnabled: () -> Boolean,
    private val isImmediateEnabled: () -> Boolean = { false },
    private val isEarlyTextEnabled: () -> Boolean = { false },
    private val getWhitelist: () -> Set<String> = { emptySet() },
    private val panel: () -> ContentDragTarget?,
) {
    private val context: Context get() = service
    private val main = Handler(Looper.getMainLooper())
    private val detector = PressTriggerDetector(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val blocker = BackgroundTouchBlocker(context)
    private val preview = DragContentPreview(context, scope)
    private val capture = PressContentCapture(service)
    private val captureGate = Mutex()

    private var sourceCheck: Job? = null
    private var preparation: Job? = null
    private var preparationTimeout: Job? = null
    private var windowCheck: Job? = null
    private var interruptionAllowed: AtomicBoolean? = null
    private var sourcePackage: String? = null
    private var earlyText: String? = null
    private var downX = 0f
    private var downY = 0f
    @Volatile private var generation = 0L
    private var active = false
    private var shown = false
    private var preparing = false
    private var readyNotified = false
    val isGestureActive: Boolean get() = active || shown || preparing
    private var systemDrag = false
    private var captureX = 0f
    private var captureY = 0f
    private var latestX = 0f
    private var latestY = 0f

    private val input = TouchInputSource(context, scope, ::onPointer) { cancel("input_disconnected") }

    private val arm = Runnable {
        if (!allowed()) {
            cancel("confirmation_not_allowed")
        } else {
            detector.arm(SystemClock.uptimeMillis())
            captureX = latestX
            captureY = latestY
            notifyReady()
            if (isImmediateEnabled() && detector.isReady) {
                prepareDrag()
            }
        }
    }

    private val expiry = Runnable { cancel("gesture_timeout") }

    fun start() {
        panel()?.setOnContentConvertedListener { convertedClip, _ ->
            if (!shown && !preparing) return@setOnContentConvertedListener
            val bounds = getScreenBounds()
            scope.launch {
                preview.show(convertedClip, latestX, latestY, bounds)
            }
        }
        if (isEnabled() && screenReady()) {
            input.start()
        } else {
            input.stop()
        }
    }

    fun onSettingsChanged() {
        cancel("settings_changed")
        if (isEnabled() && screenReady()) {
            input.start()
        } else {
            input.stop()
        }
    }

    private fun screenReady(): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return (power?.isInteractive == true) && (keyguard?.isKeyguardLocked != true)
    }

    private fun allowed(): Boolean = isEnabled() && active && !systemDrag && screenReady() && panel() != null

    private fun onPointer(action: Int, x: Float, y: Float, time: Long) {
        if (kotlin.math.abs(SystemClock.uptimeMillis() - time) > 1000) {
            cancel("stale_input")
            return
        }
        latestX = x
        latestY = y
        if (action == MotionEvent.ACTION_DOWN) {
            cancel("next_down")
            if (systemDrag || !isEnabled() || !screenReady() || panel() == null) return
            active = true
            downX = x
            downY = y
            detector.start(x, y, time, getTimeoutMs().coerceIn(200, 2000).toLong())
            checkSource(x, y)
            main.postAtTime(arm, detector.deadline())
            main.postDelayed(expiry, 15_000)
            return
        }
        if (!active) return
        if (!allowed()) {
            cancel("pointer_not_allowed")
            return
        }
        if (action == MotionEvent.ACTION_UP) {
            if (shown) panel()?.updateContentDrag(x, y, release = true)
            cancel("pointer_up")
            return
        }
        detector.onPointerEvent(action, x, y, 1)
        if (!detector.isActive) {
            cancel(if (action == MotionEvent.ACTION_MOVE) "moved_before_hold" else "contact_cancelled")
            return
        }
        if (detector.isFired) {
            prepareDrag()
        }
        if (shown || preparing) {
            panel()?.updateContentDrag(x, y)
            preview.move(x, y, getScreenBounds())
        }
    }

    private fun getScreenBounds(): android.graphics.Rect {
        val wm = context.getSystemService(WindowManager::class.java)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun checkSource(x: Float, y: Float) {
        val request = generation
        sourceCheck = scope.launch(Dispatchers.IO) {
            try {
                val origin = capture.sourcePackageAt(x, y)
                val whitelist = getWhitelist()
                val rejection = when {
                    origin == null -> "source_unavailable"
                    capture.excludedPackage(origin) -> "protected_package"
                    whitelist.isNotEmpty() && origin !in whitelist -> "package_not_whitelisted"
                    else -> null
                }
                withContext(Dispatchers.Main) {
                    if (generation != request || !allowed()) return@withContext
                    if (rejection != null || origin == null) {
                        cancel(rejection ?: "source_unavailable")
                        return@withContext
                    }
                    sourcePackage = origin
                    notifyReady()
                    if (detector.isFired || (isImmediateEnabled() && detector.isReady)) prepareDrag()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation == request) cancel("source_check_failed")
                }
            }
        }
    }

    private fun notifyReady() {
        if (readyNotified || !detector.isReady || sourcePackage == null || !allowed()) return
        readyNotified = true
        if (isVibrateEnabled()) {
            performHapticFeedback()
        }
    }

    private fun performHapticFeedback() {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun prepareDrag() {
        if (shown || preparing || !(detector.isFired || (isImmediateEnabled() && detector.isReady)) || !allowed()) return
        val origin = sourcePackage ?: return
        val savedText = earlyText
        preparing = true
        val request = generation
        val started = SystemClock.uptimeMillis()
        val x = captureX
        val y = captureY
        val inputX = latestX
        val inputY = latestY
        @Suppress("DEPRECATION")
        val displayId = context.getSystemService(WindowManager::class.java).defaultDisplay.displayId
        val permitted = AtomicBoolean(true).also { interruptionAllowed = it }
        val guard = object : ITouchInterruptionGuard.Stub() {
            override fun isCurrent(): Boolean = permitted.get()
        }
        preparation = scope.launch(Dispatchers.IO) {
            if (!captureGate.tryLock()) {
                withContext(Dispatchers.Main) {
                    if (generation == request) cancel("capture_busy")
                }
                return@launch
            }
            try {
                if (!capture.isSourceAppCurrent(origin)) {
                    withContext(Dispatchers.Main) {
                        if (generation == request) cancel("source_changed")
                    }
                    return@launch
                }
                coroutineScope {
                    val content = async {
                        if (savedText != null) {
                            ClipData.newPlainText("drag_text", savedText)
                        } else {
                            capture.capture(x, y)
                        }
                    }
                    val interruption = async {
                        try {
                            input.interruptSourceTouch(
                                origin,
                                inputX,
                                inputY,
                                displayId,
                                SystemClock.uptimeMillis() + 500,
                                guard
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Log.w("GlobalPressDragSource", "interruptSourceTouch failed: ${error.message}")
                            false
                        }
                    }
                    val payload = content.await()
                    if (payload == null) {
                        withContext(Dispatchers.Main) {
                            if (generation == request) cancel("content_unavailable")
                        }
                        return@coroutineScope
                    }
                    interruption.await()
                    val sourceCurrent = capture.isSourceAppCurrent(origin)
                    withContext(Dispatchers.Main) {
                        if (generation != request || !permitted.get() || !allowed()) return@withContext
                        if (!sourceCurrent) {
                            cancel("source_app_changed")
                            return@withContext
                        }
                        if (SystemClock.uptimeMillis() - started > 1800) {
                            cancel("preparation_timeout")
                            return@withContext
                        }
                        preparationTimeout?.cancel()
                        preparationTimeout = null
                        beginPanel(payload, request)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("GlobalPressDragSource", "Press preparation failed", error)
                withContext(Dispatchers.Main) {
                    if (generation == request) cancel("preparation_failed")
                }
            } finally {
                captureGate.unlock()
            }
        }
        preparationTimeout = scope.launch(Dispatchers.Main) {
            delay(1800)
            if (generation == request && preparing && !shown) cancel("preparation_timeout")
        }
    }

    private fun beginPanel(content: ClipData, request: Long) {
        if (generation != request || !allowed()) return
        val accepted = panel()?.beginContentDrag(
            content,
            latestX,
            latestY,
            stillCurrent = { generation == request && allowed() },
            onReady = {
                if (generation == request && allowed()) {
                    preparing = false
                    shown = true
                }
            },
            onFailed = { reason ->
                if (generation == request) cancel("panel_$reason")
            }
        ) == true
        if (!accepted) {
            cancel("panel_begin_failed")
        } else {
            blocker.start { generation == request && allowed() }
            scope.launch(Dispatchers.Main) {
                if (generation == request && allowed()) {
                    preview.show(content, latestX, latestY, getScreenBounds())
                }
            }
        }
    }

    fun onWindowEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            if (!isEarlyTextEnabled()) return
            val origin = sourcePackage ?: return
            if (earlyText != null || preparing || shown || !allowed() ||
                event.eventTime < detector.downTime() || event.packageName?.toString() != origin
            ) return
            earlyText = runCatching { capture.longClickText(event, downX, downY, origin) }.getOrNull()
            return
        }
        if (!active || (event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        ) return
        val origin = sourcePackage ?: return
        val request = generation
        windowCheck?.cancel()
        windowCheck = scope.launch(Dispatchers.IO) {
            try {
                val current = capture.isSourceAppCurrent(origin)
                withContext(Dispatchers.Main) {
                    if (generation == request && active && !current) cancel("source_app_changed")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation == request) cancel("source_check_failed")
                }
            }
        }
    }

    fun onSystemDragStarted() {
        systemDrag = true
        cancel("system_drag_started")
    }

    fun onSystemDragEnded() {
        systemDrag = false
    }

    fun onScreenChanged() {
        cancel("screen_changed")
        if (isEnabled() && screenReady()) {
            input.start()
        } else {
            input.stop()
        }
    }

    fun cancel(reason: String = "lifecycle") {
        generation++
        interruptionAllowed?.set(false)
        interruptionAllowed = null
        preparation?.cancel()
        preparation = null
        active = false
        shown = false
        preparing = false
        readyNotified = false
        sourcePackage = null
        earlyText = null
        detector.cancel()
        main.removeCallbacks(arm)
        main.removeCallbacks(expiry)
        sourceCheck?.cancel()
        sourceCheck = null
        preparationTimeout?.cancel()
        preparationTimeout = null
        windowCheck?.cancel()
        windowCheck = null
        panel()?.cancelContentDrag()
        preview.clear()
        blocker.stop()
    }

    fun close() {
        cancel("source_closed")
        preview.clear()
        input.stop()
        blocker.close()
    }
}
