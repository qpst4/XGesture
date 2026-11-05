package com.slideindex.app.settings

/**
 * 一次性迁移：给旧版本自动落盘过图标的分享 / 外部跳转引擎补上「图标跟随的应用包名」。
 *
 * 旧版本创建这类引擎时，会把目标应用的图标存成 `search_icons/custom-*.png` 并当作固定图标；
 * 于是图标库里换了应用图标，搜索面板 dock 也不会跟随。
 *
 * 这里按「目标包名 + 快照路径」推断来源，**保留** [SearchEngineConfig.iconPath]：
 * 目标应用被卸载 / 换设备没装时还能回退到那张快照。猜错的代价也只是回退图仍可用。
 */
internal object SearchEngineIconSourceMigration {

    fun migrate(engines: List<SearchEngineConfig>): List<SearchEngineConfig> =
        engines.map(::migrateOne)

    private fun migrateOne(engine: SearchEngineConfig): SearchEngineConfig {
        if (engine.iconSourcePackage != null) return engine
        if (engine.iconType != SearchIconType.URI) return engine
        val iconPath = engine.iconPath ?: return engine
        if (!iconPath.startsWith("search_icons/")) return engine
        val packageName = engine.targetPackage?.takeIf { it.isNotBlank() }
            ?: engine.externJumpPackage?.takeIf { it.isNotBlank() }
            ?: return engine
        return engine.copy(iconSourcePackage = packageName)
    }
}
