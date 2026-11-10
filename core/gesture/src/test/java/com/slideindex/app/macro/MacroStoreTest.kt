package com.slideindex.app.macro

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 宏持久化：增删改查与 id 语义。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MacroStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(MacroStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun macro(id: String, name: String = "宏") = Macro(
        id = id,
        name = name,
        targetPackage = "com.example.app",
        createdAtMs = 1L,
        updatedAtMs = 1L,
        steps = listOf(MacroStep.Wait(300L), MacroStep.Tap(0.4f, 0.6f, 60L)),
    )

    @Test
    fun startsEmpty() {
        assertTrue(MacroStore.all(context).isEmpty())
        assertNull(MacroStore.byId(context, "nope"))
    }

    @Test
    fun upsert_appendsThenOverwritesInPlace() {
        MacroStore.upsert(context, macro("a", "第一个"))
        MacroStore.upsert(context, macro("b", "第二个"))
        assertEquals(listOf("a", "b"), MacroStore.all(context).map { it.id })

        MacroStore.upsert(context, macro("a", "第一个（改过）"))
        val all = MacroStore.all(context)
        assertEquals(listOf("a", "b"), all.map { it.id })
        assertEquals("第一个（改过）", all[0].name)
        assertEquals(2, all[0].steps.size)
    }

    @Test
    fun delete_removesOnlyThatMacro() {
        MacroStore.upsert(context, macro("a"))
        MacroStore.upsert(context, macro("b"))

        MacroStore.delete(context, "a")

        assertEquals(listOf("b"), MacroStore.all(context).map { it.id })
        assertNull(MacroStore.byId(context, "a"))
        assertNotNull(MacroStore.byId(context, "b"))
    }

    @Test
    fun newId_isUniqueUuid() {
        val first = MacroStore.newId()
        val second = MacroStore.newId()
        assertEquals(36, first.length)
        assertNotEquals(first, second)
    }

    @Test
    fun newlyCreatedPlaceholderWithoutTargetApp_isReadableBack() {
        // 回归用例：新建宏的流程是"先落一条目标 App 还没选的占位宏 → 再进编辑页去选"，
        // 如果解码时把空目标包当坏数据丢掉，编辑页会立刻读不到宏并退回列表（表现为"点 + 没反应"）。
        MacroStore.upsert(
            context,
            Macro(
                id = "placeholder",
                name = Macro.FALLBACK_NAME,
                targetPackage = "",
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )

        val loaded = MacroStore.byId(context, "placeholder")
        assertNotNull(loaded)
        assertEquals("", loaded!!.targetPackage)
        assertEquals(Macro.FALLBACK_NAME, loaded.name)
    }

    @Test
    fun brokenStoredJson_degradesToEmptyListInsteadOfCrashing() {
        context.getSharedPreferences(MacroStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(MacroJson.PREF_KEY, "{坏掉的数据")
            .commit()

        assertTrue(MacroStore.all(context).isEmpty())
    }

    @Test
    fun everyWrite_bumpsRevision() {
        // 图标 / 名字都按 revision 做缓存失效：写宏必须让代次前进，否则面板上改完不刷新。
        val beforeUpsert = MacroStore.revision
        MacroStore.upsert(context, macro("rev"))
        assertTrue(MacroStore.revision > beforeUpsert)

        val beforeDelete = MacroStore.revision
        MacroStore.delete(context, "rev")
        assertTrue(MacroStore.revision > beforeDelete)
    }
}
