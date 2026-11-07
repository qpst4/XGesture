package com.slideindex.app.overlay.holographic

import com.slideindex.app.overlay.overlayHostContext

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HolographicAppMode
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.util.PermissionHelper

@SuppressLint("StaticFieldLeak")
object HolographicLauncherOverlayWindow {
    private const val TAG = "HolographicLauncher"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controller: HolographicLauncherOverlayController? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
    private var appContext: Context? = null

    val isShowing: Boolean get() = controller?.isVisible == true

    fun show(
        context: Context,
        settings: AppSettings,
        executeGesture: (GestureAction, AppSettings) -> Unit,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context, settings, executeGesture)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }

        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }

        val hostContext = overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return false
            }
        val deps = com.slideindex.app.overlay.panels.PanelRuntimeHooks.appRepository?.invoke(hostContext)
        val appRepository = deps
            ?: run {
                Log.w(TAG, "show: app repository unavailable")
                return false
            }

        val apps = resolveApps(hostContext, settings, appRepository)
        if (apps.isEmpty()) {
            // 「仅显示选中」但一个都没挑 → 必须给用户看得见的提示：只写日志的话表现就是
            // 「点了完全没反应」，用户根本不知道要去设置里挑应用。
            Log.w(TAG, "show: no launchable apps (mode=${settings.holographicLauncher.appMode})")
            if (settings.holographicLauncher.appMode == HolographicAppMode.SELECTED_ONLY) {
                Toast.makeText(
                    hostContext,
                    hostContext.getString(R.string.holographic_no_selected_apps),
                    Toast.LENGTH_LONG,
                ).show()
            }
            return false
        }

        val overlayController = controller ?: HolographicLauncherOverlayController(hostContext, mainHandler).also {
            controller = it
        }
        val holographicSettings = settings.holographicLauncher
        val shown = overlayController.show(
            apps = apps,
            settings = holographicSettings,
            listener = object : HolographicLauncherOverlayView.Listener {
                override fun onLaunch(app: HolographicLauncherApp) {
                    screenOffDismissReceiver.unregister()
                    overlayController.removeNow()
                    releaseOverlayState()
                    executeGesture(
                        GestureAction.LaunchApp(app.packageName, cloneUserId = app.cloneUserId),
                        settings,
                    )
                }

                override fun onClosed() {
                    screenOffDismissReceiver.unregister()
                    overlayController.removeNow()
                    releaseOverlayState()
                }
            },
        )
        if (!shown) return false

        appContext = hostContext
        screenOffDismissReceiver.register(hostContext)
        // 图标一律后台补：resolveApps 只取内存缓存里已有的，缺图的节点在球面上先不画，
        // 补齐后再重排一次（同一个列表 → 位置不变，只是多了图）。
        HolographicLauncherIconLoader.loadMissingIconsAsync(
            context = hostContext,
            appRepository = appRepository,
            apps = apps,
            onIconsReady = {
                if (controller === overlayController && overlayController.isVisible) {
                    overlayController.refreshAppIcons(apps)
                }
            },
        )
        return true
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        controller?.removeNow()
        screenOffDismissReceiver.unregister()
        releaseOverlayState()
    }

    /**
     * 组装球面用的应用列表。
     *
     * 两档模式决定怎么用**同一份**选中列表（见 `HolographicAppMode`）：
     * - `ALL` → 全部已安装应用（自身除外）；
     * - `SELECTED_ONLY` → 只用选中列表，顺序即挑选顺序（球面按列表顺序铺点，顺序稳定用户才有肌肉记忆）；
     * - `EXCLUDE_SELECTED` → 全部已安装应用 − 选中列表。
     *
     * 模式与列表分开存的意义：切换模式时**选择不会被清掉**，也永远不存在
     * 「两种模式都添加过、到底听谁的」这种说不清的状态 —— 只有一个模式生效。
     *
     * 图标刻意**不在这里同步解析**：这一步跑在主线程，而魅族/小米等 OEM 的
     * `PackageManager.getApplicationIcon` 单次可达秒级（见 `OverlayIconLoader` 的事故记录），
     * 几百个包逐个解析会直接把触摸事件卡到 ANR。这里统一交给 [HolographicLauncherIconLoader]
     * 后台补齐，球面先画缺图的节点。
     */
    private fun resolveApps(
        context: Context,
        settings: AppSettings,
        appRepository: AppRepository,
    ): List<HolographicLauncherApp> {
        val selfPackage = context.packageName
        val holographic = settings.holographicLauncher
        val selectedPackages = holographic.selectedAppPackages
        val cachedByPackage = appRepository.getCachedApps().associateBy { it.packageName }
        // 稳定顺序：以前球面是 `shuffled(Random).take(60)`，用户每次打开看到的应用都不一样、
        // 有些永远找不到。选中列表保持挑选顺序，其余按拼音排。
        val allSorted = appRepository.getCachedApps().sortedBy { it.pinyinKey }

        // 选中列表允许是目标串（包名\u0002userId）：旧数据是裸包名，解析出来就是 user 0。
        val targets = selectedPackages.map(com.slideindex.app.launcher.AppTarget::parse)
        val apps: Sequence<AppInfo> = when (holographic.appMode) {
            HolographicAppMode.ALL -> allSorted.asSequence()

            HolographicAppMode.SELECTED_ONLY ->
                targets.asSequence().mapNotNull { target ->
                    val base = cachedByPackage[target.packageName]
                        ?: appRepository.lookupApp(target.packageName)
                        ?: return@mapNotNull null
                    if (!target.isClone) {
                        base
                    } else {
                        val label = com.slideindex.app.overlay.panels.PanelRuntimeHooks.cloneAppLabel?.invoke(
                            context,
                            target.cloneUserId,
                            base.label,
                        ) ?: base.label
                        base.copy(
                            label = label,
                            cloneUserId = target.cloneUserId,
                            letter = com.slideindex.app.util.PinyinHelper.firstLetter(label),
                            pinyinKey = com.slideindex.app.util.PinyinHelper.sortKey(label),
                        )
                    }
                }

            HolographicAppMode.EXCLUDE_SELECTED -> {
                val excluded = targets
                    .filterNot { it.isClone }
                    .mapTo(HashSet()) { it.packageName }
                allSorted.asSequence().filter { it.packageName !in excluded }
            }
        }

        return apps
            .filter { it.packageName != selfPackage }
            .map { info ->
                HolographicLauncherApp(
                    packageName = info.packageName,
                    label = info.label,
                    icon = run {
                        // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）。
                        // 预填成系统原图后渲染方不会再查覆盖（真机实测）。
                        val base = com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideDrawable
                            ?.invoke(context, info.packageName, 128)
                            ?: appRepository.peekLaunchIconDrawable(info.packageName)
                        base?.let {
                            com.slideindex.app.util.CloneBadge.wrap(it, info.cloneUserId > 0)
                        }
                    },
                    cloneUserId = info.cloneUserId,
                )
            }
            .toList()
    }

    private fun releaseOverlayState() {
        controller = null
        appContext = null
    }
}
