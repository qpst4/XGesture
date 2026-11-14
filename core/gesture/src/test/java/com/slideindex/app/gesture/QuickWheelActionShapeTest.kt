package com.slideindex.app.gesture

import com.slideindex.app.launcher.QuickLauncherItemCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GestureAction.QuickWheel] 新增「呼出形态」后的载荷编解码单测。
 *
 * 关键约束：形态为 [QuickWheelLaunchShape.DEFAULT] 时，载荷必须与旧版**逐字节一致**（仅 wheelId），
 * 以免影响已保存的记录。
 */
class QuickWheelActionShapeTest {

    private fun roundTrip(action: GestureAction): GestureAction? =
        QuickLauncherItemCodec.parseActionPayload(QuickLauncherItemCodec.encodeActionPayload(action))

    @Test
    fun defaultShape_keepsLegacyPayload() {
        val action = GestureAction.QuickWheel(wheelId = "wheel-uuid")
        assertEquals("wheel-uuid", action.payload)
    }

    @Test
    fun explicitShape_encodesWheelIdAndShape() {
        val action = GestureAction.QuickWheel(
            wheelId = "wheel-uuid",
            shape = QuickWheelLaunchShape.RECT,
        )
        assertEquals("wheel-uuid\u0001RECT", action.payload)
        assertEquals(action, GestureAction.from(GestureActionType.QUICK_WHEEL, action.payload))
    }

    @Test
    fun circleShape_roundTripsThroughLauncherCodec() {
        val action = GestureAction.QuickWheel(
            wheelId = "wheel-uuid",
            shape = QuickWheelLaunchShape.CIRCLE,
        )
        assertEquals(action, roundTrip(action))
    }

    @Test
    fun blankWheelIdWithShape_roundTrips() {
        val action = GestureAction.QuickWheel(shape = QuickWheelLaunchShape.RECT)
        val decoded = GestureAction.from(GestureActionType.QUICK_WHEEL, action.payload)
        assertEquals("", (decoded as GestureAction.QuickWheel).wheelId)
        assertEquals(QuickWheelLaunchShape.RECT, decoded.shape)
    }

    @Test
    fun legacyPlainWheelIdPayload_decodesAsDefaultShape() {
        // 旧记录：payload 就是一个 UUID，没有形态分隔符。
        val decoded = GestureAction.from(GestureActionType.QUICK_WHEEL, "legacy-uuid")
        assertEquals(GestureAction.QuickWheel("legacy-uuid"), decoded)
        assertEquals(QuickWheelLaunchShape.DEFAULT, (decoded as GestureAction.QuickWheel).shape)
    }

    @Test
    fun unknownShapeToken_fallsBackToDefault() {
        val decoded = GestureAction.from(GestureActionType.QUICK_WHEEL, "wheel-uuid\u0001NOPE")
        assertEquals(QuickWheelLaunchShape.DEFAULT, (decoded as GestureAction.QuickWheel).shape)
        assertEquals("wheel-uuid", decoded.wheelId)
    }

    @Test
    fun manualSectorMask_encodesThirdSegmentAndRoundTrips() {
        val action = GestureAction.QuickWheel(
            wheelId = "wheel-uuid",
            shape = QuickWheelLaunchShape.CIRCLE_CIRCLE,
            manualSectorMask = 0b0110,
        )
        assertEquals("wheel-uuid\u0001CIRCLE_CIRCLE\u00016", action.payload)
        assertEquals(action, roundTrip(action))
    }

    @Test
    fun autoSector_omitsThirdSegment() {
        // 未手动指定扇区（自动）→ 第三段不写入，载荷与旧版一致。
        val action = GestureAction.QuickWheel(
            wheelId = "wheel-uuid",
            shape = QuickWheelLaunchShape.CIRCLE_CIRCLE,
        )
        assertEquals(null, action.manualSectorMask)
        assertEquals("wheel-uuid\u0001CIRCLE_CIRCLE", action.payload)
        assertEquals(action, roundTrip(action))
    }

    @Test
    fun invalidOrEmptySectorSegment_decodesAsAuto() {
        // 0（一个都没选）/ 越界 / 非数字 / 空段 → 一律按"自动"，且不影响 wheelId 与形态解析。
        listOf("0", "16", "abc", "").forEach { token ->
            val decoded = GestureAction.from(
                GestureActionType.QUICK_WHEEL,
                "wheel-uuid\u0001CIRCLE\u0001$token",
            ) as GestureAction.QuickWheel
            assertEquals("token=$token", null, decoded.manualSectorMask)
            assertEquals(QuickWheelLaunchShape.CIRCLE, decoded.shape)
            assertEquals("wheel-uuid", decoded.wheelId)
        }
    }

    @Test
    fun followFingerAnchor_encodesFourthSegmentAndRoundTrips() {
        val action = GestureAction.QuickWheel(
            wheelId = "wheel-uuid",
            shape = QuickWheelLaunchShape.CIRCLE_CIRCLE,
            manualSectorMask = 0b0110,
            anchorMode = QuickWheelAnchorMode.FOLLOW_FINGER,
        )
        assertEquals("wheel-uuid\u0001CIRCLE_CIRCLE\u00016\u0001FOLLOW_FINGER", action.payload)
        assertEquals(action, roundTrip(action))
    }

    @Test
    fun followFingerAnchorWithAutoSector_usesPlaceholderZero() {
        // 锚点非默认时必须写第 4 段 → 扇区段用 0 占位（0 解析回"自动"），否则段位有歧义。
        val action = GestureAction.QuickWheel(
            wheelId = "wheel-uuid",
            shape = QuickWheelLaunchShape.CIRCLE,
            anchorMode = QuickWheelAnchorMode.FOLLOW_FINGER,
        )
        assertEquals("wheel-uuid\u0001CIRCLE\u00010\u0001FOLLOW_FINGER", action.payload)
        val decoded = roundTrip(action) as GestureAction.QuickWheel
        assertEquals(null, decoded.manualSectorMask)
        assertEquals(QuickWheelAnchorMode.FOLLOW_FINGER, decoded.anchorMode)
    }

    @Test
    fun touchDownAnchor_omitsFourthSegment_andUnknownTokenFallsBack() {
        // 按下点（默认）不写第 4 段，载荷与旧版"未设置锚点"逐字节一致。
        val action = GestureAction.QuickWheel(
            wheelId = "wheel-uuid",
            shape = QuickWheelLaunchShape.CIRCLE,
            manualSectorMask = 0b0110,
        )
        assertEquals("wheel-uuid\u0001CIRCLE\u00016", action.payload)
        // 锚点段缺失 / 未知 → 回退按下点。
        listOf(
            "wheel-uuid\u0001CIRCLE\u00010",
            "wheel-uuid\u0001CIRCLE\u00010\u0001NOPE",
        ).forEach { raw ->
            val decoded = GestureAction.from(GestureActionType.QUICK_WHEEL, raw) as GestureAction.QuickWheel
            assertEquals("raw=$raw", QuickWheelAnchorMode.TOUCH_DOWN, decoded.anchorMode)
        }
    }

    @Test
    fun legacyEdgeToken_mapsToTouchDown() {
        // 旧记录「贴边」= 按下点（边缘触钮本身贴边）→ 读回后不再有 EDGE，写回也换成 TOUCH_DOWN。
        val decoded = GestureAction.from(
            GestureActionType.QUICK_WHEEL,
            "wheel-uuid\u0001CIRCLE\u00010\u0001EDGE",
        ) as GestureAction.QuickWheel
        assertEquals(QuickWheelAnchorMode.TOUCH_DOWN, decoded.anchorMode)
        // 读回即默认值 → 编码时第 4 段被省略。
        assertEquals("wheel-uuid\u0001CIRCLE", decoded.payload)
    }

    // ── 一级 + 二级四组合 ───────────────────────────────────

    @Test
    fun shapeCombos_encodeByEnumNameAndRoundTrip() {
        val combos = listOf(
            QuickWheelLaunchShape.CIRCLE_CIRCLE,
            QuickWheelLaunchShape.CIRCLE_RECT,
            QuickWheelLaunchShape.RECT_CIRCLE,
            QuickWheelLaunchShape.RECT_RECT,
        )
        combos.forEach { combo ->
            val action = GestureAction.QuickWheel(wheelId = "wheel-uuid", shape = combo)
            // 载荷格式不变（仍是 wheelId + SOH + 枚举名），因此不需要数据迁移。
            assertEquals("wheel-uuid\u0001${combo.name}", action.payload)
            assertEquals(action, roundTrip(action))
        }
    }

    @Test
    fun comboShapes_splitIntoPerLevelOverrides() {
        fun levels(shape: QuickWheelLaunchShape) = shape.primaryLevel to shape.secondaryLevel

        assertEquals(
            QuickWheelLaunchLevelShape.CIRCLE to QuickWheelLaunchLevelShape.CIRCLE,
            levels(QuickWheelLaunchShape.CIRCLE_CIRCLE),
        )
        assertEquals(
            QuickWheelLaunchLevelShape.CIRCLE to QuickWheelLaunchLevelShape.RECT,
            levels(QuickWheelLaunchShape.CIRCLE_RECT),
        )
        assertEquals(
            QuickWheelLaunchLevelShape.RECT to QuickWheelLaunchLevelShape.CIRCLE,
            levels(QuickWheelLaunchShape.RECT_CIRCLE),
        )
        assertEquals(
            QuickWheelLaunchLevelShape.RECT to QuickWheelLaunchLevelShape.RECT,
            levels(QuickWheelLaunchShape.RECT_RECT),
        )
    }

    @Test
    fun legacyShapes_onlyOverridePrimaryLevel() {
        // 旧值语义必须保持：一级被覆盖，二级继续跟随轮盘设置。
        assertEquals(
            QuickWheelLaunchLevelShape.CIRCLE to QuickWheelLaunchLevelShape.FOLLOW,
            QuickWheelLaunchShape.CIRCLE.primaryLevel to
                QuickWheelLaunchShape.CIRCLE.secondaryLevel,
        )
        assertEquals(
            QuickWheelLaunchLevelShape.RECT to QuickWheelLaunchLevelShape.FOLLOW,
            QuickWheelLaunchShape.RECT.primaryLevel to QuickWheelLaunchShape.RECT.secondaryLevel,
        )
        // 默认值两级都跟随。
        assertEquals(
            QuickWheelLaunchLevelShape.FOLLOW to QuickWheelLaunchLevelShape.FOLLOW,
            QuickWheelLaunchShape.DEFAULT.primaryLevel to
                QuickWheelLaunchShape.DEFAULT.secondaryLevel,
        )
    }

    @Test
    fun legacyShapeTokens_decodeToTheirLegacySemantics() {
        // 老记录里的 CIRCLE / RECT 仍按"只覆盖一级"解释，行为不变。
        val circle = GestureAction.from(GestureActionType.QUICK_WHEEL, "wheel-uuid\u0001CIRCLE")
        assertEquals(QuickWheelLaunchShape.CIRCLE, (circle as GestureAction.QuickWheel).shape)
        assertEquals(
            QuickWheelLaunchLevelShape.FOLLOW,
            circle.shape.secondaryLevel,
        )
    }
}
