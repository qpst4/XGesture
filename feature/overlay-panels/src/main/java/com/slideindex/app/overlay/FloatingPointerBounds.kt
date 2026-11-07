package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.slideindex.app.settings.AppSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


object FloatingPointerBounds {
    /** Linear mapping (1.0) keeps pointer speed proportional to finger speed at all positions. */
    const val EDGE_CURVE_POWER_X = 1.0f
    const val EDGE_CURVE_POWER_Y = 1.0f

    const val SENSITIVITY_MIN = 0.2f
    const val SENSITIVITY_MAX = 0.75f
    const val DEFAULT_SENSITIVITY_FRACTION = 0.52f

    /**
     * 取词「加号」指针速度下限：允许 0%（比 [SENSITIVITY_MIN] 更慢）。
     * 0% 时手指需拖过约 95% 屏幕尺寸才能把指针推满整屏，用于精细取词。
     */
    const val PICK_POINTER_SPEED_MIN = 0f

    /**
     * 取词光标「起步加速区」内保证的最小倍率。
     *
     * 这一段的目的是让光标从边缘出发时**先跑起来**（对应 FV 里靠近边缘那条 2 倍带）：
     * 低速档（倍率 < 2）的人也能靠它在一屏行程内够到对侧；高速档本来就比 2 大，此项对它们不生效。
     * 因此"两条滑条相乘"不会出现 10×：区内取 `max(倍率, 2)`，区外才是用户自己的倍率。
     */
    const val PICK_ACCEL_GAIN = 2f

    /** 「起步加速区」宽度占屏幕尺寸的比例（0 = 关闭）。 */
    const val PICK_ACCEL_ZONE_DEFAULT = 0.5f
    const val PICK_ACCEL_ZONE_MIN = 0f
    const val PICK_ACCEL_ZONE_MAX = 1f

    /** 光标锚点相对唤醒侧屏幕边缘向屏外偏移的距离（dp）：让红十字"从屏幕外滑进来"。 */
    // 12dp = 十字半宽 7.5dp + 4.5dp 余量：静止时整体在屏幕外（看不见、不会露边），
    // 出现所需手指位移约 6dp（2× 起步区）/ 7dp（默认倍率），比 20dp 更跟手。
    const val PICK_ANCHOR_OUTSIDE_DP = 12f

    /**
     * 取词拖动死区（slop）的可调范围（dp）。
     *
     * 下限**不取 0**：0 时按下的 1~2px 抖动就会被判成拖动，长按/单击都会被吞掉。
     * 默认值仍是 4dp，想看"几乎零延迟"时可以调到 1dp（≈3px）。
     */
    const val PICK_SLOP_MIN_DP = 1f
    const val PICK_SLOP_MAX_DP = 32f

    /**
     * 红十字被"压"在手指上方时保留的竖直余量（dp）。
     *
     * 40dp ≈ 球半径（24dp）+ 16dp：被地板压住时十字停在**球顶上方约 16dp**，不会压到球上、
     * 也不会和球心齐平。取值越大，十字越早被压住（例如 72dp 时等于往下永远 1:1）。
     */
    /** 「贴底过渡区」滑条上限（0 = 关闭；最大 40% 屏高）。 */
    const val PICK_BOTTOM_TRANSITION_MAX = 0.4f

    const val PICK_FINGER_MARGIN_DP = 40f

    /**
     * 「贴底过渡区」：红十字的**下限（最大 Y）**。
     *
     * - 带子外：`手指Y − 余量`（红十字只能待在手指上方，纵向倍率再大也追不过手指）；
     * - 手指进到底部带子后：按 smoothstep 从"手指上方"平滑放开到"屏幕底边"，
     *   红十字于是从贴着手指上沿慢慢滑到手指下方；到最底时完全不限制。
     *
     * @param bandFraction 过渡带高度占屏高的比例（设置项 0~40%）
     */
    fun pickCursorFloorY(
        fingerY: Float,
        screenHeight: Float,
        bandFraction: Float,
        marginPx: Float
    ): Float {
        if (screenHeight <= 0f) return 0f
        val band = (bandFraction.coerceIn(0f, PICK_BOTTOM_TRANSITION_MAX) * screenHeight)
            .coerceAtLeast(1f)
        val bandTop = (screenHeight - band).coerceAtLeast(0f)
        val t = if (fingerY <= bandTop) 0f else ((fingerY - bandTop) / band).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        val nearFinger = fingerY - marginPx
        return nearFinger + (screenHeight - nearFinger) * eased
    }

  /** Reference width used when migrating legacy px-based area settings. */
    private const val MIGRATION_REFERENCE_SCREEN_WIDTH = 1080f

    fun sensitivityFraction(settings: AppSettings): Float =
        settings.floatingPointerSensitivityFraction.coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)

    /** UI speed fraction (higher = faster) → finger travel fraction (lower = faster). */
    fun travelFractionFromSpeed(speedFraction: Float): Float {
        // 下限取 0：取词「加号」滑杆允许 0%（最慢）。其余入口在读取设置时已限制在
        // [SENSITIVITY_MIN] 以上，因此不受影响。
        val speed = speedFraction.coerceIn(PICK_POINTER_SPEED_MIN, SENSITIVITY_MAX)
        return SENSITIVITY_MIN + SENSITIVITY_MAX - speed
    }

    /**
     * Finger travel along each axis needed to move the pointer across the full screen.
     * Higher [sensitivityFraction] (UI speed %) = shorter travel = faster pointer.
     */
    fun effectivePointerTravel(
        settings: AppSettings,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> = effectivePointerTravelForSpeed(
        speedFraction = sensitivityFraction(settings),
        screenWidth = screenWidth,
        screenHeight = screenHeight
    )

    /** Same mapping as edge floating pointer, but takes an explicit speed fraction. */
    fun effectivePointerTravelForSpeed(
        speedFraction: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float> {
        val travelFraction = travelFractionFromSpeed(speedFraction)
        return (screenWidth * travelFraction) to (screenHeight * travelFraction)
    }

    /** Migrates legacy width/zoom area prefs to the unified speed fraction. */
    fun migrateLegacySensitivityFraction(
        legacyWidthPx: Float,
        legacyZoomFraction: Float
    ): Float {
        val travelPx = legacyWidthPx.coerceIn(120f, 800f) * legacyZoomFraction.coerceIn(0.1f, 1f)
        val travelFraction =
            (travelPx / MIGRATION_REFERENCE_SCREEN_WIDTH).coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX)
        return travelFractionFromSpeed(travelFraction)
    }

    /** Maps finger travel since touch-down to pointer movement from the pointer position at down. */
    fun pointerForFingerDeltaInArea(
        deltaX: Float,
        deltaY: Float,
        travelWidth: Float,
        travelHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        pointerAnchorX: Float,
        pointerAnchorY: Float
    ): Offset {
        val normDeltaX = if (travelWidth > 0f) deltaX / travelWidth else 0f
        val normDeltaY = if (travelHeight > 0f) deltaY / travelHeight else 0f
        return Offset(
            x = (pointerAnchorX + normDeltaX * screenWidth).coerceIn(0f, screenWidth),
            y = (pointerAnchorY + normDeltaY * screenHeight).coerceIn(0f, screenHeight)
        )
    }

    /**
     * 取词光标：把「手指位移」折成「光标位移（px）」。
     *
     * - `zonePx` 以内：按 `max(用户倍率, [PICK_ACCEL_GAIN])` 走（默认 2×）——起步先跑起来，
     *   低速档也能在一屏行程内够到对侧；
     * - `zonePx` 以外：回到用户自己的倍率（`屏宽 / travelPx`）做 1:1 精调；
     * - `zonePx = 0` 时退化成原来的纯线性映射。
     *
     * 位置在交界处连续：两段都从 `fastGain × zonePx` 接上，所以只会改变"速度"，不会产生跳变。
     */
    fun pickCursorTravelPx(
        delta: Float,
        travelPx: Float,
        screenPx: Float,
        zonePx: Float
    ): Float {
        if (travelPx <= 0f || screenPx <= 0f || delta == 0f) return 0f
        val gain = screenPx / travelPx
        val zone = zonePx.coerceIn(0f, screenPx)
        val d = abs(delta)
        val inZone = min(d, zone)
        val fastGain = max(gain, PICK_ACCEL_GAIN)
        val moved = fastGain * inZone + gain * (d - inZone)
        return if (delta < 0f) -moved else moved
    }

    /**
     * 「起步加速区」宽度，单位是**手指行程**（px）：配置比例 × 该轴「走完整屏所需的手指行程」。
     *
     * 用行程而不是屏幕尺寸做基准，是为了让高速档不被"吃掉"：高速档的行程本来就很短，
     * 区宽随之等比缩小；低速档行程接近整屏，区宽也就接近半屏——和"屏幕的一半"的直觉一致，
     * 同时保证任何档位都不会出现"整段都在加速区里"。
     */
    fun pickAccelZonePx(fraction: Float, travelPx: Float): Float =
        if (travelPx <= 0f) {
            0f
        } else {
            (fraction.coerceIn(PICK_ACCEL_ZONE_MIN, PICK_ACCEL_ZONE_MAX) * travelPx)
                .coerceAtLeast(0f)
        }

    /**
     * Initial pointer position when continuing an in-flight edge gesture without lifting the finger.
     * Snaps to the nearest screen edge at the trigger finger's proportional position.
     */
    fun pointerStartForEdgeTrigger(
        rawX: Float,
        rawY: Float,
        screenWidth: Float,
        screenHeight: Float,
        edgeThresholdPx: Float
    ): Offset {
        if (screenWidth <= 0f || screenHeight <= 0f) {
            return Offset(screenWidth / 2f, screenHeight / 2f)
        }
        val x = rawX.coerceIn(0f, screenWidth)
        val y = rawY.coerceIn(0f, screenHeight)
        val threshold = edgeThresholdPx.coerceAtLeast(1f)
        val nearLeft = x <= threshold
        val nearRight = x >= screenWidth - threshold
        val nearTop = y <= threshold
        val nearBottom = y >= screenHeight - threshold
        return when {
            nearLeft && nearTop -> Offset(0f, 0f)
            nearRight && nearTop -> Offset(screenWidth, 0f)
            nearLeft && nearBottom -> Offset(0f, screenHeight)
            nearRight && nearBottom -> Offset(screenWidth, screenHeight)
            nearLeft -> Offset(0f, y)
            nearRight -> Offset(screenWidth, y)
            nearTop -> Offset(x, 0f)
            nearBottom -> Offset(x, screenHeight)
            else -> Offset(x, y)
        }
    }

    fun clampJoystickCenter(
        rawX: Float,
        rawY: Float,
        joystickRadiusPx: Float,
        travelWidth: Float,
        travelHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        density: Float
    ): Offset {
        val margin = 16f * density
        val insetX = maxOf(joystickRadiusPx, travelWidth / 2f) + margin
        val insetY = maxOf(joystickRadiusPx, travelHeight / 2f) + margin
        val x = if (insetX * 2f > screenWidth) {
            screenWidth / 2f
        } else {
            rawX.coerceIn(insetX, screenWidth - insetX)
        }
        val y = if (insetY * 2f > screenHeight) {
            screenHeight / 2f
        } else {
            rawY.coerceIn(insetY, screenHeight - insetY)
        }
        return Offset(x, y)
    }

    data class AreaPreviewLayout(
        val trigger: Offset,
        val joystickCenter: Offset,
        val joystickRadiusPx: Float,
        val travelWidth: Float,
        val travelHeight: Float,
        val travelRect: Rect,
        val travelRectOnScreen: Rect,
        val pointerPosition: Offset
    )

    fun computeAreaPreviewLayout(
        settings: AppSettings,
        density: Float,
        screenWidth: Float,
        screenHeight: Float,
        triggerRawX: Float,
        triggerRawY: Float
    ): AreaPreviewLayout {
        val joystickRadiusPx = settings.floatingPointerJoystickDiameterPx / 2f
        val (travelWidth, travelHeight) = effectivePointerTravel(settings, screenWidth, screenHeight)
        val joystickCenter = clampJoystickCenter(
            rawX = triggerRawX,
            rawY = triggerRawY,
            joystickRadiusPx = joystickRadiusPx,
            travelWidth = travelWidth,
            travelHeight = travelHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density
        )
        val travelLeft = joystickCenter.x - travelWidth / 2f
        val travelTop = joystickCenter.y - travelHeight / 2f
        val travelRect = Rect(
            left = travelLeft,
            top = travelTop,
            right = travelLeft + travelWidth,
            bottom = travelTop + travelHeight
        )
        val screenRect = Rect(0f, 0f, screenWidth, screenHeight)
        val edgeThresholdPx = 48f * density
        val pointerPosition = pointerStartForEdgeTrigger(
            rawX = triggerRawX,
            rawY = triggerRawY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeThresholdPx = edgeThresholdPx
        )
        return AreaPreviewLayout(
            trigger = Offset(triggerRawX, triggerRawY),
            joystickCenter = joystickCenter,
            joystickRadiusPx = joystickRadiusPx,
            travelWidth = travelWidth,
            travelHeight = travelHeight,
            travelRect = travelRect,
            travelRectOnScreen = travelRect.intersect(screenRect),
            pointerPosition = pointerPosition
        )
    }

    fun mapTravel(
        start: Float,
        normalized: Float,
        min: Float,
        max: Float,
        curvePower: Float
    ): Float {
        val curved = applyDeflectionCurve(normalized, curvePower)
        return when {
            normalized < 0f -> (start + curved * (start - min)).coerceIn(min, max)
            normalized > 0f -> (start + curved * (max - start)).coerceIn(min, max)
            else -> start.coerceIn(min, max)
        }
    }

    private fun applyDeflectionCurve(normalized: Float, curvePower: Float): Float {
        val sign = if (normalized < 0f) -1f else 1f
        val magnitude = kotlin.math.abs(normalized).coerceIn(0f, 1f)
        return sign * magnitude.pow(curvePower)
    }

    fun clamp(
        position: Offset,
        screenWidth: Float,
        screenHeight: Float
    ): Offset {
        return Offset(
            x = position.x.coerceIn(0f, screenWidth),
            y = position.y.coerceIn(0f, screenHeight)
        )
    }
}
