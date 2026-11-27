package com.slideindex.app.xposed.takeover

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.HookConfigReader
import com.slideindex.app.xposed.bridge.ModuleGestureBridgeClient
import com.slideindex.app.xposed.bridge.ModuleHookSnapshot

/**
 * system_server 侧接管编排：读配置 → 实时算接管区域 → 命中则吞流并转发给 app。
 *
 * 任何一环不可用（无配置、桥未连接、计算失败）都直接放行，保证“宁可不生效，也不要吞掉事件”。
 */
internal class SystemGestureTakeoverController(
  private val context: Context,
  private val log: (String) -> Unit,
) {
  private val configReader = HookConfigReader(log)
  private val bridge = ModuleGestureBridgeClient(context, log)
  private val policy = TakeoverSessionPolicy()
  private var primaryPointerId = MotionEvent.INVALID_POINTER_ID

  @Volatile
  private var cachedRects: List<TakeoverRect> = emptyList()

  @Volatile
  private var cachedSnapshot: ModuleHookSnapshot? = null

  @Volatile
  private var rectsComputedAtMs: Long = 0L

  @Volatile
  private var lastCapabilitySideId: Int = -1

  @Volatile
  private var lastCapabilityResult: Boolean = false

  @Volatile
  private var lastCapabilityCheckedAtMs: Long = 0L

  fun snapshot(): ModuleHookSnapshot? = configReader.current()

  fun hasEnabledGroups(): Boolean = (snapshot()?.takeoverGroups ?: 0) != 0

  fun onConfigBroadcast(json: String?) {
    configReader.applyBroadcast(json)
    cachedSnapshot = null
    rectsComputedAtMs = 0L
    cachedRects = emptyList()
    if (hasEnabledGroups()) {
      bridge.ensureBound()
    } else {
      // 关闭接管时不留半条会话，避免吞掉属于系统的事件。
      endActiveSession(TakeoverSessionPolicy.REASON_RESET)
    }
    log("takeover config updated: groups=${snapshot()?.takeoverGroups ?: 0}")
  }

  fun ensureBridgeBound() {
    if (hasEnabledGroups()) bridge.ensureBound()
  }

  fun close() {
    endActiveSession(TakeoverSessionPolicy.REASON_RESET)
    bridge.close()
  }

  fun requestSnapshotIfNeeded() {
    configReader.requestSnapshotIfNeeded(context)
  }

  fun endActiveSession(reason: Int = TakeoverSessionPolicy.REASON_RESET) {
    if (!policy.hasActiveSession) return
    val sessionId = policy.reset()
    bridge.endSession(sessionId, reason)
  }

  /** 输入层过滤器每次收到事件时调用；返回 true 表示已吞掉该事件。 */
  fun handleEvent(event: MotionEvent, policyFlags: Int): Boolean {
    return runCatching { handleEventInternal(event, policyFlags) }.getOrElse { throwable ->
      log("takeover handleEvent failed: ${throwable.message}")
      endActiveSession(TakeoverSessionPolicy.REASON_CANCEL)
      false
    }
  }

  fun statusDetail(): String {
    val snapshot = snapshot() ?: return STATUS_NO_CONFIG
    if (snapshot.takeoverGroups == 0) return STATUS_DISABLED
    if (!bridge.isConnected) {
      bridge.ensureBound()
      return STATUS_BRIDGE_PENDING
    }
    // 桥连上了不代表能接管：app 侧 overlay 宿主没就绪时 canAcceptTouch* 一律 false，
    // 此时接管同样不会发生，必须如实报告，不能沿用旧的"桥在手就算 ready"。
    if (!bridge.isHostReady()) return STATUS_HOST_NOT_READY
    return "$STATUS_READY_PREFIX:${snapshot.takeoverGroups}"
  }

  /**
   * app 侧广播宿主就绪状态变化。
   *
   * - 就绪：立刻补一次绑定（绕过重试冷却），并清掉能力缓存，让随后的 DOWN 现场复核。
   * - 失活：立刻结束可能存在的吞流会话，避免事件被吞掉却没人处理。
   */
  fun onHostStateChanged(ready: Boolean) {
    lastCapabilityCheckedAtMs = 0L
    if (ready) {
      bridge.ensureBound(force = true)
      return
    }
    endActiveSession(TakeoverSessionPolicy.REASON_RESET)
  }

  private fun handleEventInternal(event: MotionEvent, policyFlags: Int): Boolean {
    val snapshot = configReader.current() ?: return false
    if (snapshot.takeoverGroups == 0) return false

    val action = event.actionMasked
    if (action == MotionEvent.ACTION_DOWN) {
      bridge.ensureBound()
    }
    val bridgeAvailable = bridge.isConnected
    if (!bridgeAvailable) return false

    val rects = resolveRects(snapshot, action)
    if (action == MotionEvent.ACTION_DOWN && !policy.hasActiveSession) {
      val hit = rects.firstOrNull { it.contains(event.rawX, event.rawY) } ?: return false
      // 确认 app 真的能处理这一目标，避免吞掉 DOWN 却无人处理。
      // 触钮：带 TTL 缓存，避免每条触摸都跨进程；扩展目标：带坐标实时复核（命中区随位置/键盘变化）。
      if (!canAccept(hit.sideId, event.rawX, event.rawY)) return false
    }
    val injected = (policyFlags and POLICY_FLAG_INJECTED) != 0
    val activeSide = policy.activeSideId()
    val keepRegionalPick = !injected && action == MotionEvent.ACTION_POINTER_DOWN &&
      activeSide != TakeoverSessionPolicy.NO_SIDE &&
      bridge.cancelRegionalPickSelection(policy.activeSessionId(), activeSide)
    val decision = policy.onEvent(
      actionMasked = action,
      pointerCount = event.pointerCount,
      x = event.rawX,
      y = event.rawY,
      eventTimeMs = event.eventTime,
      injected = injected,
      bridgeAvailable = bridgeAvailable,
      rects = rects,
      keepRegionalPick = keepRegionalPick,
      primaryPointerLifted = action == MotionEvent.ACTION_POINTER_UP &&
        event.getPointerId(event.actionIndex) == primaryPointerId,
    )
    if (!decision.swallow) return false
    if (decision.isSessionStart) primaryPointerId = event.getPointerId(0)

    if (decision.forwardToApp) {
      val forwarded = bridge.sendTouch(
        sessionId = decision.sessionId,
        sideId = decision.sideId,
        action = action,
        x = event.rawX,
        y = event.rawY,
        eventTime = event.eventTime,
        downTime = event.downTime,
        metaState = event.metaState,
      )
      if (!forwarded) {
        // 转发失败时立刻结束会话并放行，避免事件被吞掉却没人处理。
        policy.reset()
        return false
      }
    }
    if (decision.notifyAppEnd) {
      bridge.endSession(decision.sessionId, decision.endReason)
    }
    return true
  }

  private fun resolveRects(snapshot: ModuleHookSnapshot, action: Int): List<TakeoverRect> {
    val now = SystemClock.elapsedRealtime()
    val cached = cachedSnapshot === snapshot && cachedRects.isNotEmpty() &&
      now - rectsComputedAtMs < RECTS_TTL_MS
    val isSessionStart = action == MotionEvent.ACTION_DOWN && !policy.hasActiveSession
    if (!cached || isSessionStart) {
      cachedRects = computeRects(snapshot)
      cachedSnapshot = snapshot
      rectsComputedAtMs = now
    }
    return cachedRects
  }

  private fun canAcceptCached(sideId: Int): Boolean {
    val now = SystemClock.elapsedRealtime()
    if (sideId == lastCapabilitySideId && now - lastCapabilityCheckedAtMs < CAPABILITY_TTL_MS) {
      return lastCapabilityResult
    }
    val result = bridge.canAcceptTouch(sideId)
    lastCapabilitySideId = sideId
    lastCapabilityResult = result
    lastCapabilityCheckedAtMs = now
    return result
  }

  /** 触钮沿用旧的按边缓存查询；扩展目标（角轮盘/悬浮球线条）改带坐标实时复核。 */
  private fun canAccept(target: Int, x: Float, y: Float): Boolean = when (target) {
    ModuleHookBridgeContract.SIDE_LEFT,
    ModuleHookBridgeContract.SIDE_RIGHT,
    ModuleHookBridgeContract.SIDE_BOTTOM,
    ModuleHookBridgeContract.SIDE_TOP,
    -> canAcceptCached(target)
    else -> bridge.canAcceptTouchAt(target, x, y)
  }

  private fun computeRects(snapshot: ModuleHookSnapshot): List<TakeoverRect> = runCatching {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val bounds = windowManager.currentWindowMetrics.bounds
    // 导航模式读取实时值：用户切到三键导航时底部接管应立即失效。
    val liveNavigationMode = runCatching {
      Settings.Secure.getInt(context.contentResolver, NAVIGATION_MODE_KEY, snapshot.navigationMode)
    }.getOrDefault(snapshot.navigationMode)
    TakeoverGeometry.rectsForGroups(
      snapshot = snapshot.copy(navigationMode = liveNavigationMode),
      screenWidthPx = bounds.width(),
      screenHeightPx = bounds.height(),
    )
  }.getOrElse {
    log("takeover rects failed: ${it.message}")
    emptyList()
  }

  companion object {
    private const val NAVIGATION_MODE_KEY = "navigation_mode"

    /** 控制器状态串：还没读到 app 下发的配置快照。 */
    const val STATUS_NO_CONFIG = "no-config"

    /** 控制器状态串：三个接管开关全关。 */
    const val STATUS_DISABLED = "disabled"

    /** 控制器状态串：app 事件桥还没连上（正在重试绑定）。 */
    const val STATUS_BRIDGE_PENDING = "bridge-pending"

    /** 控制器状态串：桥已连上，但 app 侧 overlay 宿主没就绪，接管仍不会发生。 */
    const val STATUS_HOST_NOT_READY = "host-not-ready"

    /** 控制器状态串前缀：`ready:<分组掩码>`，此时接管真的会生效。 */
    const val STATUS_READY_PREFIX = "ready"

    /** `WindowManagerPolicyConstants.POLICY_FLAG_INJECTED`，注入事件一律放行。 */
    private const val POLICY_FLAG_INJECTED = 0x01000000

    private const val RECTS_TTL_MS = 1_000L

    private const val CAPABILITY_TTL_MS = 1_000L
  }
}
