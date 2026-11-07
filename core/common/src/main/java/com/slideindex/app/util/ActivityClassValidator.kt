package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache

/**
 * 校验与过滤 Activity 类名的工具。
 * 避免将 Android View/ViewGroup 控件（如 DecorView 对应的 FrameLayout）误认作 Activity。
 */
object ActivityClassValidator {

    private val activityCache = LruCache<String, Boolean>(256)

    /**
     * 快速判断是否为明显的非 Activity 类（系统 View 控件、布局容器、弹窗、输入法等）。
     */
    fun isIgnoredNonActivityClass(className: String): Boolean {
        if (className.isBlank()) return true
        return className.startsWith("android.widget.") ||
            className.startsWith("android.view.") ||
            className.startsWith("androidx.compose.") ||
            className.startsWith("android.inputmethodservice.") ||
            className.startsWith("androidx.appcompat.widget.") ||
            className == "android.app.Dialog" ||
            className == "android.app.AlertDialog" ||
            className.contains("inputmethod", ignoreCase = true) ||
            className.contains("InputMethodService", ignoreCase = true)
    }

    /**
     * 校验给定的 className 是否为对应 package 下已声明的合法 Activity。
     * 优先走高速排除与内存缓存，未命中时通过 PackageManager 查询。
     */
    fun isRealActivity(context: Context, packageName: String, className: String): Boolean {
        if (packageName.isBlank() || isIgnoredNonActivityClass(className)) {
            return false
        }
        val fullClassName = if (className.startsWith(".")) {
            "$packageName$className"
        } else {
            className
        }
        if (!fullClassName.contains(".")) {
            return false
        }

        val cacheKey = "$packageName/$fullClassName"
        activityCache.get(cacheKey)?.let { return it }

        val isActivity = runCatching {
            val pm = context.packageManager
            val component = ComponentName(packageName, fullClassName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, 0)
            }
            true
        }.getOrDefault(false)

        activityCache.put(cacheKey, isActivity)
        return isActivity
    }
}
