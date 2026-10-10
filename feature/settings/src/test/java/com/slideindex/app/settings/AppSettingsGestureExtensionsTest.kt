package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AppSettingsGestureExtensionsTest {

    @Test
    fun slotAction_defaultSettings_returnsFactoryDefaultActions() {
        val settings = AppSettings()

        // Fresh install without custom rules should resolve factory defaults, not GestureAction.None
        assertEquals(
            GestureAction.Back,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
        assertEquals(
            GestureAction.OpenIndex,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_UP),
        )
    }

    @Test
    fun slotAction_explicitlyConfiguredNone_returnsNone() {
        val base = AppSettings()
        val settings = base.withSlotAction(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.None,
        )

        assertEquals(
            GestureAction.None,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
    }

    @Test
    fun slotAction_explicitlyConfiguredAction_returnsConfiguredAction() {
        val base = AppSettings()
        val settings = base.withSlotAction(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.Screenshot,
        )

        assertEquals(
            GestureAction.Screenshot,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
    }

    @Test
    fun slotAction_groupWithoutOwnRules_usesFactoryDefaultsOnly() {
        val base = AppSettings()
            .withSlotAction(
                side = PanelSide.LEFT,
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                action = GestureAction.Screenshot,
                handleId = TriggerHandle.DEFAULT_ID,
            )
            .withSlotAction(
                side = PanelSide.LEFT,
                trigger = GestureTriggerType.SHORT_SWIPE_IN_AND_BACK,
                action = GestureAction.Flashlight,
                handleId = TriggerHandle.DEFAULT_ID,
            )
        val otherGroup = TriggerHandle(id = "other", topFraction = 0.50f, heightFraction = 0.20f)
        val settings = base.copy(
            edgeTrigger = base.edgeTrigger.copy(leftTriggerHandles = base.leftTriggerHandles + otherGroup),
        )

        // 出厂表里有的槽位 → 出厂动作（返回），不是 default 组改过的截图
        assertEquals(
            GestureAction.Back,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, "other"),
        )
        // 出厂表里没有的槽位 → 无动作，不把 default 组的自定义借过来（"新组全是动作"的根因）
        assertEquals(
            GestureAction.None,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN_AND_BACK, "other"),
        )
        // default 组自己的配置不受影响
        assertEquals(
            GestureAction.Flashlight,
            settings.slotAction(
                PanelSide.LEFT,
                GestureTriggerType.SHORT_SWIPE_IN_AND_BACK,
                TriggerHandle.DEFAULT_ID,
            ),
        )
    }

    @Test
    fun slotAction_explicitNoneOnNonDefaultGroup_staysNone() {
        val otherGroup = TriggerHandle(id = "other", topFraction = 0.50f, heightFraction = 0.20f)
        val base = AppSettings().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(leftTriggerHandles = it.leftTriggerHandles + otherGroup))
        }

        val settings = base.withSlotAction(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.None,
            handleId = "other",
        )

        assertEquals(
            GestureAction.None,
            settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, "other"),
        )
    }

    @Test
    fun withAddedTriggerHandlePair_newPairOwnsFactorySlots_notTheOtherGroup() {
        val base = AppSettings().withSlotAction(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.Screenshot,
            handleId = TriggerHandle.DEFAULT_ID,
        )

        val added = base.withAddedTriggerHandlePair()
        val newId = added.leftTriggerHandles.last().id

        // 新组自己存了规则：与识别/读取都按自己的走，不再借用别组。
        assertTrue(
            added.gestureRules.any {
                it.handleId == newId && it.side == PanelSide.LEFT &&
                    it.trigger == GestureTriggerType.SHORT_SWIPE_IN
            },
        )
        assertEquals(
            GestureAction.Back,
            added.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, newId),
        )
    }

    @Test
    fun withAddedTriggerHandlePair_newPairDoesNotFollowOtherGroupChanges() {
        val added = AppSettings().withAddedTriggerHandlePair()
        val newId = added.leftTriggerHandles.last().id

        val firstGroupEdited = added.withSlotAction(
            side = PanelSide.LEFT,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            action = GestureAction.Screenshot,
            handleId = TriggerHandle.DEFAULT_ID,
        )

        // 组装完之后改其他组的动作，不会连带改到新组（早前靠回退时这里会跟着变）。
        assertEquals(
            GestureAction.Back,
            firstGroupEdited.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, newId),
        )
    }

    @Test
    fun withAddedBottomTriggerHandle_newHandleOwnsFactorySlots() {
        val added = AppSettings().withAddedBottomTriggerHandle()
        val newId = added.bottomTriggerHandles.last().id

        assertTrue(added.gestureRules.any { it.handleId == newId && it.side == PanelSide.BOTTOM })
        assertEquals(
            GestureAction.Home,
            added.slotAction(PanelSide.BOTTOM, GestureTriggerType.SHORT_SWIPE_IN, newId),
        )
    }

    @Test
    fun withAddedTriggerHandlePair_restoreSideWithoutAlign_getsFactorySlots() {
        // 删掉一侧（对齐关闭：不镜像同组手势）后再添加 → 补回的那侧也要有自己的出厂动作。
        val base = AppSettings()
            .withTriggerAlignOppositeGestures(TriggerHandle.DEFAULT_ID, alignOppositeGestures = false)
            .withSlotAction(
                side = PanelSide.RIGHT,
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                action = GestureAction.Screenshot,
                handleId = TriggerHandle.DEFAULT_ID,
            )
            .withRemovedTriggerHandle(PanelSide.LEFT, TriggerHandle.DEFAULT_ID)

        val restored = base.withAddedTriggerHandlePair()

        assertTrue(restored.gestureRules.any { it.handleId == TriggerHandle.DEFAULT_ID && it.side == PanelSide.LEFT })
        assertEquals(
            GestureAction.Back,
            restored.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, TriggerHandle.DEFAULT_ID),
        )
    }

    private fun landscapeSettings(
        left: List<TriggerHandle> = listOf(TriggerHandle.default()),
        rules: List<GestureRule> = emptyList(),
    ): AppSettings = AppSettings().copy(
        edgeTrigger = EdgeTriggerSettings(
            leftTriggerHandles = listOf(TriggerHandle.default(topFraction = 0.10f, heightFraction = 0.20f)),
            leftTriggerHandlesLandscape = left,
            landscapeTriggersInitialized = true,
            gestureRulesLandscape = rules,
        ),
    )

    @Test
    fun withPortraitCopiedToLandscape_sameHandleCount_syncsActions() {
        val portrait = AppSettings()
            .withSlotAction(
                side = PanelSide.LEFT,
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                action = GestureAction.Screenshot,
            )

        val merged = portrait.withPortraitCopiedToLandscape(landscapeSettings())

        assertEquals(
            GestureAction.Screenshot,
            merged.rules.first {
                it.side == PanelSide.LEFT &&
                    it.trigger == GestureTriggerType.SHORT_SWIPE_IN &&
                    it.handleId == TriggerHandle.DEFAULT_ID
            }.action,
        )
    }
    @Test
    fun withPortraitCopiedToLandscape_portraitHasMoreHandles_appendsThem() {
        val portrait = AppSettings()
            .withAddedBottomTriggerHandle()
            .withAddedBottomTriggerHandle()
        val extraId = portrait.bottomTriggerHandles.last().id
        val handleCount = portrait.bottomTriggerHandles.size

        val merged = portrait.withPortraitCopiedToLandscape(landscapeSettings())

        assertEquals(handleCount, merged.handles.getValue(PanelSide.BOTTOM).size)
        assertTrue(merged.handles.getValue(PanelSide.BOTTOM).any { it.id == extraId })
        // 新补的触钮也要带上竖屏的动作，而不是空的。
        assertTrue(
            merged.rules.any {
                it.side == PanelSide.BOTTOM &&
                    it.handleId == extraId &&
                    it.action.type != GestureActionType.NONE
            },
        )
    }

    @Test
    fun withPortraitCopiedToLandscape_landscapeOnlyHandle_isKept() {
        val landscapeOnly = TriggerHandle.default().copy(id = "landscape-only")
        val landscape = landscapeSettings(
            left = listOf(TriggerHandle.default(), landscapeOnly),
            rules = listOf(
                GestureRule(
                    id = GestureRule.slotId(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, landscapeOnly.id),
                    side = PanelSide.LEFT,
                    trigger = GestureTriggerType.SHORT_SWIPE_IN,
                    action = GestureAction.Home,
                    handleId = landscapeOnly.id,
                ),
            ),
        )

        val merged = AppSettings().withPortraitCopiedToLandscape(landscape)

        // 横屏独有的触钮没有被删掉，它自己的动作也保留。
        assertEquals(2, landscape.leftTriggerHandlesLandscape.size)
        assertEquals(
            GestureAction.Home,
            merged.rules.first {
                it.handleId == landscapeOnly.id && it.trigger == GestureTriggerType.SHORT_SWIPE_IN
            }.action,
        )
    }

    @Test
    fun withPortraitCopiedToLandscape_notInitialized_changesNothing() {
        val merged = AppSettings().withPortraitCopiedToLandscape(AppSettings())

        assertTrue(merged.handles.isEmpty())
        assertTrue(merged.rules.isEmpty())
    }
}
