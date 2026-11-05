package com.slideindex.app.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.graphics.Bitmap
import com.slideindex.app.R
import com.slideindex.app.autofill.OtpAutoInputNodeHelper
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.privilege.PrivilegeUiStrings
import com.slideindex.app.service.LaunchTrampolineActivity
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.PackageActivityResolver
import com.slideindex.app.util.TaskManagerUtil

object SearchEngineLauncher {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun launchImageShare(context: Context, engine: SearchEngineConfig, bitmap: Bitmap): Boolean {
        if (engine.engineType != SearchEngineType.SHARE_IMAGE_TO_APP) return false
        val pkg = engine.targetPackage?.takeIf { it.isNotBlank() } ?: return false
        val activity = engine.targetActivity?.takeIf { it.isNotBlank() } ?: return false
        return FloatBallTextPick.shareScreenshotTo(
            context,
            bitmap,
            ComponentName(pkg, activity)
        )
    }

    fun launchTextShare(context: Context, engine: SearchEngineConfig, text: String): Boolean {
        if (engine.engineType != SearchEngineType.SHARE_TO_APP) return false
        // 没有可用的分享入口时不能静默返回 false：调用方（尤其是流转栏）拿到的 false
        // 无法区分"没配置"和"用户取消"，不提示就又是一次"松手没反应"。
        val pkg = engine.targetPackage?.takeIf { it.isNotBlank() } ?: return launchFailed(context)
        val activity = engine.targetActivity?.takeIf { it.isNotBlank() } ?: return launchFailed(context)
        return FloatBallTextPick.shareTextTo(
            context,
            text,
            ComponentName(pkg, activity)
        )
    }

    /**
     * 跳转类引擎的实际启动。分享类不会走到这里——统一由 [dispatchTextEngine] 分派。
     */
    private fun launch(
        context: Context,
        engine: SearchEngineConfig,
        query: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false
    ): Boolean {
        val text = query.trim()
        if (text.isBlank()) {
            Toast.makeText(context, R.string.search_engine_query_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        return when (engine.engineType) {
            SearchEngineType.DIRECT_LINK ->
                launchDirectLink(context, engine, text, settings, longPressTriggered, null)
            SearchEngineType.EXTERN_JUMP_LINK ->
                launchExternJump(context, engine, text, settings, longPressTriggered, null)
            SearchEngineType.JUMP_TO_ACTIVITY ->
                launchJumpActivity(context, engine, text, settings, longPressTriggered, null)
            // 只由 dispatchTextEngine 的 OPEN 分支调用，分享类到不了这里；
            // 需要 bitmap / 文件的目标走 launchImageShare / 文件分享链路。
            SearchEngineType.SHARE_TO_APP,
            SearchEngineType.SHARE_IMAGE_TO_APP,
            SearchEngineType.SHARE_FILE_TO_APP,
            -> false
        }
    }

    /**
     * 文字类引擎的统一入口。
     *
     * 「分享到 App」的引擎不能走跳转链路：那个函数只处理跳转类，遇到分享类型会直接
     * 返回 false（它没有 bitmap / 文件可给）。以前每个入口各抄一份 when 就漏了流转栏，
     * 表现是把文字拖到「分享到 QQ」上松手完全没反应。
     *
     * 所有"已经拿到文字、要交给引擎"的入口都应该调这里，不要再自己判断 engineType。
     */
    fun launchText(
        context: Context,
        engine: SearchEngineConfig,
        text: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false,
    ): Boolean = dispatchTextEngine(
        context = context,
        engine = engine,
        text = text,
        settings = settings,
        longPressTriggered = longPressTriggered,
        fullscreenOverride = null,
    )

    /**
     * 同 [launchText]，但由调用方指定窗口形态（取词面板长按直搜用）。
     * 窗口形态只对跳转类有意义，分享类会忽略它。
     */
    fun launchTextWithWindowMode(
        context: Context,
        engine: SearchEngineConfig,
        text: String,
        settings: AppSettings,
        fullscreen: Boolean,
    ): Boolean = dispatchTextEngine(
        context = context,
        engine = engine,
        text = text,
        settings = settings,
        longPressTriggered = false,
        fullscreenOverride = fullscreen,
    )

    /**
     * 文字引擎唯一的执行入口：类型 → 执行方式只在这里判定。
     *
     * 调用方（流转栏 / 搜索面板 / 取词面板）不再各自写 when；新增入口只要调
     * [launchText] 或 [launchTextWithWindowMode]，不会漏掉分享类。
     */
    private fun dispatchTextEngine(
        context: Context,
        engine: SearchEngineConfig,
        text: String,
        settings: AppSettings,
        longPressTriggered: Boolean,
        fullscreenOverride: Boolean?,
    ): Boolean {
        val value = text.trim()
        if (value.isBlank()) {
            Toast.makeText(context, R.string.search_engine_query_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        return when (textLaunchKind(engine.engineType)) {
            TextLaunchKind.SHARE_TEXT -> launchTextShare(context, engine, value)
            TextLaunchKind.OPEN -> if (fullscreenOverride == null) {
                launch(context, engine, value, settings, longPressTriggered)
            } else {
                launchOpenWithWindowMode(context, engine, value, settings, fullscreenOverride)
            }
            TextLaunchKind.UNSUPPORTED -> launchFailed(context)
        }
    }

    /**
     * 以显式窗口形态启动文本搜索：`fullscreen = true` 全屏，`false` 小窗（小窗未开启时仍按全屏）。
     *
     * 只供 [dispatchTextEngine] 在"调用方指定了窗口形态"时使用；对外请走
     * [launchTextWithWindowMode]，别直接调这里（否则又会绕过类型分派）。
     */
    private fun launchOpenWithWindowMode(
        context: Context,
        engine: SearchEngineConfig,
        query: String,
        settings: AppSettings,
        fullscreen: Boolean
    ): Boolean {
        val text = query.trim()
        if (text.isBlank()) {
            Toast.makeText(context, R.string.search_engine_query_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        return when (engine.engineType) {
            SearchEngineType.DIRECT_LINK ->
                launchDirectLink(context, engine, text, settings, false, fullscreen)
            SearchEngineType.EXTERN_JUMP_LINK ->
                launchExternJump(context, engine, text, settings, false, fullscreen)
            SearchEngineType.JUMP_TO_ACTIVITY ->
                launchJumpActivity(context, engine, text, settings, false, fullscreen)
            SearchEngineType.SHARE_TO_APP,
            SearchEngineType.SHARE_IMAGE_TO_APP,
            SearchEngineType.SHARE_FILE_TO_APP,
            -> false // use launchTextShare / launchImageShare instead
        }
    }

    fun launchOpenableUri(
        context: Context,
        uri: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false
    ): Boolean {
        val normalized = PickResultUrl.normalizeOpenableUrl(uri) ?: uri.trim()
        if (normalized.isBlank()) {
            Toast.makeText(context, R.string.search_engine_query_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        val link = resolveLink(context, normalized) { candidate -> buildOpenableUriIntent(candidate) }
            ?: return launchFailed(context)
        return when (link.target) {
            LaunchTarget.UNRESOLVED -> launchFailed(context)
            LaunchTarget.PRIVILEGED -> launchPrivilegedIntent(context, link.intent, autoInputQuery = null)
            LaunchTarget.DIRECT -> startActivity(
                context = context,
                intent = link.intent,
                settings = settings,
                longPressTriggered = longPressTriggered,
                useTrampoline = PickResultUrl.isIntentUri(link.url)
            )
        }
    }

    private fun buildOpenableUriIntent(uri: String): Intent? {
        if (PickResultUrl.isIntentUri(uri)) {
            return parseIntentUri(uri)
        }
        return when {
            uri.startsWith("tel:", ignoreCase = true) -> {
                Intent(Intent.ACTION_DIAL, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            uri.startsWith("mailto:", ignoreCase = true) -> {
                Intent(Intent.ACTION_SENDTO, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            uri.startsWith("sms:", ignoreCase = true) -> {
                Intent(Intent.ACTION_SENDTO, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            else -> buildViewIntent(uri)
        }
    }

    private fun launchDirectLink(
        context: Context,
        engine: SearchEngineConfig,
        query: String,
        settings: AppSettings,
        longPressTriggered: Boolean,
        fullscreenOverride: Boolean?
    ): Boolean {
        val template = engine.searchLink?.takeIf { it.isNotBlank() }
            ?: engine.externJumpLink?.takeIf { it.isNotBlank() }
            ?: return false
        val targetPkg = engine.targetPackage?.takeIf { it.isNotBlank() }
            ?: engine.externJumpPackage?.takeIf { it.isNotBlank() }
        val link = resolveLink(context, formatQuery(template, query)) { candidate ->
            buildViewIntent(candidate, targetPkg)
        } ?: return launchFailed(context)
        val hasPlaceholder = template.contains("%s") || template.contains("%q")
        val autoInput = engine.autoInputEnter && !hasPlaceholder
        return when (link.target) {
            LaunchTarget.UNRESOLVED -> launchFailed(context)
            LaunchTarget.PRIVILEGED -> launchPrivilegedIntent(
                context = context,
                intent = link.intent,
                autoInputQuery = query.takeIf { autoInput },
            )
            LaunchTarget.DIRECT -> {
                val started = startActivity(
                    context,
                    link.intent,
                    settings,
                    longPressTriggered,
                    useTrampoline = isIntentUri(link.url),
                    fullscreenOverride = fullscreenOverride
                )
                if (started && autoInput) {
                    scheduleAutoInput(query)
                }
                started
            }
        }
    }

    private fun launchExternJump(
        context: Context,
        engine: SearchEngineConfig,
        query: String,
        settings: AppSettings,
        longPressTriggered: Boolean,
        fullscreenOverride: Boolean?
    ): Boolean = launchDirectLink(context, engine, query, settings, longPressTriggered, fullscreenOverride)

    private fun launchJumpActivity(
        context: Context,
        engine: SearchEngineConfig,
        query: String,
        settings: AppSettings,
        longPressTriggered: Boolean,
        fullscreenOverride: Boolean?
    ): Boolean {
        val pkg = engine.targetPackage?.takeIf { it.isNotBlank() }
        val activity = engine.targetActivity?.takeIf { it.isNotBlank() }
        if (pkg == null) return false
        if (activity != null) {
            if (PackageActivityResolver.isActivityExported(context, pkg, activity)) {
                val intent = Intent()
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(ComponentName(pkg, activity))
                val started = startActivity(
                    context,
                    intent,
                    settings,
                    longPressTriggered,
                    fullscreenOverride = fullscreenOverride
                )
                if (started) {
                    if (engine.autoInputEnter) {
                        scheduleAutoInput(query)
                    }
                    return true
                }
            }
            return launchNonExportedActivity(
                context,
                pkg,
                activity,
                query,
                engine.autoInputEnter
            )
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        val started = startActivity(
            context,
            intent,
            settings,
            longPressTriggered,
            fullscreenOverride = fullscreenOverride
        )
        if (started && engine.autoInputEnter) {
            scheduleAutoInput(query)
        }
        return started
    }

    private fun launchNonExportedActivity(
        context: Context,
        pkg: String,
        activity: String,
        query: String,
        autoInputEnter: Boolean
    ): Boolean {
        if (!TaskManagerUtil.hasPermission()) {
            Toast.makeText(context, PrivilegeUiStrings.privilegedAccessRequiredRes(), Toast.LENGTH_LONG).show()
            return false
        }
        if (autoInputEnter && !SlideIndexAccessibilityService.isConnected()) {
            Toast.makeText(context, R.string.search_engine_accessibility_required, Toast.LENGTH_LONG).show()
            return false
        }
        NonExportedActivityLauncher.launch(
            context = context,
            packageName = pkg,
            activityName = activity
        ) { success ->
            if (!success) {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (autoInputEnter) {
                scheduleAutoInput(query, initialDelayMs = 700L, retryDelaysMs = listOf(600L, 900L))
            }
        }
        return true
    }

    private fun scheduleAutoInput(
        query: String,
        initialDelayMs: Long = 600L,
        retryDelaysMs: List<Long> = listOf(500L, 800L, 1200L)
    ) {
        val delays = buildList {
            add(initialDelayMs)
            var accumulated = initialDelayMs
            retryDelaysMs.forEach { step ->
                accumulated += step
                add(accumulated)
            }
        }
        var filled = false
        delays.forEach { delayMs ->
            mainHandler.postDelayed({
                if (filled) return@postDelayed
                val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return@postDelayed
                val root = service.rootInActiveWindow ?: return@postDelayed
                val result = OtpAutoInputNodeHelper.performAutoInput(
                    root = root,
                    code = query,
                    autoEnter = true
                )
                if (result.success) {
                    filled = true
                }
                @Suppress("DEPRECATION")
                root.recycle()
            }, delayMs)
        }
    }

    private fun formatQuery(template: String, query: String): String {
        val encoded = Uri.encode(query)
        return template
            .replace("%q", encoded)
            .replace("%s", encoded)
    }

    private fun isIntentUri(url: String): Boolean =
        url.startsWith("intent://") || url.startsWith("intent:")

    private fun buildViewIntent(url: String, packageName: String? = null): Intent? {
        if (isIntentUri(url)) {
            return parseIntentUri(url, packageName)
        }
        return Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            packageName?.takeIf { it.isNotBlank() }?.let { setPackage(it) }
        }
    }

    private fun parseIntentUri(uri: String, overridePackage: String? = null): Intent? =
        runCatching {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (component == null) {
                    overridePackage?.takeIf { it.isNotBlank() }?.let { setPackage(it) }
                }
            }
        }.getOrNull()

    private enum class LaunchTarget { DIRECT, PRIVILEGED, UNRESOLVED }

    private class ResolvedLink(
        val url: String,
        val intent: Intent,
        val target: LaunchTarget,
    )

    /**
     * 解析链接并判断接收方；原样解析不到接收方时，再按「补了 `intent:` 前缀」的形态试一次。
     *
     * 第三方分享出来的快捷方式串常常只给 `kugousc://search#Intent;…;end` 这一段、不写
     * `intent:` 前缀（详见 [IntentUriNormalizer]）。原样发出去会退化成 ACTION_VIEW，
     * 而目标 App 没注册那个自定义 scheme，最后必然「操作失败」；补完前缀才是一份
     * [Intent.parseUri] 认得的 intent URI，能按 component 定点启动。
     *
     * 只在原样解析失败时才补，所以本来就能打开的链接行为不变。
     */
    private fun resolveLink(
        context: Context,
        url: String,
        build: (String) -> Intent?,
    ): ResolvedLink? {
        val direct = build(url) ?: return null
        val directTarget = resolveLaunchTarget(context, direct)
        if (directTarget != LaunchTarget.UNRESOLVED) return ResolvedLink(url, direct, directTarget)
        val repaired = IntentUriNormalizer.normalize(url)
        if (repaired == url) return ResolvedLink(url, direct, directTarget)
        val repairedIntent = build(repaired) ?: return ResolvedLink(url, direct, directTarget)
        return ResolvedLink(repaired, repairedIntent, resolveLaunchTarget(context, repairedIntent))
    }

    /**
     * 显式组件：存在且导出就直接启动；未导出交给特权链路。
     * 隐式 Intent：先确认有 Activity 能接，避免走到 trampoline 后才静默失败。
     */
    private fun resolveLaunchTarget(context: Context, intent: Intent): LaunchTarget {
        val component = intent.component
        if (component != null) {
            val info = runCatching {
                context.packageManager.getActivityInfo(
                    component,
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }.getOrNull() ?: return LaunchTarget.UNRESOLVED
            return if (info.exported || component.packageName == context.packageName) {
                LaunchTarget.DIRECT
            } else {
                LaunchTarget.PRIVILEGED
            }
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            LaunchTarget.DIRECT
        } else {
            LaunchTarget.UNRESOLVED
        }
    }

    private fun launchFailed(context: Context): Boolean {
        Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun launchPrivilegedIntent(
        context: Context,
        intent: Intent,
        autoInputQuery: String?,
    ): Boolean {
        if (!TaskManagerUtil.hasPermission()) {
            Toast.makeText(context, PrivilegeUiStrings.privilegedAccessRequiredRes(), Toast.LENGTH_LONG).show()
            return false
        }
        NonExportedActivityLauncher.launch(context, intent) { success ->
            if (!success) {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            } else {
                autoInputQuery?.takeIf { it.isNotBlank() }?.let {
                    scheduleAutoInput(it, initialDelayMs = 700L, retryDelaysMs = listOf(600L, 900L))
                }
            }
        }
        return true
    }

    private fun startActivity(
        context: Context,
        intent: Intent,
        settings: AppSettings,
        longPressTriggered: Boolean,
        useTrampoline: Boolean = false,
        fullscreenOverride: Boolean? = null
    ): Boolean {
        return runCatching {
            val fullscreen = fullscreenOverride ?: settings.shouldLaunchFullscreen(longPressTriggered)
            if (useTrampoline) {
                val freeform = !fullscreen && settings.freeWindowEnabled
                // vivo / OriginOS：小窗由系统的「小窗分享」开出，ActivityOptions 在这里不起作用，
                // 改由 trampoline（已经有前台上下文）在内部发这次分享。
                val shareProxy = freeform && settings.resolvedFreeWindowMode() == FreeWindowMode.ORIGINOS
                val launchOptions = if (freeform && !shareProxy) {
                    FreeWindowLauncher.launchOptionsBundle(context, settings)
                } else {
                    null
                }
                val launchIntent = LaunchTrampolineActivity.createIntent(
                    context = context,
                    launchIntent = intent,
                    launchOptions = launchOptions,
                    shareProxy = shareProxy,
                )
                context.startActivity(launchIntent)
            } else {
                FreeWindowLauncher.launch(context, intent, settings, fullscreen)
            }
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }
}

/**
 * 文字引擎的执行方式：所有"拿文字去执行引擎"的入口都必须按这里分派。
 *
 * 单独抽成纯函数是为了能直接单测——漏掉一个分支的表现就是"点了/拖了没反应"，
 * 而这种错误编译器不会报（[SearchEngineLauncher.launch] 返回 Boolean，漏了也是合法返回）。
 */
internal enum class TextLaunchKind { OPEN, SHARE_TEXT, UNSUPPORTED }

internal fun textLaunchKind(type: SearchEngineType): TextLaunchKind = when (type) {
    SearchEngineType.SHARE_TO_APP -> TextLaunchKind.SHARE_TEXT
    SearchEngineType.SHARE_IMAGE_TO_APP,
    SearchEngineType.SHARE_FILE_TO_APP,
    -> TextLaunchKind.UNSUPPORTED
    SearchEngineType.DIRECT_LINK,
    SearchEngineType.EXTERN_JUMP_LINK,
    SearchEngineType.JUMP_TO_ACTIVITY,
    -> TextLaunchKind.OPEN
}
