package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchEngineIconSourceMigrationTest {

    @Test
    fun autoSnapshotShareEngineGetsSourcePackage() {
        val engine = SearchEngineConfig(
            id = "e1",
            name = "发送给微信",
            engineType = SearchEngineType.SHARE_IMAGE_TO_APP,
            iconType = SearchIconType.URI,
            iconPath = "search_icons/custom-1.png",
            targetPackage = "com.tencent.mm",
        )

        val migrated = SearchEngineIconSourceMigration.migrate(listOf(engine)).single()

        assertEquals("com.tencent.mm", migrated.iconSourcePackage)
        // 快照保留：目标应用没装时还能回退
        assertEquals("search_icons/custom-1.png", migrated.iconPath)
    }

    @Test
    fun externJumpEngineUsesExternPackage() {
        val engine = SearchEngineConfig(
            id = "e2",
            name = "跳转淘宝",
            engineType = SearchEngineType.EXTERN_JUMP_LINK,
            iconType = SearchIconType.URI,
            iconPath = "search_icons/custom-2.png",
            externJumpPackage = "com.taobao.taobao",
        )

        assertEquals(
            "com.taobao.taobao",
            SearchEngineIconSourceMigration.migrate(listOf(engine)).single().iconSourcePackage,
        )
    }

    @Test
    fun presetIconWithoutTargetPackageIsUntouched() {
        val engine = SearchEngineConfig(
            id = "default-google",
            name = "Google",
            engineType = SearchEngineType.DIRECT_LINK,
            iconType = SearchIconType.URI,
            iconPath = "search_icons/custom-3.png",
        )

        assertNull(SearchEngineIconSourceMigration.migrate(listOf(engine)).single().iconSourcePackage)
    }

    @Test
    fun textIconIsUntouched() {
        val engine = SearchEngineConfig(
            id = "e3",
            name = "文字引擎",
            engineType = SearchEngineType.SHARE_TO_APP,
            iconType = SearchIconType.TEXT,
            textIcon = "微",
            targetPackage = "com.tencent.mm",
        )

        val migrated = SearchEngineIconSourceMigration.migrate(listOf(engine)).single()

        assertNull(migrated.iconSourcePackage)
        assertEquals(SearchIconType.TEXT, migrated.iconType)
    }

    @Test
    fun existingSourceIsKept() {
        val engine = SearchEngineConfig(
            id = "e4",
            name = "已跟随",
            engineType = SearchEngineType.SHARE_TO_APP,
            iconType = SearchIconType.URI,
            iconPath = "search_icons/custom-4.png",
            targetPackage = "com.tencent.mm",
            iconSourcePackage = "com.tencent.mobileqq",
        )

        assertEquals(
            "com.tencent.mobileqq",
            SearchEngineIconSourceMigration.migrate(listOf(engine)).single().iconSourcePackage,
        )
    }
}
