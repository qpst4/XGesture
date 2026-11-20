package com.slideindex.app.ui.picker

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.SlotPickerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 圆环槽位的「无」：快捷启动器目录默认不含 None（多选「添加条目」页不需要），
 * 槽位单选页由 [withNonePinnedAtTop] 在过滤前补进去。
 *
 * 回归背景：v1.70.0 槽位编辑页改版（57500a68）后漏了这一步，动作 tab 里没有「无」，
 * 圆环槽位因此完全没有解绑入口；v1.66.0 及以前是有的（ebdfff95）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GestureActionCatalogNonePinnedTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun quickLauncherCatalog_hasNoNoneByDefault() {
        assertFalse(
            "多选「添加条目」用的快捷启动器目录不应包含「无」",
            quickLauncherCatalog().any { it.type == GestureActionType.NONE },
        )
    }

    @Test
    fun withNonePinnedAtTop_addsSingleNoneAtHead_andIsIdempotent() {
        val pinned = quickLauncherCatalog().withNonePinnedAtTop()

        assertEquals("「无」必须排在列表头", GestureActionType.NONE, pinned.first().type)
        assertEquals("「无」只能出现一次", 1, pinned.count { it.type == GestureActionType.NONE })
        assertEquals(
            "重复调用不能叠加「无」",
            1,
            pinned.withNonePinnedAtTop().count { it.type == GestureActionType.NONE },
        )
    }

    @Test
    fun filter_keepsNoneByLabel_butNotForOtherQueries() {
        // 槽位页的列表 = 目录 withNonePinnedAtTop()，再交给 filter（顺序不能反：
        // filter 的 pinNoneAtTop 只重排、不插入，单靠它「无」不会出现）。
        val actions = listOf(GestureAction.None, GestureAction.Home).withNonePinnedAtTop()
        // 文案跟系统语言走：默认 values 是 "None"，values-zh 才是「无」。测试不写死中文。
        val noneLabel = context.getString(R.string.gesture_action_none)

        assertEquals(
            "空查询时「无」仍在列表头",
            GestureActionType.NONE,
            GestureActionCatalog.filter(context, actions, "").first().type,
        )
        assertTrue(
            "搜索「无」的文案应保留「无」",
            GestureActionCatalog.filter(context, actions, noneLabel).any { it.type == GestureActionType.NONE },
        )
        assertFalse(
            "搜索具体动作时不应出现「无」",
            GestureActionCatalog.filter(context, actions, "home").any { it.type == GestureActionType.NONE },
        )
    }

    private fun quickLauncherCatalog(): List<GestureAction> = GestureActionCatalog.build(
        scope = GestureActionCatalogScope.QuickLauncher,
        catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.QuickLauncher),
    )
}
