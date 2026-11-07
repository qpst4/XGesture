package com.slideindex.app.overlay.pickresult

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.queryIntentActivitiesCompat
import com.slideindex.app.util.toSafeImageBitmap

/**
 * 「用其他应用处理」列表里的一个入口。
 *
 * 和取词面板自己的动作不同，这些是别的 App 注册给系统文本菜单的 `ACTION_PROCESS_TEXT`
 * Activity：标签取的是 **Activity 自己的 label**（系统菜单里显示的就是它，如「在 Edge 中搜索」），
 * 不是应用名；同一个应用可以有两个入口（如便签 / 待办）。
 */
data class PickResultProcessTextTarget(
    val packageName: String,
    val activityName: String,
    val label: String,
    /** 已经在 IO 线程序列化好的图标，Compose 侧直接用。 */
    val icon: ImageBitmap?,
    /** 排序键：Activity 标签的拼音，中文标签才不会按 Unicode 乱序。 */
    val sortKey: String = PinyinHelper.sortKey(label),
)

/**
 * 查询能接收取词文本的 `ACTION_PROCESS_TEXT` 入口。
 *
 * 为什么不用系统选择器：部分 ROM（实测 Flyme）把 PROCESS_TEXT 的候选收敛成系统翻译一个，
 * 系统选择器按 AOSP 的「单候选自动启动」逻辑直接把人带进翻译页，列表根本不显示。
 * 所以这里自己查、自己列，点哪项就用 component 明确启动——不再经过系统选择器。
 */
object PickResultProcessText {

    fun listTargets(context: Context): List<PickResultProcessTextTarget> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply { type = "text/plain" }
        return pm.queryIntentActivitiesCompat(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                // 导不出的入口从浮窗里起不来，别列给用户点。
                if (!activityInfo.exported) return@mapNotNull null
                if (activityInfo.packageName == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString().trim()
                    .ifBlank { activityInfo.packageName }
                PickResultProcessTextTarget(
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    label = label,
                    icon = runCatching { resolveInfo.loadIcon(pm).toSafeImageBitmap(96) }.getOrNull(),
                )
            }
            .distinctBy { "${it.packageName}/${it.activityName}" }
            .sortedWith(
                compareBy<PickResultProcessTextTarget> { it.sortKey }
                    .thenBy { it.packageName }
                    .thenBy { it.activityName },
            )
    }
}
