package com.slideindex.app.macro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.ui.AppIconOverride

/**
 * 「运行宏」条目的图标：显示宏目标 App 的图标，而不是通用的播放键。
 *
 * 宏的绑定只保存 macroId（见 `GestureAction.RunMacro`），快速启动器 / 圆环 / 蜂窝
 * 这些格子拿到的条目里没有目标包名，只能按宏表反查。宏表是独立 prefs，
 * 每格每帧都解析整份 JSON 太贵，所以按 [MacroStore.revision] 缓存一份 id → 包名：
 * 宏一改（目标 App / 增删），代次变化，下一次读取自动重建。
 */
object MacroTargetIconResolver {

    private var cachedRevision = -1
    private var cachedTargets: Map<String, String> = emptyMap()

    /**
     * 纯函数部分：宏 → 目标包名。
     *
     * 目标可能带分身后缀（`包名\u0002userId`，见 [AppTarget]），这里只要包名；
     * 没选目标 App（新建占位宏）返回 null。
     */
    internal fun targetPackageOf(macro: Macro?): String? =
        macro?.targetPackage
            ?.let { AppTarget.parse(it).packageName }
            ?.takeIf { it.isNotBlank() }

    /** 宏 id → 目标 App 包名；宏不存在 / 没选目标时返回 null。 */
    @Synchronized
    fun targetPackageFor(context: Context, macroId: String): String? {
        if (macroId.isBlank()) return null
        val revision = MacroStore.revision
        if (revision != cachedRevision) {
            val targets = HashMap<String, String>()
            MacroStore.all(context).forEach { macro ->
                targetPackageOf(macro)?.let { targets[macro.id] = it }
            }
            cachedTargets = targets
            cachedRevision = revision
        }
        return cachedTargets[macroId]
    }

    /**
     * 宏目标 App 的图标。
     *
     * 与「宏列表」那一行同一条优先级：全局图标覆盖（扩展 → 图标库 → 应用图标）优先，
     * 再退回系统应用图标；目标 App 被卸载时返回 null，调用方回退通用动作图标。
     */
    fun drawable(context: Context, macroId: String, sizePx: Int): Drawable? {
        val packageName = targetPackageFor(context, macroId) ?: return null
        return AppIconOverride.drawable(context, packageName, sizePx)
            ?: runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    fun bitmap(context: Context, macroId: String, sizePx: Int): Bitmap? {
        val size = sizePx.coerceAtLeast(1)
        return runCatching { drawable(context, macroId, size)?.toBitmap(size, size) }.getOrNull()
    }
}
