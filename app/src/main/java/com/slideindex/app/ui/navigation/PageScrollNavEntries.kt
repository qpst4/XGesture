package com.slideindex.app.ui.navigation

import com.slideindex.app.pagescroll.PageScrollSettingsScreen
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

/** 「页面滚动」设置页的导航入口。 */
fun NavEntryBuilder.pageScrollNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.PageScrollSettings> {
        PageScrollSettingsScreen(
            onBack = { ctx.navigateBack() },
        )
    }
}
