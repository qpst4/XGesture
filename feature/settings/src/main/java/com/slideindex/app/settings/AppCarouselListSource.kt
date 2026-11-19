package com.slideindex.app.settings

/**
 * 应用切换器（卡片轮播）的列表来源。
 *
 * - [RECENT_APPS]：历史行为。真实任务优先，再用「最近使用」和已安装应用补满，
 *   卡片可能只是使用记录、没有对应的后台任务；
 * - [BACKGROUND_TASKS]：只显示系统最近任务（与任务切换器同源），不补历史/装机项，
 *   需要 Shizuku / root，卡片都带 taskId，关闭时可以精确划掉。
 */
enum class AppCarouselListSource(val id: Int) {
    RECENT_APPS(0),
    BACKGROUND_TASKS(1),
    ;

    companion object {
        fun fromId(id: Int): AppCarouselListSource =
            entries.firstOrNull { it.id == id } ?: RECENT_APPS
    }
}
