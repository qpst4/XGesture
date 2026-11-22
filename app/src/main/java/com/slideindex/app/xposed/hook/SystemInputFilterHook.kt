package com.slideindex.app.xposed.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.hookMethod
import com.slideindex.app.xposed.takeover.SystemGestureTakeoverController
import com.slideindex.app.xposed.takeover.TakeoverSessionPolicy
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * 输入层接管：hook `InputManagerService.filterInputEvent`（EdgeX 同款路径）。
 *
 * 状态通道（广播接收器）与 hook 安装完全解耦、优先建立：任何一步失败都能通过
 * 状态响应回传到 app，避免"看着装上了但没生效、又查不到原因"的黑盒。
 */
class SystemInputFilterHook(private val moduleApkPath: () -> String? = { null }) {
  @Volatile
  private var systemContext: Context? = null

  @Volatile
  private var currentClassLoader: ClassLoader? = null

  @Volatile
  private var xposedInterface: XposedInterface? = null

  @Volatile
  private var controller: SystemGestureTakeoverController? = null

  @Volatile
  private var receiverRegistered = false

  @Volatile
  private var filterHookInstalled = false

  @Volatile
  private var enableHookInstalled = false

  @Volatile
  private var startHookInstalled = false

  @Volatile
  private var inputManagerService: Any? = null

  @Volatile
  private var enableApplier: ((Boolean) -> Unit)? = null

  @Volatile
  private var forwardingFilterInstalled = false

  /** 代理自身转发事件时的重入标记，避免同一事件被反复转发。 */
  private val forwardingInProgress = ThreadLocal.withInitial { false }

  private val moduleErrors: MutableList<String> = Collections.synchronizedList(mutableListOf())
  private val eventsSeen = AtomicLong()
  private val eventsSwallowed = AtomicLong()
  private val eventsPassed = AtomicLong()
  private var applyingEnable = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private var bridgeReceiver: BroadcastReceiver? = null
  private var forwardingFilter: Any? = null
  @Volatile private var retired = false

  /** 热重载前断开旧代的系统回调，避免旧 receiver 继续回报已经卸载的 Hook。 */
  fun detach() {
    retired = true
    mainHandler.removeCallbacksAndMessages(null)
    bridgeReceiver?.let { receiver -> runCatching { systemContext?.unregisterReceiver(receiver) } }
    bridgeReceiver = null
    receiverRegistered = false
    controller?.close()
    controller = null
    val ims = inputManagerService
    val filter = forwardingFilter
    if (ims != null && filter != null) {
      runCatching {
        if (LibXposedReflect.getObjectField(ims, "mInputFilter") === filter) {
          LibXposedReflect.callMethod(ims, "setInputFilter", null)
        }
      }.onFailure { XposedLog.w(TAG, "release forwarding filter failed: ${it.message}") }
    }
    forwardingFilter = null
    xposedInterface = null
  }

  /**
   * 建立状态通道（广播接收器）。
   *
   * `onSystemServerStarting` 阶段 ActivityManagerService 往往还没就绪，
   * 此时 `registerReceiver` 会因 IActivityManager 为 null 抛 NPE，因此按固定间隔重试。
   */
  fun registerStatusChannel(classLoader: ClassLoader) {
    currentClassLoader = classLoader
    if (receiverRegistered) return
    attemptReceiverRegistration(classLoader, attempt = 0)
  }

  private fun attemptReceiverRegistration(classLoader: ClassLoader, attempt: Int) {
    if (retired) return
    if (receiverRegistered) return
    runCatching {
      val context = systemContext ?: resolveSystemContext(classLoader)
        ?: throw IllegalStateException("system context unavailable")
      systemContext = context
      registerBridgeReceiver(context)
      receiverRegistered = true
    }.onSuccess {
      XposedLog.i(TAG, "Takeover status channel registered in system_server")
      // 注册成功后再补齐：模块可能错过了 app 启动时的配置广播。
      mainHandler.post {
        ensureController()?.let { controller ->
          controller.requestSnapshotIfNeeded()
          syncFilterEnabled(controller)
        }
      }
    }.onFailure { throwable ->
      if (attempt >= MAX_RECEIVER_REGISTER_ATTEMPTS) {
        recordModuleError(describe("receiver", throwable))
      } else {
        mainHandler.postDelayed(
          { attemptReceiverRegistration(classLoader, attempt + 1) },
          RECEIVER_REGISTER_RETRY_MS,
        )
      }
    }
  }

  fun recordModuleError(message: String) {
    moduleErrors.add(message)
    XposedLog.w(TAG, message)
  }

  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    if (retired) return emptyList()
    xposedInterface = xposed
    currentClassLoader = classLoader
    if (!receiverRegistered) {
      registerStatusChannel(classLoader)
    }
    val takeoverController = ensureController()
      ?: run {
        recordModuleError("controller: unavailable")
        return emptyList()
      }

    val handles = mutableListOf<XposedInterface.HookHandle>()
    val imsClass = LibXposedReflect.findClassIfExists(
      "com.android.server.input.InputManagerService",
      classLoader,
    )
    if (imsClass == null) {
      recordModuleError("ims: InputManagerService not found")
      return handles
    }
    // 热重载发生时 IMS.start 已执行过，不能再等启动回调提供实例。
    if (inputManagerService == null) {
      inputManagerService = runCatching {
        val serviceManager = LibXposedReflect.findClass("android.os.ServiceManager", classLoader)
        LibXposedReflect.callStaticMethod(serviceManager, "getService", "input")
          ?.takeIf { imsClass.isInstance(it) }
      }.getOrNull()
    }
    if (!enableHookInstalled) {
      runCatching { handles += hookFilterEnable(xposed, imsClass, classLoader, takeoverController) }
        .onSuccess { enableHookInstalled = true }
        .onFailure { recordModuleError(describe("enableFilter", it)) }
    }
    if (!filterHookInstalled) {
      runCatching { handles += hookFilterInputEvent(xposed, imsClass, takeoverController) }
        .onSuccess { filterHookInstalled = true }
        .onFailure { recordModuleError(describe("filterInputEvent", it)) }
    }
    if (!startHookInstalled) {
      runCatching { handles += hookStart(xposed, imsClass, takeoverController) }
        .onSuccess { startHookInstalled = true }
        .onFailure { recordModuleError(describe("start", it)) }
    }
    XposedLog.i(
      TAG,
      "System input filter hook installed: proxy=$forwardingFilterInstalled " +
        "enable=$enableHookInstalled start=$startHookInstalled receiver=$receiverRegistered " +
        "hooks=${handles.size}",
    )
    return handles
  }

  /** 状态请求到达时按需重试未完成的步骤（例如首次安装部分失败）。 */
  private fun retryInstallIfNeeded() {
    if (filterHookInstalled && enableHookInstalled && startHookInstalled && receiverRegistered) return
    val xposed = xposedInterface ?: return
    val classLoader = currentClassLoader ?: return
    install(xposed, classLoader)
  }

  private fun ensureController(): SystemGestureTakeoverController? {
    controller?.let { return it }
    val context = systemContext ?: return null
    return SystemGestureTakeoverController(context) { message -> XposedLog.i(TAG, message) }
      .also { controller = it }
  }

  private fun describe(step: String, throwable: Throwable): String =
    "$step: ${throwable.javaClass.simpleName}: ${throwable.message}"

  private fun hookFilterEnable(
    xposed: XposedInterface,
    imsClass: Class<*>,
    classLoader: ClassLoader,
    takeoverController: SystemGestureTakeoverController,
  ): List<XposedInterface.HookHandle> {
    val handles = mutableListOf<XposedInterface.HookHandle>()
    handles += hookEnableLikeMethod(xposed, imsClass, takeoverController, "ims")
    handles += hookEnableLikeMethod(
      xposed,
      LibXposedReflect.findClassIfExists(
        "com.android.server.input.NativeInputManagerService\$NativeImpl",
        classLoader,
      ),
      takeoverController,
      "native",
    )

    // 关键：必须装一个过滤器，native 才会在"派发给手势监听通道之前"就咨询过滤器，
    // 否则SystemUI 的左右返回手势会绕过我们的丢弃（实测：不装过滤器时返回手势照样触发）。
    // 它只负责把未被接管的事件原样转发回输入管线。
    currentClassLoader?.let { ensureForwardingFilter(it) }

    return handles
  }

  /**
   * 第一层（已验证有效）：在 Java 回调入口丢弃被接管的事件。
   *
   * 该路径不依赖 `mInputFilter` 是谁——即使系统或无障碍把过滤器换掉，这里依然能拦住窗口派发。
   */
  private fun hookFilterInputEvent(
    xposed: XposedInterface,
    imsClass: Class<*>,
    takeoverController: SystemGestureTakeoverController,
  ): List<XposedInterface.HookHandle> {
    val methods = imsClass.declaredMethods.filter { it.name == FILTER_INPUT_EVENT }
    if (methods.isEmpty()) throw IllegalStateException("filterInputEvent not found")
    return methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun beforeHookedMethod(param: HookParam) {
            val event = param.arg(0) as? MotionEvent ?: return
            val policyFlags = (param.arg(1) as? Int) ?: 0
            eventsSeen.incrementAndGet()
            if (!takeoverController.handleEvent(event, policyFlags)) {
              eventsPassed.incrementAndGet()
              return
            }
            eventsSwallowed.incrementAndGet()
            param.returnEarly = true
            param.result = false
          }
        },
        id = "xgesture_input_filter_event_${method.parameterTypes.joinToString { it.simpleName }}",
      )
    }
  }

  private fun hookEnableLikeMethod(
    xposed: XposedInterface,
    target: Class<*>?,
    takeoverController: SystemGestureTakeoverController,
    idSuffix: String,
  ): List<XposedInterface.HookHandle> {
    target ?: return emptyList()
    val methods = target.declaredMethods.filter { it.name == SET_INPUT_FILTER_ENABLED }
    return methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun afterHookedMethod(param: HookParam) {
            if (applyingEnable) return
            if (!takeoverController.hasEnabledGroups()) return
            // 期望开启时若被别的组件关掉，这里补回一次。
            applyFilterEnabled(true)
          }
        },
        id = "xgesture_input_filter_enable_${idSuffix}_${
          method.parameterTypes.joinToString { it.simpleName }
        }",
      )
    }
  }

  private fun hookStart(
    xposed: XposedInterface,
    imsClass: Class<*>,
    takeoverController: SystemGestureTakeoverController,
  ): List<XposedInterface.HookHandle> {
    val methods = imsClass.declaredMethods.filter { it.name == "start" && it.parameterCount == 0 }
    return methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun afterHookedMethod(param: HookParam) {
            inputManagerService = param.thisObject
            mainHandler.post {
              takeoverController.requestSnapshotIfNeeded()
              takeoverController.ensureBridgeBound()
              syncFilterEnabled(takeoverController)
            }
          }
        },
        id = "xgesture_input_filter_start",
      )
    }
  }

  private fun registerBridgeReceiver(context: Context) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(receiverContext: Context?, intent: Intent?) {
        if (retired) return
        val takeoverController = controller
        when (intent?.action) {
          ModuleHookBridgeContract.ACTION_CONFIG_CHANGED -> {
            val configJson = intent.getStringExtra(ModuleHookBridgeContract.EXTRA_CONFIG_JSON)
            ensureController()?.onConfigBroadcast(configJson)
            // 剪贴板白名单的 reader 不会自己收广播（它的 HookConfigReader 只被这里转手才会更新），
            // 漏掉这一行 = 白名单只能靠磁盘那份旧快照，用户后加的包永远不生效。
            ClipboardWhitelistHook.onConfigBroadcast(configJson)
            persistSystemSnapshot(configJson)
            ensureController()?.let { syncFilterEnabled(it) }
          }
          ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST -> {
            retryInstallIfNeeded()
            // 顺带拉一次配置：app 先于模块启动时会错过变更广播。
            ensureController()?.requestSnapshotIfNeeded()
            sendStatusResponse(context)
          }
          ModuleHookBridgeContract.ACTION_HOST_STATE_CHANGED -> {
            // app 侧 overlay 宿主就绪/失活：就绪时立刻重绑，失活时立刻收掉吞流会话。
            ensureController()?.onHostStateChanged(
              intent.getBooleanExtra(ModuleHookBridgeContract.EXTRA_HOST_READY, false),
            )
          }
          Intent.ACTION_SCREEN_OFF -> {
            takeoverController?.endActiveSession(TakeoverSessionPolicy.REASON_RESET)
          }
          Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
            ensureController()?.requestSnapshotIfNeeded()
            ensureController()?.let { syncFilterEnabled(it) }
          }
        }
      }
    }
    val filter = IntentFilter().apply {
      addAction(ModuleHookBridgeContract.ACTION_CONFIG_CHANGED)
      addAction(ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST)
      addAction(ModuleHookBridgeContract.ACTION_HOST_STATE_CHANGED)
      addAction(Intent.ACTION_SCREEN_OFF)
      addAction(Intent.ACTION_SCREEN_ON)
      addAction(Intent.ACTION_USER_PRESENT)
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    bridgeReceiver = receiver
  }

  /**
   * 模块侧也持久化一份到 `/data/system`（system_server 有写权限，电话进程没有）。
   *
   * 为什么需要：磁盘兜底顺序是「app 外部快照 → app 设备保护目录 → 这份遗留快照」，
   * 而前两份在部分 ROM（实测 Flyme/Android 16）system_server 读不到 —— 外部目录被 FUSE 拒、
   * 设备保护目录是 app 私有。于是这份遗留快照就成了冷启动唯一读得到的配置：
   * 不刷新它，模块重启后会一直用几天前那份旧名单（现象：白名单里只有自己）。
   */
  private fun persistSystemSnapshot(json: String?) {
    if (json.isNullOrBlank()) return
    runCatching {
      val dir = File(ModuleHookBridgeContract.SYSTEM_SNAPSHOT_DIR)
      if (!dir.isDirectory) dir.mkdirs()
      File(dir, ModuleHookBridgeContract.SNAPSHOT_FILE_NAME).writeText(json)
    }.onFailure { XposedLog.w(TAG, "persist system snapshot failed: ${it.message}") }
  }

  private fun sendStatusResponse(context: Context, attempt: Int = 0) {
    val report = buildStatusReport()
    // 桥在重连中、或 app 宿主刚随进程启动还没挂上时，稍等一下再回，避免 app 误判为故障。
    val waitForRecovery = report.detail.contains(SystemGestureTakeoverController.STATUS_BRIDGE_PENDING) ||
      report.detail.contains(SystemGestureTakeoverController.STATUS_HOST_NOT_READY)
    if (waitForRecovery && attempt < BRIDGE_WAIT_ATTEMPTS) {
      mainHandler.postDelayed(
        { sendStatusResponse(context, attempt + 1) },
        BRIDGE_WAIT_INTERVAL_MS,
      )
      return
    }
    runCatching {
      context.sendBroadcast(
        Intent(ModuleHookBridgeContract.ACTION_MODULE_STATUS_RESPONSE).apply {
          setPackage(ModuleHookBridgeContract.MODULE_PACKAGE)
          addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
          putExtra(ModuleHookBridgeContract.EXTRA_STATUS_ACTIVE, report.state == ACTIVE_STATE)
          putExtra(ModuleHookBridgeContract.EXTRA_STATUS_STATE, report.state)
          putExtra(ModuleHookBridgeContract.EXTRA_STATUS_DETAIL, report.detail)
        },
      )
    }.onFailure { XposedLog.w(TAG, "Status response failed: ${it.message}") }
  }

  private data class StatusReport(val state: String, val detail: String)

  /**
   * 状态串：`<state>:hook=ok,proxy=ok,enable=ok,start=ok,receiver=ok,controller=<detail>[,errors=...]`。
   *
   * 三态（同时随 [ModuleHookBridgeContract.EXTRA_STATUS_STATE] 回传）：
   *
   * - `ready`：接管**真的会生效**——hook 装齐、输入过滤器可用、且控制器已连上 app 事件桥并确认 app 宿主就绪。
   * - `armed`：模块本身装好了，但接管此刻不生效。`controller=` 段给出原因：
   *   `disabled`（三个开关全关）/ `bridge-pending`（事件桥没连上）/ `host-not-ready`（桥在但 app 宿主没起来）。
   * - `not-ready`：模块没装齐（hook / 状态通道缺失）。
   *
   * 旧实现把 `bridge-pending` 也算 `ready`，于是出现了「状态显示已就绪、实际一个事件都没接管」的假阳性。
   */
  private fun buildStatusReport(): StatusReport {
    val controllerDetail = controller?.statusDetail() ?: CONTROLLER_NO_CONTROLLER
    val errors = synchronized(moduleErrors) { moduleErrors.toList() }
    val steps = buildString {
      append("hook=").append(if (filterHookInstalled) "ok" else "fail")
      append(",proxy=").append(if (forwardingFilterInstalled) "ok" else "fail")
      append(",enable=").append(if (enableHookInstalled) "ok" else "fail")
      append(",start=").append(if (startHookInstalled) "ok" else "fail")
      append(",receiver=").append(if (receiverRegistered) "ok" else "fail")
      append(",controller=").append(controllerDetail)
      append(",events=").append(eventsSeen.get())
      append(",passed=").append(eventsPassed.get())
      append(",swallowed=").append(eventsSwallowed.get())
      // 模块代码版本 + 剪贴板白名单 hook 状态：app 侧据此判断"是不是覆盖安装后还没重启"。
      append(",code=").append(ModuleHookBridgeContract.MODULE_CODE_VERSION)
      moduleApkPath()?.let { append(",apk=").append(it) }
      append(",clip=").append(ClipboardWhitelistHook.installStatus)
      // 模块当前生效的白名单大小：app 侧拿它和本地设置比对，不一致就是"配置没下发到 system_server"。
      append(",wl=").append(ClipboardWhitelistHook.activeWhitelist().size)
      if (errors.isNotEmpty()) {
        append(",errors=").append(errors.joinToString(" | "))
      }
    }
    val moduleArmed = filterHookInstalled &&
      enableHookInstalled &&
      startHookInstalled &&
      receiverRegistered
    // 吞流靠"原样转发过滤器"（proxy）或 Java 侧 filterInputEvent hook，两者至少要有一个可用。
    val transportReady = filterHookInstalled || forwardingFilterInstalled
    val active = moduleArmed &&
      transportReady &&
      controllerDetail.startsWith(SystemGestureTakeoverController.STATUS_READY_PREFIX)
    val state = when {
      active -> ACTIVE_STATE
      moduleArmed -> ModuleHookBridgeContract.STATUS_STATE_ARMED
      else -> ModuleHookBridgeContract.STATUS_STATE_NOT_READY
    }
    return StatusReport(state = state, detail = "$state:$steps")
  }

  /** 让 native 层的 InputFilter 开关与当前接管开关保持一致。 */
  private fun syncFilterEnabled(takeoverController: SystemGestureTakeoverController) {
    val enabled = takeoverController.hasEnabledGroups()
    val ims = inputManagerService
    if (ims != null) {
      currentClassLoader?.let { resolveEnableApplier(ims, it) }
    }
    if (enabled) {
      currentClassLoader?.let { ensureForwardingFilter(it) }
    }
    applyFilterEnabled(enabled)
  }

  private fun applyFilterEnabled(enabled: Boolean) {
    if (applyingEnable) return
    applyingEnable = true
    runCatching { enableApplier?.invoke(enabled) }
      .onFailure { XposedLog.w(TAG, "setInputFilterEnabled($enabled) failed: ${it.message}") }
    applyingEnable = false
  }

  /**
   * 安装"原样转发"的 IInputFilter（EdgeX 同款）。
   *
   * 注意：它会让每条输入事件都经由 `IInputFilterHost.sendInputEvent` 回到派发队列，
   * 因此 app 侧在被接管会话期间**不得**再走"注入点击放行"（会形成 app↔模块回环）。
   */
  private fun ensureForwardingFilter(classLoader: ClassLoader) {
    if (forwardingFilterInstalled) return
    val ims = inputManagerService ?: return
    val filterClass = LibXposedReflect.findClassIfExists("android.view.IInputFilter", classLoader)
      ?: run {
        recordModuleError("forwardingFilter: IInputFilter not found")
        return
      }
    val hostClass = LibXposedReflect.findClassIfExists("android.view.IInputFilterHost", classLoader)
      ?: run {
        recordModuleError("forwardingFilter: IInputFilterHost not found")
        return
      }
    val sendInputEvent = runCatching {
      hostClass.getMethod("sendInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType)
    }.getOrNull() ?: run {
      recordModuleError("forwardingFilter: sendInputEvent not found")
      return
    }

    var hostRef: Any? = null
    val proxy = Proxy.newProxyInstance(classLoader, arrayOf(filterClass), InvocationHandler { _, method, args ->
      when (method.name) {
        "install" -> {
          hostRef = args?.firstOrNull()
          null
        }
        "filterInputEvent" -> {
          val host = hostRef
          val event = args?.firstOrNull() as? android.view.InputEvent
          val policyFlags = (args?.getOrNull(1) as? Int) ?: 0
          if (host != null && event != null) {
            handleFilteredEvent(host, event, policyFlags, sendInputEvent)
          }
          null
        }
        "asBinder" -> android.os.Binder()
        else -> null
      }
    })
    runCatching {
      LibXposedReflect.callMethod(ims, "setInputFilter", proxy)
      forwardingFilter = proxy
      forwardingFilterInstalled = true
      XposedLog.i(TAG, "Forwarding input filter installed")
    }.onFailure { recordModuleError(describe("forwardingFilter", it)) }
  }

  /**
   * EdgeX 式丢弃：命中接管区域就**不转发**（事件在输入层消失），其余原样送回管线。
   *
   * 不再依赖 `filterInputEvent` 的返回值——A16 上该方法已经不是可靠的可改返回值路径。
   */
  private fun handleFilteredEvent(
    host: Any,
    event: android.view.InputEvent,
    policyFlags: Int,
    sendInputEvent: java.lang.reflect.Method,
  ) {
    if (forwardingInProgress.get() == true) {
      // 正在转发中再次进入：只放行，避免同一事件被反复转发。
      runCatching { sendInputEvent.invoke(host, event, policyFlags) }
      return
    }
    val motion = event as? MotionEvent
    if (motion != null) {
      eventsSeen.incrementAndGet()
      val takeoverController = controller
      if (takeoverController != null && takeoverController.handleEvent(motion, policyFlags)) {
        eventsSwallowed.incrementAndGet()
        return
      }
      eventsPassed.incrementAndGet()
    }
    forwardingInProgress.set(true)
    try {
      runCatching { sendInputEvent.invoke(host, event, policyFlags) }
    } finally {
      forwardingInProgress.set(false)
    }
  }

  /** 解析“启用/停用 InputFilter”的可用策略；只做能力探测，不改变当前状态。 */
  private fun resolveEnableApplier(ims: Any, classLoader: ClassLoader) {
    if (enableApplier != null) return

    ims.javaClass.declaredMethods
      .firstOrNull { it.name == SET_INPUT_FILTER_ENABLED && it.parameterCount == 1 }
      ?.let { method ->
        method.isAccessible = true
        enableApplier = { value -> method.invoke(ims, value) }
        XposedLog.i(TAG, "InputFilter strategy: InputManagerService.$SET_INPUT_FILTER_ENABLED")
        return
      }

    val native = runCatching { LibXposedReflect.getObjectField(ims, "mNative") }.getOrNull()
    val nativeMethod = native?.javaClass?.declaredMethods
      ?.firstOrNull { it.name == SET_INPUT_FILTER_ENABLED && it.parameterCount == 1 }
    if (native != null && nativeMethod != null) {
      nativeMethod.isAccessible = true
      enableApplier = { value -> nativeMethod.invoke(native, value) }
      XposedLog.i(TAG, "InputFilter strategy: NativeImpl.$SET_INPUT_FILTER_ENABLED")
      return
    }

    val legacyMethod = ims.javaClass.declaredMethods
      .firstOrNull { it.name == NATIVE_SET_INPUT_FILTER_ENABLED && it.parameterCount == 2 }
    if (legacyMethod != null) {
      legacyMethod.isAccessible = true
      val pointer = runCatching { readNativePtr(ims) }.getOrNull()
      if (pointer != null) {
        enableApplier = { value -> legacyMethod.invoke(null, pointer, value) }
        XposedLog.i(TAG, "InputFilter strategy: $NATIVE_SET_INPUT_FILTER_ENABLED")
        return
      }
    }
    recordModuleError("enableStrategy: none (android=${Build.VERSION.SDK_INT})")
  }

  private fun readNativePtr(ims: Any): Long {
    for (field in listOf("mPtr", "mNativePtr")) {
      val value = runCatching { LibXposedReflect.getLongField(ims, field) }.getOrNull()
      if (value != null && value != 0L) return value
    }
    throw NoSuchFieldException("mPtr")
  }

  private fun resolveSystemContext(classLoader: ClassLoader): Context? =
    runCatching {
      val activityThreadClass = LibXposedReflect.findClass("android.app.ActivityThread", classLoader)
      val currentActivityThread =
        LibXposedReflect.callStaticMethod(activityThreadClass, "currentActivityThread")
      LibXposedReflect.callMethod(currentActivityThread!!, "getSystemContext") as Context
    }.getOrNull()

  private companion object {
    const val TAG = "SlideIndexInputFilter"
    const val FILTER_INPUT_EVENT = "filterInputEvent"
    const val SET_INPUT_FILTER_ENABLED = "setInputFilterEnabled"
    const val NATIVE_SET_INPUT_FILTER_ENABLED = "nativeSetInputFilterEnabled"
    const val CONTROLLER_NO_CONTROLLER = "no-controller"
    const val ACTIVE_STATE = ModuleHookBridgeContract.STATUS_STATE_READY
    const val RECEIVER_REGISTER_RETRY_MS = 500L
    const val MAX_RECEIVER_REGISTER_ATTEMPTS = 40
    const val BRIDGE_WAIT_ATTEMPTS = 5
    const val BRIDGE_WAIT_INTERVAL_MS = 300L
  }
}
