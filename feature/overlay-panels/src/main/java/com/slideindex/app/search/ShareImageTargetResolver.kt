package com.slideindex.app.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.queryIntentActivitiesCompat

data class ShareImageTarget(
    val packageName: String,
    val activityClassName: String,
    /** Activity 在分享选择器里显示的名称，如「拍立淘」。 */
    val label: String,
    /** 所属应用的名称，如「淘宝」。 */
    val appLabel: String,
    val icon: Drawable?,
    /** 应用名的拼音排序键（中文转拼音）：分组按它排，中文应用才不会按 Unicode 乱序。 */
    val appPinyinKey: String = PinyinHelper.sortKey(appLabel),
    /** 应用名首字母键，拼音缩写搜索（如「wx」→ 微信）用。 */
    val appInitialKey: String = PinyinHelper.initialKey(appLabel),
    /** 入口名的拼音排序键：同一应用内部多个入口按它排。 */
    val labelPinyinKey: String = PinyinHelper.sortKey(label),
)

/**
 * 同一个应用暴露出来的分享入口集合。
 *
 * 选择器的分组头只需要「应用」这一层信息（名称 / 包名），逐个 Activity 的名称和类名
 * 留给展开后的子项，避免一屏里重复几十遍相同的应用名和包名。
 */
data class ShareTargetAppGroup(
    val packageName: String,
    val appLabel: String,
    /** 该应用可接收分享的入口，顺序与 [ShareImageTargetResolver.listTargets] 一致。 */
    val targets: List<ShareImageTarget>,
)

object ShareImageTargetResolver {
    private val imageMimeTypes = listOf(
        "image/*",
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/bmp",
        "image/heic",
        "image/heif"
    )

    /**
     * 分享目标的统一排序：先按应用名拼音，再按入口名拼音，最后用包名兜底保证稳定。
     *
     * 图片 / 文本 / 文件三种列表共用这一份比较器；用拼音而不是 lowercase 直接比较，
     * 是因为中文应用名按 Unicode 排出来和用户预期的「首字母」顺序完全对不上。
     */
    val targetOrder: Comparator<ShareImageTarget> = compareBy<ShareImageTarget> { it.appPinyinKey }
        .thenBy { it.labelPinyinKey }
        .thenBy { it.packageName }

    fun listTargets(context: Context): List<ShareImageTarget> {
        val pm = context.packageManager
        return listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
            .flatMap { action ->
                imageMimeTypes.flatMap { mimeType ->
                    val intent = Intent(action).apply {
                        type = mimeType
                    }
                    pm.queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
                }
            }
            .mapNotNull { resolveInfo -> toTarget(pm, resolveInfo) }
            .distinctBy { "${it.packageName}/${it.activityClassName}" }
            .sortedWith(targetOrder)
    }

    /**
     * 按应用分组，组内保持 [targets] 的原始顺序（调用方已经按应用名 / 入口名排好序）。
     *
     * 图片 / 文本 / 文件三种分享目标共用同一套分组逻辑。
     */
    fun groupByApp(targets: List<ShareImageTarget>): List<ShareTargetAppGroup> =
        targets.groupBy { it.packageName }.map { (packageName, appTargets) ->
            ShareTargetAppGroup(
                packageName = packageName,
                appLabel = appTargets.first().appLabel,
                targets = appTargets,
            )
        }

    fun searchTargets(targets: List<ShareImageTarget>, query: String): List<ShareImageTarget> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return targets
        val lower = trimmed.lowercase()
        val pinyinQuery = PinyinHelper.sortKey(trimmed)
        val initialQuery = PinyinHelper.initialKey(trimmed)
        return targets.filter { target ->
            target.appLabel.lowercase().contains(lower) ||
                target.label.lowercase().contains(lower) ||
                target.packageName.lowercase().contains(lower) ||
                target.activityClassName.lowercase().contains(lower) ||
                target.appPinyinKey.contains(pinyinQuery) ||
                target.appInitialKey.contains(initialQuery) ||
                target.labelPinyinKey.contains(pinyinQuery)
        }
    }

    fun displaySubtitle(target: ShareImageTarget): String {
        return if (target.label.equals(target.appLabel, ignoreCase = true)) {
            target.packageName
        } else {
            "${target.appLabel} · ${target.packageName}"
        }
    }

    /**
     * 单入口应用在选择器里的标题：`应用名 · 入口名`。
     *
     * 入口名与应用名相同（很多应用的分享 Activity 直接叫应用名）时只显示应用名，
     * 避免出现「QQ · QQ」这种重复。
     */
    fun displayGroupTitle(target: ShareImageTarget): String {
        return if (target.label.equals(target.appLabel, ignoreCase = true)) {
            target.appLabel
        } else {
            "${target.appLabel} · ${target.label}"
        }
    }

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
