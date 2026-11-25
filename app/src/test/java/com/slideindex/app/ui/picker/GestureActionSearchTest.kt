package com.slideindex.app.ui.picker

import android.content.Context
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.ui.ActionCenterCatalog
import com.slideindex.app.ui.gesturepicker.filterGestureActions
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.gesturepicker.gestureActionMatchesSearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 动作搜索的命中口径：只认「显示名称（含拼音）」和人工维护的同义词，简介不参与。
 *
 * 回归背景：搜「悬浮」时，简介里带「悬浮窗 / 悬浮触控板」的 触控板 / 钉到屏幕 /
 * 前台活动探测 / 冰箱 也会出现在结果里，而结果格只显示名称、看不出为什么命中；
 * 另外「钉到屏幕」还有别名「悬浮便签」，只删简介匹配它仍会漏进来，所以一并删掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "zh-rCN")
class GestureActionSearchTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val pickerCatalog: List<GestureAction> = GestureActionCatalog.build(
        scope = GestureActionCatalogScope.GesturePicker,
        catalogPolicy = ActionPickerCatalogPolicy.EdgeGesture,
    )

    @Test
    fun search_float_returns_only_actions_whose_label_contains_keyword() {
        val results = filterGestureActions(context, pickerCatalog, "悬浮")

        assertEquals(
            "搜「悬浮」只应有名称含该词的 5 个动作",
            setOf(
                GestureActionType.FLOATING_POINTER,
                GestureActionType.TOGGLE_FLOAT_BALL,
                GestureActionType.MOVE_FLOAT_BALL,
                GestureActionType.SNOOZE_OVERLAYS,
                GestureActionType.QUICK_TOOLS_OVERLAY,
            ),
            results.map { it.type }.toSet(),
        )
        results.forEach { action ->
            val label = gestureActionLabelText(context, action)
            assertTrue(
                "结果「$label」的名称里没有搜索词，用户无法解释它为什么被搜出来",
                label.contains("悬浮"),
            )
        }
    }

    @Test
    fun description_only_matches_are_gone() {
        val hitTypes = filterGestureActions(context, pickerCatalog, "悬浮").map { it.type }.toSet()

        // 这 4 个动作的名称里都没有「悬浮」，旧实现是靠简介里的「悬浮窗 / 悬浮触控板」命中的。
        listOf(
            GestureActionType.SCREEN_CONTROL,
            GestureActionType.PIN_TO_SCREEN,
            GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR,
            GestureActionType.FREEZER_PANEL,
        ).forEach { type ->
            assertFalse("「$type」只靠简介命中，不应出现在搜「悬浮」的结果里", type in hitTypes)
        }
    }

    @Test
    fun aliases_still_work_but_misleading_one_is_removed() {
        fun hits(query: String): Set<GestureActionType> =
            filterGestureActions(context, pickerCatalog, query).map { it.type }.toSet()

        // 「悬浮便签」已从「钉到屏幕」的别名里删掉：它会让名称不含「悬浮」的动作混进结果。
        assertFalse(GestureActionType.PIN_TO_SCREEN in hits("悬浮便签"))
        // 别名机制本身保留：「屏幕便签」仍能搜到「钉到屏幕」。
        assertTrue(GestureActionType.PIN_TO_SCREEN in hits("屏幕便签"))
        // 开关类动作主要靠英文别名检索，不能被这次改动误伤。
        assertTrue(GestureActionType.TOGGLE_WIFI in hits("wifi"))
        assertTrue(GestureActionType.FORCE_STOP_CURRENT_APP in hits("kill app"))
    }

    @Test
    fun action_center_uses_the_same_rule_as_picker() {
        val hitTypes = ActionCenterCatalog.entries
            .filter { gestureActionMatchesSearchQuery(context, it.sample, "悬浮") }
            .map { it.sample.type }
            .toSet()

        assertEquals(
            "扩展 → 内置动作页必须与选择器同一口径（简介参与就会多出 4 条）",
            setOf(
                GestureActionType.FLOATING_POINTER,
                GestureActionType.TOGGLE_FLOAT_BALL,
                GestureActionType.MOVE_FLOAT_BALL,
                GestureActionType.SNOOZE_OVERLAYS,
                GestureActionType.QUICK_TOOLS_OVERLAY,
            ),
            hitTypes,
        )
    }
}
