package com.slideindex.app.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.slideindex.app.util.queryIntentActivitiesCompat

object ShareTextTargetResolver {
    fun listTargets(context: Context): List<ShareImageTarget> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }
        return pm.queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo -> toTarget(pm, resolveInfo) }
            .distinctBy { "${it.packageName}/${it.activityClassName}" }
            .sortedWith(ShareImageTargetResolver.targetOrder)
    }

    fun searchTargets(targets: List<ShareImageTarget>, query: String): List<ShareImageTarget> =
        ShareImageTargetResolver.searchTargets(targets, query)

    fun displaySubtitle(target: ShareImageTarget): String =
        ShareImageTargetResolver.displaySubtitle(target)

    private fun toTarget(pm: PackageManager, resolveInfo: ResolveInfo): ShareImageTarget? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val appLabel = runCatching {
            val appInfo = pm.getApplicationInfo(activityInfo.packageName, 0)
            pm.getApplicationLabel(appInfo).toString().trim()
        }.getOrDefault(activityInfo.packageName)
        val label = resolveInfo.loadLabel(pm).toString().trim().takeIf { it.isNotBlank() }
            ?: activityInfo.loadLabel(pm).toString().trim().takeIf { it.isNotBlank() }
            ?: appLabel
        return ShareImageTarget(
            packageName = activityInfo.packageName,
            activityClassName = activityInfo.name,
            label = label,
            appLabel = appLabel,
            icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
        )
    }
}
