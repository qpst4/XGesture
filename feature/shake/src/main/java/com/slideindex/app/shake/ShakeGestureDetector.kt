package com.slideindex.app.shake

import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Shake detection aligned with com.ksxkq.floating.runtime.ShakeController (8.0.8):
 * gyroscope angular velocity thresholds per axis, 500ms cooldown, reverse-direction guard.
 * 无陀螺仪的设备回退到加速度计差分。
 */
class ShakeGestureDetector(
    private val sensorHub: MotionSensorHub,
    private val onGestureDetected: (ShakeGestureType) -> Unit,
) {

    val isAvailable: Boolean get() = sensorHub.hasGyroscope || sensorHub.hasAccelerometer
    val isRunning: Boolean get() = listenerRegistered

    private var useAccelerometer = false
    private var globalSensitivity = 6.0f
    private var independentEnabled = false
    private var perDirectionSensitivity: Map<ShakeGestureType, Float> = emptyMap()
    private var listenerRegistered = false

    /**
     * 两次手势之间的最短间隔。
     *
     * 普通模式固定 500ms；「晃动菜单」激活后由 Host 调到更短的入场冷却（默认 300ms），
     * 否则用户打开菜单后要干等 500ms 才能晃第二个方向，手感发涩。
     */
    private var cooldownMs = NORMAL_COOLDOWN_MS

    /**
     * 反向手势的额外忽略窗口。
     *
     * 普通模式 800ms：一次物理晃动会带回摆，防止「左翻」被立刻跟一个「右翻」。
     * 菜单模式下必须收窄到与入场冷却同一量级——「左翻打开菜单、右翻执行」是正常操作，
     * 留着 800ms 会让用户觉得晃了没反应。
     */
    private var reverseIgnoreMs = REVERSE_IGNORE_MS

    private var lastTriggerTime = 0L
    private var lastDirection: ShakeGestureType? = null
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var accelReady = false

    @Synchronized
    fun setSensitivity(
        global: Float,
        independentEnabled: Boolean,
        perDirection: Map<ShakeGestureType, Float>,
    ) {
        globalSensitivity = ShakeGestureClassifier.clampSensitivity(global)
        this.independentEnabled = independentEnabled
        perDirectionSensitivity = perDirection
    }

    /** 调整手势间隔；限制在 [MIN_COOLDOWN_MS, NORMAL_COOLDOWN_MS]，不允许关掉去噪。 */
    @Synchronized
    fun setCooldownMs(valueMs: Long) {
        cooldownMs = valueMs.coerceIn(MIN_COOLDOWN_MS, NORMAL_COOLDOWN_MS)
    }

    /** 调整反向忽略窗口；菜单模式传入场冷却即可，`0` 表示不额外忽略。 */
    @Synchronized
    fun setReverseIgnoreMs(valueMs: Long) {
        reverseIgnoreMs = valueMs.coerceIn(0L, REVERSE_IGNORE_MS)
    }

    @Synchronized
    fun start() {
        if (listenerRegistered) return
        useAccelerometer = !sensorHub.hasGyroscope && sensorHub.hasAccelerometer
        accelReady = false
        val kind = if (useAccelerometer) {
            MotionSensorHub.Kind.ACCELEROMETER
        } else {
            MotionSensorHub.Kind.GYROSCOPE
        }
        val registered = sensorHub.register(
            kind = kind,
            owner = this,
            // 与悬浮菜单一致：SENSOR_DELAY_UI（约 66ms / 16Hz）。
            // 原来用 SENSOR_DELAY_GAME（20ms / 50Hz），事件量和判定开销是现在的 3 倍。
            delayUs = SensorManager.SENSOR_DELAY_UI,
        ) { event ->
            if (useAccelerometer) handleAccelerometer(event) else handleGyroscope(event)
        }
        listenerRegistered = registered
        Log.d(TAG, "start: kind=$kind accelFallback=$useAccelerometer registered=$registered")
    }

    @Synchronized
    fun stop() {
        sensorHub.unregister(this)
        listenerRegistered = false
        lastTriggerTime = 0L
        lastDirection = null
        accelReady = false
    }

    /**
     * 重新注册传感器。部分机型息屏后会挂起非唤醒传感器（加速度计/陀螺仪），
     * 重新注册可以恢复采样；屏幕状态切换时调用。
     */
    @Synchronized
    fun restartSensor() {
        if (!listenerRegistered) return
        sensorHub.unregister(this)
        listenerRegistered = false
        start()
    }

    @Synchronized
    private fun handleGyroscope(event: SensorEvent) {
        val gx = roundAxis(event.values[0])
        val gy = roundAxis(event.values[1])
        val gz = roundAxis(event.values[2])
        val direction = detectDirection(gx, gy, gz, abs(gx), abs(gy), abs(gz)) ?: return
        emitGesture(direction)
    }

    @Synchronized
    private fun handleAccelerometer(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        if (!accelReady) {
            lastAccel[0] = ax
            lastAccel[1] = ay
            lastAccel[2] = az
            accelReady = true
            return
        }

        val dx = ax - lastAccel[0]
        val dy = ay - lastAccel[1]
        val dz = az - lastAccel[2]
        lastAccel[0] = ax
        lastAccel[1] = ay
        lastAccel[2] = az

        val magnitude = sqrt(dx * dx + dy * dy + dz * dz)
        val threshold = ShakeGestureClassifier.effectiveThreshold(globalSensitivity) * ACCEL_DELTA_SCALE
        if (magnitude <= threshold) return

        val direction = detectDirection(
            dx,
            dy,
            dz,
            abs(dx),
            abs(dy),
            abs(dz),
            axisScale = 1f,
        ) ?: return
        emitGesture(direction)
    }

    private fun emitGesture(direction: ShakeGestureType) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < cooldownMs) return

        if (lastDirection != null && isReverseDirection(direction, lastDirection!!)) {
            if (now - lastTriggerTime < reverseIgnoreMs) return
        }

        lastTriggerTime = now
        lastDirection = direction
        Log.d(TAG, "gesture detected: $direction")
        onGestureDetected(direction)
    }

    private fun detectDirection(
        axisX: Float,
        axisY: Float,
        axisZ: Float,
        absX: Float,
        absY: Float,
        absZ: Float,
        axisScale: Float = 1f,
    ): ShakeGestureType? =
        ShakeGestureClassifier.detectDirection(
            axisX = axisX,
            axisY = axisY,
            axisZ = axisZ,
            absX = absX,
            absY = absY,
            absZ = absZ,
            globalSensitivity = globalSensitivity,
            independentEnabled = independentEnabled,
            perDirectionSensitivity = perDirectionSensitivity,
            axisScale = axisScale,
        )

    private fun isReverseDirection(current: ShakeGestureType, previous: ShakeGestureType): Boolean =
        when (current) {
            ShakeGestureType.LEFT_FLIP -> previous == ShakeGestureType.RIGHT_FLIP
            ShakeGestureType.RIGHT_FLIP -> previous == ShakeGestureType.LEFT_FLIP
            ShakeGestureType.FORWARD_FLIP -> previous == ShakeGestureType.BACKWARD_FLIP
            ShakeGestureType.BACKWARD_FLIP -> previous == ShakeGestureType.FORWARD_FLIP
            ShakeGestureType.LEFT_FLICK -> previous == ShakeGestureType.RIGHT_FLICK
            ShakeGestureType.RIGHT_FLICK -> previous == ShakeGestureType.LEFT_FLICK
        }

    private fun roundAxis(value: Float): Float =
        (value * 1000f).roundToInt() / 1000f

    companion object {
        private const val TAG = "ShakeGestureDetector"
        private const val NORMAL_COOLDOWN_MS = 500L
        private const val MIN_COOLDOWN_MS = 100L
        private const val REVERSE_IGNORE_MS = 800L
        /** 把界面上的 1～10 档阈值折算成加速度计差分阈值（沿用原陀螺仪阈值的换算系数）。 */
        private const val ACCEL_DELTA_SCALE = 0.35f

        /** Reference stores UI slider 1–10 directly as rad/s threshold. */
        fun effectiveThreshold(uiValue: Float): Float = ShakeGestureClassifier.effectiveThreshold(uiValue)
    }
}
