package com.slideindex.app.shake

object ShakeSensitivityScale {
    const val UI_MIN = 1f
    const val UI_MAX = 20f
    const val UI_STEPS = 18

    /** 默认档：新阈值区间下约 4.7 rad/s，正常晃动即可触发（旧区间里同档位要 7.7）。 */
    const val DEFAULT_UI = 14f

    const val LEGACY_UI_MIN = 1f
    const val LEGACY_UI_MAX = 10f
    const val LEGACY_DEFAULT_UI = 3f

    /**
     * 陀螺仪阈值区间（rad/s）。
     *
     * 旧版是 2..20，但 20 rad/s 远超手持晃动的物理范围（轻晃 1–3、正常 3–6、猛甩 6–12），
     * 结果滑条 1–13 档全部落在"晃不到"的区间，只有 15–20 有真实手感。
     *
     * 现在改用旧滑条 5..20 档实际对应的阈值区间（16.2..2.0）：最难端留给"嫌灵敏"的用户
     * （相当于旧 5 档，基本要刻意甩），其余档位再线性铺开；配合
     * [com.slideindex.app.shake.ShakeGestureClassifier] 里翻转 margin 从 1.5 降到 0.5。
     */
    private const val THRESHOLD_MIN = 2f
    private const val THRESHOLD_MAX = 16.2f

    /** MIUIX Custom Key Points：range 均分 4 段（5 个刻度，含两端）。 */
    val UI_KEY_POINTS: List<Float> = run {
        val span = UI_MAX - UI_MIN
        List(5) { index -> UI_MIN + span * index / 4f }
    }

    fun clampUi(value: Float): Float = value.coerceIn(UI_MIN, UI_MAX)

    fun effectiveThreshold(uiValue: Float): Float {
        val ui = clampUi(uiValue)
        return THRESHOLD_MAX - (ui - UI_MIN) * (THRESHOLD_MAX - THRESHOLD_MIN) / (UI_MAX - UI_MIN)
    }

    /** Preserves gyro threshold when upgrading stored UI values from the 1–10 scale. */
    fun migrateUiFromV2(legacyUi: Float): Float {
        val clamped = legacyUi.coerceIn(LEGACY_UI_MIN, LEGACY_UI_MAX)
        return clampUi(1f + (9f + clamped) * (UI_MAX - UI_MIN) / 18f)
    }
}
