package com.slideindex.app.xposed.takeover

/**
 * 输入层接管会话状态机（纯逻辑，便于单测）。
 *
 * 只有“DOWN 命中接管区域 + 桥可用 + 单指 + 非注入事件”才开启会话；
 * 会话期间吞掉整条触摸流并转发给 app；UP/CANCEL、超时、多指、桥断开都会结束或放弃会话。
 */
class TakeoverSessionPolicy(
  private val sessionTimeoutMs: Long = DEFAULT_SESSION_TIMEOUT_MS,
) {
  data class Decision(
    /** true 时该事件不进入系统正常派发。 */
    val swallow: Boolean,
    /** true 时把该事件通过事件桥转发给 app 现有手势引擎。 */
    val forwardToApp: Boolean,
    val sessionId: Long,
    val sideId: Int,
    val isSessionStart: Boolean,
    /** true 时需要给 app 发一次 onSessionEnd，让 app 放弃当前手势。 */
    val notifyAppEnd: Boolean,
    val endReason: Int,
  )

  private data class Session(
    val id: Long,
    val sideId: Int,
    var lastEventAtMs: Long,
    var appDetached: Boolean = false,
    var keepRegionalPick: Boolean = false,
  )

  private var session: Session? = null
  private var nextSessionId: Long = 1L

  val hasActiveSession: Boolean get() = session != null

  fun activeSessionId(): Long = session?.id ?: NO_SESSION
  fun activeSideId(): Int = session?.takeUnless { it.appDetached }?.sideId ?: NO_SIDE

  /** 丢弃当前会话并返回其 id（用于屏幕关闭、配置变更等场景）。 */
  fun reset(): Long {
    val id = session?.id ?: NO_SESSION
    session = null
    return id
  }

  fun onEvent(
    actionMasked: Int,
    pointerCount: Int,
    x: Float,
    y: Float,
    eventTimeMs: Long,
    injected: Boolean,
    bridgeAvailable: Boolean,
    rects: List<TakeoverRect>,
    keepRegionalPick: Boolean = false,
    primaryPointerLifted: Boolean = false,
  ): Decision {
    if (injected) return pass()

    val active = session
    if (active != null) {
      if (!bridgeAvailable) {
        session = null
        return pass()
      }
      if (eventTimeMs - active.lastEventAtMs > sessionTimeoutMs) {
        session = null
        return pass()
      }
      active.lastEventAtMs = eventTimeMs

      val ending = actionMasked == ACTION_UP || actionMasked == ACTION_CANCEL
      if (ending) {
        val decision = Decision(
          swallow = true,
          forwardToApp = !active.appDetached,
          sessionId = active.id,
          sideId = active.sideId,
          isSessionStart = false,
          notifyAppEnd = true,
          endReason = if (actionMasked == ACTION_CANCEL) REASON_CANCEL else REASON_UP,
        )
        session = null
        return decision
      }

      if (pointerCount > 1) {
        if (keepRegionalPick) active.keepRegionalPick = true
        if (active.keepRegionalPick && !active.appDetached && !primaryPointerLifted) {
          return Decision(
            swallow = true,
            forwardToApp = false,
            sessionId = active.id,
            sideId = active.sideId,
            isSessionStart = false,
            notifyAppEnd = false,
            endReason = REASON_NONE,
          )
        }
        // 多指：保持吞流以免半条流漏给系统，同时让 app 放弃当前手势。
        val notify = !active.appDetached
        active.appDetached = true
        return Decision(
          swallow = true,
          forwardToApp = false,
          sessionId = active.id,
          sideId = active.sideId,
          isSessionStart = false,
          notifyAppEnd = notify,
          endReason = REASON_MULTI_TOUCH,
        )
      }
      return Decision(
        swallow = true,
        forwardToApp = !active.appDetached,
        sessionId = active.id,
        sideId = active.sideId,
        isSessionStart = false,
        notifyAppEnd = false,
        endReason = REASON_NONE,
      )
    }

    if (actionMasked != ACTION_DOWN || pointerCount > 1 || !bridgeAvailable) return pass()
    val matched = rects.firstOrNull { it.contains(x, y) } ?: return pass()
    val newSession = Session(id = nextSessionId++, sideId = matched.sideId, lastEventAtMs = eventTimeMs)
    session = newSession
    return Decision(
      swallow = true,
      forwardToApp = true,
      sessionId = newSession.id,
      sideId = newSession.sideId,
      isSessionStart = true,
      notifyAppEnd = false,
      endReason = REASON_NONE,
    )
  }

  private fun pass(): Decision = Decision(
    swallow = false,
    forwardToApp = false,
    sessionId = NO_SESSION,
    sideId = NO_SIDE,
    isSessionStart = false,
    notifyAppEnd = false,
    endReason = REASON_NONE,
  )

  companion object {
    const val NO_SESSION = -1L
    const val NO_SIDE = -1

    const val REASON_NONE = 0
    const val REASON_UP = 1
    const val REASON_CANCEL = 2
    const val REASON_MULTI_TOUCH = 3
    const val REASON_BRIDGE_LOST = 4
    const val REASON_RESET = 5

    const val DEFAULT_SESSION_TIMEOUT_MS = 2_000L

    private const val ACTION_DOWN = 0
    private const val ACTION_UP = 1
    private const val ACTION_CANCEL = 3
  }
}
