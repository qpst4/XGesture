package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchEngineStoreTest {

    private fun engine(
        id: String,
        showInPickPanel: Boolean = true,
        engineType: SearchEngineType = SearchEngineType.DIRECT_LINK,
        sortOrder: Int = 0,
    ) = SearchEngineConfig(
        id = id,
        name = id,
        engineType = engineType,
        showInPickPanel = showInPickPanel,
        sortOrder = sortOrder,
    )

    @Test
    fun `resolves hidden engine as default`() {
        val engines = listOf(
            engine("visible", sortOrder = 0),
            engine("hidden", showInPickPanel = false, sortOrder = 1),
        )

        assertEquals("hidden", SearchEngineStore.findTextEngineById(engines, "hidden")?.id)
    }

    @Test
    fun `returns null when id is missing or unknown`() {
        val engines = listOf(engine("visible"))

        assertNull(SearchEngineStore.findTextEngineById(engines, null))
        assertNull(SearchEngineStore.findTextEngineById(engines, "  "))
        assertNull(SearchEngineStore.findTextEngineById(engines, "ghost"))
    }

    @Test
    fun `ignores image share engines`() {
        val engines = listOf(
            engine("image", engineType = SearchEngineType.SHARE_IMAGE_TO_APP),
        )

        assertNull(SearchEngineStore.findTextEngineById(engines, "image"))
    }

    @Test
    fun `appends new engines after existing ones`() {
        val existing = listOf(
            engine("a", sortOrder = 3),
            engine("b", sortOrder = 7),
        )

        val merged = SearchEngineStore.appendEngines(
            existing,
            listOf(engine("c"), engine("d")),
        )

        assertEquals(listOf("a", "b", "c", "d"), merged.map { it.id })
        assertEquals(listOf(3, 7, 8, 9), merged.map { it.sortOrder })
    }

    @Test
    fun `appending nothing keeps the list untouched`() {
        val existing = listOf(engine("a", sortOrder = 3))

        assertEquals(existing, SearchEngineStore.appendEngines(existing, emptyList()))
    }
}
