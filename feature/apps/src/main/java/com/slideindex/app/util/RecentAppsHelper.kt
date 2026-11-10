package com.slideindex.app.util

import com.slideindex.app.data.AppInfo

data class RecentAppEntry(
    val app: AppInfo,
    val lastUsed: Long,
    val isLocked: Boolean = false,
    val taskId: Int = 0,
    val rawIdentifier: String = app.packageName,
    val topComponent: String = "",
    /** 多开/分身实例：与主实例同包名同图标，靠名字后缀 + 图标角标区分。 */
    val isClone: Boolean = false,
    /** 分身/工作资料所在的 user；0 = 主用户或未知。 */
    val userId: Int = 0,
)
