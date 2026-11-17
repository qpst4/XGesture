package com.slideindex.app.external

import android.net.Uri

/**
 * 「XGesture」对外协议的唯一真源。
 *
 * scheme 会被三处消费，且这三处必须永远一致：
 *
 *  1. [com.slideindex.app.MainActivity]、[com.slideindex.app.service.StashClipboardTrampolineActivity]、
 *     [com.slideindex.app.service.SearchPanelTrampolineActivity] 的 intent-filter —— 外部调用方
 *     （Tasker / MacroDroid / adb / 浏览器）经系统路由进来；
 *  2. 同样的 trampoline 用 `Intent(ACTION_VIEW, uri)` + `setClass(...)` 发的显式 Intent ——
 *     应用内部跳转，**不经过 Manifest**，所以过滤器和解析器两边都要对；
 *  3. [ExternalInvocationCatalog] —— 把格式展示给用户。
 *
 * 以前这个字符串在 4 个 Kotlin 文件、4 个 Manifest 节点和 4 个语言的文案里各写一遍，
 * 改一次要数 12 个地方。现在字面量只在这里出现。
 */
object AppLinks {
    const val SCHEME = "xgesture"
    const val HOST = "open"
    const val QUERY_PARAM = "q"

    /** 动作详情页深链的参数名：`xgesture://open/action?type=OPEN_LINK`。 */
    const val TYPE_PARAM = "type"

    const val PATH_NOTIFICATION_HISTORY = "notification-history"
    const val PATH_STASH = "stash"
    const val PATH_CLIPBOARD = "clipboard"
    const val PATH_SEARCH_PANEL = "search-panel"
    const val PATH_ACTION = "action"

    /**
     * 解析入口。各处不要再自己比较 scheme —— 漏改一处就是「能唤起但面板开错」，
     * 既不崩溃也不报错，最难查。
     */
    fun isAppLink(uri: Uri?): Boolean =
        uri != null &&
            uri.scheme?.equals(SCHEME, ignoreCase = true) == true &&
            uri.host == HOST

    fun uri(path: String, query: String? = null): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(path)
            .apply {
                query?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    appendQueryParameter(QUERY_PARAM, it)
                }
            }
            .build()

    fun uriString(path: String): String = uri(path).toString()

    /**
     * 动作详情页深链：`xgesture://open/action?type=<GestureActionType.name>`。
     *
     * 这里单独拼 `type` 参数，因为 [uri] 的 `q` 是给「打开后搜索」用的，不适用于动作类型。
     */
    fun actionUri(type: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(PATH_ACTION)
            .appendQueryParameter(TYPE_PARAM, type)
            .build()

    fun actionUriString(type: String): String = actionUri(type).toString()
}
