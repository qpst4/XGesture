package com.slideindex.app.xposed.hook.otp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import com.slideindex.app.otp.SmsBlacklistMatch
import com.slideindex.app.otp.SmsBlacklistMatcher
import com.slideindex.app.otp.OtpExtractionConfig
import com.slideindex.app.otp.VerificationCodeExtractor
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.bridge.HookConfigReader
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleHookOtpPolicy

/**
 * 电话进程 / 短信存储进程里的验证码短信策略运行时。
 *
 * 职责三件：
 * 1. 通过 [HookConfigReader] 读取 App 下发的配置快照里的 `otp` 段（含黑名单、屏蔽、已读、删除）；
 * 2. 作为短信入口/短信库 hook 的判定依据（[shouldBlock]、[isVerificationCodeSms]）；
 * 3. 建立**本进程**的状态通道，回应 App 的状态探测（detail 带 `proc=` 便于 App 分槽展示）。
 *
 * 注意：这里只能判定"关键词命中的验证码短信"，不复制 App 侧的官方/用户规则引擎（详见文档
 * docs/xposed_smscode_port_analysis.md 的近似说明）。
 */
internal object SmsPolicyRuntime {
  private const val TAG = "SmsPolicyRuntime"
  private const val MAX_ATTEMPTS = 12
  private const val RETRY_MS = 500L
  private const val STATUS_PROC_PREFIX = "proc="
  private const val RESTART_DELAY_MS = 300L

  private val handler = Handler(Looper.getMainLooper())
  private val configReader = HookConfigReader { line -> XposedLog.d(TAG, line) }

  /**
   * 本进程已注册的通道集合。
   *
   * 同一进程可能同时承载多个作用域包（例如 Flyme 把「短信存储」并进电话进程：pid 与 uid 都属于
   * `com.android.phone`），此时 phone 与 telephony 两个通道会先后注册进同一个进程。以前只存一个
   * 通道名，后注册的会把先注册的覆盖掉，App 侧对应槽位就永远收不到回执（表现为「短信通道：未就绪」）。
   */
  private val channels = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

  @Volatile
  private var processContext: Context? = null

  @Volatile
  private var receiverRegistered = false

  @Volatile
  private var dispatchHooked = false

  @Volatile
  private var providerHooked = false

  @Volatile
  private var cachedKeywords: Pair<String, Regex?>? = null
  private var registeredReceiver: BroadcastReceiver? = null
  private var moduleApkPath: String? = null
  @Volatile private var retired = false

  fun detach() {
    retired = true
    handler.removeCallbacksAndMessages(null)
    registeredReceiver?.let { receiver -> runCatching { processContext?.unregisterReceiver(receiver) } }
    registeredReceiver = null
    receiverRegistered = false
    channels.clear()
  }

  fun register(channel: String, apkPath: String? = null) {
    moduleApkPath = apkPath
    channels.add(channel)
    attemptRegistration(attempt = 0)
  }

  /** 当前通道快照（回执按通道逐个发，避免一个进程只点亮 App 侧一个槽位）。 */
  private fun currentChannels(): List<String> = synchronized(channels) { channels.toList() }

  fun markDispatchHooked(installed: Boolean) {
    dispatchHooked = installed
  }

  fun markProviderHooked(installed: Boolean) {
    providerHooked = installed
  }

  /** 短信入口/短信库 hook 拿到真实 Context 后调用，补齐状态通道注册。 */
  fun ensureRegistered(context: Context) {
    processContext = context.applicationContext ?: context
    attemptRegistration(attempt = 0)
  }

  fun policy(): ModuleHookOtpPolicy {
    val snapshot = configReader.current()
    if (snapshot == null) {
      // 本进程还没拿到任何配置（典型：先于 App 启动）。按默认值放行的同时主动拉一次，
      // 否则「短信安全」会一直停在默认值上。requestSnapshotIfNeeded 自带 30s 节流。
      processContext?.let { configReader.requestSnapshotIfNeeded(it) }
      return ModuleHookOtpPolicy()
    }
    return snapshot.otp ?: ModuleHookOtpPolicy()
  }

  /** 是否被策略要求拦截投递（黑名单动作优先于"屏蔽所有验证码短信"）。 */
  fun shouldBlock(sender: String?, body: String?): BlockDecision {
    val policy = policy()
    val blacklistMatch = SmsBlacklistMatcher.match(policy.blacklist, sender, body)
    if (blacklistMatch.matched && blacklistMatch.actionBlock) {
      return BlockDecision(block = true, reason = "blacklist", detail = blacklistMatch)
    }
    if (policy.blockCodeSmsEnabled && isVerificationCodeSms(body)) {
      return BlockDecision(block = true, reason = "block_code_sms", detail = SmsBlacklistMatch.None)
    }
    return BlockDecision(block = false, reason = null, detail = blacklistMatch)
  }

  fun isVerificationCodeSms(body: String?): Boolean {
    val content = body.orEmpty()
    if (content.isEmpty()) return false
    val pattern = policy().keywordsRegex
    if (pattern.isBlank()) return false
    val cached = cachedKeywords
    val regex = if (cached != null && cached.first == pattern) {
      cached.second
    } else {
      runCatching { Regex(pattern) }.getOrNull().also { cachedKeywords = pattern to it }
    }
    return regex?.containsMatchIn(content) == true
  }

  /**
   * 用关键词规则尝试提取验证码；提不到就不算"验证码短信"。
   *
   * 用于"提取后删除 / 标记已读"：只按关键词命中会误伤"含关键词但没有验证码"的短信，
   * 因此这里要求真的能抽出一个 4~8 位验证码。规则集不下发，故只用关键词（见文档的近似说明）。
   */
  fun extractVerificationCode(body: String?): String? {
    val content = body.orEmpty()
    if (content.isEmpty()) return null
    val pattern = policy().keywordsRegex
    if (pattern.isBlank()) return null
    val result = VerificationCodeExtractor.extract(
      packageName = "",
      title = "",
      text = content,
      config = OtpExtractionConfig(keywordsRegex = pattern, matchRules = emptyList()),
    )
    return result.code
  }

  private fun attemptRegistration(attempt: Int) {
    if (retired) return
    if (receiverRegistered) return
    val context = processContext ?: resolveProcessContext()?.also { processContext = it }
    if (context != null && tryRegister(context)) return
    if (attempt >= MAX_ATTEMPTS) {
      XposedLog.w(TAG, "status channel not registered in ${currentChannels()} process")
      return
    }
    handler.postDelayed({ attemptRegistration(attempt + 1) }, RETRY_MS)
  }

  private fun tryRegister(context: Context): Boolean = runCatching {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(receiverContext: Context?, intent: Intent?) {
        when (intent?.action) {
          ModuleHookBridgeContract.ACTION_CONFIG_CHANGED ->
            configReader.applyBroadcast(
              intent.getStringExtra(ModuleHookBridgeContract.EXTRA_CONFIG_JSON),
            )
          ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST -> {
            val statusContext = receiverContext ?: context
            configReader.requestSnapshotIfNeeded(statusContext)
            currentChannels().forEach { sendStatusResponse(statusContext, it) }
          }
          ModuleHookBridgeContract.ACTION_RESTART_PROCESS -> {
            val confirm = intent.getStringExtra(ModuleHookBridgeContract.EXTRA_RESTART_CONFIRM)
            if (confirm != ModuleHookBridgeContract.RESTART_CONFIRM_VALUE) return
            XposedLog.i(
              TAG,
              "Restart requested in ${currentChannels()} process, reloading module code",
            )
            handler.postDelayed({
              runCatching { Process.killProcess(Process.myPid()) }
              runCatching { System.exit(0) }
            }, RESTART_DELAY_MS)
          }
        }
      }
    }
    val filter = IntentFilter().apply {
      addAction(ModuleHookBridgeContract.ACTION_CONFIG_CHANGED)
      addAction(ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST)
      addAction(ModuleHookBridgeContract.ACTION_RESTART_PROCESS)
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    registeredReceiver = receiver
    receiverRegistered = true
    XposedLog.i(TAG, "status channel registered in ${currentChannels()} process")
    configReader.requestSnapshotIfNeeded(context)
  }.onFailure {
    XposedLog.d(TAG, "register status channel retry in ${currentChannels()}: ${it.message}")
  }.isSuccess

  private fun sendStatusResponse(context: Context, channel: String) {
    val policy = configReader.current()?.otp
    val policyLoaded = policy != null
    val hookInstalled = if (channel == ModuleHookBridgeContract.CHANNEL_TELEPHONY) {
      providerHooked
    } else {
      dispatchHooked
    }
    val effective = policy ?: ModuleHookOtpPolicy()
    val state = when {
      !hookInstalled -> ModuleHookBridgeContract.STATUS_STATE_NOT_READY
      !policyLoaded -> ModuleHookBridgeContract.STATUS_STATE_ARMED
      else -> ModuleHookBridgeContract.STATUS_STATE_READY
    }
    val detail = buildString {
      append(STATUS_PROC_PREFIX).append(channel)
      append(",hook=").append(if (hookInstalled) "ok" else "fail")
      append(",config=").append(if (policyLoaded) "ok" else "fail")
      append(",code=").append(ModuleHookBridgeContract.MODULE_CODE_VERSION)
      moduleApkPath?.let { append(",apk=").append(it) }
      append(",capture=").append(if (effective.captureEnabled) "on" else "off")
      append(",block=").append(if (effective.blockCodeSmsEnabled) "on" else "off")
      append(",read=").append(if (effective.markAsReadEnabled) "on" else "off")
      append(",del=").append(if (effective.deleteSmsEnabled) "on" else "off")
      append(",bl=").append(if (effective.blacklist.enabled) "on" else "off")
    }
    runCatching {
      context.sendBroadcast(
        Intent(ModuleHookBridgeContract.ACTION_MODULE_STATUS_RESPONSE).apply {
          setPackage(ModuleHookBridgeContract.MODULE_PACKAGE)
          addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
          putExtra(
            ModuleHookBridgeContract.EXTRA_STATUS_CHANNEL,
            channel,
          )
          putExtra(
            ModuleHookBridgeContract.EXTRA_STATUS_ACTIVE,
            state == ModuleHookBridgeContract.STATUS_STATE_READY,
          )
          putExtra(ModuleHookBridgeContract.EXTRA_STATUS_STATE, state)
          putExtra(ModuleHookBridgeContract.EXTRA_STATUS_DETAIL, detail)
        },
      )
    }.onFailure { XposedLog.w(TAG, "status response failed: ${it.message}") }
  }

  /** 进程级 Context：LSPosed 只给到 ClassLoader，这里用 ActivityThread 拿上下文，失败则退回 hook 回调时机。 */
  private fun resolveProcessContext(): Context? = runCatching {
    val activityThreadClass = Class.forName("android.app.ActivityThread")
    val currentThread = activityThreadClass
      .getDeclaredMethod("currentActivityThread")
      .apply { isAccessible = true }
      .invoke(null)
    activityThreadClass
      .getDeclaredMethod("getSystemContext")
      .apply { isAccessible = true }
      .invoke(currentThread) as? Context
  }.getOrNull()

  data class BlockDecision(
    val block: Boolean,
    val reason: String?,
    val detail: SmsBlacklistMatch,
  )
}
