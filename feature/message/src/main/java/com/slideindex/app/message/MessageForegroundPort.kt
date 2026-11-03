package com.slideindex.app.message

/** Resolves foreground app package for message suppression rules. */
interface MessageForegroundPort {
    fun foregroundPackage(): String?

    /**
     * 打开消息前抓「阅读前应用」用的实时前台：优先窗口事件驱动的实时值，
     * 缓存只作最后兜底。默认沿用 [foregroundPackage]，由宿主覆盖。
     */
    fun liveForegroundPackage(): String? = foregroundPackage()
}
