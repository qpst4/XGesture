package com.slideindex.app.ui

import com.slideindex.app.data.AppInfo

/** 应用列表里的一个条目：已安装（含 AppInfo）或缺失（只剩包名）。 */
sealed class AppPackageEntry {
    abstract val packageName: String

    data class Installed(val app: AppInfo) : AppPackageEntry() {
        override val packageName: String = app.packageName
    }

    data class Missing(override val packageName: String) : AppPackageEntry()
}