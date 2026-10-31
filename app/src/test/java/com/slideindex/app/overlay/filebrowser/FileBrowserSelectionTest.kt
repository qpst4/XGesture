package com.slideindex.app.overlay.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileBrowserSelectionTest {
    private val ordered = listOf("/root/a", "/root/b", "/root/c", "/root/d", "/root/e")

    @Test
    fun rangeBetween_includesBothEnds_forward() {
        assertEquals(
            listOf("/root/b", "/root/c", "/root/d"),
            FileBrowserSelection.rangeBetween(ordered, "/root/b", "/root/d"),
        )
    }

    @Test
    fun rangeBetween_includesBothEnds_backward() {
        assertEquals(
            listOf("/root/b", "/root/c", "/root/d"),
            FileBrowserSelection.rangeBetween(ordered, "/root/d", "/root/b"),
        )
    }

    @Test
    fun rangeBetween_adjacentRowsSelectsBoth() {
        assertEquals(
            listOf("/root/a", "/root/b"),
            FileBrowserSelection.rangeBetween(ordered, "/root/a", "/root/b"),
        )
    }

    @Test
    fun rangeBetween_sameTargetNeedsNoRange() {
        assertNull(FileBrowserSelection.rangeBetween(ordered, "/root/c", "/root/c"))
    }

    @Test
    fun rangeBetween_withoutAnchorNeedsNoRange() {
        assertNull(FileBrowserSelection.rangeBetween(ordered, null, "/root/c"))
    }

    @Test
    fun rangeBetween_anchorNotInCurrentListNeedsNoRange() {
        // 搜索词变化后 anchor 可能已不在过滤结果里：此时退化为只勾选目标行。
        assertNull(FileBrowserSelection.rangeBetween(ordered, "/root/zzz", "/root/c"))
    }
}
