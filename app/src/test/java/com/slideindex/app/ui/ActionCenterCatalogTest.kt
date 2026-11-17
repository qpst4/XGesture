package com.slideindex.app.ui

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动作中心必须按 [GestureActionType] 全量生成：只排除无操作、需要目标选择器的启动动作和提醒旧变体，
 * 并且每个未排除的类型恰好出现一次。
 */
class ActionCenterCatalogTest {

    private val excluded = buildSet {
        add(GestureActionType.NONE)
        add(GestureActionType.LAUNCH_APP)
        add(GestureActionType.LAUNCH_SHORTCUT)
        addAll(GestureAction.legacyRemindTypes)
    }

    @Test
    fun entriesCoverEveryNonExcludedTypeExactlyOnce() {
        val entries = ActionCenterCatalog.entries
        val expectedTypes = GestureActionType.entries
            .filterNot { it in excluded }
            .toSet()

        assertEquals(entries.size, entries.map { it.type }.distinct().size)
        assertEquals(expectedTypes, entries.map { it.type }.toSet())
        assertTrue(entries.none { it.type in excluded })
        assertEquals(1, entries.count { it.type == GestureActionType.REMIND })
    }

    @Test
    fun specialEntriesAreMarkedForTheirEntry() {
        val entries = ActionCenterCatalog.entries
        assertEquals(6, entries.count { it.special == ActionSpecialMark.POINTER })
        assertEquals(2, entries.count { it.special == ActionSpecialMark.CORNER })
        assertEquals(1, entries.count { it.special == ActionSpecialMark.SINGLE_TAP })
        assertEquals(1, entries.count { it.special == ActionSpecialMark.SHAKE })

        assertTrue(
            entries.filter { it.special == ActionSpecialMark.CORNER }
                .all { it.section == ActionCenterSection.SPECIAL },
        )
        assertTrue(
            entries.filter { it.special == ActionSpecialMark.SHAKE }
                .all { it.section == ActionCenterSection.SPECIAL },
        )
    }

    @Test
    fun parameterAndDisabledPoliciesMatchTheDetailScreen() {
        val entries = ActionCenterCatalog.entries
        assertEquals(
            ActionTestKind.OPEN_LINK,
            entries.first { it.type == GestureActionType.OPEN_LINK }.testKind,
        )
        assertEquals(
            ActionTestKind.SHELL,
            entries.first { it.type == GestureActionType.EXECUTE_SHELL_COMMAND }.testKind,
        )
        assertEquals(
            ActionTestKind.KEY,
            entries.first { it.type == GestureActionType.SIMULATE_KEY_EVENT }.testKind,
        )
        assertEquals(
            ActionTestKind.REMIND,
            entries.first { it.type == GestureActionType.REMIND }.testKind,
        )
        assertEquals(
            ActionTestKind.DISABLED,
            entries.first { it.type == GestureActionType.RUN_MACRO }.testKind,
        )
        assertTrue(entries.first { it.type == GestureActionType.OPEN_LINK }.needsParams)
        assertTrue(entries.first { it.type == GestureActionType.QUICK_LAUNCHER }.needsParams)
        assertTrue(!entries.first { it.type == GestureActionType.BACK }.needsParams)
    }
}
