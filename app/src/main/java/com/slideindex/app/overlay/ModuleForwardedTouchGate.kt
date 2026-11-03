package com.slideindex.app.overlay

import android.os.SystemClock
import kotlin.math.hypot

/**
 * 「点击穿透」注入窗口：只用来识别**我们自己刚注入的那一击**，不再拿它拦用户的点击。
 *
 * 旧实现（已废弃）在模块每一次转发触摸时都盖一个「最近转发过」的章，而点击穿透看到这个章就
 * 整段跳过注入（窗口 1500ms）。可是接管区里的单击**必然**由模块转发——于是每次单击都落在窗口里，
 * 表现为「只有振动、点击没生效」，把「屏蔽系统手势」调到 0%（模块不接管）后立刻正常。
 *
 * 现在的语义收窄成两件事，都只围绕「我们自己注入的那一击」：
 *
 * 1. [isInjectionPending]：注入后的一小段时间内，模块桥最好直接放行，让注入事件直达目标应用；
 * 2. [isInjectedEcho]：万一注入事件还是被转发回来（老模块 / ROM 没给注入事件打
 *    `POLICY_FLAG_INJECTED`），在 app 侧把它当回声丢掉，绝不进第二次手势解析。
 *
 * 用户真正的新点击不受影响：窗口只有几百毫秒，且只认注入点附近的坐标。
 */
object ModuleForwardedTouchGate {
    /** 注入窗口：覆盖注入事件的投递抖动即可，不能长到吞掉用户紧接着的第二次点击。 */
    private const val INJECT_WINDOW_MS = 400L

    /** 回声判定半径：注入点附近才算（手指抖动、坐标取整都在这个量级内）。 */
    private const val ECHO_SLOP_PX = 48f

    @Volatile
    private var injectedAtMs = 0L

    @Volatile
    private var injectedX = Float.NaN

    @Volatile
    private var injectedY = Float.NaN

    /** 注入前调用：记下这一击的位置与时刻。 */
    fun markInjected(
        rawX: Float,
        rawY: Float,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ) {
        injectedAtMs = nowMs
        injectedX = rawX
        injectedY = rawY
    }

    /** 注入窗口内（不判坐标）：模块桥应放行，让注入事件直达目标应用。 */
    fun isInjectionPending(nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
        injectedAtMs > 0L && nowMs - injectedAtMs <= INJECT_WINDOW_MS

    /**
     * 这一条模块转发事件是不是我们自己注入那一击的回声。
     *
     * @return true 表示必须丢弃，不能进手势引擎（否则会再触发一次同样的动作）。
     */
    fun isInjectedEcho(
        rawX: Float,
        rawY: Float,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        if (!isInjectionPending(nowMs)) return false
        if (injectedX.isNaN() || injectedY.isNaN()) return true
        val distance = hypot((rawX - injectedX).toDouble(), (rawY - injectedY).toDouble()).toFloat()
        return distance <= ECHO_SLOP_PX
    }

    fun clear() {
        injectedAtMs = 0L
        injectedX = Float.NaN
        injectedY = Float.NaN
    }
}
