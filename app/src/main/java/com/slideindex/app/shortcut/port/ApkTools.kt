package com.slideindex.app.shortcut.port

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

class ApkTools(private val context: Context) {

    fun getAppName(packageName: String): String? = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    fun getAppVersion(packageName: String): String? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    fun getAppIcon(packageName: String): Drawable? = runCatching {
        val info: ApplicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationIcon(info)
    }.getOrNull()

    fun getAppInfo(packageName: String): AppInfo? = runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        val version = pm.getPackageInfo(packageName, 0).versionName.orEmpty()
        AppInfo(
            name = pm.getApplicationLabel(info).toString(),
            version = version,
            icon = pm.getApplicationIcon(info),
        )
    }.getOrNull()
}

data class AppInfo(
    val name: String,
    val version: String,
    val icon: Drawable,
)
