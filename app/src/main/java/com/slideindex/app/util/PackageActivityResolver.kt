package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.LruCache

data class ExportedActivityInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val exported: Boolean,
    /** 是否该包的 MAIN/LAUNCHER 入口，也就是点桌面图标会进的那个。 */
    val launcherEntry: Boolean = false,
)

object PackageActivityResolver {
    private const val TAG = "PackageActivityResolver"

    /**
     * 默认 flags 下 `PackageInfo.activities` 不含被禁用的 Activity，而系统应用大量 Activity
     * 是厂商默认关闭/隐藏的——不带 `MATCH_DISABLED_COMPONENTS` 就会漏掉「系统应用的隐藏页」
     * 这一整类目标。此处的 flags 需与 AppRepository#hasAnyActivity 保持一致。
     */
    private const val ACTIVITY_FLAGS = PackageManager.GET_ACTIVITIES or
        PackageManager.MATCH_DISABLED_COMPONENTS
    private val listCache = object : LruCache<String, List<ExportedActivityInfo>>(8) {}

    fun listActivities(context: Context, packageName: String): List<ExportedActivityInfo> {
        if (packageName.isBlank()) return emptyList()
        listCache.get(packageName)?.let { return it }
        val pm = context.packageManager
        val activities = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(ACTIVITY_FLAGS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, ACTIVITY_FLAGS)
            }
            packageInfo.activities.orEmpty()
        } catch (_: PackageManager.NameNotFoundException) {
            return emptyList()
        }
        val entryClassNames = launcherEntryClassNames(pm, packageName)
        val rows = activities
            .asSequence()
            .map { info ->
                val className = resolveClassName(info)
                runCatching {
                    PickerAppIconBitmap.putActivityIcon(packageName, className, info.loadIcon(pm))
                }
                ExportedActivityInfo(
                    packageName = packageName,
                    className = className,
                    label = info.loadLabel(pm).toString().ifBlank { className },
                    exported = info.exported,
                    launcherEntry = className in entryClassNames,
                )
            }
            .sortedWith(compareBy({ !it.exported }, { it.className }, { it.label }))
            .toList()
        val deduped = dedupeActivitiesByComponent(rows)
        if (deduped.size != rows.size) {
            // 同一个组件被枚举两次（本次反馈里是企业微信的 wxapi.WXEntryActivity：manifest
            // 重复声明，或定制 PackageManager 的枚举口径）。列表按「包名/类名」做 key，
            // 重复会直接抛「Key ... was already used」把整页打崩。留痕便于回看机型差异。
            Log.w(TAG, "$packageName 的 Activity 有重复项，已合并 ${rows.size - deduped.size} 条")
        }
        return deduped.also { listCache.put(packageName, it) }
    }

    /**
     * 该包 MAIN/LAUNCHER 入口的类名集合，用来在列表里标「入口」。
     *
     * 用 `queryIntentActivities` 而不是翻 `IntentFilter`：前者已经做过 intent-filter 匹配，
     * 与桌面实际能点到的入口一致；老设备/新设备共用 [queryIntentActivitiesCompat]。
     */
    private fun launcherEntryClassNames(pm: PackageManager, packageName: String): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        return runCatching {
            pm.queryIntentActivitiesCompat(intent, 0)
                .mapNotNull { it.activityInfo?.name }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun isActivityExported(context: Context, packageName: String, className: String): Boolean {
        if (packageName.isBlank() || className.isBlank()) return false
        val pm = context.packageManager
        val activities = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(ACTIVITY_FLAGS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, ACTIVITY_FLAGS)
            }
            packageInfo.activities.orEmpty()
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        for (info in activities) {
            val resolved = resolveClassName(info)
            if (resolved == className || info.name == className) {
                return info.exported
            }
        }
        return false
    }

    fun searchActivities(activities: List<ExportedActivityInfo>, query: String): List<ExportedActivityInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return activities
        return activities.filter { activity ->
            activity.label.lowercase().contains(q) ||
                activity.className.lowercase().contains(q) ||
                activity.className.substringAfterLast('.').lowercase().contains(q)
        }
    }

    private fun resolveClassName(info: ActivityInfo): String {
        val name = info.name.orEmpty()
        return when {
            name.startsWith('.') -> info.packageName + name
            name.isNotBlank() -> name
            else -> info.packageName
        }
    }
}

/**
 * 同一个「包名/类名」只留一条。
 *
 * Activity 选择页的列表以组件做 LazyColumn key，重复会直接抛
 * 「Key ... was already used」把整页打崩。调用方已按「导出优先」排过序，这里保留首条，
 * 行尾的「需特权」胶囊不会被同名的未导出条目误标。
 */
internal fun dedupeActivitiesByComponent(
    activities: List<ExportedActivityInfo>,
): List<ExportedActivityInfo> {
    if (activities.size < 2) return activities
    return activities.distinctBy { "${it.packageName}/${it.className}" }
}
