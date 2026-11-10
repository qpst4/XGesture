package com.slideindex.app.overlay.ringlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.FvRingLauncherSlotIconOverride
import com.slideindex.app.shell.ShellCommand

object RingLauncherSlotIconBitmap {
    private val cache = object : LruCache<String, Bitmap>(96) {}

    fun clear() {
        cache.evictAll()
    }

    fun get(
        context: Context,
        item: QuickLauncherItem,
        sizePx: Int,
        appsByPackage: Map<String, com.slideindex.app.data.AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        resolvedIcon: Drawable? = null,
        appRepository: AppRepository? = null,
        iconOverride: FvRingLauncherSlotIconOverride? = null,
    ): Bitmap {
        // 图标覆盖变更后必须换 key，否则一直是旧缓存
        val cacheKey = "${item.type}:${item.payload}:${item.label}:$sizePx:${iconOverride?.iconPath}:${iconOverride?.textIcon}:${com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconCacheRevision?.invoke()}:${com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconPlateModeKey?.invoke()}"
        cache.get(cacheKey)?.let { return it }
        val bitmap = resolveBitmap(
            context = context,
            item = item,
            sizePx = sizePx,
            appsByPackage = appsByPackage,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
            resolvedIcon = resolvedIcon,
            appRepository = appRepository,
            iconOverride = iconOverride,
        ) ?: createPlaceholderBitmap(sizePx, item.label.ifBlank { item.payload })
        // 分身黄点不烘进位图：圆环按圆形/圆角裁剪图标，角上的点会被裁掉。
        // 由 RingLauncherRenderer 在裁剪之外按 target.isClone 绘制。
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun resolveBitmap(
        context: Context,
        item: QuickLauncherItem,
        sizePx: Int,
        appsByPackage: Map<String, com.slideindex.app.data.AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        resolvedIcon: Drawable?,
        appRepository: AppRepository?,
        iconOverride: FvRingLauncherSlotIconOverride?,
    ): Bitmap? {
        iconOverride?.takeIf { it.isConfigured() }?.let { override ->
            FvRingLauncherSlotIconResolver.resolveBitmap(
                context = context,
                override = override,
                fallbackLabel = item.label.ifBlank { item.payload },
                sizePx = sizePx,
            )?.let { return it }
        }
        // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）。
        // 必须在 resolvedIcon 之前：圆环的 target.icon 在 show 时就被预填成系统原图，
        // 先走 resolvedIcon 的话覆盖永远不生效（真机实测）。
        if (item.type == QuickLauncherItemType.APP && item.payload.isNotBlank()) {
            val packageName = item.appTarget?.packageName ?: item.payload
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideBitmap
                ?.invoke(context, packageName, sizePx)
                ?.let { return it }
        }
        resolvedIcon?.let { return drawableToBitmap(it, sizePx) }
        if (item.type == QuickLauncherItemType.APP && item.payload.isNotBlank() && appRepository != null) {
            val packageName = item.appTarget?.packageName ?: item.payload
            appRepository.peekLaunchIconBitmap(packageName, sizePx)?.let { return it }
            appRepository.peekLaunchIconDrawable(packageName)?.let { return drawableToBitmap(it, sizePx) }
        }
        return com.slideindex.app.overlay.panels.PanelRuntimeHooks.quickLauncherIconBitmap
            ?.invoke(item, appsByPackage, sizePx, context, activityShortcuts, shellCommands)
    }

    private fun createPlaceholderBitmap(sizePx: Int, label: String): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val rect = android.graphics.RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        val cornerRadius = sizePx * 0.22f

        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(48, 48, 52)
        }
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 255, 255, 255)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = (sizePx * 0.04f).coerceAtLeast(1f)
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        val char = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.44f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(char, sizePx / 2f, baseline, textPaint)
        return bitmap
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val instance = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        instance.setBounds(0, 0, sizePx, sizePx)
        instance.draw(canvas)
        return bitmap
    }
}
