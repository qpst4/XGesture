package com.slideindex.app.search

import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchGroupKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineValidatorTest {

    private fun group(
        memberIds: List<String> = listOf("a"),
        intervalMillis: Int = 800,
    ) = SearchEngineConfig(
        id = "group",
        name = "Group",
        engineType = SearchEngineType.SEARCH_GROUP,
        groupKind = SearchGroupKind.TEXT,
        groupMemberIds = memberIds,
        groupIntervalMillis = intervalMillis,
    )

    @Test
    fun `group needs a name and at least one member`() {
        assertFalse(SearchEngineValidator.validate(group(memberIds = emptyList())))
        assertTrue(SearchEngineValidator.validate(group()))
    }

    @Test
    fun `group interval is bounded`() {
        assertFalse(SearchEngineValidator.validate(group(intervalMillis = 199)))
        assertFalse(SearchEngineValidator.validate(group(intervalMillis = 2001)))
        assertTrue(SearchEngineValidator.validate(group(intervalMillis = 200)))
        assertTrue(SearchEngineValidator.validate(group(intervalMillis = 2000)))
    }

    @Test
    fun `group cannot contain itself`() {
        assertFalse(SearchEngineValidator.validate(group(memberIds = listOf("group"))))
    }
}
