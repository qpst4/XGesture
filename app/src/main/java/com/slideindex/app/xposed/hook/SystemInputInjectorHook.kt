package com.slideindex.app.xposed.hook

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.hookMethod
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

class SystemInputInjectorHook {
  private var registered = false
  private var registerAttempts = 0
  private var amsSystemReadyHooked = false
  private var broadcastHooked = false
  private var lastAutoInputCallerUid = -1
  private var lastAutoInputCallerUidAt = 0L
  private val mainHandler = Handler(Looper.getMainLooper())
  private var inputManagerInstance: Any? = null
  private var injectMethod: Method? = null
  private var injectMethodParamCount = 0
  private var receiverContext: Context? = null
  private var registeredReceiver: BroadcastReceiver? = null
  @Volatile private var retired = false

  fun detach() {
    retired = true
    mainHandler.removeCallbacksAndMessages(null)
    registeredReceiver?.let { receiver -> runCatching { receiverContext?.unregisterReceiver(receiver) } }
    registeredReceiver = null
    receiverContext = null
    registered = false
  }

  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    return runCatching {
      XposedLog.i(TAG, "Installing system input injector in system_server")
      val handles = mutableListOf<XposedInterface.HookHandle>()
      handles += hookBroadcastCallerUid(xposed, classLoader)
      handles += hookAmsSystemReadyFallback(xposed, classLoader)
      val systemContext = resolveSystemContext(classLoader)
      if (systemContext != null) {
        scheduleRegister(systemContext, classLoader)
      } else {
        XposedLog.w(TAG, "System context unavailable, waiting for systemReady")
      }
      handles
    }.getOrElse {
      XposedLog.e(TAG, "SystemInputInjectorHook failed", it)
      emptyList()
    }
  }

  private fun hookBroadcastCallerUid(
    xposed: XposedInterface,
    classLoader: ClassLoader,
  ): List<XposedInterface.HookHandle> {
    if (broadcastHooked) return emptyList()
    val queueClass = LibXposedReflect.findClassIfExists("com.android.server.am.BroadcastQueueImpl", classLoader)
      ?: LibXposedReflect.findClassIfExists("com.android.server.am.BroadcastQueue", classLoader)
      ?: return emptyList()
    val methods = queueClass.declaredMethods.filter { it.name == "enqueueBroadcastLocked" }
    if (methods.isEmpty()) {
      Log.w(TAG, "BroadcastQueue.enqueueBroadcastLocked not found")
      return emptyList()
    }
    val handles = methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun afterHookedMethod(param: HookParam) {
            val record = param.args.firstOrNull() ?: return
            val intent = extractBroadcastIntent(record) ?: return
            if (intent.action != OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT) return
            val (callerUid, callerPkg) = extractCallerInfo(record)
            if (callerUid >= 0) {
              lastAutoInputCallerUid = callerUid
              lastAutoInputCallerUidAt = SystemClock.elapsedRealtime()
            }
            Log.d(TAG, "Broadcast enqueue: callerUid=$callerUid callerPkg=$callerPkg")
          }
        },
        id = "broadcast_caller_${method.parameterTypes.joinToString { it.simpleName }}",
      )
    }
    broadcastHooked = true
    XposedLog.i(TAG, "Hooked BroadcastQueue.enqueueBroadcastLocked for caller uid")
    return handles
  }

  private fun extractBroadcastIntent(record: Any): Intent? {
    if (record is Intent) return record
    runCatching {
      val intent = LibXposedReflect.getObjectField(record, "intent")
      if (intent is Intent) return intent
    }
    runCatching {
      val intent = LibXposedReflect.callMethod(record, "getIntent")
      if (intent is Intent) return intent
    }
    return null
  }

  private fun extractCallerInfo(record: Any): Pair<Int, String?> {
    for (fieldName in listOf("callerUid", "callingUid", "uid")) {
      runCatching {
        val uid = LibXposedReflect.getIntField(record, fieldName)
        if (uid >= 0) {
          val pkg = runCatching {
            LibXposedReflect.getObjectField(record, "callerPackage") as? String
          }.getOrNull()
          return uid to pkg
        }
      }
    }
    runCatching {
      val callerApp = LibXposedReflect.getObjectField(record, "callerApp") ?: return -1 to null
      val uid = LibXposedReflect.getIntField(callerApp, "uid")
      if (uid >= 0) return uid to null
    }
    return -1 to null
  }

  private fun resolveSendingUid(receiver: BroadcastReceiver): Int {
    val cachedUid = lastAutoInputCallerUid
    if (cachedUid >= 0) {
      val ageMs = SystemClock.elapsedRealtime() - lastAutoInputCallerUidAt
      if (ageMs in 0..CACHED_UID_TTL_MS) return cachedUid
    }
    if (Build.VERSION.SDK_INT >= 34) {
      runCatching {
        val uid = LibXposedReflect.callMethod(receiver, "getSendingUid") as? Int
        if (uid != null && uid >= 0) return uid
      }
    }
    return -1
  }

  private fun resolvePackagesForUid(context: Context, uid: Int): Set<String> {
    if (uid < 0) return emptySet()
    return context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
  }

  private fun shouldAllowAutoInputSender(
    sendingUid: Int,
    systemContextUid: Int,
    senderPackages: Set<String>,
  ): Boolean {
    if (sendingUid == -1) return true
    if (sendingUid == 1000 || sendingUid == 1001 || sendingUid == systemContextUid) return true
    if (senderPackages.contains(MODULE_PACKAGE)) return true
    if (senderPackages.any { it in TRUSTED_SENDER_PACKAGES }) return true
    return false
  }

  private fun resolveSystemContext(classLoader: ClassLoader): Context? =
    runCatching {
      val activityThreadClass = LibXposedReflect.findClass("android.app.ActivityThread", classLoader)
      val currentActivityThread = LibXposedReflect.callStaticMethod(activityThreadClass, "currentActivityThread")
      LibXposedReflect.callMethod(currentActivityThread!!, "getSystemContext") as Context
    }.getOrNull()

  private fun hookAmsSystemReadyFallback(
    xposed: XposedInterface,
    classLoader: ClassLoader,
  ): List<XposedInterface.HookHandle> {
    if (amsSystemReadyHooked) return emptyList()
    val amsClass = LibXposedReflect.findClass("com.android.server.am.ActivityManagerService", classLoader)
    val methods = amsClass.declaredMethods.filter { it.name == "systemReady" }
    if (methods.isEmpty()) {
      Log.w(TAG, "ActivityManagerService.systemReady not found")
      return emptyList()
    }
    val handles = methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun afterHookedMethod(param: HookParam) {
            val systemContext = resolveSystemContext(classLoader) ?: return
            XposedLog.i(TAG, "systemReady fired, scheduling receiver registration")
            scheduleRegister(systemContext, classLoader)
          }
        },
        id = "ams_system_ready_${method.parameterTypes.joinToString { it.simpleName }}",
      )
    }
    amsSystemReadyHooked = true
    XposedLog.i(TAG, "Hooked ActivityManagerService.systemReady fallback")
    return handles
  }

  private fun scheduleRegister(context: Context, classLoader: ClassLoader) {
    if (retired) return
    if (registered) return
    mainHandler.postDelayed({
      runCatching { registerReceiver(context, classLoader) }
        .onFailure { XposedLog.e(TAG, "registerReceiver attempt failed", it) }
      if (!registered && registerAttempts < MAX_REGISTER_ATTEMPTS) {
        scheduleRegister(context, classLoader)
      } else if (!registered) {
        XposedLog.e(TAG, "Giving up receiver registration after $registerAttempts attempts")
      }
    }, DELAY_REGISTER_MS)
  }

  private fun registerReceiver(context: Context, classLoader: ClassLoader) {
    if (retired) return
    if (registered) return
    registerAttempts++
    if (!resolveInputInjector(classLoader)) {
      Log.w(TAG, "Input injector unresolved on attempt $registerAttempts")
      return
    }
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        val attemptId = intent.getLongExtra(OtpAutoInputBroadcastContract.EXTRA_ATTEMPT_ID, 0L)
        val isProbe = intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_PROBE, false)
        if (!isProbe &&
          !intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_ALLOW_SYSTEM_INJECT, true)
        ) {
          Log.d(TAG, "System inject disabled by app setting, skipping")
          sendAutoInputResult(
            context,
            attemptId,
            false,
            OtpAutoInputBroadcastContract.SystemInjectReason.INJECT_DISABLED,
          )
          return
        }
        // 这里曾用 `if (isOrderedBroadcast && resultCode != 0) return` 判断"已被高优先级接收者处理"。
        // 那个判断是错的：App 侧用 sendOrderedBroadcast(intent, null) 发送，首个接收者看到的初始
        // resultCode 是 Activity.RESULT_OK(-1) 而不是 0，于是本方法每次都在这里静默 return，
        // 既不回执也不 abort —— App 侧表现为探测超时（状态行「LSPosed 系统注入：未就绪」）与自动
        // 填充 timeout。真正的"已被处理"信号是 abortBroadcast()，不需要这个前置返回。
        val sendingUid = resolveSendingUid(this)
        val senderPackages = resolvePackagesForUid(context, sendingUid)
        if (!shouldAllowAutoInputSender(sendingUid, context.applicationInfo.uid, senderPackages)) {
          Log.w(
            TAG,
            "Rejected auto-input from uid=$sendingUid packages=${senderPackages.joinToString()}",
          )
          sendAutoInputResult(
            context,
            attemptId,
            false,
            OtpAutoInputBroadcastContract.SystemInjectReason.UID_REJECTED,
          )
          return
        }
        if (isProbe) {
          sendAutoInputResult(
            context,
            attemptId,
            true,
            OtpAutoInputBroadcastContract.SystemInjectReason.PROBE,
          )
          if (isOrderedBroadcast) abortBroadcast()
          XposedLog.i(TAG, "Probe OK: system inject receiver is active in system_server")
          return
        }
        val request = OtpAutoInputBroadcastContract.readRequest(intent)
        if (request == null) {
          sendAutoInputResult(
            context,
            attemptId,
            false,
            OtpAutoInputBroadcastContract.SystemInjectReason.INVALID_REQUEST,
          )
          return
        }
        Log.i(
          TAG,
          "System inject request: codeLen=${request.code.length} autoEnter=${request.autoEnter}",
        )
        XposedLog.i(TAG, "System inject request codeLen=${request.code.length}")
        val interval = request.inputIntervalMs.coerceAtMost(MAX_SYNC_INPUT_INTERVAL_MS)
        val result = performInjectText(
          request.code,
          request.autoEnter,
          interval,
          request.allowPaste,
          classLoader,
        )
        sendAutoInputResult(context, request.attemptId, result.success, result.reason)
        if (result.success) {
          abortBroadcast()
          XposedLog.i(TAG, "System inject success, aborting accessibility fallback")
        } else {
          Log.w(TAG, "System inject failed (${result.reason}), allowing accessibility fallback")
        }
      }
    }
    val filter = IntentFilter(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT).apply {
      priority = OtpAutoInputBroadcastContract.RECEIVER_PRIORITY_SYSTEM
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    receiverContext = context
    registeredReceiver = receiver
    registered = true
    XposedLog.i(TAG, "SystemInputInjectorHook receiver registered in system_server (process ready)")
  }

  private data class InjectResult(val success: Boolean, val reason: String)

  private fun performInjectText(
    text: String,
    autoEnter: Boolean,
    intervalMs: Long,
    allowPaste: Boolean,
    classLoader: ClassLoader,
  ): InjectResult {
    if (!resolveInputInjector(classLoader)) {
      return InjectResult(
        false,
        OtpAutoInputBroadcastContract.SystemInjectReason.MANAGER_UNRESOLVED,
      )
    }
    val manager = inputManagerInstance ?: return InjectResult(
      false,
      OtpAutoInputBroadcastContract.SystemInjectReason.MANAGER_UNRESOLVED,
    )
    val method = injectMethod ?: return InjectResult(
      false,
      OtpAutoInputBroadcastContract.SystemInjectReason.INJECT_METHOD_UNRESOLVED,
    )
    val mode = resolveInjectMode(classLoader)
    val direct = runCatching {
      val keyMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
      var injectedCount = 0
      for ((index, ch) in text.withIndex()) {
        val events = keyMap.getEvents(charArrayOf(ch)) ?: continue
        for (event in events) {
          event.source = InputDeviceSourceKeyboard
          if (invokeInject(manager, method, event, mode)) {
            injectedCount++
          }
        }
        // 输入间隔按"每个字符"等一次（不是 down/up 各一次），最后一个字符后不再等。
        if (intervalMs > 0 && index < text.lastIndex) {
          Thread.sleep(intervalMs)
        }
      }
      if (autoEnter) {
        val now = SystemClock.uptimeMillis()
        injectKeyEvent(manager, method, mode, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, now)
        injectKeyEvent(manager, method, mode, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, now)
      }
      if (injectedCount > 0 || autoEnter) {
        InjectResult(true, OtpAutoInputBroadcastContract.SystemInjectReason.OK)
      } else {
        InjectResult(false, OtpAutoInputBroadcastContract.SystemInjectReason.NO_KEY_EVENTS)
      }
    }.getOrElse {
      Log.e(TAG, "performInjectText failed", it)
      InjectResult(false, OtpAutoInputBroadcastContract.SystemInjectReason.INJECT_EXCEPTION)
    }
    if (direct.success || !allowPaste) return direct
    // 第二层降级：按键注入失败时改用 Ctrl+V 粘贴。
    // 只在 App 侧确认剪贴板里已放入本次验证码（"提取后自动复制"开启）时才允许，
    // 否则可能把剪贴板里的旧内容粘进输入框。
    return runCatching {
      Thread.sleep(PASTE_FALLBACK_DELAY_MS)
      injectPasteShortcut(manager, method, mode)
      XposedLog.i(TAG, "Direct inject failed (${direct.reason}), fell back to Ctrl+V paste")
      InjectResult(true, OtpAutoInputBroadcastContract.SystemInjectReason.PASTE_FALLBACK)
    }.getOrElse {
      Log.e(TAG, "Ctrl+V paste fallback failed", it)
      direct
    }
  }

  /** Ctrl+V：两个带 CTRL 修饰的按键事件。 */
  private fun injectPasteShortcut(manager: Any, method: Method, mode: Int) {
    val now = SystemClock.uptimeMillis()
    val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
    injectKeyEvent(manager, method, mode, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, now, meta)
    injectKeyEvent(manager, method, mode, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V, now, meta)
  }

  private fun injectKeyEvent(
    manager: Any,
    method: Method,
    mode: Int,
    action: Int,
    keyCode: Int,
    eventTime: Long,
    metaState: Int = 0,
  ) {
    val event = KeyEvent(
      eventTime,
      eventTime,
      action,
      keyCode,
      if (metaState != 0) 1 else 0,
      metaState,
      -1,
      0,
      0,
      InputDeviceSourceKeyboard,
    )
    invokeInject(manager, method, event, mode)
  }

  private fun invokeInject(manager: Any, method: Method, event: InputEvent, mode: Int): Boolean {
    val result = when (injectMethodParamCount) {
      2 -> method.invoke(manager, event, mode)
      3 -> method.invoke(manager, event, mode, 0)
      else -> return false
    }
    return when (result) {
      is Boolean -> result
      is Number -> result.toInt() != 0
      else -> true
    }
  }

  private fun resolveInputInjector(classLoader: ClassLoader): Boolean {
    if (inputManagerInstance != null && injectMethod != null) return true
    val classNames = if (Build.VERSION.SDK_INT >= 34) {
      listOf("android.hardware.input.InputManagerGlobal", "android.hardware.input.InputManager")
    } else {
      listOf("android.hardware.input.InputManager", "android.hardware.input.InputManagerGlobal")
    }
    for (className in classNames) {
      val managerClass = LibXposedReflect.findClassIfExists(className, classLoader) ?: continue
      val instance = resolveManagerInstance(managerClass) ?: continue
      val method = findInjectMethod(managerClass) ?: continue
      inputManagerInstance = instance
      injectMethod = method
      injectMethodParamCount = method.parameterTypes.size
      XposedLog.i(TAG, "Resolved input injector via $className (${method.name}, params=$injectMethodParamCount)")
      return true
    }
    return false
  }

  private fun resolveManagerInstance(managerClass: Class<*>): Any? {
    runCatching {
      val getInstance = LibXposedReflect.findMethodExactIfExists(managerClass, "getInstance")
      if (getInstance != null) {
        return LibXposedReflect.callStaticMethod(managerClass, "getInstance")
      }
    }
    runCatching {
      val getInstance = managerClass.getMethod("getInstance")
      return getInstance.invoke(null)
    }
    return null
  }

  private fun findInjectMethod(managerClass: Class<*>): Method? {
    val candidates = (managerClass.declaredMethods + managerClass.methods)
      .distinctBy { "${it.name}#${it.parameterTypes.joinToString()}" }
      .filter { it.name == "injectInputEvent" }
    val twoArg = candidates.firstOrNull {
      val params = it.parameterTypes
      params.size == 2 &&
        InputEvent::class.java.isAssignableFrom(params[0]) &&
        params[1] == Int::class.javaPrimitiveType
    }
    if (twoArg != null) {
      twoArg.isAccessible = true
      return twoArg
    }
    val threeArg = candidates.firstOrNull {
      val params = it.parameterTypes
      params.size == 3 &&
        InputEvent::class.java.isAssignableFrom(params[0]) &&
        params[1] == Int::class.javaPrimitiveType &&
        params[2] == Int::class.javaPrimitiveType
    }
    threeArg?.isAccessible = true
    return threeArg
  }

  /**
   * 注入模式：优先 ASYNC —— 不等每次按键派发完成就返回。
   *
   * WAIT_FOR_FINISH 模式下每注入一个按键事件都要等它走完"输入法 → 目标应用"整条派发链，
   * 6 位验证码 12 个事件累加实测 3～5 秒；ASYNC 只把事件交给输入系统，耗时回落到
   * "每个字符的输入间隔"这一项（新版上游模块用的就是这个模式）。
   */
  private fun resolveInjectMode(classLoader: ClassLoader): Int {
    val managerClassNames = listOf(
      "android.hardware.input.InputManager",
      "android.hardware.input.InputManagerGlobal",
    )
    for (className in managerClassNames) {
      val mode = runCatching {
        LibXposedReflect.getStaticIntField(
          LibXposedReflect.findClass(className, classLoader),
          "INJECT_INPUT_EVENT_MODE_ASYNC",
        )
      }.getOrNull()
      if (mode != null) return mode
    }
    // 拿不到 ASYNC 常量时退回 1（WAIT_FOR_FINISH）：慢，但保证事件派发完成。
    return 1
  }

  private fun sendAutoInputResult(
    context: Context,
    attemptId: Long,
    success: Boolean,
    reason: String,
  ) {
    context.sendBroadcast(
      OtpAutoInputBroadcastContract.buildResultIntent(
        attemptId,
        success,
        OtpAutoInputBroadcastContract.STRATEGY_SYSTEM_INJECT,
        reason,
      ),
    )
  }

  companion object {
    private const val TAG = "SystemInputInjector"
    private const val MODULE_PACKAGE = "com.slideindex.app"
    private const val DELAY_REGISTER_MS = 500L
    private const val MAX_REGISTER_ATTEMPTS = 10
    private const val MAX_SYNC_INPUT_INTERVAL_MS = 200L
    /** 改用 Ctrl+V 前留一点时间，让 App 侧把验证码写进剪贴板。 */
    private const val PASTE_FALLBACK_DELAY_MS = 150L
    private const val CACHED_UID_TTL_MS = 10_000L
    private const val InputDeviceSourceKeyboard = android.view.InputDevice.SOURCE_KEYBOARD
    private val TRUSTED_SENDER_PACKAGES = setOf(
      "com.android.phone",
      "com.android.providers.telephony",
      "com.android.mms",
    )
  }
}
