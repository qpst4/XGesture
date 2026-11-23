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

    @Test
    fun `text group resolves as a text engine`() {
        val group = SearchEngineConfig(
            id = "group",
            name = "Group",
            engineType = SearchEngineType.SEARCH_GROUP,
            groupKind = SearchGroupKind.TEXT,
            groupMemberIds = listOf("a", "b"),
        )
        val engines = listOf(engine("a"), group)

        assertEquals("group", SearchEngineStore.findTextEngineById(engines, "group")?.id)
    }

    @Test
    fun `image share group only appears in the image share list`() {
        val group = SearchEngineConfig(
            id = "image-group",
            name = "Image group",
            engineType = SearchEngineType.SEARCH_GROUP,
            groupKind = SearchGroupKind.IMAGE_SHARE,
            groupMemberIds = listOf("image"),
        )
        val engines = listOf(
            engine("image", engineType = SearchEngineType.SHARE_IMAGE_TO_APP),
            group,
        )

        assertEquals(
            listOf("image", "image-group"),
            SearchEngineStore.imageSharePanelEngines(engines).map { it.id },
        )
        assertNull(SearchEngineStore.findTextEngineById(engines, "image-group"))
    }

    @Test
    fun `group fields survive encode and decode`() {
        val group = SearchEngineConfig(
            id = "group",
            name = "Group",
            engineType = SearchEngineType.SEARCH_GROUP,
            groupKind = SearchGroupKind.IMAGE_SHARE,
            groupMemberIds = listOf("a", "b"),
            groupIntervalMillis = 1250,
        )

        val decoded = SearchEngineStore.decodeOrEmpty(SearchEngineStore.encode(listOf(group))).single()

        assertEquals(SearchGroupKind.IMAGE_SHARE, decoded.groupKind)
        assertEquals(listOf("a", "b"), decoded.groupMemberIds)
        assertEquals(1250, decoded.groupIntervalMillis)
    }
}
