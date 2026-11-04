package com.slideindex.app.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.core.graphics.createBitmap
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommandIconResolver
import com.slideindex.app.launcher.QuickLauncherItemType

/**
 * 仅当快捷命中「应用内直达」目录时，才解析原生/自定义快捷图标；
 * 未命中时返回 null，由调用方回退到 App 图标 + 角标。
 */
object ManagedShortcutIconResolver {
    fun drawableForQuickItem(
        context: Context,
        item: QuickLauncherItem,
        catalog: List<ActivityShortcut>
    ): Drawable? {
        if (item.type != QuickLauncherItemType.SHORTCUT) return null
        val managed = catalog.findForQuickLauncherItem(item) ?: return null
        return drawableForManaged(context, managed)
    }

    fun drawableForLaunchShortcut(
        context: Context,
        action: GestureAction.LaunchShortcut,
        catalog: List<ActivityShortcut>
    ): Drawable? {
        val managed = catalog.findForLaunchShortcut(action.payloadKey) ?: return null
        return drawableForManaged(context, managed)
    }

    fun bitmapForLaunchShortcut(
        context: Context,
        action: GestureAction.LaunchShortcut,
        catalog: List<ActivityShortcut>,
        sizePx: Int
    ): Bitmap? {
        val drawable = drawableForLaunchShortcut(context, action, catalog) ?: return null
        return drawableToBitmap(drawable, sizePx)
    }

    fun hostPackageForLaunchShortcut(payloadKey: String): String? {
        val decoded = GestureShortcutPayload.decode(payloadKey)
        if (decoded is GestureShortcutPayload.Decoded.IntentShortcut &&
            ActivityShortcutShellSupport.isShellUri(decoded.intentUri)
        ) {
            return ActivityShortcutShellSupport.HOST_PACKAGE
        }
        return when (decoded) {
            is GestureShortcutPayload.Decoded.Dynamic -> decoded.packageName
            is GestureShortcutPayload.Decoded.Component ->
                decoded.componentFlat.substringBefore('/').takeIf { it.isNotBlank() }
            is GestureShortcutPayload.Decoded.IntentShortcut ->
                GestureShortcutPayload.intentHostPackage(payloadKey)
                    ?: packageFromIntentUri(decoded.intentUri)
            is GestureShortcutPayload.Decoded.IntentsShortcut ->
                decoded.intentUris.firstNotNullOfOrNull { packageFromIntentUri(it) }
            null -> null
        }
    }

    fun drawableForManaged(context: Context, shortcut: ActivityShortcut): Drawable? {
        ActivityShortcutShellSupport.shellCommandFrom(shortcut)?.let { shell ->
            ShellCommandIconResolver.resolveBitmap(context, shell, 96)?.let { bitmap ->
                return bitmap.toDrawable(context.resources)
            }
        }
        shortcut.iconPath?.let { path ->
            ShortcutIconStorage.loadBitmap(context, path)?.let { bitmap ->
                return bitmap.toDrawable(context.resources)
            }
        }
        // 应用快捷方式优先用系统快捷方式图标；INTENT 类型也可能带 shortcutId（启动兜底）。
        if (shortcut.isAppShortcut) {
            dynamicShortcutIcon(context, shortcut.packageName, shortcut.shortcutId)?.let { return it }
        }
        return when (shortcut.kind) {
            ActivityShortcutKind.COMPONENT -> activityIcon(context, shortcut.packageName, shortcut.activityClassName)
            ActivityShortcutKind.DYNAMIC -> dynamicShortcutIcon(context, shortcut.packageName, shortcut.shortcutId)
                ?: appIcon(context, shortcut.packageName)
            ActivityShortcutKind.INTENT -> intentTargetIcon(context, shortcut)
                ?: appIcon(context, shortcut.packageName)
        } ?: appIcon(context, shortcut.packageName)
    }

    private fun activityIcon(context: Context, packageName: String, className: String): Drawable? {
        if (packageName.isBlank() || className.isBlank()) return null
        val component = ComponentName(packageName, className)
        return runCatching {
            val info = context.packageManager.getActivityInfo(component, 0)
            info.loadIcon(context.packageManager)
        }.getOrNull()
    }

    private fun dynamicShortcutIcon(context: Context, packageName: String, shortcutId: String): Drawable? {
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return null
        return runCatching {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setShortcutIds(listOf(shortcutId))
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val info = launcherApps.getShortcuts(query, Process.myUserHandle())?.firstOrNull() ?: return null
            launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
        }.getOrNull()
    }

    private fun intentTargetIcon(context: Context, shortcut: ActivityShortcut): Drawable? {
        val uri = shortcut.intentUris.firstOrNull() ?: return null
        val intent = runCatching {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        val component = intent.component
        if (component != null) {
            activityIcon(context, component.packageName, component.className)?.let { return it }
        }
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        return runCatching { resolved.loadIcon(context.packageManager) }.getOrNull()
    }

    private fun appIcon(context: Context, packageName: String): Drawable? {
        if (packageName.isBlank()) return null
        // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）：快捷方式回退到宿主应用图标时也要用覆盖后的图
        return com.slideindex.app.ui.AppIconOverride.drawable(context, packageName, 128)
            ?: runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private fun packageFromIntentUri(uri: String): String? = runCatching {
        val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        intent.`package`?.takeIf { it.isNotBlank() }
            ?: intent.component?.packageName?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val icon = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        icon.setBounds(0, 0, size, size)
        icon.draw(canvas)
        return bitmap
    }
}
