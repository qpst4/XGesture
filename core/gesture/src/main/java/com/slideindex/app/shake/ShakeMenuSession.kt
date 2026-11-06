package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction

/**
 * 一次「晃动菜单」激活会话：从晃动打开菜单开始，到执行动作 / 超时 / 失效为止。
 *
 * @property menuId 当前激活的菜单 id。
 * @property activatedAtElapsedMs 激活时刻（[android.os.SystemClock.elapsedRealtime]）。
 */
data class ShakeMenuSession(
    val menuId: String,
    val activatedAtElapsedMs: Long,
)

/** 菜单状态下一次晃动事件的处理结果。 */
sealed interface ShakeMenuDecision {
    /** 当前不在菜单状态：调用方按普通映射处理这次晃动。 */
    data object NotInMenu : ShakeMenuDecision

    /** 菜单内命中动作：执行它，并退出菜单状态。 */
    data class Execute(val menu: ShakeMenu, val action: GestureAction) : ShakeMenuDecision

    /**
     * 入场冷却内：这次晃动大概率还是「打开菜单」那一下的余波，吞掉但**保持菜单打开**，
     * 否则浮层会刚出现就被同一串抖动关掉。
     */
    data class CoolingDown(val menu: ShakeMenu) : ShakeMenuDecision

    /**
     * 晃到了菜单里没有配置动作的方向：这次选择作废，**退出菜单状态**。
     *
     * 与 [CoolingDown] 的区别是「用户确实做完了一次方向选择」——按约定，晃动菜单是用完即走，
     * 不管这一下有没有命中动作，浮层都该收起。
     */
    data class Unbound(val menu: ShakeMenu) : ShakeMenuDecision

    /** 会话已失效（超时或菜单被删）：退出菜单状态，这次晃动作废。 */
    data class Expired(val menuId: String) : ShakeMenuDecision
}

/** [ShakeMenuRuntime.decide] 的返回值：新会话（`null` 表示已退出菜单状态）+ 决定。 */
data class ShakeMenuOutcome(
    val session: ShakeMenuSession?,
    val decision: ShakeMenuDecision,
)

/**
 * 「晃动菜单」的会话状态机。
 *
 * 刻意做成无副作用的纯函数：Host 只负责把传感器事件、当前时间和菜单表喂进来，
 * 拿回去要执行什么。时序、冷却、超时这些最容易出错的部分因此可以单独写单测。
 */
object ShakeMenuRuntime {
    /** 菜单激活后的入场冷却默认值（毫秒）：这段时间内不接受方向动作。 */
    const val DEFAULT_GUARD_MS = 300L

    /** 入场冷却的可调范围；上限压住是避免用户把菜单调成「几乎没反应」。 */
    const val GUARD_MS_MIN = 100L
    const val GUARD_MS_MAX = 1_000L

    /** 菜单浮层延迟显示的默认值与范围；0 表示立即显示。 */
    const val DEFAULT_DISPLAY_DELAY_MS = 0L
    const val DISPLAY_DELAY_MS_MAX = 3_000L

    /** 菜单激活后的存活时间：到期自动退出，避免浮层一直挂在屏幕上。 */
    const val DEFAULT_SESSION_TIMEOUT_MS = 5_000L

    fun clampGuardMs(value: Long): Long = value.coerceIn(GUARD_MS_MIN, GUARD_MS_MAX)

    fun clampDisplayDelayMs(value: Long): Long = value.coerceIn(0L, DISPLAY_DELAY_MS_MAX)

    /** 进入菜单状态。 */
    fun open(menuId: String, nowElapsedMs: Long): ShakeMenuSession =
        ShakeMenuSession(menuId = menuId, activatedAtElapsedMs = nowElapsedMs)

    /**
     * 解析要打开的菜单。
     *
     * 动作里记的 id 找不到时（菜单被删、数据被手工改坏）回落到第一个菜单；
     * 一个菜单都没有则返回 `null`，调用方按「无动作」处理，不进入菜单状态。
     */
    fun resolveMenu(menus: List<ShakeMenu>, menuId: String): ShakeMenu? {
        if (menus.isEmpty()) return null
        if (menuId.isNotBlank()) {
            menus.firstOrNull { it.id == menuId }?.let { return it }
        }
        return menus.first()
    }

    /**
     * 处理菜单状态下的一次晃动。
     *
     * @param session 当前会话，`null` 表示不在菜单状态。
     * @param guardMs 入场冷却：距离激活不足该时长的事件一律吞掉。
     * @param timeoutMs 会话存活时长，超过即失效。
     */
    fun decide(
        session: ShakeMenuSession?,
        menus: List<ShakeMenu>,
        type: ShakeGestureType,
        nowElapsedMs: Long,
        guardMs: Long = DEFAULT_GUARD_MS,
        timeoutMs: Long = DEFAULT_SESSION_TIMEOUT_MS,
    ): ShakeMenuOutcome {
        if (session == null) return ShakeMenuOutcome(session = null, decision = ShakeMenuDecision.NotInMenu)

        val ageMs = nowElapsedMs - session.activatedAtElapsedMs
        if (ageMs >= timeoutMs) {
            return ShakeMenuOutcome(session = null, decision = ShakeMenuDecision.Expired(session.menuId))
        }

        val menu = menus.firstOrNull { it.id == session.menuId }
            ?: return ShakeMenuOutcome(session = null, decision = ShakeMenuDecision.Expired(session.menuId))

        if (ageMs < guardMs) {
            return ShakeMenuOutcome(session = session, decision = ShakeMenuDecision.CoolingDown(menu))
        }

        val action = menu.actionFor(type)
        if (action == GestureAction.None) {
            // 用户完成了一次方向选择，只是这个方向没配动作：同样收起菜单。
            return ShakeMenuOutcome(session = null, decision = ShakeMenuDecision.Unbound(menu))
        }
        return ShakeMenuOutcome(session = null, decision = ShakeMenuDecision.Execute(menu, action))
    }
}
