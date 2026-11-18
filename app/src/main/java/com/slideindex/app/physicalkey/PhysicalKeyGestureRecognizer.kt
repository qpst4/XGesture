package com.slideindex.app.physicalkey

/**
 * 驱动级物理实体按键手势状态机识别器。
 *
 * 负责接收 Linux evdev 的原始按键事件，识别单键长按、快速双击以及音量双键并击。
 * 纯逻辑实现，与 Android UI / Binder 解耦，支持 100% 确定性单测。
 */
class PhysicalKeyGestureRecognizer(
    var longPressTimeoutMs: Long = DEFAULT_LONG_PRESS_TIMEOUT_MS,
    var doubleClickTimeoutMs: Long = DEFAULT_DOUBLE_CLICK_TIMEOUT_MS,
    var chordWindowMs: Long = DEFAULT_CHORD_WINDOW_MS,
) {
    companion object {
        const val KEY_VOLUMEDOWN = 114
        const val KEY_VOLUMEUP = 115
        const val KEY_POWER = 116

        const val DEFAULT_LONG_PRESS_TIMEOUT_MS = 500L
        const val DEFAULT_DOUBLE_CLICK_TIMEOUT_MS = 350L
        const val DEFAULT_CHORD_WINDOW_MS = 150L
    }

    private class KeyState {
        var isDown: Boolean = false
        var downTimeMs: Long = 0L
        var longPressFired: Boolean = false
        var lastClickTimeMs: Long = 0L
        var inChord: Boolean = false
    }

    private val keyStates = mutableMapOf<Int, KeyState>()

    private fun getOrCreateState(keyCode: Int): KeyState =
        keyStates.getOrPut(keyCode) { KeyState() }

    fun reset() {
        keyStates.clear()
    }

    /**
     * 处理一次按键状态改变。
     *
     * @param keyCode Linux KEY_* 键码（如 114=音量减, 115=音量加, 116=电源键）
     * @param isDown true 为按下（value=1），false 为松开（value=0）；value=2（系统连击）可忽略或视为按住
     * @param nowMs 单调时钟时间戳
     * @return 识别到的手势列表（一般为 0 或 1 个）
     */
    fun onKeyEvent(keyCode: Int, isDown: Boolean, nowMs: Long): List<PhysicalKeyGesture> {
        val state = getOrCreateState(keyCode)
        val recognized = mutableListOf<PhysicalKeyGesture>()

        if (isDown) {
            if (state.isDown) {
                // 重复按下信号（如系统的 repeat 帧），保持按下状态，驱动长按检测
                val tickResults = onTick(nowMs)
                recognized.addAll(tickResults)
                return recognized
            }

            state.isDown = true
            state.downTimeMs = nowMs
            state.longPressFired = false
            state.inChord = false

            // 检查双键并击（Vol+ 与 Vol-）
            if (keyCode == KEY_VOLUMEUP || keyCode == KEY_VOLUMEDOWN) {
                val otherCode = if (keyCode == KEY_VOLUMEUP) KEY_VOLUMEDOWN else KEY_VOLUMEUP
                val otherState = keyStates[otherCode]
                if (otherState != null && otherState.isDown && !otherState.inChord && !otherState.longPressFired) {
                    val timeDiff = kotlin.math.abs(nowMs - otherState.downTimeMs)
                    if (timeDiff <= chordWindowMs) {
                        state.inChord = true
                        otherState.inChord = true
                        state.longPressFired = true
                        otherState.longPressFired = true
                        state.lastClickTimeMs = 0L
                        otherState.lastClickTimeMs = 0L
                        recognized.add(PhysicalKeyGesture.VOLUME_BOTH_PRESS)
                    }
                }
            }
        } else {
            // 松开按键
            state.isDown = false

            if (state.inChord) {
                state.inChord = false
                return recognized
            }

            if (state.longPressFired) {
                state.longPressFired = false
                return recognized
            }

            // 短按松开：判定是否构成双击
            if (state.lastClickTimeMs > 0L && (nowMs - state.lastClickTimeMs <= doubleClickTimeoutMs)) {
                state.lastClickTimeMs = 0L
                when (keyCode) {
                    KEY_POWER -> recognized.add(PhysicalKeyGesture.POWER_DOUBLE_PRESS)
                    KEY_VOLUMEUP -> recognized.add(PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS)
                    KEY_VOLUMEDOWN -> recognized.add(PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS)
                }
            } else {
                state.lastClickTimeMs = nowMs
            }
        }

        return recognized
    }

    /**
     * 定时心跳轮询（在没有新按键事件时驱动长按超时判定）。
     */
    fun onTick(nowMs: Long): List<PhysicalKeyGesture> {
        val recognized = mutableListOf<PhysicalKeyGesture>()

        for ((keyCode, state) in keyStates) {
            if (state.isDown && !state.longPressFired && !state.inChord) {
                if (nowMs - state.downTimeMs >= longPressTimeoutMs) {
                    state.longPressFired = true
                    state.lastClickTimeMs = 0L
                    when (keyCode) {
                        KEY_POWER -> recognized.add(PhysicalKeyGesture.POWER_LONG_PRESS)
                        KEY_VOLUMEUP -> recognized.add(PhysicalKeyGesture.VOLUME_UP_LONG_PRESS)
                        KEY_VOLUMEDOWN -> recognized.add(PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS)
                    }
                }
            } else if (!state.isDown && state.lastClickTimeMs > 0L) {
                // 清理过期的单击时间戳
                if (nowMs - state.lastClickTimeMs > doubleClickTimeoutMs) {
                    state.lastClickTimeMs = 0L
                }
            }
        }

        return recognized
    }
}
