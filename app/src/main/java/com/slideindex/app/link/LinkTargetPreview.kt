package com.slideindex.app.link

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import com.slideindex.app.util.toSafeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LinkTargetPreview(
    val label: String,
    val icon: ImageBitmap?,
    val rawIcon: Drawable? = null
)

/**
 * 预测链接命中分发规则后的目标应用信息（支持短链解析与内存缓存）。
 */
suspend fun previewLinkTarget(
    context: Context,
    url: String,
    sourcePackage: String = "",
    sourceText: String? = null,
    resolveShortLinks: Boolean = true,
): LinkTargetPreview? = withContext(Dispatchers.IO) {
    val result = LinkDispatcher.route(context, url, sourcePackage, sourceText, resolveShortLinks = resolveShortLinks)
    val routed = result as? LinkDispatchResult.Routed
    val destination = when (result) {
        is LinkDispatchResult.Routed -> result.url
        is LinkDispatchResult.Exempted -> result.url
        is LinkDispatchResult.SystemDefault -> result.url
        LinkDispatchResult.NoRule -> url
        LinkDispatchResult.Failed -> return@withContext null
    }
    val intent = LinkLauncher.buildLaunchIntent(
        url = destination,
        targetPackage = routed?.rule?.targetPackage.orEmpty(),
        excludeFromRecents = false,
        component = routed?.rule?.component
    ) ?: return@withContext null

    val manager = context.packageManager
    val explicitPackage = intent.component?.packageName ?: intent.`package`
    val pkg = explicitPackage ?: manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let { info ->
        info.packageName.takeUnless {
            it == "android" || info.name.contains("ResolverActivity") || info.name.contains("ChooserActivity")
        }
    } ?: return@withContext null

    try {
        val appInfo = manager.getApplicationInfo(pkg, 0)
        val label = manager.getApplicationLabel(appInfo).toString()
        val rawDrawable = runCatching { manager.getApplicationIcon(appInfo) }.getOrNull()
        val imageBitmap = runCatching { rawDrawable?.toSafeImageBitmap(96) }.getOrNull()
        LinkTargetPreview(label, imageBitmap, rawDrawable)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
