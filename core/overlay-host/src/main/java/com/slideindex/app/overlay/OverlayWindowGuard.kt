package com.slideindex.app.overlay

import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * 所有 `TYPE_ACCESSIBILITY_OVERLAY` 窗口的统一创建入口。
 *
 * 只做一件事：把“建窗失败”从各处的 `runCatching` 里捞出来，变成 [AccessibilityLink]
 * 的一条真实证据。异常仍然照原样抛出，调用方原有的重试 / 回滚逻辑不变。
 *
 * 为什么必须集中：无障碍连接被系统摘掉之后，每个建窗点都会抛
 * `BadTokenException: Unable to add window -- token null is not valid`，
 * 但历史上这些异常被各自的 `runCatching` 吞掉，用户只看到“点了没反应”。
 */
object OverlayWindowGuard {
    private const val TAG = "OverlayWindowGuard"

    fun addView(
        windowManager: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
        tag: String,
    ) {
        // 无名窗口在 `dumpsys window` 里只能看到一个包名，排查泄漏时没法反推来源。
        // tag 本来就是调用方传进来的稳定标识（如 "corner-overlay"），直接拿来当标题。
        if (params.title.isNullOrBlank()) {
            params.title = "XG:$tag"
        }
        try {
            windowManager.addView(view, params)
        } catch (error: WindowManager.BadTokenException) {
            noteIfTokenInvalid(tag, error)
            throw error
        } catch (error: IllegalArgumentException) {
            noteIfTokenInvalid(tag, error)
            throw error
        }
    }

    private fun noteIfTokenInvalid(tag: String, error: Throwable) {
        val message = error.message.orEmpty()
        if (message.contains("token null") || message.contains("not valid", ignoreCase = true)) {
            Log.e(TAG, "accessibility overlay window rejected: $tag", error)
            AccessibilityLink.noteBroken("window-add:$tag", message)
        }
    }
}
