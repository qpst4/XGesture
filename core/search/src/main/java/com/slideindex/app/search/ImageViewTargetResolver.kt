package com.slideindex.app.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.MediaStore
import com.slideindex.app.util.queryIntentActivitiesCompat

data class ImageViewTarget(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object ImageViewTargetResolver {
    fun listTargets(context: Context): List<ImageViewTarget> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val pm = context.packageManager
        val selfPackage = context.packageName
        return pm.queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo -> toTarget(pm, resolveInfo) }
            .filter { it.packageName != selfPackage }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun toTarget(pm: PackageManager, resolveInfo: android.content.pm.ResolveInfo): ImageViewTarget? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val packageName = activityInfo.packageName ?: return null
        val appLabel = runCatching {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().trim()
        }.getOrDefault(packageName)
        val icon = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
        return ImageViewTarget(
            packageName = packageName,
            label = appLabel,
            icon = icon
        )
    }
}
