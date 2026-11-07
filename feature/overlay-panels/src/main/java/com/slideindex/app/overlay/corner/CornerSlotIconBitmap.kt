package com.slideindex.app.overlay.corner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver

object CornerSlotIconBitmap {
    private val appIconCache = object : LruCache<String, Bitmap>(64) {}

    fun clear() {
        appIconCache.evictAll()
    }

    fun get(
        context: Context,
        action: GestureAction,
        sizePx: Int,
        tintArgb: Int,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
        appRepository: AppRepository? = null,
    ): Bitmap {
        // 全局图标覆盖优先（扩展 → 图标库）
        if (action is GestureAction.LaunchApp) {
            val cacheKey = "app:${action.packageName}:${action.cloneUserId}:$sizePx"
            appIconCache.get(cacheKey)?.let { return it }
            resolveAppIconBitmap(action.packageName, sizePx, appRepository, context)?.let { bitmap ->
                val decorated = com.slideindex.app.util.CloneBadge.decorate(bitmap, action.cloneUserId > 0) ?: bitmap
                appIconCache.put(cacheKey, decorated)
                return decorated
            }
        }
        if (action is GestureAction.LaunchShortcut) {
            val managedKey = "managed:${action.payloadKey}:$sizePx:${activityShortcuts.map { it.identityKey() to it.iconPath }.hashCode()}"
            appIconCache.get(managedKey)?.let { return it }
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.managedShortcutBitmap?.invoke(
                context,
                action,
                sizePx,
                activityShortcuts,
            )?.let { bitmap ->
                appIconCache.put(managedKey, bitmap)
                return bitmap
            }
            val hostPackage = com.slideindex.app.overlay.panels.PanelRuntimeHooks.managedShortcutHostPackage?.invoke(action.payloadKey)
            if (!hostPackage.isNullOrBlank()) {
                val cacheKey = "host:$hostPackage:$sizePx"
                appIconCache.get(cacheKey)?.let { return it }
                resolveAppIconBitmap(hostPackage, sizePx, appRepository, context)?.let { bitmap ->
                    appIconCache.put(cacheKey, bitmap)
                    return bitmap
                }
            }
        }
        if (action is GestureAction.ExecuteShellCommand) {
            ShellCommandIconResolver.findForCommandLine(action.command, shellCommands)?.let { matched ->
                val cacheKey =
                    "shell:${matched.id}:${matched.iconType}:${matched.iconPath.orEmpty()}:${matched.textIcon.orEmpty()}:$sizePx"
                appIconCache.get(cacheKey)?.let { return it }
                ShellCommandIconResolver.resolveBitmap(context, matched, sizePx)?.let { bitmap ->
                    appIconCache.put(cacheKey, bitmap)
                    return bitmap
                }
            }
        }
        return com.slideindex.app.overlay.panels.PanelRuntimeHooks.actionIconBitmapFull?.invoke(
            action,
            sizePx,
            android.graphics.Color.WHITE,
            true,
            true,
            1, // CIRCLE
        ) ?: Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    private fun resolveAppIconBitmap(
        packageName: String,
        sizePx: Int,
        appRepository: AppRepository?,
        context: Context,
    ): Bitmap? {
        if (packageName.isBlank()) return null
        appRepository?.peekLaunchIconBitmap(packageName, sizePx)?.let { return it }
        appRepository?.peekLaunchIconDrawable(packageName)?.let { return drawableToBitmap(it, sizePx) }
        val drawable = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        return drawableToBitmap(drawable, sizePx)
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val icon = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        return bitmap
    }
}
