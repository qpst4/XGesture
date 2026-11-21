package com.slideindex.app.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.FreeWindowLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 链接启动与小窗拉起调度器。
 */
object LinkLauncher {

    suspend fun openFromText(
        context: Context,
        text: String,
        sourcePackage: String = "",
        bypassRules: Boolean = false
    ): Boolean {
        val raw = LinkExtractor.extractWithStoredPatterns(context, text)
        if (raw.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.link_dispatch_no_url_found, Toast.LENGTH_SHORT).show()
                Unit
            }
            return false
        }
        if (bypassRules) {
            return launch(context, raw, "", false)
        }
        return openUrl(context, raw, sourcePackage, sourceText = text)
    }

    suspend fun openUrl(
        context: Context,
        rawUrl: String,
        sourcePackage: String = "",
        sourceText: String? = null,
        bypassRules: Boolean = false
    ): Boolean {
        if (bypassRules) {
            return launch(context, rawUrl, "", false)
        }
        val result = LinkDispatcher.route(context, rawUrl, sourcePackage, sourceText)
        return openRouted(context, rawUrl, result)
    }

    private suspend fun openRouted(
        context: Context,
        rawUrl: String,
        result: LinkDispatchResult
    ): Boolean {
        val dispatched = when (result) {
            is LinkDispatchResult.Routed ->
                launch(
                    context,
                    result.url,
                    result.rule.targetPackage,
                    result.rule.excludeFromRecents,
                    result.rule.component
                )

            is LinkDispatchResult.Exempted -> launch(context, result.url, "", false)
            is LinkDispatchResult.SystemDefault -> launch(context, result.url, "", false)
            LinkDispatchResult.NoRule -> launch(context, rawUrl, "", false)
            LinkDispatchResult.Failed -> {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.link_dispatch_no_app, Toast.LENGTH_SHORT).show()
                    Unit
                }
                false
            }
        }

        if (dispatched) recordRuleHit(context, result)
        return dispatched
    }

    private suspend fun recordRuleHit(context: Context, result: LinkDispatchResult) {
        val rule = (result as? LinkDispatchResult.Routed)?.rule ?: return
        try {
            LinkRuleRepository.updateRules(context) { current ->
                current.map {
                    if (it.id == rule.id) it.copy(
                        hitCount = it.hitCount + 1,
                        lastHitAt = System.currentTimeMillis()
                    ) else it
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Ignore statistics write failure
        }
    }

    internal suspend fun launch(
        context: Context,
        url: String,
        targetPackage: String,
        excludeFromRecents: Boolean,
        component: String? = null
    ): Boolean = withContext(Dispatchers.Main) {
        val intent = buildLaunchIntent(url, targetPackage, excludeFromRecents, component)
            ?: run {
                Toast.makeText(context, R.string.link_dispatch_no_app, Toast.LENGTH_SHORT).show()
                return@withContext false
            }

        val openInSmallWindow = LinkRuleRepository.isOpenInSmallWindowEnabled(context)
        val success = runCatching {
            if (openInSmallWindow) {
                FreeWindowLauncher.launch(
                    context = context,
                    intent = intent,
                    settings = AppSettings(freeWindow = com.slideindex.app.settings.FreeWindowSettings(freeWindowEnabled = true)),
                    fullscreen = false
                )
            } else {
                context.startActivity(intent)
            }
            true
        }.getOrElse { false }

        if (success) return@withContext true

        // 尝试降级：如果是 component 指定启动失败，回退为纯包名或系统选择
        if (intent.component != null) {
            val unpinned = Intent(intent).apply { this.component = null }
            val fallbackSuccess = runCatching {
                if (openInSmallWindow) {
                    FreeWindowLauncher.launch(
                        context = context,
                        intent = unpinned,
                        settings = AppSettings(freeWindow = com.slideindex.app.settings.FreeWindowSettings(freeWindowEnabled = true)),
                        fullscreen = false
                    )
                } else {
                    context.startActivity(unpinned)
                }
                true
            }.getOrElse { false }
            if (fallbackSuccess) return@withContext true
        }

        // 终极回退：系统浏览器/系统选择器
        val systemFallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (excludeFromRecents) addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        val systemSuccess = runCatching {
            context.startActivity(systemFallback)
            true
        }.getOrElse { false }

        if (!systemSuccess) {
            Toast.makeText(context, R.string.link_dispatch_no_app, Toast.LENGTH_SHORT).show()
        }
        systemSuccess
    }

    fun buildLaunchIntent(
        url: String,
        targetPackage: String,
        excludeFromRecents: Boolean,
        component: String?
    ): Intent? {
        val normalized = PickResultUrl.normalizeOpenableUrl(url) ?: url
        val intent = if (normalized.startsWith("intent://", ignoreCase = true)) {
            runCatching {
                Intent.parseUri(normalized, Intent.URI_INTENT_SCHEME)
            }.getOrNull() ?: return null
        } else {
            val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
            Intent(Intent.ACTION_VIEW, uri)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (excludeFromRecents) intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        if (targetPackage.isNotEmpty()) {
            intent.setPackage(targetPackage)
            component?.let { activity ->
                intent.component = ComponentName.unflattenFromString(
                    if ('/' in activity) activity else "$targetPackage/$activity"
                )
            }
        }
        return intent
    }
}
