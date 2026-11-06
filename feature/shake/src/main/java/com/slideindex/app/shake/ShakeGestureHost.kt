package com.slideindex.app.shake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class ShakeGestureHost @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val runtimePort: ShakeRuntimePort,
    private val actionPort: ShakeActionPort,
    private val feedbackPort: ShakeFeedbackPort,
    private val sensorHub: MotionSensorHub,
) {
    private var detector: ShakeGestureDetector? = null
    private var settingsJob: Job? = null
    private var hostScope: CoroutineScope? = null
    private var latestSettings: AppSettings? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiverRegistered = false
    private var screenInteractive = true
    private var lastAppliedScreenInteractive: Boolean? = null

    /**
     * 熄屏实时判定需要 CPU 不进入 suspend：普通加速度计/陀螺仪不是 wake-up 传感器，
     * AP 睡着后事件只会进 FIFO，等亮屏才补发（FIFO 小或没有时直接丢）。
     * 代价是待机耗电，所以只在模式包含熄屏且确实配置了熄屏动作时才持锁。
     */
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockHeld = false

    /**
     * 当前激活的「晃动菜单」会话；非 `null` 时所有方向晃动都路由到菜单表，
     * 不再走基础动作 / 独立应用 / 锁屏那套映射。
     */
    private var menuSession: ShakeMenuSession? = null

    /** 菜单会话超时：到点自动关浮层并恢复普通冷却。 */
    private val menuDismissRunnable = Runnable {
        if (menuSession == null) return@Runnable
        Log.d(TAG, "shake menu session timed out")
        dismissMenuOverlay()
    }

    private val wakeLockRenewRunnable = Runnable {
        if (!wakeLockHeld) return@Runnable
        if (screenInteractive) {
            releaseWakeLock()
            return@Runnable
        }
        runCatching { wakeLock?.release() }
        wakeLockHeld = false
        acquireWakeLock()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    latestSettings?.let(::applyRuntime)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    dismissMenuOverlay()
                    latestSettings?.let(::applyRuntime)
                }
                Intent.ACTION_USER_PRESENT -> {
                    latestSettings?.let(::applyRuntime)
                }
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (settingsJob != null) return
        hostScope = scope
        screenInteractive = powerManager().isInteractive
        registerScreenReceiver()
        settingsJob = scope.launch {
            settingsRepository.settings.collectLatest { settings ->
                latestSettings = settings
                applyRuntime(settings)
            }
        }
        Log.d(TAG, "started (interactive=$screenInteractive)")
    }

    fun stop() {
        settingsJob?.cancel()
        settingsJob = null
        hostScope = null
        unregisterScreenReceiver()
        dismissMenuOverlay()
        detector?.stop()
        detector = null
        lastAppliedScreenInteractive = null
        releaseWakeLock()
        wakeLock = null
        feedbackPort.detachFeedbackOverlay()
        Log.d(TAG, "stopped")
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        appContext.registerReceiver(screenReceiver, filter)
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun applyRuntime(settings: AppSettings) {
        val shake = settings.shakeGestureSettings
        val accessibilityEnabled = runtimePort.isAccessibilityServiceEnabled()
        val screenOn = screenInteractive
        val screenChanged = lastAppliedScreenInteractive != screenOn
        lastAppliedScreenInteractive = screenOn

        val keyguardLocked = runtimePort.isLockScreenActive()
        val modeAllows = shake.workModes.allows(screenOn, keyguardLocked)
        // 熄屏但又没配置任何锁屏动作时，不必常驻采样，也不用持锁。
        val lockScreenReady = screenOn || shake.hasLockScreenAction()
        val shouldRun = shake.enabled &&
            accessibilityEnabled &&
            modeAllows &&
            lockScreenReady

        if (!shouldRun) {
            if (detector != null) {
                val reason = when {
                    !shake.enabled -> "shake disabled"
                    !accessibilityEnabled -> "accessibility disabled"
                    !modeAllows ->
                        "work mode ${ShakeWorkModes.describe(shake.workModes)} " +
                            "excludes screenOn=$screenOn locked=$keyguardLocked"
                    !lockScreenReady -> "no lock screen action"
                    else -> "unknown"
                }
                Log.d(TAG, "stopping detector: $reason")
            }
            detector?.stop()
            detector = null
            releaseWakeLock()
            return
        }

        if (screenOn) releaseWakeLock() else acquireWakeLock()

        val activeDetector = detector ?: ShakeGestureDetector(sensorHub) { type ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                handleGestureDetected(type)
            } else {
                mainHandler.post { handleGestureDetected(type) }
            }
        }.also { detector = it }

        if (!activeDetector.isAvailable) {
            Log.w(TAG, "stopping detector: no gyroscope or accelerometer")
            activeDetector.stop()
            detector = null
            releaseWakeLock()
            return
        }

        activeDetector.setSensitivity(
            global = shake.globalSensitivity,
            independentEnabled = shake.independentSensitivityEnabled,
            perDirection = shake.perDirectionSensitivity,
        )
        // 菜单状态下用更短的入场冷却，保证「打开菜单 → 再晃方向」不会卡在 500ms 上。
        activeDetector.setCooldownMs(
            if (menuSession != null) shake.menuGuardMs else NORMAL_COOLDOWN_MS,
        )
        // 反向忽略同理：菜单内的反方向是正常操作，不能按普通模式的 800ms 拦。
        activeDetector.setReverseIgnoreMs(
            if (menuSession != null) shake.menuGuardMs else NORMAL_REVERSE_IGNORE_MS,
        )
        // 部分机型息屏后会挂起非唤醒传感器，切屏幕状态时重新注册一次恢复采样。
        if (activeDetector.isRunning && screenChanged) {
            activeDetector.restartSensor()
        }
        activeDetector.start()
        Log.d(
            TAG,
            "detector running mode=${ShakeWorkModes.describe(shake.workModes)} " +
                "screenOn=$screenOn locked=$keyguardLocked " +
                "sensitivity=${shake.globalSensitivity} wakeLock=$wakeLockHeld",
        )
    }

    private fun handleGestureDetected(type: ShakeGestureType) {
        runCatching {
            handleGestureDetectedInternal(type)
        }.onFailure { error ->
            Log.e(TAG, "handle shake gesture failed: $type", error)
        }
    }

    private fun handleGestureDetectedInternal(type: ShakeGestureType) {
        val settings = latestSettings ?: run {
            Log.d(TAG, "gesture $type blocked: settings not loaded")
            return
        }
        val shake = settings.shakeGestureSettings
        if (!shake.enabled) {
            Log.d(TAG, "gesture $type blocked: shake disabled")
            return
        }

        val screenOn = powerManager().isInteractive
        val lockScreenActive = runtimePort.isLockScreenActive()
        if (!shake.workModes.allows(screenOn, lockScreenActive)) {
            Log.d(
                TAG,
                "gesture $type blocked: work mode ${ShakeWorkModes.describe(shake.workModes)} " +
                    "screenOn=$screenOn locked=$lockScreenActive",
            )
            return
        }
        if (!screenOn && !shake.hasLockScreenAction()) {
            Log.d(TAG, "gesture $type blocked: no lock screen action")
            return
        }

        // 锁屏/熄屏不走独立应用动作：后台拿不到可靠的前台包名，也容易在锁屏上误执行。
        val foregroundPackage = if (screenOn && !lockScreenActive) {
            runtimePort.foregroundPackage()
        } else {
            null
        }
        if (foregroundPackage != null && foregroundPackage in shake.blacklistedPackages) {
            Log.d(TAG, "gesture $type blocked: blacklisted package $foregroundPackage")
            return
        }
        if (screenOn && shake.disableInLandscape && runtimePort.isLandscape()) {
            Log.d(TAG, "gesture $type blocked: landscape disabled")
            return
        }

        // 菜单状态优先：会话有效时，方向事件只看菜单表，不再走全局映射。
        val activeMenuSession = menuSession
        if (activeMenuSession != null) {
            handleMenuGesture(activeMenuSession, shake, type, settings, screenOn)
            return
        }

        val action = resolveShakeAction(
            shake = shake,
            type = type,
            foregroundPackage = foregroundPackage,
            lockScreenActive = lockScreenActive,
            screenOn = screenOn,
        )
        if (action == GestureAction.None) {
            Log.d(TAG, "gesture $type detected, action is None")
            return
        }

        // 基础方向绑的是「晃动菜单」：进入菜单状态，等第二次带方向的晃动。
        if (action is GestureAction.OpenShakeMenu) {
            openShakeMenu(action, shake, type, screenOn)
            return
        }

        dispatchAction(action, settings, shake, type, screenOn)
    }

    /** 菜单状态下的一次晃动：命中动作就执行，没配动作也收起菜单；冷却内则只吞掉这次事件。 */
    private fun handleMenuGesture(
        session: ShakeMenuSession,
        shake: ShakeGestureSettings,
        type: ShakeGestureType,
        settings: AppSettings,
        screenOn: Boolean,
    ) {
        val outcome = ShakeMenuRuntime.decide(
            session = session,
            menus = shake.menus,
            type = type,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            guardMs = shake.menuGuardMs,
        )
        menuSession = outcome.session
        when (val decision = outcome.decision) {
            is ShakeMenuDecision.Execute -> {
                Log.i(TAG, "menu ${decision.menu.id} direction $type -> ${decision.action}")
                dismissMenuOverlay()
                if (decision.action != GestureAction.None) {
                    dispatchAction(decision.action, settings, shake, type, screenOn)
                }
            }
            is ShakeMenuDecision.Unbound -> {
                // 用户确实做完了一次方向选择，只是这个方向没配动作：收菜单，不做别的。
                Log.d(TAG, "menu ${decision.menu.id} direction $type unbound, closing")
                dismissMenuOverlay()
            }
            is ShakeMenuDecision.CoolingDown -> {
                Log.d(TAG, "menu ${decision.menu.id} direction $type ignored (cooldown)")
            }
            is ShakeMenuDecision.Expired -> {
                Log.d(TAG, "menu ${decision.menuId} expired")
                dismissMenuOverlay()
            }
            ShakeMenuDecision.NotInMenu -> Unit
        }
    }

    /** 打开「晃动菜单」：建立会话、切换冷却、按设置弹出浮层。 */
    private fun openShakeMenu(
        action: GestureAction.OpenShakeMenu,
        shake: ShakeGestureSettings,
        type: ShakeGestureType,
        screenOn: Boolean,
    ) {
        val menu = ShakeMenuRuntime.resolveMenu(shake.menus, action.menuId)
        if (menu == null) {
            Log.d(TAG, "shake menu open skipped: no menu configured")
            return
        }
        menuSession = ShakeMenuRuntime.open(menu.id, SystemClock.elapsedRealtime())
        detector?.setCooldownMs(shake.menuGuardMs)
        // 「左翻打开、右翻执行」在菜单里是正常操作，反向忽略也必须缩到同一量级。
        detector?.setReverseIgnoreMs(shake.menuGuardMs)
        mainHandler.removeCallbacks(menuDismissRunnable)
        mainHandler.postDelayed(menuDismissRunnable, ShakeMenuRuntime.DEFAULT_SESSION_TIMEOUT_MS)
        Log.i(
            TAG,
            "shake menu opened id=${menu.id} name=${menu.name} " +
                "directions=${menu.configuredDirections} guard=${shake.menuGuardMs}ms",
        )
        deliverFeedback(shake, type, action, screenOn)

        if (!shake.showMenuOverlay) {
            // 关掉「显示菜单状态」时菜单仍然生效，只是盲操作——这条日志用来区分
            // 「浮层没画出来」和「设置里根本没让它画」。
            Log.d(TAG, "shake menu overlay suppressed: showMenuOverlay=false")
            return
        }
        if (!screenOn) return
        val show = { feedbackPort.showMenuOverlay(appContext, menu) }
        val delayMs = shake.menuDisplayDelayMs
        if (delayMs > 0L) {
            // 延迟显示期间用户可能已经完成操作（或会话已被替换），只给仍然有效的会话弹层。
            mainHandler.postDelayed({ if (menuSession?.menuId == menu.id) show() }, delayMs)
        } else {
            show()
        }
    }

    /** 关掉菜单浮层并恢复普通冷却/反向忽略；会话本身由调用方清空。 */
    private fun dismissMenuOverlay() {
        menuSession = null
        mainHandler.removeCallbacks(menuDismissRunnable)
        detector?.setCooldownMs(NORMAL_COOLDOWN_MS)
        detector?.setReverseIgnoreMs(NORMAL_REVERSE_IGNORE_MS)
        feedbackPort.detachMenuOverlay()
    }

    private fun dispatchAction(
        action: GestureAction,
        settings: AppSettings,
        shake: ShakeGestureSettings,
        type: ShakeGestureType,
        screenOn: Boolean,
    ) {
        if (action == GestureAction.None) {
            // 只有真正绑定了动作的手势才给震动/动画反馈；未设置的方向保持静默。
            Log.d(TAG, "gesture $type detected, action is None")
            return
        }

        Log.i(TAG, "gesture $type executing action=$action screenOn=$screenOn")
        if (screenOn) {
            runtimePort.captureGestureForegroundPackage()
        }

        if (requiresAccessibilityService(action)) {
            dispatchAccessibilityAction(action, type, shake, screenOn, attempt = 0)
            return
        }

        runCatching {
            val ok = actionPort.execute(
                action = action,
                settings = settings,
                anchorRawX = runtimePort.screenCenterX(),
                anchorRawY = runtimePort.screenCenterY(),
            )
            if (ok) {
                Log.i(TAG, "gesture $type action $action succeeded")
            } else {
                Log.w(TAG, "gesture $type action $action returned false")
            }
        }.onFailure { error ->
            Log.e(TAG, "execute shake action failed: $action", error)
        }
        deliverFeedback(shake, type, action, screenOn)
    }

    private fun dispatchAccessibilityAction(
        action: GestureAction,
        type: ShakeGestureType,
        shake: ShakeGestureSettings,
        screenOn: Boolean,
        attempt: Int,
    ) {
        val ok = runtimePort.performAccessibilityAction(action)
        if (ok) {
            deliverFeedback(shake, type, action, screenOn)
            Log.i(TAG, "gesture $type action $action succeeded")
            return
        }
        if (attempt < 2) {
            Log.d(TAG, "gesture $type action $action retry ${attempt + 1}")
            mainHandler.postDelayed({
                dispatchAccessibilityAction(action, type, shake, screenOn, attempt + 1)
            }, 100L)
            return
        }
        Log.w(
            TAG,
            "gesture $type action $action FAILED — accessibility connected=${runtimePort.isAccessibilityConnected()}",
        )
    }

    private fun deliverFeedback(
        shake: ShakeGestureSettings,
        type: ShakeGestureType,
        action: GestureAction,
        screenOn: Boolean,
    ) {
        if (shake.vibrationFeedbackEnabled) {
            feedbackPort.vibrate(appContext)
        }
        // 熄屏时动画看不见，还可能被熄屏清屏逻辑收掉，只保留震动。
        if (screenOn && shake.animationFeedbackEnabled) {
            feedbackPort.showGestureFeedback(appContext, type, action, shake.animationColorArgb)
        }
    }

    private fun ShakeGestureSettings.hasLockScreenAction(): Boolean =
        lockScreenActions.values.any { action -> action != GestureAction.None }

    private fun acquireWakeLock() {
        if (wakeLockHeld) return
        val lock = wakeLock ?: powerManager()
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SlideIndex:Shake")
            .also { wakeLock = it }
        runCatching {
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        }.onFailure { error ->
            Log.w(TAG, "acquire wake lock failed", error)
            return
        }
        wakeLockHeld = true
        mainHandler.removeCallbacks(wakeLockRenewRunnable)
        mainHandler.postDelayed(wakeLockRenewRunnable, WAKE_LOCK_RENEW_MS)
    }

    private fun releaseWakeLock() {
        mainHandler.removeCallbacks(wakeLockRenewRunnable)
        if (!wakeLockHeld) return
        runCatching { wakeLock?.release() }
            .onFailure { error -> Log.w(TAG, "release wake lock failed", error) }
        wakeLockHeld = false
    }

    private fun requiresAccessibilityService(action: GestureAction): Boolean = when (action) {
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.OpenNotifications,
        GestureAction.OpenQuickSettings,
        GestureAction.LockScreen,
        GestureAction.Screenshot,
        GestureAction.PowerMenu,
        GestureAction.PreviousApp,
        GestureAction.KeepScreenOn,
        GestureAction.ScrollToTop,
        GestureAction.ScrollToBottom,
        -> true
        else -> false
    }

    private fun powerManager(): PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    companion object {
        private const val TAG = "ShakeGestureHost"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_MS = WAKE_LOCK_TIMEOUT_MS - 60_000L

        /** 普通模式的手势间隔，与 [ShakeGestureDetector] 的默认值保持一致。 */
        private const val NORMAL_COOLDOWN_MS = 500L

        /** 普通模式的反向忽略窗口，与 [ShakeGestureDetector] 的默认值保持一致。 */
        private const val NORMAL_REVERSE_IGNORE_MS = 800L
    }
}
