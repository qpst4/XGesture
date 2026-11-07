package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object LauncherUtils {
    private var cachedHomePackages: Set<String>? = null

    fun isHomePackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName in TaskExclusions.LAUNCHER_AND_SYSTEM) {
            return packageName != "com.android.systemui" && packageName != "com.slideindex.app"
        }
        return resolveHomePackages(context).contains(packageName)
    }

    private fun resolveHomePackages(context: Context): Set<String> {
        cachedHomePackages?.let { return it }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val packages = context.packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName?.takeIf { name -> name.isNotBlank() } }
            .toSet()
        cachedHomePackages = packages
        return packages
    }
}
