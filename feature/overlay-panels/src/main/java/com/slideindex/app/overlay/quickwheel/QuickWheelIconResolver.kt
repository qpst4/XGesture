package com.slideindex.app.overlay.quickwheel

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.QuickWheelIconSource
import com.slideindex.app.settings.QuickWheelSlot
import com.slideindex.app.ui.ThinActionIcons
import java.io.File

/** 图标库条目：仅图标，不附带文案（对齐参考软件的图标网格）。 */
data class QuickWheelLibraryIcon(
    val key: String,
    val vector: ImageVector,
)

/**
 * 轮盘容器图标解析：
 * - [QuickWheelIconSource.ICON_LIBRARY]：复用项目自绘 `ThinActionIcons` 作为内置图标库；
 * - [QuickWheelIconSource.APP_ICON]：取应用启动图标（值 = 包名）；
 * - [QuickWheelIconSource.GALLERY]：读应用私有目录下的图片文件（值 = 文件路径）。
 *
 * 位图结果做 LRU 缓存，避免画布拖拽时反复解码。
 */
object QuickWheelIconResolver {

    /** 内置图标库（`ThinActionIcons` 的常用子集）。 */
    val library: List<QuickWheelLibraryIcon> = listOf(
        QuickWheelLibraryIcon("back", ThinActionIcons.Back),
        QuickWheelLibraryIcon("home", ThinActionIcons.Home),
        QuickWheelLibraryIcon("recents", ThinActionIcons.Recents),
        QuickWheelLibraryIcon("restore", ThinActionIcons.Restore),
        QuickWheelLibraryIcon("close", ThinActionIcons.Close),
        QuickWheelLibraryIcon("apps", ThinActionIcons.Apps),
        QuickWheelLibraryIcon("sort_by_alpha", ThinActionIcons.SortByAlpha),
        QuickWheelLibraryIcon("hive", ThinActionIcons.Hive),
        QuickWheelLibraryIcon("view_carousel", ThinActionIcons.ViewCarousel),
        QuickWheelLibraryIcon("menu_open", ThinActionIcons.MenuOpen),
        QuickWheelLibraryIcon("globe", ThinActionIcons.Globe),
        QuickWheelLibraryIcon("widgets", ThinActionIcons.Widgets),
        QuickWheelLibraryIcon("inventory", ThinActionIcons.Inventory),
        QuickWheelLibraryIcon("quick_tools", Icons.Outlined.Handyman),
        QuickWheelLibraryIcon("free_window", ThinActionIcons.FreeWindow),
        QuickWheelLibraryIcon("code", ThinActionIcons.Code),
        QuickWheelLibraryIcon("play_circle", ThinActionIcons.PlayCircle),
        QuickWheelLibraryIcon("content_paste", ThinActionIcons.ContentPaste),
        QuickWheelLibraryIcon("text_fields", ThinActionIcons.TextFields),
        QuickWheelLibraryIcon("my_location", ThinActionIcons.MyLocation),
        QuickWheelLibraryIcon("touch_app", ThinActionIcons.TouchApp),
        QuickWheelLibraryIcon("gesture", ThinActionIcons.Gesture),
        QuickWheelLibraryIcon("click_passthrough", ThinActionIcons.ClickPassthrough),
        QuickWheelLibraryIcon("flashlight", ThinActionIcons.Flashlight),
        QuickWheelLibraryIcon("brightness", ThinActionIcons.Brightness),
        QuickWheelLibraryIcon("brightness_auto", ThinActionIcons.BrightnessAuto),
        QuickWheelLibraryIcon("volume_up", ThinActionIcons.VolumeUp),
        QuickWheelLibraryIcon("volume_off", ThinActionIcons.VolumeOff),
        QuickWheelLibraryIcon("play_pause", ThinActionIcons.PlayPause),
        QuickWheelLibraryIcon("skip_previous", ThinActionIcons.SkipPrevious),
        QuickWheelLibraryIcon("skip_next", ThinActionIcons.SkipNext),
        QuickWheelLibraryIcon("notifications", ThinActionIcons.Notifications),
        QuickWheelLibraryIcon("quick_settings", ThinActionIcons.QuickSettings),
        QuickWheelLibraryIcon("lock", ThinActionIcons.Lock),
        QuickWheelLibraryIcon("lock_silence_ring", ThinActionIcons.LockSilenceRing),
        QuickWheelLibraryIcon("lock_mute_all", ThinActionIcons.LockMuteAll),
        QuickWheelLibraryIcon("screenshot", ThinActionIcons.Screenshot),
        QuickWheelLibraryIcon("screenshot_region", ThinActionIcons.ScreenshotRegion),
        QuickWheelLibraryIcon("screen_record", ThinActionIcons.ScreenRecord),
        QuickWheelLibraryIcon("screen_off_keep_awake", ThinActionIcons.ScreenOffKeepAwake),
        QuickWheelLibraryIcon("pin", ThinActionIcons.Pin),
        QuickWheelLibraryIcon("search", ThinActionIcons.Search),
        QuickWheelLibraryIcon("assistant", ThinActionIcons.Assistant),
        QuickWheelLibraryIcon("voice_search", ThinActionIcons.VoiceSearch),
        QuickWheelLibraryIcon("voice_assistant", ThinActionIcons.VoiceAssistant),
        QuickWheelLibraryIcon("alarm", ThinActionIcons.Alarm),
        QuickWheelLibraryIcon("fridge", ThinActionIcons.Fridge),
        QuickWheelLibraryIcon("snowflake", ThinActionIcons.Snowflake),
        QuickWheelLibraryIcon("power", ThinActionIcons.Power),
        QuickWheelLibraryIcon("keep_screen_on", ThinActionIcons.KeepScreenOn),
        QuickWheelLibraryIcon("scroll_to_top", ThinActionIcons.ScrollToTop),
        QuickWheelLibraryIcon("scroll_to_bottom", ThinActionIcons.ScrollToBottom),
        QuickWheelLibraryIcon("do_not_disturb", ThinActionIcons.DoNotDisturb),
        QuickWheelLibraryIcon("wifi", ThinActionIcons.Wifi),
        QuickWheelLibraryIcon("cellular", ThinActionIcons.Cellular),
        QuickWheelLibraryIcon("keyboard", ThinActionIcons.Keyboard),
        QuickWheelLibraryIcon("toggle_auto_rotate", ThinActionIcons.ToggleAutoRotate),
        QuickWheelLibraryIcon("force_portrait", ThinActionIcons.ForcePortrait),
        QuickWheelLibraryIcon("force_landscape", ThinActionIcons.ForceLandscape),
        QuickWheelLibraryIcon("shortcut", ThinActionIcons.Shortcut),
        QuickWheelLibraryIcon("block", ThinActionIcons.Block),
        QuickWheelLibraryIcon("visibility_off", ThinActionIcons.VisibilityOff),
    )

    private const val MAX_BITMAP_CACHE = 64
    private const val MIN_APP_ICON_PX = 32
    private const val MAX_APP_ICON_PX = 192
    private val bitmapCache = object : LinkedHashMap<String, ImageBitmap?>(MAX_BITMAP_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?): Boolean =
            size > MAX_BITMAP_CACHE
    }

    fun vectorFor(key: String): ImageVector? =
        library.firstOrNull { it.key == key }?.vector

    /** 解析位图类图标；[QuickWheelIconSource.ICON_LIBRARY] 与 [QuickWheelIconSource.NONE] 返回 null。 */
    fun bitmapFor(context: Context, slot: QuickWheelSlot, targetSizePx: Int): ImageBitmap? {
        if (slot.iconValue.isBlank()) return null
        // revision 进 key：在图标库里改完应用图标，轮盘不会继续用旧图
        val key = "${slot.iconSource.name}:${slot.iconValue}:$targetSizePx:" +
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconCacheRevision?.invoke().orEmpty()
        if (bitmapCache.containsKey(key)) return bitmapCache[key]
        val bitmap = when (slot.iconSource) {
            QuickWheelIconSource.APP_ICON -> loadAppIcon(context, slot.iconValue, targetSizePx)
            QuickWheelIconSource.GALLERY -> loadGalleryIcon(slot.iconValue)
            // TEXT（文字图标）由容器直接绘制文字，这里不产出位图。
            QuickWheelIconSource.ICON_LIBRARY,
            QuickWheelIconSource.NONE,
            QuickWheelIconSource.TEXT,
            -> null
        }
        bitmapCache[key] = bitmap
        return bitmap
    }

    /**
     * 动作的**真实图标**位图（与「动作选择器」列表里显示的一致）。
     *
     * - 打开应用 → 应用启动图标；
     * - 快捷方式 → 快捷方式自身图标（取不到退回宿主应用图标）；
     * - 其余动作 → null（由调用方走矢量动作图标）。
     *
     * 用途：容器没配图标、以及动作提示条（HUD）时，让"打开应用/快捷方式"显示真实图标，
     * 而不是千篇一律的矢量箭头。
     */
    fun actionBitmapFor(context: Context, action: GestureAction, targetSizePx: Int): ImageBitmap? =
        when (action) {
            // 分身黄点不烘进位图（容器会做圆形裁剪），由 QuickWheelContainer 在裁剪外画
            is GestureAction.LaunchApp -> appIcon(context, action.packageName, targetSizePx)

            is GestureAction.LaunchShortcut ->
                shortcutIcon(context, action, targetSizePx)
                    ?: com.slideindex.app.overlay.panels.PanelRuntimeHooks.managedShortcutHostPackage
                        ?.invoke(action.payloadKey)
                        ?.let { host -> appIcon(context, host, targetSizePx) }

            // 「运行宏」：复用快速启动器统一解析，显示宏目标 App 图标
            is GestureAction.RunMacro ->
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.quickLauncherIconBitmap
                    ?.invoke(
                        com.slideindex.app.launcher.QuickLauncherItem.action(action),
                        emptyMap(),
                        targetSizePx,
                        context,
                        emptyList(),
                        emptyList(),
                    )
                    ?.asImageBitmap()

            else -> null
        }

    private fun appIcon(context: Context, packageName: String, targetSizePx: Int): ImageBitmap? =
        packageName.takeIf { it.isNotBlank() }?.let { pkg ->
            bitmapFor(
                context = context,
                slot = QuickWheelSlot(
                    iconSource = QuickWheelIconSource.APP_ICON,
                    iconValue = pkg,
                ),
                targetSizePx = targetSizePx,
            )
        }

    /**
     * 快捷方式图标。目录（应用内直达）会变，所以缓存键带上目录指纹，避免换了图标仍取旧图。
     */
    private fun shortcutIcon(
        context: Context,
        action: GestureAction.LaunchShortcut,
        targetSizePx: Int,
    ): ImageBitmap? {
        val catalog = activityShortcuts(context)
        val fingerprint = catalog.map { it.identityKey() to it.iconPath }.hashCode()
        // revision 进 key：图标库里改完宿主应用图标后，快捷方式图标要跟着换
        val key = "shortcut:${action.payloadKey}:$targetSizePx:$fingerprint:" +
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconCacheRevision?.invoke().orEmpty()
        if (bitmapCache.containsKey(key)) return bitmapCache[key]
        val target = targetSizePx.coerceIn(MIN_APP_ICON_PX, MAX_APP_ICON_PX)
        val bitmap = runCatching {
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.managedShortcutBitmap
                ?.invoke(context, action, target, catalog)?.asImageBitmap()
        }.getOrNull()
        bitmapCache[key] = bitmap
        return bitmap
    }

    /** 应用内直达（快捷方式）目录：图标解析与角标判定（[QuickWheelIconBadge]）共用同一份。 */
    internal fun activityShortcuts(context: Context): List<ActivityShortcut> =
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.activityShortcutsCatalog
            ?.invoke(context).orEmpty()

    fun clearBitmapCache() = bitmapCache.clear()

    private fun loadAppIcon(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        val target = sizePx.coerceIn(MIN_APP_ICON_PX, MAX_APP_ICON_PX)
        // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideBitmap
            ?.invoke(context, packageName, target)
            ?.let { return it.asImageBitmap() }
        val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.appRepository
            ?.invoke(context) ?: return null
        return runCatching { repository.launchIconBitmap(packageName, target).asImageBitmap() }.getOrNull()
    }

    private fun loadGalleryIcon(path: String): ImageBitmap? {
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }

    /** 图库图片落盘目录。 */
    fun galleryDir(context: Context): File =
        File(context.filesDir, "quick_wheel_icons").apply { if (!exists()) mkdirs() }
}
