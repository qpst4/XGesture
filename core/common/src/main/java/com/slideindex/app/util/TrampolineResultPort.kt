package com.slideindex.app.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Trampoline（中转 Activity）结果的跨进程回传通道。
 *
 * 多进程后"发起方在 :overlay、Activity 在默认进程"这类组合很常见，而 trampoline 原来靠
 * 进程内静态变量传回调：另一个进程读到的永远是空副本 → 回调不触发、标记卡死
 * （Shell 面板"只能触发一次"就是这个原因）。
 *
 * 现在：发起方按 token 注册回调 → Intent 带 token → Activity 结束时把结果广播回来，
 * 由发起进程收到后调用本地回调。发起方与宿主同进程/跨进程走同一条路径。
 */
object TrampolineResultPort {
    private const val TAG = "TrampolineResultPort"
    private const val ACTION_RESULT = "com.slideindex.app.action.TRAMPOLINE_RESULT"

    const val EXTRA_TOKEN = "trampoline_token"
    const val EXTRA_CANCELLED = "trampoline_cancelled"

    private val pending = ConcurrentHashMap<String, (Bundle) -> Unit>()

    fun register(context: Context, token: String, onResult: (Bundle) -> Unit) {
        pending[token] = onResult
    }

    fun clearToken(token: String) {
        pending.remove(token)
    }

    /** Activity 侧调用：单进程直接回调发起方（token 仅用于传递与校验）。 */
    fun deliver(context: Context, token: String, payload: Bundle) {
        val callback = pending.remove(token)
        if (callback == null) {
            Log.w(TAG, "deliver($token): 没有等待中的回调")
            return
        }
        runCatching { callback(payload) }
            .onFailure { Log.w(TAG, "onResult($token) failed", it) }
    }
}
