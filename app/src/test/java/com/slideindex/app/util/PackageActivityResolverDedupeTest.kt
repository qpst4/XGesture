package com.slideindex.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 回归：Activity 选择页以组件做 LazyColumn key，同一个组件被枚举两次时会抛
 * 「Key ... was already used」整页闪退（反馈日志里重复的就是企业微信的
 * `com.tencent.wework.wxapi.WXEntryActivity`）。
 */
class PackageActivityResolverDedupeTest {

    @Test
    fun dedupe_keepsFirstRowForRepeatedComponent() {
        val exported = activity("com.tencent.wework.wxapi.WXEntryActivity", exported = true)
        val disabled = activity("com.tencent.wework.wxapi.WXEntryActivity", exported = false)

        val rows = dedupeActivitiesByComponent(listOf(exported, disabled))

        assertEquals(1, rows.size)
        // 调用方已按「导出优先」排序，保留首条就是能点开的那条
        assertSame(exported, rows[0])
    }

    @Test
    fun dedupe_keepsSameClassNameInDifferentPackages() {
        val rows = dedupeActivitiesByComponent(
            listOf(
                activity("com.example.Main", packageName = "com.example.one"),
                activity("com.example.Main", packageName = "com.example.two"),
            ),
        )

        assertEquals(2, rows.size)
    }

    @Test
    fun dedupe_keepsDistinctActivitiesInOrder() {
        val first = activity("com.example.A")
        val second = activity("com.example.B")

        val rows = dedupeActivitiesByComponent(listOf(first, second))

        assertEquals(listOf(first, second), rows)
    }

    @Test
    fun dedupe_leavesShortListUntouched() {
        val single = listOf(activity("com.example.A"))
        val empty = emptyList<ExportedActivityInfo>()

        assertSame(single, dedupeActivitiesByComponent(single))
        assertSame(empty, dedupeActivitiesByComponent(empty))
    }

    private fun activity(
        className: String,
        exported: Boolean = true,
        packageName: String = "com.tencent.wework",
    ): ExportedActivityInfo = ExportedActivityInfo(
        packageName = packageName,
        className = className,
        label = className.substringAfterLast('.'),
        exported = exported,
    )
}
