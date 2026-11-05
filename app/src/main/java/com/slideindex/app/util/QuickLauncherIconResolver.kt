package com.slideindex.app.util

import android.content.Context
import androidx.core.graphics.drawable.toDrawable
import android.graphics.drawable.Drawable
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ManagedShortcutIconResolver
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver

object QuickLauncherIconResolver {

    /**
     * 进程内图标缓存。
     *
     * 必须缓存的原因：魅族/小米等 OEM 的 [PackageManager.getApplicationIcon] 会做主题图标重建
     * （Flyme: `FlymeThemeHelper.cropTransparentSpace` 逐像素 `Bitmap.getPixel`），单个图标可达秒级
     * —— 编辑器一次要画几十个图标，不缓存就是"等一分钟"（真机实测）。
     */
    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 24).toInt().coerceIn(48, 256)
    ) {}

    fun clear() {
        bitmapCache.evictAll()
    }

    fun iconBitmap(
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        size: Int = 128,
        context: Context? = null,
        actionIconTintArgb: Int = Color.WHITE,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
    ): Bitmap? {
        val cacheKey = buildString {
            append(item.type.id).append('\u0000').append(item.payload).append('\u0000').append(item.label)
            append('\u0000').append(size).append('\u0000').append(actionIconTintArgb)
            append('\u0000').append(activityShortcuts.size).append(':').append(activityShortcuts.hashCode())
            append('\u0000').append(shellCommands.size).append(':').append(shellCommands.hashCode())
            // 图标覆盖变更后必须换 key，否则会一直命中改图标前的旧缓存
            append('\u0000').append(com.slideindex.app.ui.GlobalIconStore.revision)
            append('\u0000').append(com.slideindex.app.ui.IconPlateModeState.mode)
            append('\u0000').append(com.slideindex.app.ui.IconPlateModeState.lastResolvedDark)
        }
        bitmapCache.get(cacheKey)?.let { return it }
        val bitmap = resolveIconBitmap(
            item = item,
            appsByPackage = appsByPackage,
            size = size,
            context = context,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
        )
        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun resolveIconBitmap(
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        size: Int,
        context: Context?,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ): Bitmap? {
        // 全局图标覆盖优先（扩展 → 图标库）：面板格子是 Canvas 绘制，这里先转 Bitmap
    if (item.type == QuickLauncherItemType.ACTION && context != null) {
        QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
            com.slideindex.app.ui.ActionIconBitmap.load(
                context = context,
                action = action,
                sizePx = size,
                tintArgb = android.graphics.Color.WHITE,
                withPlate = true,
            )?.let { return it }
        }
    }

    if (item.type == QuickLauncherItemType.FOLDER) {
            return renderFolderBitmap(
                item = item,
                appsByPackage = appsByPackage,
                size = size.coerceAtLeast(1),
                context = context,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
            )
        }
        if (item.type == QuickLauncherItemType.ACTION &&
            shouldUseGestureVectorIcon(item, shellCommands)
        ) {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
            return GestureActionIconBitmap.get(
                action = action,
                sizePx = size.coerceAtLeast(1),
                tintArgb = Color.WHITE,
                outlined = true,
                withPlate = true,
            )
        }
        val drawable = iconDrawable(item, appsByPackage, context, activityShortcuts, shellCommands) ?: return null
        return iconBitmapFromDrawable(drawable, size)
    }

    fun iconDrawable(
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        context: Context? = null,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
    ): Drawable? {
        // 全局图标覆盖优先（蜂窝启动器等走 Drawable 路径）
        if (item.type == QuickLauncherItemType.ACTION && context != null) {
            QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                com.slideindex.app.ui.ActionIconBitmap.load(
                    context = context,
                    action = action,
                    sizePx = 96,
                    tintArgb = android.graphics.Color.WHITE,
                    withPlate = true,
                )?.let { bmp ->
                    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
                }
            }
        }
        return when (item.type) {
            QuickLauncherItemType.FOLDER -> {
                val bmp = renderFolderBitmap(
                    item = item,
                    appsByPackage = appsByPackage,
                    size = 128,
                    context = context,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                )
                bmp.toDrawable(context?.resources ?: android.content.res.Resources.getSystem())
            }
            QuickLauncherItemType.APP -> {
                val packageName = item.appTarget?.packageName ?: item.payload
                // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）
                if (context != null) {
                    com.slideindex.app.ui.AppIconOverride
                        .drawable(context, packageName, 128)
                        ?.let { return it }
                }
                getIconSafe(appsByPackage[packageName], context)
                    ?: getIconSafe(packageName, context)
            }
            QuickLauncherItemType.SHORTCUT -> {
                context?.let { ctx ->
                    ManagedShortcutIconResolver.drawableForQuickItem(ctx, item, activityShortcuts)
                        ?.let { return it }
                }
                shortcutDrawable(item.payload, context)
            }
            QuickLauncherItemType.ACTION -> {
                val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
                when (action) {
                    is GestureAction.LaunchApp -> getIconSafe(appsByPackage[action.packageName], context)
                    is GestureAction.LaunchShortcut -> {
                        context?.let { ctx ->
                            ManagedShortcutIconResolver.drawableForLaunchShortcut(
                                ctx,
                                action,
                                activityShortcuts,
                            )?.let { return it }
                        }
                        gestureShortcutDrawable(action.payloadKey, context)
                    }
                    is GestureAction.ExecuteShellCommand -> {
                        val matched = ShellCommandIconResolver.findForCommandLine(action.command, shellCommands)
                        if (matched != null && context != null) {
                            ShellCommandIconResolver.resolveBitmap(context, matched, 128)?.let { bitmap ->
                                return bitmap.toDrawable(context.resources)
                            }
                        }
                        gestureActionDrawable(action, context)
                    }
                    else -> gestureActionDrawable(action, context)
                }
            }
            QuickLauncherItemType.WIDGET -> null
        }
    }

    private fun getIconSafe(packageName: String, context: Context?): Drawable? {
        if (context == null) return null
        return runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private fun getIconSafe(appInfo: AppInfo?, context: Context?): Drawable? {
        if (appInfo == null || context == null) return null
        return runCatching { context.packageManager.getApplicationIcon(appInfo.packageName) }.getOrNull()
    }

    /**
     * 宿主应用图标：**先查全局图标覆盖**（扩展 → 图标库 → 应用图标），再退回系统图标。
     *
     * 快捷方式自己的专属图标由 [ManagedShortcutIconResolver] 负责；这里只处理「只能按包名
     * 取图」的兜底路径。它必须和 APP 条目一样跟随图标库，否则快捷方式图标永远停在原图。
     */
    private fun hostAppDrawable(packageName: String, context: Context?): Drawable? {
        if (packageName.isBlank()) return null
        val ctx = context ?: return null
        com.slideindex.app.ui.AppIconOverride.drawable(ctx, packageName, 128)?.let { return it }
        return runCatching { ctx.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    fun shouldUseGestureVectorIcon(
        item: QuickLauncherItem,
        shellCommands: List<ShellCommand> = emptyList(),
    ): Boolean {
        if (item.type != QuickLauncherItemType.ACTION) return false
        val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return false
        if (action is GestureAction.ExecuteShellCommand &&
            ShellCommandIconResolver.findForCommandLine(action.command, shellCommands) != null
        ) {
            return false
        }
        return action !is GestureAction.LaunchApp && action !is GestureAction.LaunchShortcut
    }

    private fun gestureActionDrawable(action: GestureAction, context: Context?): Drawable? {
        val ctx = context ?: return null
        val bitmap = GestureActionIconBitmap.get(
            action = action,
            sizePx = 128,
            tintArgb = Color.WHITE,
            outlined = true,
            withPlate = true,
        )
        return bitmap.toDrawable(ctx.resources)
    }

    private fun shortcutDrawable(
        payload: String,
        context: Context?,
    ): Drawable? {
        QuickLauncherItemCodec.resolveHostPackageName(payload) { uri ->
            KnownAppShortcuts.packageForIntentUri(uri)
        }?.let { packageName ->
            hostAppDrawable(packageName, context)?.let { return it }
        }
        if (payload.startsWith("c:")) {
            val componentFlat = payload.removePrefix("c:").substringBefore('\u001D')
            packageFromComponentFlat(componentFlat)?.let { hostAppDrawable(it, context) }?.let { return it }
        }
        context?.let { ctx ->
            resolvePackageFromIntentPayload(ctx, payload)?.let { packageName ->
                hostAppDrawable(packageName, context)?.let { return it }
            }
        }
        return null
    }

    private fun gestureShortcutDrawable(
        payloadKey: String,
        context: Context?,
    ): Drawable? = when (val decoded = GestureShortcutPayload.decode(payloadKey)) {
        is GestureShortcutPayload.Decoded.Dynamic ->
            hostAppDrawable(decoded.packageName, context)
        is GestureShortcutPayload.Decoded.Component ->
            packageFromComponentFlat(decoded.componentFlat)?.let { hostAppDrawable(it, context) }
        is GestureShortcutPayload.Decoded.IntentShortcut ->
            resolveIntentShortcutDrawable(decoded.intentUri, context)
        is GestureShortcutPayload.Decoded.IntentsShortcut ->
            decoded.intentUris.firstNotNullOfOrNull { uri ->
                resolveIntentShortcutDrawable(uri, context)
            }
        null -> null
    }

    private fun resolveIntentShortcutDrawable(
        intentUri: String,
        context: Context?,
    ): Drawable? {
        QuickLauncherItemCodec.resolveHostPackageName(
            "${QuickLauncherItemCodec.INTENT_PAYLOAD_PREFIX}$intentUri",
        ) { uri -> KnownAppShortcuts.packageForIntentUri(uri) }?.let { hostAppDrawable(it, context) }?.let { return it }
        context?.let { ctx ->
            packageFromIntentUri(ctx, intentUri)?.let { hostAppDrawable(it, context) }?.let { return it }
        }
        return null
    }

    private fun resolvePackageFromIntentPayload(context: Context, payload: String): String? {
        QuickLauncherItemCodec.parseIntentPayload(payload)?.let { uri ->
            packageFromIntentUri(context, uri)?.let { return it }
        }
        QuickLauncherItemCodec.parseIntentListPayload(payload)?.firstOrNull()?.let { uri ->
            packageFromIntentUri(context, uri)?.let { return it }
        }
        return null
    }

    private fun packageFromIntentUri(context: Context, intentUri: String): String? {
        val intent = runCatching {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        return context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.takeIf { it.isNotBlank() }
    }

    private fun packageFromComponentFlat(componentFlat: String): String? =
        componentFlat.substringBefore('/').trim().takeIf { it.isNotBlank() }

    private fun iconBitmapFromDrawable(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val mutate = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        mutate.setBounds(0, 0, size, size)
        mutate.draw(canvas)
        return bitmap
    }

    private fun renderFolderBitmap(
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        size: Int,
        context: Context?,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val children = item.folderItems()
        val corner = size * 0.22f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 48, 48, 54)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = (size * 0.02f).coerceAtLeast(1f)
        }
        val plateRect = RectF(
            strokePaint.strokeWidth / 2f,
            strokePaint.strokeWidth / 2f,
            size - strokePaint.strokeWidth / 2f,
            size - strokePaint.strokeWidth / 2f,
        )
        canvas.drawRoundRect(plateRect, corner, corner, bgPaint)
        canvas.drawRoundRect(plateRect, corner, corner, strokePaint)

        if (children.isEmpty()) {
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = (size * 0.05f).coerceAtLeast(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val w = size * 0.44f
            val h = size * 0.34f
            val cx = size / 2f
            val cy = size / 2f
            val l = cx - w / 2f
            val t = cy - h / 2f
            val r = cx + w / 2f
            val b = cy + h / 2f
            val tabW = w * 0.38f
            val tabH = h * 0.25f
            val folderPath = Path().apply {
                moveTo(l, t + tabH)
                lineTo(l + tabW * 0.8f, t + tabH)
                lineTo(l + tabW, t)
                lineTo(r - corner * 0.5f, t)
                quadTo(r, t, r, t + corner * 0.5f)
                lineTo(r, b - corner * 0.5f)
                quadTo(r, b, r - corner * 0.5f, b)
                lineTo(l + corner * 0.5f, b)
                quadTo(l, b, l, b - corner * 0.5f)
                close()
            }
            canvas.drawPath(folderPath, iconPaint)
            return bitmap
        }

        val miniItems = children.take(4)
        val miniSize = size * 0.36f
        val gap = (size - miniSize * 2f) / 3f
        val miniClipPath = Path()
        val miniRect = RectF()
        val miniCorner = miniSize * 0.22f
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (i in miniItems.indices) {
            val child = miniItems[i]
            val col = i % 2
            val row = i / 2
            val left = gap + col * (miniSize + gap)
            val top = gap + row * (miniSize + gap)
            miniRect.set(left, top, left + miniSize, top + miniSize)

            val childBmp = iconBitmap(
                item = child,
                appsByPackage = appsByPackage,
                size = miniSize.toInt().coerceAtLeast(16),
                context = context,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
            )

            miniClipPath.reset()
            miniClipPath.addRoundRect(miniRect, miniCorner, miniCorner, Path.Direction.CW)
            if (childBmp != null) {
                canvas.withClip(miniClipPath) {
                    drawBitmap(childBmp, null, miniRect, bmpPaint)
                }
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(120, 100, 100, 110)
                }
                canvas.drawRoundRect(miniRect, miniCorner, miniCorner, placeholderPaint)
            }
        }
        return bitmap
    }
}
