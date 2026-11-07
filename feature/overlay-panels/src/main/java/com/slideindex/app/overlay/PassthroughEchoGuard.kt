package com.slideindex.app.overlay

import android.os.SystemClock
import kotlin.math.hypot

/**
 * 「点击穿透」放行的注入回声抑制。
 *
 * [OverlayPassthrough] 的流程是「隐藏触发器 → 注入一次点击 → 恢复触发器」。如果触发器比这次注入
 * 更早恢复可用（主线程卡顿、窗口重排都会造成），注入的这一下会被自己的触发器接住，于是又触发
 * 同一个放行动作：隐藏/恢复反复进行，表现就是触发钮不停抽搐，日志里同一坐标反复出现
 * `dispatchTap start`。
 *
 * 这里的语义是「一次放行事务」：**从隐藏触发器那一拍开始，到触发器恢复完成（外加一小段落地宽限）
 * 结束**。事务期间落在注入点附近的放行请求都是回声，直接丢弃。
 *
 * 为什么不再用固定时间窗（旧实现是注入后 400ms）：那个窗口会连带吞掉用户紧接着的第二次点击——
 * 真机表现就是「点一下没反应，再点还是没反应，第三下才好」。现在回声窗口与「触发器实际不可触摸
 * 的那段时间」严格对齐，[disarm] 之后用户再点一定生效。
 *
 * 与 [PointerTapEchoGuard] 的分工：那个守卫吞咽的是指针注入事件本身，这个守卫拦的是「放行动作
 * 被回声再次触发」。
 */
class PassthroughEchoGuard {
    private var armed = false
    private var injectX = 0f
    private var injectY = 0f
    private var echoSlopPx = DEFAULT_ECHO_SLOP_PX
    private var armedUntilMs = 0L

    /**
     * 开始一次放行事务。
     *
     * @param safetyMs 兜底失效时间：正常路径由 [disarm] 结束事务，这个值只防「恢复流程异常没跑到」
     *   导致守卫永久挡住后续点击。
     */
    fun arm(
        rawX: Float,
        rawY: Float,
        nowMs: Long = SystemClock.elapsedRealtime(),
        safetyMs: Long = MAX_ARMED_MS,
        echoSlopPx: Float = DEFAULT_ECHO_SLOP_PX
    ) {
        armed = true
        injectX = rawX
        injectY = rawY
        this.echoSlopPx = echoSlopPx.coerceAtLeast(MIN_ECHO_SLOP_PX)
        armedUntilMs = nowMs + safetyMs.coerceAtLeast(0L)
    }

    /** 事务结束（触发器已恢复）：此后所有点击都当作用户的新意图。 */
    fun disarm() {
        armed = false
    }

    fun disarmIfExpired(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (armed && nowMs >= armedUntilMs) {
            armed = false
        }
    }

    /**
     * @return true 表示这次放行请求是上一次注入的回声，必须丢弃。
     */
    fun isEcho(
        rawX: Float,
        rawY: Float,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        disarmIfExpired(nowMs)
        if (!armed) return false
        val distance = hypot((rawX - injectX).toDouble(), (rawY - injectY).toDouble()).toFloat()
        return distance <= echoSlopPx
    }

    companion object {
        /**
         * 兜底失效时间。正常路径由 [OverlayPassthrough] 在触发器恢复后 [disarm]；
         * 这个值取得比后者的 2s 安全恢复更长，只用于异常兜底。
         */
        const val MAX_ARMED_MS = 3_000L

        const val DEFAULT_ECHO_SLOP_PX = 32f
        private const val MIN_ECHO_SLOP_PX = 8f
    }
}
