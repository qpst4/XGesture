package com.slideindex.app.overlay.dragdrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收起态是否保留「操作」枢纽的判定。
 *
 * 守两条回归：开关打开后不能再出现一个点开没内容的枢纽；
 * 文件拖拽本来就没有动作菜单，任何开关下都不该冒出枢纽。
 */
class DragDropFlowLayoutPolicyTest {

    @Test
    fun `text drag keeps hub while actions are collapsed`() {
        assertTrue(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = false, hasText = true, inlineActions = false))
    }

    @Test
    fun `image drag keeps hub while actions are collapsed`() {
        assertTrue(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = true, hasText = false, inlineActions = false))
    }

    @Test
    fun `hub disappears once actions are shown inline`() {
        assertFalse(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = false, hasText = true, inlineActions = true))
        assertFalse(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = true, hasText = false, inlineActions = true))
    }

    @Test
    fun `file drag never gets a hub under either setting`() {
        assertFalse(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = false, hasText = false, inlineActions = false))
        assertFalse(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = false, hasText = false, inlineActions = true))
    }

    /** 富文本（同时带图片和文字）走的是图片动作菜单，算有动作可展开。 */
    @Test
    fun `rich content counts as actionable`() {
        assertTrue(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = true, hasText = true, inlineActions = false))
        assertFalse(DragDropFlowLayoutPolicy.shouldKeepHub(hasImage = true, hasText = true, inlineActions = true))
    }
}
