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
import com.slideindex.app.overlay.layout.FvIconShape
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
        shape: FvIconShape = FvIconShape.CIRCLE,
        /** false = 普通动作不烘底板，只返回裸矢量图标（摇杆功能环等场景）。 */
        plateFallback: Boolean = true,
    ): Bitmap {
        // 全局图标覆盖优先（扩展 → 图标库）
        if (action is GestureAction.LaunchApp) {
            val cacheKey = "app:${action.packageName}:${action.cloneUserId}:$sizePx"
            appIconCache.get(cacheKey)?.let { return it }
            resolveAppIconBitmap(action.packageName, sizePx, appRepository, context)?.let { bitmap ->
                // 分身黄点不烘进位图：圆形槽位会把它裁掉，改由渲染器在裁剪外画
                appIconCache.put(cacheKey, bitmap)
                return bitmap
            }
        }
        // 「运行宏」：复用快速启动器的统一解析（宏目标 App 图标），否则只会落到通用播放键
        if (action is GestureAction.RunMacro) {
            val macroKey = "macro:${action.macroId}:$sizePx:${com.slideindex.app.macro.MacroStore.revision}"
            appIconCache.get(macroKey)?.let { return it }
            val item = com.slideindex.app.launcher.QuickLauncherItem.action(action)
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.quickLauncherIconDrawable
                ?.invoke(item, emptyMap(), context, activityShortcuts, shellCommands)
                ?.let { drawable ->
                    val bitmap = drawableToBitmap(drawable, sizePx)
                    appIconCache.put(macroKey, bitmap)
                    return bitmap
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
            tintArgb,
            true,
            plateFallback,
            shape.toPlateShapeOrdinal(),
        ) ?: Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    /**
     * [GestureActionPlateShape] 在 `:app` 里的声明顺序：
     * ROUNDED_RECT / CIRCLE / SQUIRCLE / SQUARE。
     * 面板侧不能依赖 `:app`，所以只在这里维护这一份序号映射。
     */
    private fun FvIconShape.toPlateShapeOrdinal(): Int = when (this) {
        FvIconShape.ROUNDED_RECT -> 0
        FvIconShape.CIRCLE -> 1
        FvIconShape.SQUIRCLE -> 2
        FvIconShape.SQUARE -> 3
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
