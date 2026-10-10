package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.slideindex.app.gesture.KeyboardTriggerBoundsAdjuster
import com.slideindex.app.service.SlideIndexAccessibilityService

/**
 * "键盘弹出时收窄 / 上移左右触钮与悬浮球"的协调器。
 *
 * ⚠️ 与剪贴板浮窗那边是**同构**的坑：
 *
 * 1. 这里的每一次重排都会去 `updateViewLayout` 我们自己的浮窗，又产生新的
 *    `TYPE_WINDOWS_CHANGED`。若按单次采样动作，一个抖动的 IME 信号就会带着触钮与悬浮球
 *    一起反复重排，再喂给下一轮采样。→ 接 [ImeSignalStabilizer]，只下发**已提交**的状态。
 * 2. 采样只在窗口事件里发生，而键盘收起后不再有窗口事件：只靠"下一次采样"确认候选会让
 *    触钮永久停在"键盘在"的姿态（该收窄的没收、该还原的没还原）。→ 必须按 [ImeSignalStabilizer.nextWakeAtMs]
 *    定时到点自查。
 */
object KeyboardTriggerImeCoordinator {

    /** IME 可见性状态机；只有它提交的翻转才会真正重排浮窗。 */
    private val stabilizer = ImeSignalStabilizer()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { onTick() }

    @Volatile
    private var lastImeTop: Int? = null

    /** 最近一次采样到的顶缘；到点提交"可见"时用它。 */
    @Volatile
    private var lastSampledImeTop: Int? = null

    fun onWindowsChanged(service: AccessibilityService) {
        val probe = ImeBoundsDetector.detectImeProbe(service)
        val top = probe.bounds?.top
        lastSampledImeTop = top
        val now = SystemClock.uptimeMillis()
        val committed = stabilizer.onSample(sampleVisible = probe.bounds != null, nowMs = now)
        if (ImeDiagnostics.enabled) {
            ImeDiagnostics.log(
                "trigger",
                "src=${probe.source} sample=${probe.bounds != null}" +
                    " committedVisible=${stabilizer.committedVisible} flipped=$committed" +
                    " appWin=${probe.applicationWindowPresent} rawInset=${probe.rawImeInsetBottom}",
                now,
            )
        }
        if (committed) {
            applyCommitted(visible = probe.bounds != null, top = top)
        } else if (probe.bounds != null && stabilizer.committedVisible) {
            // 已确认可见：仍允许跟随顶缘变化（键盘变高 / 切表情面板）。
            relayoutForImeTop(top)
        }
        scheduleTick()
    }

    /** 到点自查：键盘收起后没有新事件，也必须把状态收敛回去。 */
    private fun onTick() {
        val now = SystemClock.uptimeMillis()
        val committed = stabilizer.tick(now)
        if (ImeDiagnostics.enabled) {
            ImeDiagnostics.log(
                "trigger-tick",
                "committed=$committed committedVisible=${stabilizer.committedVisible}",
                now,
            )
        }
        if (committed) {
            applyCommitted(visible = stabilizer.committedVisible, top = lastSampledImeTop)
        }
        scheduleTick()
    }

    private fun scheduleTick() {
        mainHandler.removeCallbacks(tickRunnable)
        val wakeAt = stabilizer.nextWakeAtMs() ?: return
        val delay = (wakeAt - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        mainHandler.postDelayed(tickRunnable, delay)
    }

    private fun applyCommitted(visible: Boolean, top: Int?) {
        KeyboardTriggerImeState.update(visible = visible, top = top)
        lastImeTop = top
        FloatBallOverlay.onKeyboardImeChanged()
        SlideIndexAccessibilityService.onKeyboardImeChanged(true)
    }

    /** 可见性没变、只有顶缘变了：沿用原来的 8px 去抖口径。 */
    private fun relayoutForImeTop(top: Int?) {
        if (!KeyboardTriggerBoundsAdjuster.shouldRelayout(
                previousTop = lastImeTop,
                visible = true,
                top = top,
            )
        ) {
            return
        }
        KeyboardTriggerImeState.update(visible = true, top = top)
        lastImeTop = top
        FloatBallOverlay.onKeyboardImeChanged()
        SlideIndexAccessibilityService.onKeyboardImeChanged(false)
    }
}
