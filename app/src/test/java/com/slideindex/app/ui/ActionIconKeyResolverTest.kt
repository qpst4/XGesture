package com.slideindex.app.ui

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 动作图标覆盖的优先级：面板图标 → 动作类型图标 → 应用图标。
 *
 * 这个顺序决定「圆环/蜂窝/角落轮盘/启动面板条目」看到的是同一张图：
 * 曾经 `actionIcon ?: appIcon` 在三个入口各写一份，加一维就会漏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ActionIconKeyResolverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(GlobalIconStore.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun panelIconWinsOverActionTypeIcon() {
        GlobalIconStore.setActionIcon(context, GestureActionType.QUICK_LAUNCHER.name, "t:type")
        GlobalIconStore.setPanelIcon(context, "panel-1", "t:panel")

        assertEquals(
            "t:panel",
            ActionIconKeyResolver.resolve(context, GestureAction.QuickLauncher("panel-1")),
        )
    }

    @Test
    fun fallsBackToActionTypeIconWhenPanelHasNoIcon() {
        GlobalIconStore.setActionIcon(context, GestureActionType.QUICK_LAUNCHER.name, "t:type")
        GlobalIconStore.setPanelIcon(context, "panel-2", "t:other-panel")

        assertEquals(
            "t:type",
            ActionIconKeyResolver.resolve(context, GestureAction.QuickLauncher("panel-1")),
        )
    }

    @Test
    fun blankPanelIdIgnoresPanelIcons() {
        GlobalIconStore.setPanelIcon(context, "", "t:panel")

        assertNull(ActionIconKeyResolver.resolve(context, GestureAction.QuickLauncher("")))
    }

    @Test
    fun fallsBackToAppIconForLaunchApp() {
        GlobalIconStore.setAppIcon(context, "com.example.app", "t:app")

        assertEquals(
            "t:app",
            ActionIconKeyResolver.resolve(context, GestureAction.LaunchApp("com.example.app")),
        )
    }

    @Test
    fun returnsNullWhenNothingOverridden() {
        assertNull(ActionIconKeyResolver.resolve(context, GestureAction.QuickLauncher("panel-1")))
        assertNull(ActionIconKeyResolver.resolve(context, GestureAction.Back))
    }
}
