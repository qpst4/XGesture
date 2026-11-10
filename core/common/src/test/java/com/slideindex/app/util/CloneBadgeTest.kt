package com.slideindex.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 分身黄点的位置规则：方形贴角、圆形落在 45° 圆内。
 *
 * 回归点：早先只有"烘进方图"的版本，圆形启动器（角落轮盘 / 圆环 / 蜂窝）会把角上的黄点裁掉；
 * 圆形位置必须让黄点外缘（含描边）仍在圆内，才能完整显示。
 */
class CloneBadgeTest {

    private val centerX = 500f
    private val centerY = 500f
    private val size = 200f
    private val r = size * 0.11f

    @Test
    fun squareBadge_sitsAtTopRightCorner() {
        val (cx, cy) = CloneBadge.badgeCenter(centerX, centerY, size, circular = false)
        assertEquals(centerX + size / 2f - r * 1.3f, cx, 0.01f)
        assertEquals(centerY - size / 2f + r * 1.3f, cy, 0.01f)
    }

    @Test
    fun circularBadge_fitsInsideTheCircle() {
        val (cx, cy) = CloneBadge.badgeCenter(centerX, centerY, size, circular = true)
        val dx = cx - centerX
        val dy = cy - centerY
        val distance = sqrt(dx * dx + dy * dy)
        // 45° 右上
        assertEquals(dx, -dy, 0.01f)
        assertTrue(dx > 0f)
        // 黄点外缘（半径 r）必须还在图标圆内
        assertTrue(distance + r < size / 2f)
    }
}
