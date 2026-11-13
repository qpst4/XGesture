package com.slideindex.app.service

import android.content.Context
import android.content.Intent

/**
 * Shows shell command output in a transparent [Activity] so scrolling uses the normal window
 * touch pipeline (overlay canvas scroll is unreliable and can wedge the edge session).
 */
object ShellCommandResultTrampoline {
    data class Payload(
        val label: String,
        val command: String,
        val exitCode: Int,
        val output: String
    )

    @Volatile
    private var active = false
    private var payload: Payload? = null
    private var onPrepare: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var activityFinisher: (() -> Unit)? = null

    fun isActive(): Boolean = active

    /**
     * 打开结果页。
     *
     * @return true 表示已交给系统启动；false 表示 `startActivity` 直接失败（后台启动被拦等），
     *   调用方应自己兜底提示，否则用户会以为命令没跑。
     */
    fun launch(
        context: Context,
        result: Payload,
        onPrepare: () -> Unit,
        onDismiss: () -> Unit
    ): Boolean {
        cancelPending()
        active = true
        payload = result
        this.onPrepare = onPrepare
        this.onDismiss = onDismiss
        return runCatching {
            context.startActivity(
                ShellCommandResultTrampolineActivity.createIntent(context)
            )
            true
        }.getOrElse {
            deliverDismiss()
            false
        }
    }

    internal fun runPrepareIfNeeded() {
        onPrepare?.invoke()
        onPrepare = null
    }

    internal fun consumePayload(): Payload? {
        val data = payload
        payload = null
        return data
    }

    internal fun registerActivityFinisher(finish: () -> Unit) {
        activityFinisher = finish
    }

    internal fun unregisterActivityFinisher() {
        activityFinisher = null
    }

    internal fun deliverDismiss() {
        if (!active) return
        active = false
        val dismiss = onDismiss
        clearCallbacks()
        dismiss?.invoke()
    }

    fun closeIfActive() {
        if (!active) return
        activityFinisher?.invoke() ?: deliverDismiss()
    }

    fun cancelPending() {
        active = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        payload = null
        onPrepare = null
        onDismiss = null
    }
}
