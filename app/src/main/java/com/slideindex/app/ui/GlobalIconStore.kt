package com.slideindex.app.ui

import android.content.Context
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * **全局图标覆盖**：按「动作类型」或「应用包名」指定图标。
 *
 * 与旧版（按单个动作配置存）不同，这里是全局的：
 * - 给某个动作类型（如"屏幕绘制"）统一指定图标 → 所有绑定该类型的位置都显示它
 * - 给某个应用指定图标 → 该应用在任何列表里都显示它
 *
 * 存储仍是 SharedPreferences（纯展示层覆盖，不影响动作载荷）。
 */
object GlobalIconStore {

    /** 变更计数器：图标设置后自增，读取方靠它触发重组（SharedPreferences 本身不是 Compose 状态）。 */
    var revision by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private val listeners = mutableListOf<() -> Unit>()

    /**
     * 图标变更回调（悬浮窗 / Canvas 渲染刷新用）。
     *
     * `revision` 只对 Compose 有效；面板是 `View` + `Canvas`，没有人观察它，
     * 所以改完图标必须主动通知它们重绘，否则要退出再进才看得到。
     */
    fun addIconChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeIconChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun notifyIconChanged() {
        AppIconOverride.clear()
        // 各渲染器的 LruCache 虽然 key 里带了 revision，但这里顺手清掉避免白占内存
        runCatching { com.slideindex.app.util.QuickLauncherIconResolver.clear() }
        runCatching { com.slideindex.app.overlay.ringlauncher.RingLauncherSlotIconBitmap.clear() }
        runCatching { com.slideindex.app.overlay.corner.CornerSlotIconBitmap.clear() }
        runCatching { com.slideindex.app.overlay.quickwheel.QuickWheelIconResolver.clearBitmapCache() }
        listeners.toList().forEach { runCatching(it) }
    }

    /**
     * 外部改了图标来源（例如切换全局图标包）时，主动通知所有渲染方清缓存 + 重绘。
     *
     * 与 [setAppIcon] / [setActionIcon] 走同一条通知链，只是不写覆盖表。
     */
    fun notifyIconSourceChanged() = notifyIconChanged()

    /** 从备份恢复覆盖表后调用：revision 自增（Compose 失效）+ 清位图缓存 + 通知面板重绘。 */
    fun refreshAfterImport() {
        revision++
        notifyIconChanged()
    }

    const val PREFS = "global_icon_overrides"
    private const val ACTION_PREFIX = "action:"
    private const val APP_PREFIX = "app:"
    private const val PANEL_PREFIX = "panel:"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun actionIcon(context: Context, typeId: String): String? =
        prefs(context).getString(ACTION_PREFIX + typeId, null)?.takeIf { it.isNotBlank() }

    fun setActionIcon(context: Context, typeId: String, iconKey: String?) {
        val editor = prefs(context).edit()
        if (iconKey.isNullOrBlank()) editor.remove(ACTION_PREFIX + typeId)
        else editor.putString(ACTION_PREFIX + typeId, iconKey)
        editor.apply()
        revision++
        notifyIconChanged()
    }

    fun appIcon(context: Context, packageName: String): String? =
        prefs(context).getString(APP_PREFIX + packageName, null)?.takeIf { it.isNotBlank() }

    fun setAppIcon(context: Context, packageName: String, iconKey: String?) {
        val editor = prefs(context).edit()
        if (iconKey.isNullOrBlank()) editor.remove(APP_PREFIX + packageName)
        else editor.putString(APP_PREFIX + packageName, iconKey)
        editor.apply()
        revision++
        notifyIconChanged()
    }

    /**
     * 「启动面板」自己的图标（key = 面板 id）。
     *
     * 与动作类型/应用覆盖同一个 prefs：备份、`revision` 失效、各渲染器清缓存都走现成的链。
     * 只在「打开快速启动器」这个动作上生效，优先级高于动作类型图标（见 [ActionIconKeyResolver]）。
     */
    fun panelIcon(context: Context, panelId: String): String? {
        if (panelId.isBlank()) return null
        return prefs(context).getString(PANEL_PREFIX + panelId, null)?.takeIf { it.isNotBlank() }
    }

    fun setPanelIcon(context: Context, panelId: String, iconKey: String?) {
        if (panelId.isBlank()) return
        val editor = prefs(context).edit()
        if (iconKey.isNullOrBlank()) editor.remove(PANEL_PREFIX + panelId)
        else editor.putString(PANEL_PREFIX + panelId, iconKey)
        editor.apply()
        revision++
        notifyIconChanged()
    }

    fun allActionIcons(context: Context): Map<String, String> =
        prefs(context).all.entries
            .filter { it.key.startsWith(ACTION_PREFIX) && it.value is String }
            .associate { it.key.removePrefix(ACTION_PREFIX) to (it.value as String) }

    fun allAppIcons(context: Context): Map<String, String> =
        prefs(context).all.entries
            .filter { it.key.startsWith(APP_PREFIX) && it.value is String }
            .associate { it.key.removePrefix(APP_PREFIX) to (it.value as String) }
}

/**
 * 悬浮窗里动作图标底板的当前模式。
 *
 * 渲染发生在 Canvas/非 Compose 场景，拿不到 Compose 状态，所以这里放一个进程级当前值：
 * 设置保存时更新，渲染时用 [isDark] 判断该用深色还是浅色底板。
 */
object IconPlateModeState {
    private val listeners = mutableListOf<() -> Unit>()

    /** 注册模式变更回调（悬浮窗刷新用）。 */
    fun addPlateModeListener(listener: () -> Unit) {
        listeners += listener
    }

    @Volatile
    var mode: com.slideindex.app.settings.IconPlateMode = com.slideindex.app.settings.IconPlateMode.SYSTEM_DARK
        set(value) {
            field = value
            appContext?.let { isDark(it) }
            com.slideindex.app.util.GestureActionIconBitmap.clear()
            com.slideindex.app.util.QuickLauncherIconResolver.clear()
            com.slideindex.app.overlay.ringlauncher.RingLauncherSlotIconBitmap.clear()
            com.slideindex.app.overlay.corner.CornerSlotIconBitmap.clear()
            listeners.toList().forEach { runCatching(it) }
        }

    /** 最近一次解析出的明暗（渲染侧没有 context 时用它）。 */
    @Volatile
    var lastResolvedDark: Boolean = true
        private set

    /** 首次调用时缓存 Application context，之后渲染侧可以随时重新解析。 */
    @Volatile
    private var appContext: android.content.Context? = null

    /** 启动时调用一次，给渲染侧准备 context（否则 currentDark 只能保守地当深色）。 */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        isDark(context)
    }

    /** 渲染侧用：每次调用都重新解析（不依赖上次缓存的值）。 */
    fun currentDark(): Boolean = appContext?.let { isDark(it) }
        ?: (mode != com.slideindex.app.settings.IconPlateMode.LIGHT)

    fun isDark(context: android.content.Context): Boolean = run {
        appContext = context.applicationContext
        when (mode) {
            com.slideindex.app.settings.IconPlateMode.DARK -> true
            com.slideindex.app.settings.IconPlateMode.LIGHT -> false
            com.slideindex.app.settings.IconPlateMode.SYSTEM_DARK -> {
                val night = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                night == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }.also { lastResolvedDark = it }
}
