package com.slideindex.app.macro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.macro.record.MacroRecordOverlay
import com.slideindex.app.macro.record.MacroRecorder
import com.slideindex.app.macro.record.MacroRecordingTouchCapture
import com.slideindex.app.settings.macro.MacroRecordingLauncher
import com.slideindex.app.settings.macro.MacroRecordingStart
import com.slideindex.app.service.SlideIndexAccessibilityService

/**
 * 把「录制宏」接到界面上。
 *
 * 界面侧只有一个窄接口 [MacroRecordingStart]（`feature/settings` 里定义，避免界面依赖录制实现），
 * 这里在 App 启动时把真正的实现塞进去。录制期间同时存在两层无障碍浮层：
 * 1. **全屏触摸捕获层**（先加，在下层）：接住用户的触摸 → 记成步骤 + 注入回去；
 * 2. **录制控制条**（后加，在上层）：暂停 / 停止 / 保存按钮（所以按钮的触摸不会被捕获层吃掉）。
 */
object MacroRecordingInstaller {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlay: MacroRecordOverlay? = null
    private var capture: MacroRecordingTouchCapture? = null

    /** 录制期间的逃逸出口：灭屏 / 超时都会自动收摊，绝不让全屏捕获层长期吞着触摸。 */
    private var screenOffReceiver: BroadcastReceiver? = null
    private var safetyNetContext: Context? = null
    private var hardStopRunnable: Runnable? = null

    /** 触摸位置指示器（录制时跟随手指）。 */
    private var touchIndicator: MacroTouchIndicator? = null

    /** 录制期间是否挂起了自家边缘浮层。 */
    private var edgeOverlaysSuspended = false

    /** 在 Application.onCreate 里调一次。 */
    fun install(appContext: Context) {
        val context = appContext.applicationContext
        MacroRecordingLauncher.impl = object : MacroRecordingStart {
            override fun start(
                context: Context,
                macroId: String,
                targetPackage: String,
                macroName: String,
                listener: MacroRecordingStart.Listener,
            ): Boolean {
                if (MacroEngine.isRunning) {
                    // 宏正在执行时不允许再录，否则两套注入会互相打架。
                    return false
                }
                val host = OverlayDependencyAccess.overlayHostContext() ?: return false
                // 目标可能是分身：`包名\u0002userId`，录制同样要跨 user 启动。
                val target = com.slideindex.app.launcher.AppTarget.parse(targetPackage)
                mainHandler.post {
                    if (target.isClone) {
                        runCatching {
                            com.slideindex.app.util.CloneAppLauncher.launch(
                                context,
                                target.packageName,
                                target.cloneUserId,
                            )
                        }.onFailure { Log.w(TAG, "无法启动目标应用 $targetPackage", it) }
                    } else {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(launchIntent) }
                                .onFailure { Log.w(TAG, "无法启动目标应用 $targetPackage", it) }
                        }
                    }

                    stopInternal()
                    // 录制期间挂起自家的边缘浮层/触钮：否则屏幕边缘的触摸会被我们自己的边缘窗吃掉，
                    // 系统返回手势（从边缘内滑）就录不到。
                    runCatching { SlideIndexAccessibilityService.suspendAllEdgeOverlays() }
                    edgeOverlaysSuspended = true
                    val indicator = MacroTouchIndicator(host)
                    touchIndicator = indicator
                    val recorder = MacroRecorder(
                        context = host,
                        targetPackage = targetPackage,
                        macroName = macroName,
                        macroId = macroId,
                    )
                    val captureLayer = MacroRecordingTouchCapture(
                        hostContext = host,
                        listener = object : MacroRecordingTouchCapture.Listener {
                            override fun onDown(x: Float, y: Float, eventTimeMs: Long) {
                                indicator.showAt(x, y)
                                recorder.onDown(x, y, eventTimeMs)
                            }

                            override fun onMove(x: Float, y: Float, eventTimeMs: Long) {
                                indicator.showAt(x, y)
                                recorder.onMove(x, y, eventTimeMs)
                            }

                            override fun onUp(x: Float, y: Float, eventTimeMs: Long) {
                                // 圆点是我们唯一的那一个窗口，不会自己消失：抬手后延迟淡出。
                                recorder.onUp(x, y, eventTimeMs)
                                indicator.hideAfter(200)
                            }

                            override fun onCancel() {
                                indicator.hide()
                                recorder.onCancel()
                            }
                        },
                        onInjectionUnavailable = {
                            // 这台设备上"吞触摸 + 回注"走不通：立刻收摊，绝不把用户的屏幕一直吞着。
                            mainHandler.post {
                                stopInternal()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.macro_record_touch_unavailable),
                                    Toast.LENGTH_LONG,
                                ).show()
                                listener.onRecordingFinished(0)
                            }
                        },
                        // 捕获层给控制条"挖洞"：落在条上的触摸直接归控制条，不记录也不注入。
                        barRectProvider = { overlay?.barRectOnScreen() },
                    )
                    val recordOverlay = MacroRecordOverlay(
                        hostContext = host,
                        recorder = recorder,
                        onSaved = { macro ->
                            stopInternal()
                            listener.onRecordingFinished(macro.steps.size)
                        },
                        onDiscarded = {
                            stopInternal()
                            listener.onRecordingFinished(0)
                        },
                        // 拖动控制条时让捕获层跟着挪"洞"，否则条会被捕获窗盖住、拖不动。
                        onMoved = { capture?.updateHole() },
                        // 反馈 #4：工具条上插入「返回 / 主屏 / 最近任务」步骤。
                        onKeyStep = { key -> recorder.addSystemKey(key) },
                    )

                    capture = captureLayer
                    overlay = recordOverlay
                    // 先让控制条挂上并完成布局，再建捕获层：否则首帧拿不到条的真实矩形，
                    // 捕获窗会暂时退化成"全屏"，把整条盖住（第一次拖条就被录进宏）。
                    recordOverlay.show()
                    recordOverlay.runAfterLayout {
                        if (overlay !== recordOverlay) return@runAfterLayout
                        captureLayer.show()
                        captureLayer.updateHole()
                    }
                    // 关键安全网：录制期间全屏层会吞掉所有触摸，所以必须有"用户一定能退出"的出口。
                    // 灭屏 → 自动结束；硬超时 → 自动结束。
                    installSafetyNets(host, listener)
                    listener.onRecordingStarted()
                }
                return true
            }
        }
    }

    private fun installSafetyNets(host: Context, listener: MacroRecordingStart.Listener) {
        clearSafetyNets()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    finishBySafetyNet(host, listener, R.string.macro_record_stopped_screen_off)
                }
            }
        }
        val registered = runCatching {
            host.registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED,
            )
        }
        screenOffReceiver = if (registered.isSuccess) receiver else null
        safetyNetContext = if (registered.isSuccess) host else null
        if (registered.isFailure) {
            Log.w(TAG, "灭屏监听注册失败（仍保留超时兜底）", registered.exceptionOrNull())
        }

        val cap = Runnable { finishBySafetyNet(host, listener, R.string.macro_record_stopped_timeout) }
        hardStopRunnable = cap
        mainHandler.postDelayed(cap, HARD_STOP_MS)
    }

    private fun finishBySafetyNet(
        host: Context,
        listener: MacroRecordingStart.Listener,
        messageRes: Int,
    ) {
        mainHandler.post {
            if (overlay == null && capture == null) return@post
            stopInternal()
            runCatching { Toast.makeText(host, host.getString(messageRes), Toast.LENGTH_LONG).show() }
            listener.onRecordingFinished(0)
        }
    }

    private fun clearSafetyNets() {
        screenOffReceiver?.let { receiver ->
            val context = safetyNetContext
            if (context != null) {
                runCatching { context.unregisterReceiver(receiver) }
                    .onFailure { Log.w(TAG, "注销灭屏监听失败", it) }
            }
        }
        screenOffReceiver = null
        safetyNetContext = null
        hardStopRunnable?.let { mainHandler.removeCallbacks(it) }
        hardStopRunnable = null
    }

    private fun stopInternal() {
        clearSafetyNets()
        touchIndicator?.hide()
        touchIndicator = null
        if (edgeOverlaysSuspended) {
            runCatching { SlideIndexAccessibilityService.resumeAllEdgeOverlays() }
            edgeOverlaysSuspended = false
        }
        overlay?.dismiss()
        overlay = null
        capture?.dismiss()
        capture = null
    }

    private const val TAG = "MacroRecordingInstaller"

    /** 抬手后指示器停留的时长。 */
    private const val INDICATOR_LINGER_MS = 320L

    /** 录制硬超时：即使按钮点不到，也绝不会让全屏捕获层一直吞触摸。 */
    private const val HARD_STOP_MS = 10 * 60 * 1000L
}
