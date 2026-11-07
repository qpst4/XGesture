package com.slideindex.app

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.slideindex.app.clipboard.DragFileMirror
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStartup
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.di.OtpAutoFillStatsInstaller
import com.slideindex.app.di.OtpRecordLimitsInstaller
import com.slideindex.app.di.OcrEnginePackMigrationStartup
import com.slideindex.app.di.OcrInstalledModelStartupVerifier
import com.slideindex.app.di.ModuleHookConfigSync
import com.slideindex.app.multitouch.MultiTouchGestureCoordinator
import com.slideindex.app.di.PrivilegeModeInitializer
import com.slideindex.app.freezer.FreezerLauncherHelper
import com.slideindex.app.fvgesture.FvGestureLibrary
import com.slideindex.app.di.ShizukuInitializer
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEngineRuntime
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.GestureToggleTileWarmup
import com.slideindex.app.service.HistoryFloatLifecycle
import com.slideindex.app.service.OverlayServiceLifecycle
import com.slideindex.app.util.HiddenApiBootstrap
import com.slideindex.app.util.AppProcess
import com.slideindex.app.util.AppLocaleApplier
import com.slideindex.app.util.ForegroundNotificationChannels
import com.slideindex.app.util.ServiceEnabledStore
import com.slideindex.app.settings.AppUiLanguage
import com.slideindex.app.update.UpdateCheckScheduler
import com.slideindex.app.update.UpdatePreferencesStore
import com.slideindex.app.widget.WidgetPanelPage
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class SlideIndexApp : Application(), androidx.work.Configuration.Provider {
    private companion object {
        /** 启动期重活延后时间，给前台服务 startForeground 留出窗口。 */
        const val STARTUP_HEAVY_TASK_DELAY_MS = 1_500L

        /**
         * 非默认进程延后初始化 Shizuku 的等待时间。
         *
         * 取 binder 会把主进程拉起来并让本进程依赖它，所以要等主进程稳定后再做
         * （覆盖安装后主进程启动慢、被 AMS 判成 attach 超时杀掉时，本进程会被连带杀掉）。
         */
        const val SHIZUKU_DEFERRED_START_DELAY_MS = 20_000L

        /**
         * 给 WorkManager 声明的 JobScheduler id 范围（lint: SpecifyJobSchedulerIdRange）。
         *
         * 本应用除了 WorkManager，还自己直接用 JobScheduler（[com.slideindex.app.service.OverlayWatchdogJobService]
         * 的 id 是 0x5101 = 20737，必须落在下面这个范围之外，否则两边编号可能撞车）。
         * WorkManager 默认范围是 [0, Int.MAX_VALUE]，等于"整段都可以用"，所以必须显式收窄；
         * 收窄后老的 WorkManager 任务仍落在新范围内（它们用的是很小的自增编号），不会被漏掉。
         */
        const val WORK_MANAGER_JOB_ID_MIN = 0
        const val WORK_MANAGER_JOB_ID_MAX = 20_000
    }

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setJobSchedulerJobIdRange(WORK_MANAGER_JOB_ID_MIN, WORK_MANAGER_JOB_ID_MAX)
            .build()

    @Inject lateinit var deps: AppDependencies
    @Inject lateinit var shizukuInitializer: ShizukuInitializer
    @Inject lateinit var privilegeModeInitializer: PrivilegeModeInitializer
    @Inject lateinit var otpAutoFillStatsInstaller: OtpAutoFillStatsInstaller
    @Inject lateinit var otpRecordLimitsInstaller: OtpRecordLimitsInstaller
    @Inject lateinit var ocrInstalledModelStartupVerifier: OcrInstalledModelStartupVerifier
    @Inject lateinit var nativeEnginePackCoordinator: NativeEnginePackCoordinator
    @Inject lateinit var ocrEnginePackMigrationStartup: OcrEnginePackMigrationStartup
    @Inject lateinit var updatePreferencesStore: UpdatePreferencesStore
    @Inject lateinit var moduleHookConfigSync: ModuleHookConfigSync
    @Inject lateinit var multiTouchGestureCoordinator: MultiTouchGestureCoordinator

    override fun attachBaseContext(base: Context) {
        // 先把上次的应用语言从 SharedPreferences 同步镜像读回来（只读一个键，不碰 DataStore），
        // 再包 Configuration——否则 API<33 上冷启动首帧会先用系统语言渲染一次再被 onCreate 纠正。
        AppLocaleApplier.primeFromContext(base)
        super.attachBaseContext(AppLocaleApplier.wrapContextIfNeeded(base))
        // §0.16.25：**故意不在这里写"预测性返回"flag**。
        // 试过在这里按"同步镜像"写入，但踩到两个坑：① 这一阶段 `Application` 还没 attach 完，
        // 用 `applicationContext` 会直接 NPE（启动即崩）；② 这个时点反射调
        // `setEnableOnBackInvokedCallback` 偶发 `NoSuchMethodException`，一旦按"失败=按 false 处理"
        // 就会把 flag 写成关，而窗口若在那一瞬间创建，反而**亲手制造**出"系统按 OnBackInvoked 派发、
        // 浮窗却只等注入按键"的不一致（正是闪退事故的成因）。
        // 现在的分工：flag 保持 manifest 的 `true`（= 系统走 OnBackInvoked），运行时由
        // MainActivity.applyPredictiveBackEnabled 按**真实设置快照**写入；而
        // OverlayViewBackHandler 一律按系统真实口径装路（见 PredictiveBackHelper.resolveAppBackDispatch），
        // 所以即使这次写入被 ROM 拦掉，两边也不会再对不上。
    }

    override fun onCreate() {
        super.onCreate()
        // perf 包已下沉到 :core:common，构建标记由这里注入。
        com.slideindex.app.perf.PerfBuildFlags.debugBuild = com.slideindex.app.BuildConfig.DEBUG
        // 渠道必须先于任何 startForeground 存在：AMS 在 ServiceRecord.postNotification() 里
        // 查不到渠道就直接 killMisbehavingService()（Bad notification for startForeground），
        // 而这个判定发生在 AMS 的 handler 线程，服务侧 try/catch 抓不住。
        // 这里在进程最早期、任何 Service 被拉起之前幂等预建一遍。
        ForegroundNotificationChannels.preCreatePersistentChannels(this)
        // 抢在首帧之前把常驻服务前台化：覆盖安装后 AMS 会立刻重启 OverlayService 并要求
        // 5 秒内 startForeground()，而这段时间主线程正被首帧/图标装载占着，排在后面就会撞超时。
        // 详见 OverlayServiceLifecycle.warmStartEarly。
        if (AppProcess.isMain) {
            com.slideindex.app.service.OverlayServiceLifecycle.warmStartEarly(this)
        }
        // TaskManagerUtil 只是"存一下 app context"，不做任何跨进程动作，必须在每个进程一开始就喂上。
        // 它此前只被 ShizukuInitializer 顺带初始化，而 :overlay 侧的 Shizuku 初始化现在是"延后且
        // 主进程活着才做"——只要没走到那一步，:overlay 里所有特权调用都会抛
        // "TaskManagerUtil.initialize() must be called before use"：任务切换器空、小窗动作失效
        // （真机 01:01 日志）。惰性化只该针对"取 Shizuku binder"，不该连这个纯赋值初始化一起拖。
        com.slideindex.app.util.TaskManagerUtil.initialize(this)
        // 分身枚举（clone profile / 999）需要 LauncherApps + UserManager：只在这里喂一次 context。
        // 搜索列表的分身条目由 app 侧注入，feature 模块的 AppRepository 不认识 CloneAppsIndex。
        com.slideindex.app.overlay.CloneAppsIndex.initialize(this)
        deps.appRepository.installCloneAppProvider(
            com.slideindex.app.data.CloneAppProvider { base ->
                com.slideindex.app.overlay.CloneAppsIndex.searchEntries(base)
            }
        )
        // Shizuku 的 binder 只会投递给声明了 ShizukuProvider 的进程（这里是默认进程）。
        // 注意 enableMultiProcessSupport(flag) 的 flag 含义是"**当前进程**是不是 provider 进程"，
        // 传 true 会被 Shizuku 当成 provider 进程，紧接着的 requestBinderForNonProviderProcess 会直接 return。
        //
        // 历史上这里还给非主进程准备过"向主进程请求 binder"的通道：那会让本进程依赖主进程里的
        // provider，覆盖安装后主进程启动慢、被 AMS 判成 attach 超时杀掉时，本进程会被连带杀掉
        // （表现为"装完悬浮球半天不出来"）。`:overlay` 合并回默认进程后已经没有任何进程需要它，
        // 所以那条通道整个删掉了，这里只保留默认进程的 provider 声明。
        runCatching {
            if (AppProcess.isMain) {
                rikka.shizuku.ShizukuProvider.enableMultiProcessSupport(true)
            }
        }
        // 所有进程共有：崩溃记录 + Hidden API 放行 + 引擎运行时接入。
        com.slideindex.app.util.LocalCrashHandler.install(this)
        HiddenApiBootstrap.install()
        NativeEngineRuntime.coordinator = nativeEnginePackCoordinator
        NativeEngineRuntime.onOcrEnginePackInvalidated = {
            OcrDependencyAccess.inferenceService(this)?.invalidateEngineBlocking()
        }
        // 浮层宿主的失联诊断：overlay-host 模块不认识 :app 的诊断日志，这里把两者接起来。
        com.slideindex.app.overlay.OverlayHostLease.onHostLost = { message ->
            com.slideindex.app.diagnostic.EdgeDiag.logStack("host", message)
        }
        // overlay-host 选窗口类型时要知道"无障碍服务是否启用"和它的宿主 context。
        com.slideindex.app.overlay.OverlayHostRuntime.isAccessibilityServiceEnabled = { ctx ->
            com.slideindex.app.util.PermissionHelper.isAccessibilityServiceEnabled(ctx)
        }
        // 权限工具（已下沉到 :core:overlay-host）要用服务类拼 ComponentName。
        com.slideindex.app.overlay.OverlayHostRuntime.accessibilityServiceClass =
            com.slideindex.app.service.SlideIndexAccessibilityService::class.java
        // 浮层 Compose 的透明主题与应用内语言：资源 id 与 locale 包装器都在 :app，注入给模块。
        com.slideindex.app.overlay.OverlayHostRuntime.overlayThemeResId =
            com.slideindex.app.R.style.Theme_SlideIndex_Transparent
        com.slideindex.app.overlay.OverlayHostRuntime.wrapOverlayLocale = { ctx ->
            com.slideindex.app.util.AppLocaleApplier.wrapOverlayContext(ctx)
        }
        // 全局模糊开关：模块里的 rememberOverlayBlurEnabled() 从这里取流。
        com.slideindex.app.overlay.OverlayHostRuntime.overlayBlurEnabledFlow = { ctx ->
            com.slideindex.app.di.OverlayDependencyAccess.overlayDependencies(ctx)
                ?.settingsRepository
                ?.settings
                ?.let { flow -> flow.map { it.overlayBlurEnabled } }
        }
        // 浮层内容主题包装：模块里的 OverlayToastWindow 等用它套 Miuix 主题。
        com.slideindex.app.overlay.OverlayHostRuntime.themeWrapper =
            object : com.slideindex.app.overlay.OverlayThemeWrapper {
                @androidx.compose.runtime.Composable
                override fun wrap(content: @androidx.compose.runtime.Composable () -> Unit) {
                    com.slideindex.app.ui.theme.OverlayAwareModuleTheme { content() }
                }
            }
        // 主题层已下沉到 :feature:settings，设置流由这里注入。
        com.slideindex.app.settings.SettingsFlowHolder.provider = { ctx ->
            com.slideindex.app.di.OverlayDependencyAccess.overlayDependencies(ctx)
                ?.settingsRepository
                ?.settings
        }
        // 面板模块（:feature:overlay-panels）要的特权/任务/小窗/图标能力，全部回调注入。
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.accessibilityService = {
            com.slideindex.app.service.SlideIndexAccessibilityService.accessibilityInstance()
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.recentTaskIdentifiers = {
            runCatching {
                com.slideindex.app.util.TaskManagerUtil.refreshRecentTasks().map { it.identifier }
            }.getOrNull()
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.forceStopPackage = { pkg ->
            com.slideindex.app.util.TaskManagerUtil.forceStopPackage(pkg)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.removeRecentPackages = { packages ->
            com.slideindex.app.util.RecentTasksLoader.removePackages(packages)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.launchFreeWindow = { ctx, pkg, settings, onEnd ->
            com.slideindex.app.util.TaskSwitcherMenuActions.launchFreeWindow(
                context = ctx,
                packageName = pkg,
                settings = settings,
                onSessionEnd = onEnd,
            )
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideBitmap = { ctx, pkg, size ->
            com.slideindex.app.ui.AppIconOverride.bitmap(ctx, pkg, size)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.iconOverrideDrawable = { ctx, pkg, size ->
            com.slideindex.app.ui.AppIconOverride.drawable(ctx, pkg, size)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.appRepository = { ctx ->
            com.slideindex.app.di.OverlayDependencyAccess.overlayDependencies(ctx)?.appRepository
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.cloneAppLabel = { ctx, userId, base ->
            com.slideindex.app.overlay.CloneAppsIndex.labelForUser(ctx, userId, base)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.actionIconBitmap = { action, sizePx, tint ->
            com.slideindex.app.util.GestureActionIconBitmap.get(action, sizePx, tint)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.cornerSlotIconBitmap =
            { ctx, action, sizePx, tint, shortcuts, shellCommands ->
                com.slideindex.app.overlay.corner.CornerSlotIconBitmap.get(
                    context = ctx,
                    action = action,
                    sizePx = sizePx,
                    tintArgb = tint,
                    activityShortcuts = shortcuts,
                    shellCommands = shellCommands,
                )
            }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.captureBlurredWallpaper = { service, appCtx, radius, callback ->
            com.slideindex.app.overlay.BlurredWallpaperCache.captureFromDisplay(
                service,
                appCtx,
                radius,
                com.slideindex.app.overlay.BlurredWallpaperCache.Callback { bitmap -> callback.onReady(bitmap) },
            )
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.peekActivityIcon = { pkg, cls ->
            com.slideindex.app.util.PickerAppIconBitmap.peekActivity(pkg, cls)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.loadActivityIcon = { ctx, pkg, cls ->
            com.slideindex.app.util.PickerAppIconBitmap.loadActivity(ctx, pkg, cls)
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.appDependenciesComposition =
            object : com.slideindex.app.overlay.panels.AppDependenciesComposition {
                @androidx.compose.runtime.Composable
                override fun provide(content: @androidx.compose.runtime.Composable () -> Unit) {
                    val deps = androidx.compose.runtime.remember {
                        runCatching {
                            dagger.hilt.android.EntryPointAccessors.fromApplication(
                                this@SlideIndexApp,
                                com.slideindex.app.di.AppGraphEntryPoint::class.java,
                            ).dependencies()
                        }.getOrNull()
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        *(listOfNotNull(deps?.let { com.slideindex.app.ui.compose.LocalAppDependencies provides it }).toTypedArray()),
                    ) {
                        content()
                    }
                }
            }
        com.slideindex.app.overlay.OverlayHostRuntime.accessibilityHostContext = {
            com.slideindex.app.service.SlideIndexAccessibilityService.overlayHostContext()
        }
        // compositor 的 z-order 联动：浮球/指针窗口与无障碍呈现层都在 :app，用它四个回调接回去。
        com.slideindex.app.overlay.OverlayZOrderHooks.scheduleChromeAbovePanels = {
            com.slideindex.app.overlay.FloatBallOverlay.scheduleChromeAbovePanels()
        }
        com.slideindex.app.overlay.OverlayZOrderHooks.restoreChromeZOrderIfIdle = {
            com.slideindex.app.overlay.FloatBallOverlay.restoreChromeZOrderIfIdle()
        }
        com.slideindex.app.overlay.OverlayZOrderHooks.bringFloatingPointerToFront = {
            com.slideindex.app.overlay.FloatingPointerOverlayWindow.bringToFront(forceReAdd = false)
        }
        com.slideindex.app.overlay.OverlayZOrderHooks.edgePresentationNeedsChromeRaise = {
            com.slideindex.app.service.SlideIndexAccessibilityService.edgePresentationNeedsChromeRaise()
        }
        com.slideindex.app.overlay.OverlayZOrderHooks.isFloatBallShowing = {
            com.slideindex.app.overlay.FloatBallOverlay.isShowing
        }
        com.slideindex.app.overlay.OverlayZOrderHooks.notifyPanelAttachedAboveChrome = {
            com.slideindex.app.overlay.FloatBallOverlay.notifyPanelAttachedAboveChrome()
        }
        if (AppProcess.isEngine) return

        // 预测性返回（§0.16.26）：这里**故意不写** `enableOnBackInvokedCallback`。
        // 通用做法就是"只靠 manifest 声明 true、运行时不碰它"——单一事实来源，没有时序竞争。
        // 之前按用户设置写（默认 true）会把它写成 false，于是系统退回"兼容注入 KEYCODE_BACK"，
        // 而那条注入路在魅族 21 / Android 16 上到不了 Activity 的返回分发器 → 边缘手势返回彻底失效。
        // 现在唯一的写入点是设置页那个开关（恒定写 true + 重建），冷启动不写任何东西。

        // OCR 推理回到单进程：不再安装 binder 传输，引擎直接在调用方进程里推理
        // （省掉 AIDL 对位图的 Parcel 拷贝）。EngineOcrTransport / EngineOcrService
        // 保留但不再接管：以后若要重新拆进程，把 manifest 的 android:process 加回去即可。

        // 剪贴板监听状态镜像：监听进程发布，其它进程（设置页）读。
        com.slideindex.app.clipboard.monitor.ClipboardMonitorStatusPort.start(this)

        // —— 主进程与 :overlay 共有：常驻交互（无障碍/浮层/模块桥/系统监听）需要用到的部分 ——
        deps.launcherAppsCallbackBridge.register()
        if (AppProcess.isMain) {
            // 应用语言走同步镜像，不再在主线程 runBlocking 等一次 DataStore 全量读 + 快照解析：
            // attachBaseContext 里已经 prime 过（只读一个 SharedPreferences 键），首帧语言是对的；
            // 这里这次异步读只做"对账"（LocaleManager 上一般已生效），读失败也保持已 prime 的语言，
            // 不会像旧代码那样 getOrDefault("") 把用户设置的语言重置成系统。
            deps.applicationScope.launch(Dispatchers.IO) {
                val snapshot = runCatching { deps.settingsRepository.readFreshSnapshot() }.getOrNull()
                if (snapshot != null) {
                    com.slideindex.app.ui.IconPlateModeState.init(this@SlideIndexApp)
                    com.slideindex.app.ui.IconPlateModeState.mode =
                        com.slideindex.app.settings.IconPlateMode.fromId(snapshot.iconPlateModeId)
                }
                val language = snapshot
                    ?.let { AppUiLanguage.fromStorageTag(it.appUiLanguageTag) }
                    ?: AppLocaleApplier.currentLanguage()
                AppLocaleApplier.apply(this@SlideIndexApp, language)
            }
            shizukuInitializer.start()
            moduleHookConfigSync.start()
        } else {
            // :overlay 等进程：启动期只做廉价操作，把可能阻塞的初始化挪到后台，
            // 保证前台服务的 startForeground 窗口不被挤掉。
            AppLocaleApplier.primeFromStorage(this)
            // shizukuInitializer.start() 会立刻去取 binder（= 拉起主进程并形成依赖），
            // 非主进程要等主进程稳定后再做，否则会把本进程一起搭进去。
            deps.applicationScope.launch {
                delay(SHIZUKU_DEFERRED_START_DELAY_MS)
                if (isMainProcessAlive(this@SlideIndexApp)) {
                    shizukuInitializer.start()
                }
            }
            deps.applicationScope.launch { moduleHookConfigSync.start() }
        }
        otpAutoFillStatsInstaller.install()
        otpRecordLimitsInstaller.install()
        // 「录制宏」的接线：把录制实现塞进界面侧定义的窄接口（不接线时界面会提示尚未接入）。
        com.slideindex.app.macro.MacroRecordingInstaller.install(this)
        ClipboardMonitorStartup.applicationReady = true
        // FV「自定义手势」参考实现：只注入 appContext，不读盘（手势库文件在首次访问时惰性加载）。
        FvGestureLibrary.init(this)

        // —— 仅主进程：启动期重活、调度、拉起常驻服务 ——
        // :overlay 进程不跑这些，否则它自己就成了"启动期卡顿"的下一个受害者。
        if (AppProcess.isMain) {
        // 多指手势（Shizuku evdev 观察）：只在主进程启动，避免子进程重复观察导致一次手势执行多次。
        multiTouchGestureCoordinator.start()
        com.slideindex.app.ui.icon.AppIconTheme.ensureSelectedThemeEnabled(this)
        // 首帧后再做 OCR 校验与应用列表扫描，减轻装后首开卡顿
        // 延迟执行启动期重活：开机瞬间主线程若被 OCR 校验 / 应用列表扫描占住，
        // 剪贴板监听前台服务会来不及在 5 秒内 startForeground（实测过一次
        // ForegroundServiceDidNotStartInTimeException）。
        ClipboardMonitorStartup.runOnMainWhenReady {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 引擎包校验 + OCR 冒烟改到 :engine 进程执行（native 库与解压消耗不再压在本进程）
            com.slideindex.app.service.EngineBootService.start(this@SlideIndexApp)
            if (deps.settingsRepository.readSnapshot().onboardingCompleted) {
                deps.applicationScope.launch(Dispatchers.IO) {
                    deps.appRepository.loadApps()
                    com.slideindex.app.widget.WidgetCatalog.preload(this@SlideIndexApp)
                }
            }
            }, STARTUP_HEAVY_TASK_DELAY_MS)
        }
        deps.stashRepository
        deps.clipboardHistoryRepository
        // 常驻巡检：用户不开 App 时，overlay 掉线/进程被杀也能被自己拉回来。
        com.slideindex.app.service.OverlayWatchdogJobService.schedule(this)
        deps.applicationScope.launch(Dispatchers.IO) {
            val enabled = deps.settingsRepository.settings.first().serviceEnabled
            ServiceEnabledStore.write(this@SlideIndexApp, enabled)
        }
        deps.applicationScope.launch(Dispatchers.IO) {
            // 兜底清理进程被杀留下的拖拽镜像；按年龄判定，任何进程调用都安全。
            DragFileMirror.purgeOrphans(this@SlideIndexApp)
        }
        deps.applicationScope.launch {
            HistoryFloatLifecycle.syncFromSettings(this@SlideIndexApp, deps.settingsRepository)
            ClipboardFloatLifecycle.syncFromSettings(this@SlideIndexApp, deps.settingsRepository)
            OverlayServiceLifecycle.syncFromSettings(
                this@SlideIndexApp,
                deps.settingsRepository,
                accessibilityRecoverRetries = true,
            )
        }
        GestureToggleTileWarmup.requestListening(this, "appOnCreate")
        deps.applicationScope.launch {
            if (updatePreferencesStore.read().autoCheckUpdate) {
                UpdateCheckScheduler.schedule(this@SlideIndexApp)
            }
        }
        // §0.16.25：这里曾经还写一次"预测性返回"flag（读 settingsRepository.readSnapshot()），
        // 已**整段删除** —— 那个读的是异步填充的缓存，进程刚起时还是默认值 false，
        // 会把"用户其实开着"（本机就是开着）写成关；而这正是魅族返回键注入死循环的成因。
        // 现在唯一权威写入点是 attachBaseContext（读同步镜像），运行时翻转仍走
        // MainActivity.applyPredictiveBackEnabled。
            ocrEnginePackMigrationStartup.start()
            FreezerLauncherHelper.cleanupLegacyAlias(this)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_UI_HIDDEN) {
            com.slideindex.app.clipboard.ClipboardThumbnailCache.evictAll()
            com.slideindex.app.util.GestureActionIconBitmap.evictAll()
        }
    }

    /** 主进程此刻是否活着（用 ActivityManager 查进程列表，不依赖任何跨进程通道）。 */
    private fun isMainProcessAlive(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return@runCatching false
        manager.runningAppProcesses?.any { it.processName == context.packageName } ?: false
    }.getOrDefault(false)

    fun schedulePersistWidgetPanelPages(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.schedulePersist(pages)
    }

    suspend fun persistWidgetPanelPagesNow(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.persistNow(pages)
    }
}
