package com.android.server.clipboard

/** 系统侧权限检查替身：普通后台应用默认不能读取剪贴板。 */
class ClipboardService {
    @Suppress("UNUSED_PARAMETER")
    fun isDefaultIme(uid: Int, packageName: String): Boolean = false
}
