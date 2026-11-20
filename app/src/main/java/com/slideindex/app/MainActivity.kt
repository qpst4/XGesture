@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, kotlinx.coroutines.FlowPreview::class)

package com.slideindex.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.settings.FreeWindowLayoutDp
import com.slideindex.app.settings.FreeWindowUiSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.OverlaySettings
import com.slideindex.app.ui.LocalBareSettingIcons
import com.slideindex.app.ui.FreeWindowLayoutEditorOverlay
import com.slideindex.app.ui.FreeWindowLayoutEditorSession
import kotlinx.coroutines.launch
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStartup
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.external.AppLinks
import com.slideindex.app.freezer.FreezerLaunchState
import com.slideindex.app.freezer.FreezerTab
import com.slideindex.app.launcher.LauncherShortcutsApplier
import com.slideindex.app.notification.NotificationHistoryLaunchState
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.WidgetPickerOverlayWindow
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.OverlayServiceController
import com.slideindex.app.service.ToggleFloatBallTrampolineActivity
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.ShellCommandEditorTrampoline
import com.slideindex.app.service.ShellCommandPanelTrampoline
import com.slideindex.app.service.ShellCommandResultTrampoline
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.service.StashClipboardTrampolineActivity
import com.slideindex.app.ui.navigation.MainNavHost
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.LocalTopLayer
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.ui.miuix.theme.toModuleThemeSettings
import top.yukonga.miuix.kmp.basic.Scaffold
import com.slideindex.app.ui.miuix.MiuixPageBackdropBox
import com.slideindex.app.ui.miuix.TopLayerHost
import com.slideindex.app.ui.miuix.TopLayerState
import com.slideindex.app.ui.navigation.NavPermissionStates
import com.slideindex.app.ui.onboarding.OnboardingPracticeOverlay
import com.slideindex.app.ui.onboarding.OnboardingPracticeSession
import com.slideindex.app.update.UpdateAppForeground
import com.slideindex.app.update.UpdateIntents
import com.slideindex.app.util.AppLocaleApplier
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.PredictiveBackHelper
import com.slideindex.app.util.TaskManagerUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import androidx.core.content.pm.ShortcutManagerCompat

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deps: AppDependencies

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleApplier.wrapContextIfNeeded(newBase))
    }

    /** 进程级权限状态（见 [SlideIndexApp.permissionStates]）：旋转重建不会重置，避免首页授权卡片闪现。 */
    internal val permissionStates: NavPermissionStates
        get() = (application as SlideIndexApp).permissionStates

    private val currentIntentAction = mutableStateOf<String?>(null)
    private val pendingNavRoute = mutableStateOf<String?>(null)
    private val pendingShowUpdate = mutableStateOf(false)
    /** 设置页浮层预览与常驻服务控制的入口（同进程直连）。 */
    internal lateinit var overlayServiceController: OverlayServiceController
    private val permissionRefreshHandler = Handler(Looper.getMainLooper())
    private var accessibilitySettingsObserver: ContentObserver? = null

    private val permissionRefreshRetryRunnable = object : Runnable {
        private var retryIndex = 0

        fun reset() {
            retryIndex = 0
        }

        override fun run() {
            refreshPermissionState()
            refreshServiceState()
            if (retryIndex < PERMISSION_REFRESH_RETRY_DELAYS_MS.lastIndex) {
                retryIndex++
                permissionRefreshHandler.postDelayed(
                    this,
                    PERMISSION_REFRESH_RETRY_DELAYS_MS[retryIndex]
                )
            }
        }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        permissionStates.shizukuGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
        if (permissionStates.shizukuGranted.value) {
            TaskManagerUtil.warmUpPrivilegedBackend()
            deps.clipboardHistoryRepository.syncClipboardMonitoringFromSettings()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionStates.notificationGranted.value =
            granted || PermissionHelper.hasNotificationPermission(this)
        refreshServiceState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLaunchIntent(intent)
        overlayServiceController = OverlayServiceController(
            context = this,
            permissionStates = permissionStates,
            scope = lifecycleScope,
            settingsRepository = deps.settingsRepository
        )
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> UpdateAppForeground.isInForeground = true
                    Lifecycle.Event.ON_STOP -> UpdateAppForeground.isInForeground = false
                    else -> Unit
                }
            }
        )
        enableEdgeToEdge()
        refreshPermissionState()
        // 预测性返回 flag 由 SlideIndexApp.onCreate 恒定写成 true（建窗之前），这里**不再写**：
        // 写 `false` 会让系统退回注入 KEYCODE_BACK，而那条路到不了 Activity 的返回分发器，
        // 边缘手势返回会彻底失效（§0.16.26 真机事故）。返回手势不允许被设置关掉。

        setContent {
            val initialIntentAction by currentIntentAction
            val initialNavRoute by pendingNavRoute
            val showUpdate by pendingShowUpdate
            // 主题与小窗尺寸等设置：统一在根部监听并注入主题，确保根 Scaffold 与 TopLayerHost 均在主题作用域内。
            val overlaySettings by deps.settingsRepository.overlaySettings.collectAsStateWithLifecycle(
                initialValue = OverlaySettings.from(deps.settingsRepository.readSnapshot()),
            )
            val freeWindowUiSettings by deps.settingsRepository.freeWindowUiSettings.collectAsStateWithLifecycle(
                initialValue = FreeWindowUiSettings.from(deps.settingsRepository.readSnapshot()),
            )
            val homeMainSettings by deps.settingsRepository.homeMainSettings.collectAsStateWithLifecycle(
                initialValue = HomeMainSettings.from(deps.settingsRepository.readSnapshot()),
            )
            val editorScope = rememberCoroutineScope()
            // 根层覆盖层：玻璃下拉菜单等需要盖住顶栏 / 底栏的浮层通过 TopLayer 提升到这里。
            val topLayer = remember { TopLayerState() }
            CompositionLocalProvider(
                LocalAppDependencies provides deps,
                LocalBareSettingIcons provides !homeMainSettings.settingIconPlateEnabled,
                LocalTopLayer provides topLayer,
            ) {
                ModuleTheme(settings = overlaySettings.toModuleThemeSettings()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 根部 miuix Scaffold：它是最外层 Scaffold，`GlassDialog`/`GlassBottomSheet`/`DropdownPreference`
                        // 的弹窗宿主挂在它的 popup 层上；外层包裹 ModuleTheme 后，根弹窗层与 TopLayerHost 均能正确获取深色模式。
                        Scaffold(modifier = Modifier.fillMaxSize()) {
                            MainNavHost(
                                activity = this@MainActivity,
                                deps = deps,
                                permissionStates = permissionStates,
                                initialIntentAction = initialIntentAction,
                                initialNavRoute = initialNavRoute,
                                showUpdateFromIntent = showUpdate,
                                onNavRouteConsumed = { pendingNavRoute.value = null },
                                onShowUpdateConsumed = { pendingShowUpdate.value = false }
                            )
                        }
                        if (FreeWindowLayoutEditorSession.isOpen) {
                            FreeWindowLayoutEditorOverlay(
                                portrait = FreeWindowLayoutDp(
                                    widthDp = freeWindowUiSettings.freeWindowWidthDp,
                                    heightDp = freeWindowUiSettings.freeWindowHeightDp,
                                    leftDp = freeWindowUiSettings.freeWindowLeftDp,
                                    topDp = freeWindowUiSettings.freeWindowTopDp,
                                    edgeMarginDp = freeWindowUiSettings.freeWindowEdgeMarginDp,
                                ),
                                landscape = FreeWindowLayoutDp(
                                    widthDp = freeWindowUiSettings.freeWindowLandWidthDp,
                                    heightDp = freeWindowUiSettings.freeWindowLandHeightDp,
                                    leftDp = freeWindowUiSettings.freeWindowLandLeftDp,
                                    topDp = freeWindowUiSettings.freeWindowLandTopDp,
                                    edgeMarginDp = freeWindowUiSettings.freeWindowLandEdgeMarginDp,
                                ),
                                onDismiss = { FreeWindowLayoutEditorSession.close() },
                                onSave = { portrait, landscape ->
                                    editorScope.launch {
                                        deps.settingsRepository.setFreeWindowLayoutDp(portrait, landscape)
                                    }
                                },
                            )
                        }
                        // 「关于 → 重看新手引导」：整屏盖住导航宿主，与开屏引导共用同一份内容。
                        if (OnboardingPracticeSession.isOpen) {
                            OnboardingPracticeOverlay(
                                settingsRepository = deps.settingsRepository,
                                onPreviewTriggerBar = { active ->
                                    if (active) {
                                        overlayServiceController.startLayoutPreview(
                                            com.slideindex.app.overlay.LayoutPreviewContent.TRIGGER_ONLY,
                                        )
                                    } else {
                                        overlayServiceController.stopLayoutPreview()
                                    }
                                },
                                onClose = { OnboardingPracticeSession.close() },
                            )
                        }
                        TopLayerHost(topLayer)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
    }

    private fun applyLaunchIntent(intent: Intent?) {
        val resolvedAction = resolveLaunchAction(intent)
        currentIntentAction.value = resolvedAction
        pendingNavRoute.value = resolveNavRoute(intent)
        pendingShowUpdate.value = intent?.getBooleanExtra(UpdateIntents.EXTRA_SHOW_UPDATE, false) == true
        when (intent?.getStringExtra(EXTRA_FREEZER_TAB)) {
            FREEZER_TAB_FROZEN -> FreezerLaunchState.setPendingInitialTab(FreezerTab.FROZEN)
        }
        reportShortcutUsageIfNeeded(resolvedAction)
    }

    /**
     * 启动时要落到的导航位置。
     *
     * 显式 `EXTRA_NAV_ROUTE` 优先；否则解析 app link，把 `action?type=` 转成
     * `action_detail:<GestureActionType.name>`，交给 [MainNavHost] 建栈。
     */
    private fun resolveNavRoute(intent: Intent?): String? {
        intent?.getStringExtra(EXTRA_NAV_ROUTE)?.let { return it }
        val uri = intent?.data ?: return null
        if (!AppLinks.isAppLink(uri)) return null
        if (uri.pathSegments.firstOrNull()?.lowercase() != AppLinks.PATH_ACTION) return null
        val type = uri.getQueryParameter(AppLinks.TYPE_PARAM)?.trim().orEmpty()
        if (type.isEmpty()) return null
        return NAV_ROUTE_ACTION_DETAIL_PREFIX + type
    }

    private fun resolveLaunchAction(intent: Intent?): String? {
        intent?.data?.let { uri ->
            if (AppLinks.isAppLink(uri)) {
                when (uri.pathSegments.firstOrNull()?.lowercase()) {
                    AppLinks.PATH_NOTIFICATION_HISTORY -> {
                        NotificationHistoryLaunchState.setPendingSearchQuery(
                            uri.getQueryParameter(AppLinks.QUERY_PARAM)
                        )
                        return ACTION_OPEN_NOTIFICATION_HISTORY
                    }
                }
            }
        }
        return intent?.action
    }

    private fun reportShortcutUsageIfNeeded(action: String?) {
        val shortcutId = when (action) {
            "com.slideindex.app.action.TOGGLE_GESTURE" -> "toggle_gesture"
            ToggleFloatBallTrampolineActivity.ACTION_TOGGLE_FLOAT_BALL -> "toggle_float_ball"
            ACTION_OPEN_NOTIFICATION_HISTORY -> "notification_hub"
            "com.slideindex.app.action.OPEN_SHELL_PANEL" -> "shell_panel"
            StashClipboardTrampolineActivity.ACTION_OPEN_STASH -> StashClipboardTrampolineActivity.SHORTCUT_ID_STASH
            StashClipboardTrampolineActivity.ACTION_OPEN_CLIPBOARD -> StashClipboardTrampolineActivity.SHORTCUT_ID_CLIPBOARD
            else -> null
        }
        shortcutId?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
    }

    private fun setupDynamicShortcuts() {
        // 条目集合与顺序由「外部调用 → 桌面图标长按菜单」设置决定，见 LauncherShortcutsApplier。
        val settings = deps.settingsRepository.readSnapshot()
        LauncherShortcutsApplier.sync(
            this,
            order = settings.launcherShortcutMenuOrder,
            disabled = settings.launcherShortcutMenuDisabled,
        )
    }

    override fun onStart() {
        super.onStart()
        registerAccessibilitySettingsObserver()
    }

    override fun onResume() {
        super.onResume()
        setupDynamicShortcuts()
        refreshPermissionState()
        schedulePermissionRefreshRetries()
        refreshServiceState()
        ClipboardMonitorStartup.runOnMainWhenReady {
            // 兜底：监听没在跑时（Shizuku 未启动 / 监听服务被停 / binder 掉线）先把最新一条补进历史，
            // 再尝试拉起监听。监听正常时这个方法内部会直接返回，不会多抢一次焦点。
            deps.clipboardHistoryRepository.catchUpLatestClipboard(this)
            deps.clipboardHistoryRepository.syncClipboardMonitoringFromSettings()
        }
        com.slideindex.app.widget.WidgetPopupHost.startListening(this)
        lifecycleScope.launch {
            applyHideFromRecents(deps.settingsRepository.settings.first().hideFromRecents)
        }
    }

    override fun onDestroy() {
        cancelPermissionRefreshRetries()
        unregisterAccessibilitySettingsObserver()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    override fun onPause() {
        cancelPermissionRefreshRetries()
        if (!WidgetBindTrampolineActivity.isActive() &&
            !WidgetPickerOverlayWindow.isShowing
        ) {
            com.slideindex.app.widget.WidgetPopupHost.stopListening(this)
        }
        if (!QuickLauncherAddTrampoline.isActive() &&
            !ShellCommandPanelTrampoline.isActive() &&
            !ShellCommandEditorTrampoline.isActive() &&
            !ShellCommandResultTrampoline.isActive()
        ) {
            overlayServiceController.stopPreviewOnPause()
        }
        super.onPause()
    }

    override fun onStop() {
        unregisterAccessibilitySettingsObserver()
        super.onStop()
    }

    private fun schedulePermissionRefreshRetries() {
        cancelPermissionRefreshRetries()
        permissionRefreshRetryRunnable.reset()
        permissionRefreshHandler.postDelayed(
            permissionRefreshRetryRunnable,
            PERMISSION_REFRESH_RETRY_DELAYS_MS[0]
        )
    }

    private fun cancelPermissionRefreshRetries() {
        permissionRefreshHandler.removeCallbacks(permissionRefreshRetryRunnable)
        permissionRefreshRetryRunnable.reset()
    }

    private fun registerAccessibilitySettingsObserver() {
        if (accessibilitySettingsObserver != null) return
        val observer = object : ContentObserver(permissionRefreshHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshPermissionState()
                refreshServiceState()
            }
        }
        val resolver = contentResolver
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            false,
            observer
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer
        )
        accessibilitySettingsObserver = observer
    }

    private fun unregisterAccessibilitySettingsObserver() {
        accessibilitySettingsObserver?.let { observer ->
            runCatching { contentResolver.unregisterContentObserver(observer) }
        }
        accessibilitySettingsObserver = null
    }

    internal fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    internal fun refreshPermissionState() {
        overlayServiceController.refreshPermissionState()
    }

    internal fun applyHideFromRecents(hide: Boolean) {
        getSystemService(ActivityManager::class.java)
            ?.appTasks
            ?.firstOrNull()
            ?.setExcludeFromRecents(hide)
    }

    /**
     * 现在**只有设置页那个开关**会调它，且只传 `true`（见 `HomeNavEntries`）。冷启动不调它：
     * 预测性返回只靠 manifest 的 `android:enableOnBackInvokedCallback="true"`（通用做法）。
     *
     * ⚠️ 传 `false` 会让系统退回"兼容注入 KEYCODE_BACK"，而那条路在魅族 21 / Android 16 上到不了
     * Activity 的返回分发器 → **边缘手势返回彻底失效**（§0.16.26）。
     *
     * 写法上必须"写完跟一次重建"，否则已经建好的窗口不会按新 flag 记账（真机实测：
     * 开关一拨就恢复跟手，靠的就是这两下）。
     */
    internal fun applyPredictiveBackEnabled(enabled: Boolean) {
        PredictiveBackHelper.applyEnabled(applicationInfo, enabled)
        FloatBallPickResultPanel.refreshBackHandlingIfShowing()
        WidgetPopupOverlayWindow.refreshBackHandlingIfVisible()
    }

    @Suppress("DEPRECATION")
    internal fun recreateWithoutTransition() {
        overridePendingTransition(0, 0)
        recreate()
        overridePendingTransition(0, 0)
    }

    internal fun refreshServiceState() {
        overlayServiceController.refreshServiceState()
    }

    companion object {
        const val ACTION_OPEN_NOTIFICATION_HISTORY = "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY"
        const val EXTRA_NAV_ROUTE = "extra_nav_route"
        const val EXTRA_FREEZER_TAB = "extra_freezer_tab"
        const val NAV_ROUTE_EXTENSION_FREEZER = "extension_freezer"
        const val NAV_ROUTE_EXTENSION_FREEZER_APPS = "extension_freezer_apps"
        /** 动作详情深链的前缀；完整值是 `action_detail:<GestureActionType.name>`。 */
        const val NAV_ROUTE_ACTION_DETAIL_PREFIX = "action_detail:"
        const val FREEZER_TAB_FROZEN = "frozen"

        /** Gaps after resume; first tick is relative to scheduling (see [schedulePermissionRefreshRetries]). */
        private val PERMISSION_REFRESH_RETRY_DELAYS_MS = longArrayOf(300L, 500L)
    }
}
