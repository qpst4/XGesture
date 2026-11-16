package com.slideindex.app.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.layout.QuickWheelOpenAnimation
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.overlay.layout.QuickWheelStyleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuickWheelCodec] 与按路径读写容器（[slotAt] / [updateSlot] / 增删换位）的单测。
 *
 * 编解码走 `MutablePreferences` 内存实例，不碰磁盘；整条链路纯 JVM，无需 Robolectric。
 */
class QuickWheelCodecTest {

    private val primaryCircleStyle = QuickWheelStyleSpec(
        showLabels = true,
        initialRadiusDp = 120f,
        containerSizeDp = 48f,
        columnCount = 5,
    )
    private val primaryRectStyle = QuickWheelStyleSpec(
        initialRadiusDp = 140f,
        containerSizeDp = 52f,
        containerGapDp = 16f,
        columnCount = 7,
    )
    private val secondaryCircleStyle = QuickWheelStyleSpec(
        initialRadiusDp = 90f,
        containerSizeDp = 44f,
        ringGapDp = 50f,
        columnCount = 4,
    )
    private val secondaryRectStyle = QuickWheelStyleSpec(
        initialRadiusDp = 96f,
        containerSizeDp = 40f,
        containerGapDp = 12f,
        columnCount = 6,
    )

    private fun roundTrip(wheels: List<QuickWheel>): List<QuickWheel> {
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(wheels, prefs)
        return QuickWheelCodec.decode(prefs)
    }

    /** 一级=矩形、二级=圆形，且两套形态参数刻意不同，用以校验"切换形态不丢参数"。 */
    private fun sampleWheel(): QuickWheel = QuickWheel(
        id = "wheel-1",
        name = "  主力轮盘  ", // 期望写出 / 读回时被 trim
        primaryShape = QuickWheelShape.RECT,
        secondaryShape = QuickWheelShape.CIRCLE,
        primaryStyle = primaryRectStyle,
        secondaryStyle = secondaryCircleStyle,
        primaryCircleStyle = primaryCircleStyle,
        primaryRectStyle = primaryRectStyle,
        secondaryCircleStyle = secondaryCircleStyle,
        secondaryRectStyle = secondaryRectStyle,
        sectorMask = 0b0101,
        centerSlot = QuickWheelSlot(
            iconSource = QuickWheelIconSource.APP_ICON,
            iconValue = "com.example.center",
            name = "中心",
            tapAction = GestureAction.OpenQuickSettings,
        ),
        slots = listOf(
            QuickWheelSlot(
                iconSource = QuickWheelIconSource.TEXT,
                iconValue = "回",
                name = "返回",
                tapAction = GestureAction.Back,
                longPressAction = GestureAction.Home,
                tapTrigger = QuickWheelTapTrigger.ON_TOUCH,
                longPressTrigger = QuickWheelLongPressTrigger.ON_TIMEOUT,
                subSlots = listOf(
                    QuickWheelSlot(tapAction = GestureAction.Recents),
                    QuickWheelSlot(placeholder = true),
                ),
            ),
            QuickWheelSlot(placeholder = true),
            QuickWheelSlot(),
        ),
    )

    // ── 编解码往返 ──────────────────────────────────────────

    @Test
    fun roundTrip_preservesEveryPersistedField() {
        val wheel = sampleWheel()
        val decoded = roundTrip(listOf(wheel)).single()

        // 整对象相等：元数据（形态 / 两套样式 / 扇区 / 名称）、中心容器、一级与二级容器。
        assertEquals(wheel.normalized(), decoded)
    }

    @Test
    fun roundTrip_preservesBothShapeStylesForEachLevel() {
        val decoded = roundTrip(listOf(sampleWheel())).single()

        // 当前形态用 rect → primaryStyle 取矩形那套；圆形那套作为"另一形态"原样保留。
        assertEquals(QuickWheelLayoutEngine.normalize(primaryRectStyle), decoded.primaryStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(primaryRectStyle), decoded.primaryRectStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(primaryCircleStyle), decoded.primaryCircleStyle)

        assertEquals(QuickWheelLayoutEngine.normalize(secondaryCircleStyle), decoded.secondaryStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(secondaryCircleStyle), decoded.secondaryCircleStyle)
        assertEquals(QuickWheelLayoutEngine.normalize(secondaryRectStyle), decoded.secondaryRectStyle)
    }

    @Test
    fun roundTrip_preservesRectColumnCount() {
        val decoded = roundTrip(listOf(sampleWheel())).single()
        assertEquals(primaryRectStyle.columnCount, decoded.primaryRectStyle.columnCount)
        assertEquals(secondaryRectStyle.columnCount, decoded.secondaryRectStyle.columnCount)
    }

    @Test
    fun roundTrip_preservesTransparentContainerSwitch() {
        // 开启「容器底色全透明」后必须原样读回：漏存的话重启就弹回"铺底板"，拟物图标又露白。
        val wheel = sampleWheel().copy(
            primaryCircleStyle = primaryCircleStyle.copy(transparentUserIcons = true),
            primaryRectStyle = primaryRectStyle.copy(transparentUserIcons = true),
        )
        val decoded = roundTrip(listOf(wheel)).single()
        assertTrue(decoded.primaryCircleStyle.transparentUserIcons)
        assertTrue(decoded.primaryRectStyle.transparentUserIcons)
        // 没动过的二级两套仍是默认值（不透明 = 有底板）。
        assertEquals(false, decoded.secondaryCircleStyle.transparentUserIcons)
        assertEquals(false, decoded.secondaryRectStyle.transparentUserIcons)
        // 只开总开关时，两个子开关必须保持 false —— 内置图标照旧铺底板（历史观感）。
        assertEquals(false, decoded.secondaryCircleStyle.transparentBuiltinIcons)
        assertEquals(false, decoded.secondaryCircleStyle.transparentTextIcons)
    }

    @Test
    fun roundTrip_preservesTransparentBuiltinAndTextSwitches() {
        // 总开关打开后，两个子开关各自独立：只开"文字图标也透明"不能把内置矢量一起透明。
        val wheel = sampleWheel().copy(
            primaryCircleStyle = primaryCircleStyle.copy(
                transparentUserIcons = true,
                transparentTextIcons = true,
            ),
        )
        val decoded = roundTrip(listOf(wheel)).single()
        assertTrue(decoded.primaryCircleStyle.transparentUserIcons)
        assertTrue(decoded.primaryCircleStyle.transparentTextIcons)
        assertEquals(false, decoded.primaryCircleStyle.transparentBuiltinIcons)
        // 没动过的矩形一套三个开关全是 false。
        assertEquals(false, decoded.primaryRectStyle.transparentUserIcons)
        assertEquals(false, decoded.primaryRectStyle.transparentBuiltinIcons)
        assertEquals(false, decoded.primaryRectStyle.transparentTextIcons)
    }

    @Test
    fun roundTrip_preservesFontIconSlot() {
        // 图标库（Lucide / Remix / Tabler）字体图标：枚举按 name 存，key 原样往返。
        val wheel = sampleWheel().copy(
            slots = listOf(
                QuickWheelSlot(
                    iconSource = QuickWheelIconSource.FONT_ICON,
                    iconValue = "l:cat",
                    name = "猫咪",
                    tapAction = GestureAction.Back,
                ),
            ),
        )
        val decoded = roundTrip(listOf(wheel)).single()
        val slot = decoded.slots.single()
        assertEquals(QuickWheelIconSource.FONT_ICON, slot.iconSource)
        assertEquals("l:cat", slot.iconValue)
    }

    @Test
    fun roundTrip_preservesLongPressMs() {
        val decoded = roundTrip(listOf(sampleWheel().copy(longPressMs = 900))).single()
        assertEquals(900, decoded.longPressMs)
    }

    @Test
    fun roundTrip_preservesBackdropBlurAndDim() {
        val decoded = roundTrip(
            listOf(sampleWheel().copy(backdropBlurDp = 24, backdropDimPercent = 35)),
        ).single()
        assertEquals(24, decoded.backdropBlurDp)
        assertEquals(35, decoded.backdropDimPercent)
    }

    @Test
    fun roundTrip_preservesBackdropTintColor() {
        // 「模糊 + 色调」：颜色与浓度各自独立存；默认色是黑。
        val decoded = roundTrip(
            listOf(sampleWheel().copy(backdropDimPercent = 20, backdropDimColorArgb = 0xFFFFFFFF.toInt())),
        ).single()
        assertEquals(0xFFFFFFFF.toInt(), decoded.backdropDimColorArgb)
        assertEquals(20, decoded.backdropDimPercent)
        // 没动过的轮盘仍是黑色。
        assertEquals(
            QuickWheelLayoutEngine.DEFAULT_BACKDROP_DIM_COLOR_ARGB,
            roundTrip(listOf(sampleWheel())).single().backdropDimColorArgb,
        )
    }

    @Test
    fun normalized_forcesOpaqueTint() {
        // 色调恒为不透明色：透明度只由浓度控制，避免两处叠加。
        val wheel = sampleWheel().copy(backdropDimColorArgb = 0x00FF0000).normalized()
        assertEquals(0xFFFF0000.toInt(), wheel.backdropDimColorArgb)
    }

    @Test
    fun decode_recordWithoutBackdropFallsBackToZero() {
        // 9 段老记录（有 longPressMs、没有背景 / 动画参数）。
        // 当前 15 段 = 0..5 元数据 + 6 sector + 7 longPress + 8/9 背景 + 10/11 动画
        //            + 12 背景色调 + 13 作用范围 + 14 名称
        // → 去掉 8..13 即得 9 段（名称仍留在最后）。
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(listOf(sampleWheel().copy(backdropBlurDp = 30)), prefs)
        val entry = prefs[SettingsPreferenceKeys.QUICK_WHEEL_ENTRIES]!!.first()
        val fieldSep = "\u001D"
        val parts = entry.split(fieldSep)
        val legacy = (parts.take(8) + parts.drop(16)).joinToString(fieldSep)
        val decoded = QuickWheelCodec.decodeEntry(legacy)!!
        assertEquals(0, decoded.backdropBlurDp)
        assertEquals(0, decoded.backdropDimPercent)
        // 名称仍然是最后一段，不能被错位读成背景参数。
        assertEquals("主力轮盘", decoded.name)
        // 没有色调段 → 默认黑色（= 历史「黑纱」）；没有范围段 → 模糊整屏 + 色调仅轮盘。
        assertEquals(QuickWheelLayoutEngine.DEFAULT_BACKDROP_DIM_COLOR_ARGB, decoded.backdropDimColorArgb)
        assertEquals(QuickWheelBackdropScope.FULL_SCREEN, decoded.backdropBlurScope)
        assertEquals(QuickWheelBackdropScope.WHEEL_ONLY, decoded.backdropDimScope)
        assertEquals(QuickWheelLayoutEngine.DEFAULT_BACKDROP_FEATHER_DP, decoded.backdropFeatherDp)
    }

    @Test
    fun roundTrip_preservesBackdropScopesAndFeather() {
        val decoded = roundTrip(
            listOf(
                sampleWheel().copy(
                    backdropBlurScope = QuickWheelBackdropScope.WHEEL_ONLY,
                    backdropDimScope = QuickWheelBackdropScope.FULL_SCREEN,
                    backdropFeatherDp = 12,
                ),
            ),
        ).single()
        assertEquals(QuickWheelBackdropScope.WHEEL_ONLY, decoded.backdropBlurScope)
        assertEquals(QuickWheelBackdropScope.FULL_SCREEN, decoded.backdropDimScope)
        assertEquals(12, decoded.backdropFeatherDp)
        // 没动过的轮盘：模糊整屏、色调仅轮盘、羽化默认 24。
        val fresh = roundTrip(listOf(sampleWheel())).single()
        assertEquals(QuickWheelBackdropScope.FULL_SCREEN, fresh.backdropBlurScope)
        assertEquals(QuickWheelBackdropScope.WHEEL_ONLY, fresh.backdropDimScope)
        assertEquals(QuickWheelLayoutEngine.DEFAULT_BACKDROP_FEATHER_DP, fresh.backdropFeatherDp)
    }

    @Test
    fun decode_legacySingleScopeAppliesToBoth() {
        // 15 段旧记录只有一份 scope：模糊 / 色调都读成它，升级后观感不变。
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(
            listOf(sampleWheel().copy(backdropBlurScope = QuickWheelBackdropScope.WHEEL_ONLY)),
            prefs,
        )
        val entry = prefs[SettingsPreferenceKeys.QUICK_WHEEL_ENTRIES]!!.first()
        val fieldSep = "\u001D"
        val parts = entry.split(fieldSep)
        // 去掉 dimScope / feather 两段，保留第 15 段的单一 scope。
        val legacy = (parts.take(15) + parts.drop(16)).joinToString(fieldSep)
        val decoded = QuickWheelCodec.decodeEntry(legacy)!!
        assertEquals(QuickWheelBackdropScope.WHEEL_ONLY, decoded.backdropBlurScope)
        assertEquals(QuickWheelBackdropScope.WHEEL_ONLY, decoded.backdropDimScope)
        assertEquals(QuickWheelLayoutEngine.DEFAULT_BACKDROP_FEATHER_DP, decoded.backdropFeatherDp)
    }

    @Test
    fun decode_legacyRecordWithoutLongPressMsFallsBackToDefault() {
        // 模拟"含 sectorMask、没有 longPressMs 也没有背景 / 动画参数"的旧记录：
        // 当前 17 段 → 去掉 7..15（longPress + 背景 + 动画 + 色调 + 两个范围 + 羽化）
        // 得到旧的 8 段（元数据 + sector + 名称）。
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(listOf(sampleWheel()), prefs)
        val entry = prefs[SettingsPreferenceKeys.QUICK_WHEEL_ENTRIES]!!.first()
        val fieldSep = "\u001D"
        val parts = entry.split(fieldSep)
        val legacy = (parts.take(7) + parts.drop(16)).joinToString(fieldSep)
        val decoded = QuickWheelCodec.decodeEntry(legacy)!!
        assertEquals(QuickWheelLayoutEngine.DEFAULT_LONG_PRESS_MS, decoded.longPressMs)
        assertEquals(QuickWheelLayoutEngine.DEFAULT_BACKDROP_BLUR_DP, decoded.backdropBlurDp)
        assertEquals(
            QuickWheelLayoutEngine.DEFAULT_BACKDROP_DIM_PERCENT,
            decoded.backdropDimPercent,
        )
        assertEquals("主力轮盘", decoded.name)
    }

    @Test
    fun roundTrip_trimsName() {
        val decoded = roundTrip(listOf(sampleWheel())).single()
        assertEquals("主力轮盘", decoded.name)
    }

    @Test
    fun roundTrip_preservesPlaceholderAtPrimaryAndSecondary() {
        val decoded = roundTrip(listOf(sampleWheel())).single()
        assertTrue(decoded.slots[1].placeholder)
        assertTrue(decoded.slots[0].subSlots[1].placeholder)
    }

    // ── 文字图标颜色（第 11 段，旧记录没有）────────────────

    @Test
    fun roundTrip_preservesTextIconColor() {
        val wheel = QuickWheel(
            id = "w",
            slots = listOf(
                QuickWheelSlot(
                    iconSource = QuickWheelIconSource.TEXT,
                    iconValue = "回",
                    name = "返回",
                    textColorArgb = 0xFF43A047.toInt(),
                ),
            ),
        )
        val decoded = roundTrip(listOf(wheel)).single().slots.single()
        assertEquals(0xFF43A047.toInt(), decoded.textColorArgb)
        assertEquals("回", decoded.iconValue)
        assertEquals("返回", decoded.name)
    }

    @Test
    fun encodeSlot_withColorWritesLaunchModesAndKeepsNameLast() {
        // 有颜色时必须补写两段打开方式（哪怕都是默认），否则 9 段 / 10 段的旧记录判定会错位。
        val slot = QuickWheelSlot(
            iconSource = QuickWheelIconSource.TEXT,
            iconValue = "回",
            name = "返回",
            textColorArgb = 0xFFE53935.toInt(),
        )
        val raw = QuickWheelCodec.encodeSlot("w", "0", slot)
        val header = raw.substringBefore('\u001E')
        assertEquals(11, header.split('\u001D').size)
        assertEquals("返回", header.substringAfterLast('\u001D'))

        val decoded = QuickWheelCodec.decodeSlot(raw)!!.third
        assertEquals("返回", decoded.name)
        assertEquals(0xFFE53935.toInt(), decoded.textColorArgb)
        assertEquals(QuickWheelLaunchMode.INHERIT, decoded.tapLaunchMode)
    }

    @Test
    fun decodeSlot_legacyNineFieldRecordStaysUncolored() {
        // 9 段旧记录（整容器一份打开方式 + 名称）读回：名称不能错位，颜色必须是 null（= 自动）。
        val legacy = listOf("w", "0", "0", "TEXT", "回", "ON_RELEASE", "ON_RELEASE", "ON_TOUCH", "返回")
            .joinToString("\u001D") +
            "\u001E" + QuickLauncherItemCodec.encodeActionPayload(GestureAction.None) +
            "\u001E" + QuickLauncherItemCodec.encodeActionPayload(GestureAction.None)
        val decoded = QuickWheelCodec.decodeSlot(legacy)!!.third
        assertEquals("返回", decoded.name)
        assertEquals("回", decoded.iconValue)
        assertNull(decoded.textColorArgb)
    }

    @Test
    fun decodeSlot_uncoloredRoundTripHasNullColor() {
        val slot = QuickWheelSlot(
            iconSource = QuickWheelIconSource.TEXT,
            iconValue = "回",
            name = "返回",
        )
        val decoded = QuickWheelCodec.decodeSlot(QuickWheelCodec.encodeSlot("w", "0", slot))!!.third
        assertNull(decoded.textColorArgb)
        assertEquals("返回", decoded.name)
    }

    @Test
    fun defaultStyles_haveNoOffsetOnEitherLevel() {
        // 出厂偏移全部 0：轮盘紧贴触点，与屏幕边缘的距离完全交给用户调。
        // 引擎也没有隐藏的安全边距（ADAPTIVE_MARGIN_DP = 0），默认值就是"所见即所得"。
        val wheel = QuickWheel(id = "w").normalized()
        assertEquals(0f, wheel.primaryStyle.offsetXDp, 0.01f)
        assertEquals(0f, wheel.primaryStyle.offsetYDp, 0.01f)
        assertEquals(0f, wheel.secondaryStyle.offsetXDp, 0.01f)
        assertEquals(0f, wheel.secondaryStyle.offsetYDp, 0.01f)
    }

    @Test
    fun roundTrip_preservesActionPayloadContainingSeparators() {
        // 载荷故意混入结构分隔符，验证它们不会破坏记录切分。
        // ⚠️ 不含 `\u001D`：它已被动作正文层用作「启动应用」的启动形态分隔符
        //（见 QuickLauncherItemCodec.LAUNCH_WINDOW_MODE_SEP），不属于可用作包名的字符集。
        val tricky = "com.foo:bar\u001C\u001E\u001Fbaz"
        val wheel = QuickWheel(
            id = "wheel-sep",
            slots = listOf(QuickWheelSlot(tapAction = GestureAction.LaunchApp(tricky))),
        )
        val decoded = roundTrip(listOf(wheel)).single()
        assertEquals(tricky, decoded.slots[0].tapAction.payload)
    }

    @Test
    fun writeToPreferences_clampsToMaxWheelsAndRewritesOrder() {
        val wheels = (0 until QuickWheel.MAX_WHEELS + 3).map { index ->
            QuickWheel(
                id = "wheel-$index",
                slots = listOf(QuickWheelSlot(tapAction = GestureAction.Back)),
            )
        }
        val decoded = roundTrip(wheels)
        assertEquals(QuickWheel.MAX_WHEELS, decoded.size)
        assertEquals((0 until QuickWheel.MAX_WHEELS).toList(), decoded.map { it.order })
    }

    @Test
    fun decode_emptyPreferencesYieldsNoWheels() {
        assertTrue(QuickWheelCodec.decode(mutablePreferencesOf()).isEmpty())
    }

    @Test
    fun newWheel_seedsNavigationSlotsAndName() {
        val wheel = QuickWheelCodec.newWheel(id = "w", ordinal = 2, order = 1)
        assertEquals("w", wheel.id)
        assertEquals(1, wheel.order)
        assertEquals(QuickWheel.defaultName(2), wheel.name)
        assertEquals(QuickWheel.defaultSlots(), wheel.slots)
        assertEquals(QuickWheel.DEFAULT_SLOT_COUNT, wheel.slots.size)
    }

    @Test
    fun openAnimation_roundTripsAndFallsBackForLegacyRecords() {
        val wheel = QuickWheel(
            id = "w",
            openAnimation = QuickWheelOpenAnimation.NONE,
            animationSpeedPercent = 175,
        )
        val decoded = roundTrip(listOf(wheel)).single()
        assertEquals(QuickWheelOpenAnimation.NONE, decoded.openAnimation)
        assertEquals(175, decoded.animationSpeedPercent)

        // 旧记录（没有这两段）→ 取默认值：从中心展开 + 100%。
        val legacyRaw = QuickWheelCodec.encodeEntry(wheel).let { raw ->
            val parts = raw.split('\u001D')
            (parts.take(10) + parts.last()).joinToString("\u001D")
        }
        val legacy = QuickWheelCodec.decodeEntry(legacyRaw)
        assertEquals(QuickWheelOpenAnimation.EXPAND_FROM_CENTER, legacy?.openAnimation)
        assertEquals(
            QuickWheelLayoutEngine.DEFAULT_ANIMATION_SPEED_PERCENT,
            legacy?.animationSpeedPercent,
        )
    }

    @Test
    fun newWheel_labelsDefaultSlotsSoTheyMatchManuallyAddedContainers() {
        // 默认三容器除"是轮盘自带的"外不应有任何特征差异：名称也要与
        // 「自己新建容器后设置同一动作」一致（编辑页会把名称填成动作文案）。
        val wheel = QuickWheelCodec.newWheel(id = "w", ordinal = 1, order = 0) { action ->
            "name:${action.type.name}"
        }
        assertEquals(
            listOf("name:BACK", "name:HOME", "name:RECENTS"),
            wheel.slots.map { it.name },
        )
    }

    // ── 路径读写 ────────────────────────────────────────────

    @Test
    fun slotAt_resolvesCenterPrimaryAndSecondary() {
        val wheel = sampleWheel()
        assertEquals(wheel.centerSlot, wheel.slotAt(QuickWheelCodec.PATH_CENTER))
        assertEquals(wheel.slots[0], wheel.slotAt(QuickWheelCodec.primaryPath(0)))
        assertEquals(wheel.slots[0].subSlots[1], wheel.slotAt(QuickWheelCodec.secondaryPath(0, 1)))
        assertNull(wheel.slotAt(QuickWheelCodec.primaryPath(99)))
    }

    @Test
    fun updateSlot_appendsPrimaryAtEndIndex() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots())
        val appended = wheel.updateSlot(QuickWheelCodec.primaryPath(wheel.slots.size)) {
            it.copy(tapAction = GestureAction.Back)
        }
        assertEquals(wheel.slots.size + 1, appended.slots.size)
        assertEquals(GestureAction.Back, appended.slots.last().tapAction)
    }

    @Test
    fun updateSlot_appendsSecondaryAtEndIndex() {
        val wheel = QuickWheel(
            id = "w",
            slots = listOf(QuickWheelSlot(tapAction = GestureAction.Back)),
        )
        val appended = wheel.updateSlot(QuickWheelCodec.secondaryPath(0, 0)) {
            it.copy(tapAction = GestureAction.Home)
        }
        assertEquals(1, appended.slots[0].subSlots.size)
        assertEquals(GestureAction.Home, appended.slots[0].subSlots[0].tapAction)
    }

    @Test
    fun updateSlot_overwritesExistingPrimaryInPlace() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots())
        val updated = wheel.updateSlot(QuickWheelCodec.primaryPath(1)) {
            it.copy(name = "改名")
        }
        assertEquals(wheel.slots.size, updated.slots.size)
        assertEquals("改名", updated.slots[1].name)
    }

    @Test
    fun updateSlot_centerPathPatchesCenterSlot() {
        val wheel = QuickWheel(id = "w")
        val updated = wheel.updateSlot(QuickWheelCodec.PATH_CENTER) { it.copy(name = "中心") }
        assertEquals("中心", updated.centerSlot.name)
    }

    @Test
    fun withGapInserted_appendsTransparentPlaceholderAndSurvivesRoundTrip() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots()).withGapInserted()
        assertTrue(wheel.slots.last().placeholder)

        val decoded = roundTrip(listOf(wheel)).single()
        assertTrue(decoded.slots.last().placeholder)
    }

    @Test
    fun withPrimaryRemovedAt_dropsOnlyTargetSlot() {
        val wheel = QuickWheel(id = "w", slots = QuickWheel.defaultSlots())
        val removed = wheel.withPrimaryRemovedAt(0)
        assertEquals(wheel.slots.size - 1, removed.slots.size)
        assertEquals(wheel.slots.drop(1), removed.slots)
    }

    @Test
    fun withPrimaryMoved_reordersSlots() {
        val wheel = QuickWheel(
            id = "w",
            slots = listOf(
                QuickWheelSlot(name = "a"),
                QuickWheelSlot(name = "b"),
                QuickWheelSlot(name = "c"),
            ),
        )
        val moved = wheel.withPrimaryMoved(from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), moved.slots.map { it.name })
    }

    @Test
    fun withSecondaryMoved_reordersSubSlots() {
        val wheel = QuickWheel(
            id = "w",
            slots = listOf(
                QuickWheelSlot(
                    subSlots = listOf(
                        QuickWheelSlot(name = "x"),
                        QuickWheelSlot(name = "y"),
                    ),
                ),
            ),
        )
        val moved = wheel.withSecondaryMoved(primaryIndex = 0, from = 0, to = 1)
        assertEquals(listOf("y", "x"), moved.slots[0].subSlots.map { it.name })
    }

    @Test
    fun withSectorToggled_masksOffSingleBit() {
        val wheel = QuickWheel(id = "w", sectorMask = QuickWheelLayoutEngine.SECTOR_ALL)
        val toggled = wheel.withSectorToggled(0)
        assertEquals(QuickWheelLayoutEngine.SECTOR_ALL and 0b1110, toggled.sectorMask)
    }

    @Test
    fun sectorSpanDeg_reflectsMask() {
        assertEquals(180, QuickWheel(id = "w", sectorMask = 0b0011).sectorSpanDeg)
    }

    @Test
    fun slotLaunchMode_roundTripsAndLegacyRecordsDefaultToInherit() {
        // 单击 / 长按各一份打开方式：写在名称之前（第 8、9 段），读回时三项都不许错位。
        val custom = QuickWheelSlot(
            name = "微信",
            tapAction = GestureAction.LaunchApp("com.tencent.mm"),
            tapLaunchMode = QuickWheelLaunchMode.FREE_WINDOW,
            longPressLaunchMode = QuickWheelLaunchMode.FULLSCREEN,
        )
        val raw = QuickWheelCodec.encodeSlot("w", QuickWheelCodec.primaryPath(0), custom)
        assertEquals(10, raw.substringBefore('\u001E').split('\u001D').size)
        val decoded = QuickWheelCodec.decodeSlot(raw)!!.third
        assertEquals("微信", decoded.name)
        assertEquals(QuickWheelLaunchMode.FREE_WINDOW, decoded.tapLaunchMode)
        assertEquals(QuickWheelLaunchMode.FULLSCREEN, decoded.longPressLaunchMode)

        // 两项都是"跟随"：不写新段（8 段）→ 旧版本读到的名字仍是干净的名字。
        val legacy = QuickWheelCodec.encodeSlot(
            "w",
            QuickWheelCodec.primaryPath(0),
            QuickWheelSlot(name = "返回", tapAction = GestureAction.Back),
        )
        assertEquals(8, legacy.substringBefore('\u001E').split('\u001D').size)
        val legacyDecoded = QuickWheelCodec.decodeSlot(legacy)!!.third
        assertEquals("返回", legacyDecoded.name)
        assertEquals(QuickWheelLaunchMode.INHERIT, legacyDecoded.tapLaunchMode)
        assertEquals(QuickWheelLaunchMode.INHERIT, legacyDecoded.longPressLaunchMode)

        // 早期"整容器一份打开方式"的 9 段记录：两项按同值读回（不丢用户已做的选择）。
        val singleMode = listOf(
            "w",
            "0:0",
            "0",
            "NONE",
            "",
            "ON_RELEASE",
            "ON_RELEASE",
            QuickWheelLaunchMode.FREE_WINDOW.name,
            "返回",
        ).joinToString("\u001D") + "\u001E\u001E"
        val migrated = QuickWheelCodec.decodeSlot(singleMode)!!.third
        assertEquals("返回", migrated.name)
        assertEquals(QuickWheelLaunchMode.FREE_WINDOW, migrated.tapLaunchMode)
        assertEquals(QuickWheelLaunchMode.FREE_WINDOW, migrated.longPressLaunchMode)

        // 整轮盘往返：每个容器各自保留自己的打开方式与名称。
        val wheel = QuickWheelCodec.newWheel(id = "w", ordinal = 1, order = 0).copy(
            slots = listOf(
                QuickWheelSlot(
                    name = "a",
                    tapAction = GestureAction.Back,
                    tapLaunchMode = QuickWheelLaunchMode.FULLSCREEN,
                ),
                QuickWheelSlot(name = "b", tapAction = GestureAction.Home),
                QuickWheelSlot(
                    name = "c",
                    tapAction = GestureAction.Recents,
                    longPressLaunchMode = QuickWheelLaunchMode.FREE_WINDOW,
                ),
            ),
        )
        val slots = roundTrip(listOf(wheel)).single().slots
        assertEquals(
            listOf(
                QuickWheelLaunchMode.FULLSCREEN to QuickWheelLaunchMode.INHERIT,
                QuickWheelLaunchMode.INHERIT to QuickWheelLaunchMode.INHERIT,
                QuickWheelLaunchMode.INHERIT to QuickWheelLaunchMode.FREE_WINDOW,
            ),
            slots.map { it.tapLaunchMode to it.longPressLaunchMode },
        )
        assertEquals(listOf("a", "b", "c"), slots.map { it.name })
    }

    @Test
    fun containerLaunchMode_overridesOnlyTheLaunchPolicy() {
        val base = AppSettings(
            freeWindow = FreeWindowSettings(
                freeWindowEnabled = true,
                freeWindowModeId = FreeWindowMode.STANDARD.id,
            ),
            launcher = LauncherSettings(appLaunchPolicyId = AppLaunchPolicy.ALWAYS_FREE_WINDOW.id),
        )
        // 跟随：原样返回（连副本都不做）→ 行为与历史版本逐字节一致。
        assertSame(base, base.withQuickWheelLaunchMode(QuickWheelLaunchMode.INHERIT))
        // 显式全屏：把该容器从"全局小窗"里摘出来，只改「应用启动方式」档位。
        val forcedFullscreen = base.withQuickWheelLaunchMode(QuickWheelLaunchMode.FULLSCREEN)
        assertEquals(AppLaunchPolicy.ALWAYS_FULLSCREEN.id, forcedFullscreen.launcher.appLaunchPolicyId)
        assertEquals(base.freeWindow, forcedFullscreen.freeWindow)
        // 显式小窗：档位为"始终小窗"。
        assertEquals(
            AppLaunchPolicy.ALWAYS_FREE_WINDOW.id,
            base.withQuickWheelLaunchMode(QuickWheelLaunchMode.FREE_WINDOW).launcher.appLaunchPolicyId,
        )
        // 总开关关闭（能力未开启）：容器选小窗也不生效。
        val gateOff = base.copy(freeWindow = base.freeWindow.copy(freeWindowEnabled = false))
        assertSame(gateOff, gateOff.withQuickWheelLaunchMode(QuickWheelLaunchMode.FREE_WINDOW))
        // 目标被硬排除（桌面 / 系统界面 / 自身）：同样回落为全屏。
        assertSame(
            base,
            base.withQuickWheelLaunchMode(
                QuickWheelLaunchMode.FREE_WINDOW,
                targetSupportsFreeWindow = false,
            ),
        )
        // 但"显式全屏"与总开关无关：总开关关闭时依然生效。
        assertEquals(
            AppLaunchPolicy.ALWAYS_FULLSCREEN.id,
            gateOff.withQuickWheelLaunchMode(QuickWheelLaunchMode.FULLSCREEN).launcher.appLaunchPolicyId,
        )
    }

    @Test
    fun actionWindowMode_isAbsorbedIntoContainerAndClearedFromAction() {
        // 上游「动作自带启动形态」↔ 容器「打开方式」是同一个概念：容器是唯一来源。
        assertEquals(
            QuickWheelLaunchMode.FREE_WINDOW,
            LaunchWindowMode.ALWAYS_FREE_WINDOW.toQuickWheelLaunchMode(),
        )
        assertEquals(
            QuickWheelLaunchMode.FULLSCREEN,
            LaunchWindowMode.ALWAYS_FULLSCREEN.toQuickWheelLaunchMode(),
        )
        assertEquals(
            QuickWheelLaunchMode.INHERIT,
            LaunchWindowMode.FOLLOW_GLOBAL.toQuickWheelLaunchMode(),
        )
        assertEquals(
            LaunchWindowMode.ALWAYS_FREE_WINDOW,
            QuickWheelLaunchMode.FREE_WINDOW.toLaunchWindowMode(),
        )
        assertEquals(
            LaunchWindowMode.FOLLOW_GLOBAL,
            QuickWheelLaunchMode.INHERIT.toLaunchWindowMode(),
        )

        // 清空动作自带形态：只在真的带着形态时才产生新对象。
        val appAction = GestureAction.LaunchApp(
            packageName = "com.tencent.mm",
            windowMode = LaunchWindowMode.ALWAYS_FREE_WINDOW,
        )
        assertEquals(
            LaunchWindowMode.FOLLOW_GLOBAL,
            (appAction.withoutLaunchWindowMode() as GestureAction.LaunchApp).windowMode,
        )
        val cleanApp = GestureAction.LaunchApp("com.tencent.mm")
        assertSame(cleanApp, cleanApp.withoutLaunchWindowMode())
        assertSame(GestureAction.Back, GestureAction.Back.withoutLaunchWindowMode())
    }

    @Test
    fun opensSomething_onlyForLaunchLikeActions() {
        // 只有"真的会打开某个东西"的动作才与容器「打开方式」有关（编辑页据此决定三档是否置灰）：
        // 打开应用 / 启动快捷方式（上游没弹窗但启动链路读 settings，容器三档照样有效）/ 打开链接。
        assertTrue(GestureAction.LaunchApp("com.tencent.mm").opensSomething())
        assertTrue(GestureAction.LaunchShortcut.dynamic("com.a", "id").opensSomething())
        assertTrue(GestureAction.OpenLink("https://example.com").opensSomething())
        // 软件自带动作没有"启动"这一步。
        assertTrue(!GestureAction.Back.opensSomething())
        assertTrue(!GestureAction.Home.opensSomething())
        assertTrue(!GestureAction.Recents.opensSomething())
        assertTrue(!GestureAction.None.opensSomething())
    }

    @Test
    fun decode_absorbsLegacyActionWindowModeIntoContainerLaunchMode() {
        // 早期版本把"打开应用"的形态存在动作上（上游那套）：读回时收编进容器字段，
        // 并把动作那一段清成"跟随"，避免两处各说各话。
        val oldStyle = QuickWheelSlot(
            name = "微信",
            tapAction = GestureAction.LaunchApp(
                packageName = "com.tencent.mm",
                windowMode = LaunchWindowMode.ALWAYS_FREE_WINDOW,
            ),
        )
        val raw = QuickWheelCodec.encodeSlot("w", QuickWheelCodec.primaryPath(0), oldStyle)
        val decoded = QuickWheelCodec.decodeSlot(raw)!!.third
        assertEquals("微信", decoded.name)
        assertEquals(QuickWheelLaunchMode.FREE_WINDOW, decoded.tapLaunchMode)
        assertEquals(
            LaunchWindowMode.FOLLOW_GLOBAL,
            (decoded.tapAction as GestureAction.LaunchApp).windowMode,
        )

        // 容器自己的选择优先：不会被动作自带的形态覆盖。
        val explicit = QuickWheelCodec.encodeSlot(
            "w",
            QuickWheelCodec.primaryPath(0),
            QuickWheelSlot(
                name = "微信",
                tapAction = GestureAction.LaunchApp(
                    packageName = "com.tencent.mm",
                    windowMode = LaunchWindowMode.ALWAYS_FREE_WINDOW,
                ),
                tapLaunchMode = QuickWheelLaunchMode.FULLSCREEN,
            ),
        )
        assertEquals(
            QuickWheelLaunchMode.FULLSCREEN,
            QuickWheelCodec.decodeSlot(explicit)!!.third.tapLaunchMode,
        )
    }

    @Test
    fun decode_dropsOutOfRangeSlotIndexesInsteadOfAllocatingThem() {
        // 回归：`0:2000000000` 曾在 decode 里按索引补位（二十亿个容器 → OOM），
        // `0:-1` 会让 primary[-1] 抛 IndexOutOfBounds。两者只可能来自被改坏的存档或
        // 导入的备份，但 decode 每次读快照都会跑，一旦落盘就是"打开设置即崩"，所以必须丢弃。
        val prefs = mutablePreferencesOf()
        QuickWheelCodec.writeToPreferences(
            listOf(QuickWheelCodec.newWheel(id = "w", ordinal = 1, order = 0)),
            prefs,
        )
        prefs[SettingsPreferenceKeys.QUICK_WHEEL_SLOTS] = setOf(
            QuickWheelCodec.encodeSlot(
                "w",
                QuickWheelCodec.primaryPath(1),
                QuickWheelSlot(name = "保留"),
            ),
            QuickWheelCodec.encodeSlot("w", "0:2000000000", QuickWheelSlot(name = "越界")),
            QuickWheelCodec.encodeSlot("w", "0:-1", QuickWheelSlot(name = "负数")),
            QuickWheelCodec.encodeSlot("w", "0:0>1:-1", QuickWheelSlot(name = "二级负数")),
        )

        val wheel = QuickWheelCodec.decode(prefs).single()

        // 只剩 index=1 那条真实记录（index=0 是按索引补出来的空位），越界记录全部丢弃。
        assertEquals(2, wheel.slots.size)
        assertTrue(wheel.slots[0].isEmpty)
        assertEquals("保留", wheel.slots[1].name)
        assertTrue(wheel.slots[0].subSlots.isEmpty())
    }
}
