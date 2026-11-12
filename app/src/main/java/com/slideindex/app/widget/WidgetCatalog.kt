package com.slideindex.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import android.os.UserManager
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.ui.graphics.ImageBitmap
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutUtils
import com.slideindex.app.util.toSafeImageBitmap

import android.content.pm.LauncherApps

data class WidgetProviderEntry(
  val provider: AppWidgetProviderInfo,
  val packageName: String,
  val appLabel: String,
  val widgetLabel: String,
  val spanX: Int,
  val spanY: Int,
)

data class InstalledAppEntry(
  val packageName: String,
  val className: String,
  val appLabel: String,
  val sortKey: String,
  val initialKey: String = "",
  val iconBitmap: ImageBitmap?,
  /** 应用分身所属 user（0 = 主实例）。 */
  val cloneUserId: Int = 0,
)

data class ShortcutEntry(
  val packageName: String,
  val shortcutId: String,
  val label: String,
  val sortKey: String,
  val initialKey: String = "",
  val iconBitmap: ImageBitmap?,
  val intentUri: String,
)

data class WidgetAppGroup(
  val packageName: String,
  val appLabel: String,
  val appIcon: Drawable?,
  val widgets: List<WidgetProviderEntry>,
)

object WidgetCatalog {
  @Volatile
  var cachedGroups: List<WidgetAppGroup>? = null
    private set

  @Volatile
  var cachedInstalledApps: List<InstalledAppEntry>? = null
    private set

  @Volatile
  var cachedShortcuts: List<ShortcutEntry>? = null
    private set

  private var isPreloading = false

  fun invalidate() {
    cachedGroups = null
    cachedInstalledApps = null
    cachedShortcuts = null
    isPreloading = false
  }

  fun preload(context: Context) {
    if (isPreloading) return
    isPreloading = true
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
      runCatching {
        loadGroups(context, force = true)
        loadInstalledApps(context, force = true)
        loadShortcuts(context, force = true)
      }
      isPreloading = false
    }
  }

  suspend fun loadShortcuts(context: Context, force: Boolean = false): List<ShortcutEntry> = withContext(Dispatchers.IO) {
    if (!force && cachedShortcuts != null) {
      return@withContext cachedShortcuts!!
    }
    val result = runCatching {
      val appContext = context.applicationContext
      val pm = appContext.packageManager
      val manifestShortcuts = ShortcutUtils.getAllAppsWithShortcut(appContext)
      val list = mutableListOf<ShortcutEntry>()
      for (info in manifestShortcuts) {
        val pkg = info.packageName
        if (pkg == appContext.packageName) continue
        val appLabel = info.label
        for (entry in info.shortcuts) {
          runCatching {
            val label = entry.label.ifBlank { appLabel }
            val intentUri = entry.intents.firstOrNull().orEmpty()
            val iconDrawable = if (entry.iconRes != 0) {
              runCatching {
                val res = pm.getResourcesForApplication(pkg)
                ResourcesCompat.getDrawable(res, entry.iconRes, null)
              }.getOrNull()
            } else null
            val iconBitmap = (
              iconDrawable
                // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）
                ?: com.slideindex.app.ui.AppIconOverride.drawable(appContext, pkg, 48)
                ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
              )?.toSafeImageBitmap(48)
            val sortKey = PinyinHelper.sortKey(label)
            val initialKey = PinyinHelper.initialKey(label)
            list.add(
              ShortcutEntry(
                packageName = pkg,
                shortcutId = entry.className + "_" + label.hashCode(),
                label = if (label != appLabel) "$appLabel - $label" else label,
                sortKey = sortKey,
                initialKey = initialKey,
                iconBitmap = iconBitmap,
                intentUri = intentUri,
              )
            )
          }
        }
      }
      list.sortedBy { it.sortKey }
    }.getOrDefault(emptyList())
    cachedShortcuts = result
    result
  }

  suspend fun loadInstalledApps(context: Context, force: Boolean = false): List<InstalledAppEntry> = withContext(Dispatchers.IO) {
    if (!force && cachedInstalledApps != null) {
      return@withContext cachedInstalledApps!!
    }
    val result = runCatching {
      val appContext = context.applicationContext
      val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
      val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager
      // 用户列表：公开 profile + `pm list users` 兜底。
      // Flyme 的平行空间（user 999）在部分版本不出现在 `getUserProfiles()` 里，
      // 只信公开 API 的话分身会整条消失（`CloneAppsIndex` 就是因此加了 shell 兜底）。
      val userIds = LinkedHashSet<Int>()
      userIds += android.os.Process.myUserHandle().hashCode()
      userManager?.userProfiles.orEmpty().forEach { profile ->
        runCatching { profile.hashCode() }.getOrNull()?.let { userIds += it }
      }
      runCatching {
        val shell = com.slideindex.app.util.TaskManagerUtil.runShellCommandOutput("pm", "list", "users")
        if (shell.exitCode == 0) {
          Regex("""UserInfo\{(\d+):""").findAll(shell.output).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let { userIds += it }
          }
        }
      }
      val entries = mutableListOf<InstalledAppEntry>()
      val seen = HashSet<String>()
      if (launcherApps != null) {
        // 「应用分身 / 工作资料」下同一个包会在多个 user 里各有一个实例。
        // 每个 user 一条，身份用 (包名, userId) 区分：面板的勾选与增删都按这个身份走，
        // 同包名的主实例与分身不会再互相覆盖（旧的「按包名去重」正是分身列不出来的原因）。
        for (userId in userIds.sortedBy { if (it == android.os.Process.myUserHandle().hashCode()) 0 else 1 }) {
          val handle = runCatching {
            android.os.UserHandle.getUserHandleForUid(userId * 100_000)
          }.getOrNull() ?: continue
          runCatching { launcherApps.getActivityList(null, handle) }.getOrDefault(emptyList()).forEach { info ->
            val pkg = info.applicationInfo?.packageName ?: return@forEach
            if (pkg == appContext.packageName) return@forEach
            val cls = info.name
            if (!seen.add("$pkg/$cls/$userId")) return@forEach
            val label = runCatching { info.label.toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg
            val iconDrawable = runCatching { info.getBadgedIcon(0) }.getOrNull()
              ?: runCatching { info.getIcon(0) }.getOrNull()
            val iconBitmap = iconDrawable?.toSafeImageBitmap(48)
            entries += InstalledAppEntry(
              packageName = pkg,
              className = cls,
              appLabel = label,
              sortKey = PinyinHelper.sortKey(label),
              initialKey = PinyinHelper.initialKey(label),
              iconBitmap = iconBitmap,
              cloneUserId = userId,
            )
          }
        }
      }
      android.util.Log.i(
        "WidgetCatalog",
        "users=${userIds.toList()} entries=${entries.size} clones=${entries.count { it.cloneUserId > 0 }}",
      )
      entries.sortedBy { it.sortKey }
    }.getOrDefault(emptyList())
    cachedInstalledApps = result
    result
  }

  suspend fun loadGroups(context: Context, force: Boolean = false): List<WidgetAppGroup> = withContext(Dispatchers.IO) {
    if (!force && cachedGroups != null) {
      return@withContext cachedGroups!!
    }
    val result = runCatching {
      val appContext = context.applicationContext
      val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return@withContext emptyList()
      val pm = appContext.packageManager
      val providers = loadInstalledProviders(appContext, manager)
      val grouped = LinkedHashMap<String, MutableList<WidgetProviderEntry>>()
      for (info in providers) {
        val packageName = info.provider?.packageName ?: continue
        if (packageName == appContext.packageName) continue

        val appLabel = runCatching {
          pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
          ?: runCatching {
            info.loadLabel(pm)
          }.getOrNull()?.takeIf { it.isNotBlank() }
          ?: packageName

        val widgetLabel = runCatching { info.loadLabel(pm) }.getOrNull().orEmpty().ifBlank { appLabel }
        val (spanX, spanY) = runCatching { WidgetSpanUtil.spanFromProviderInfo(info) }.getOrDefault(Pair(2, 2))
        val entry = WidgetProviderEntry(
          provider = info,
          packageName = packageName,
          appLabel = appLabel,
          widgetLabel = widgetLabel,
          spanX = spanX,
          spanY = spanY,
        )
        grouped.getOrPut(packageName) { mutableListOf() }.add(entry)
      }
      grouped.map { (pkg, widgets) ->
        val appLabel = widgets.firstOrNull()?.appLabel ?: pkg
        // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）
        val icon = com.slideindex.app.ui.AppIconOverride.drawable(appContext, pkg, 128)
          ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        WidgetAppGroup(
          packageName = pkg,
          appLabel = appLabel,
          appIcon = icon,
          widgets = widgets.sortedBy { it.widgetLabel },
        )
      }.sortedBy { PinyinHelper.sortKey(it.appLabel) }
    }.getOrDefault(emptyList())
    cachedGroups = result
    result
  }

  private fun loadInstalledProviders(
    context: Context,
    manager: AppWidgetManager,
  ): List<AppWidgetProviderInfo> {
    val seen = HashSet<String>()
    val merged = mutableListOf<AppWidgetProviderInfo>()

    fun absorb(list: List<AppWidgetProviderInfo>?) {
      if (list.isNullOrEmpty()) return
      for (info in list) {
        val key = info.provider?.flattenToString() ?: continue
        if (seen.add(key)) merged.add(info)
      }
    }

    runCatching {
      // 1. Default installedProviders
      runCatching { absorb(manager.installedProviders) }

      // 2. Multi-user / profiles
      val userManager = runCatching { context.getSystemService(UserManager::class.java) }.getOrNull()
      if (userManager != null) {
        val profiles = runCatching { userManager.userProfiles }.getOrNull()
        if (profiles != null) {
          for (profile in profiles) {
            runCatching { absorb(manager.getInstalledProvidersForProfile(profile)) }
          }
        }
      }
    }
    return merged
  }
}

object WidgetPreviewLoader {
  fun loadPreviewBitmap(context: Context, info: AppWidgetProviderInfo, maxPx: Int): Bitmap? {
    val previewRes = info.previewImage
    if (previewRes != 0) {
      loadDrawableBitmap(context, info.provider.packageName, previewRes, maxPx)?.let { return it }
    }
    val icon = runCatching {
      info.loadIcon(context, context.resources.displayMetrics.densityDpi)
    }.getOrNull()
    return icon?.let { drawableToBitmap(it, maxPx) }
  }

  private fun loadDrawableBitmap(
    context: Context,
    packageName: String,
    resId: Int,
    maxPx: Int,
  ): Bitmap? = runCatching {
    val pm = context.packageManager
    val resources = pm.getResourcesForApplication(packageName)
    val drawable = ResourcesCompat.getDrawable(resources, resId, null) ?: return null
    drawableToBitmap(drawable, maxPx)
  }.getOrNull()

  private fun drawableToBitmap(drawable: Drawable, maxPx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      return scaleBitmap(drawable.bitmap, maxPx)
    }
    // 不信任第三方 drawable 的 intrinsic 尺寸：异常图标包可能声明 7K 画布。
    val width = drawable.intrinsicWidth.coerceIn(1, 1024)
    val height = drawable.intrinsicHeight.coerceIn(1, 1024)
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return scaleBitmap(bitmap, maxPx)
  }

  private fun scaleBitmap(source: Bitmap, maxPx: Int): Bitmap {
    val maxDim = maxOf(source.width, source.height)
    if (maxDim <= maxPx) return source
    val scale = maxPx.toFloat() / maxDim
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    return source.scale(w, h)
  }
}
