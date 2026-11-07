package com.slideindex.app.overlay.share

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 浮窗分享的统一入口。
 *
 * 开关打开时走内置面板（不离开浮层、可钉住）；关闭时保持历史行为 ——
 * 直接拉起系统分享面板。调用方只需要把原来的 `Intent.createChooser(...)`
 * 换成这里的一行调用，并把"发送前要收起哪个来源浮窗"传进来。
 */
object ShareSheetLauncher {

    private const val TAG = "ShareSheetLauncher"

    fun isEnabled(context: Context): Boolean =
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.isShareSheetEnabled?.invoke(context) == true

    /**
     * 分享 [payload]。
     *
     * @param dismissSource 发起这次分享的来源浮窗（取词面板 / 文件浏览器 / 拖放流转…）。
     * 内置面板点中目标、或退回系统面板之前，都会先调它收起来源浮窗 —— 否则目标 App
     * 会被来源浮窗盖住，等于没分享。
     */
    fun share(
        context: Context,
        payload: ShareSheetPayload,
        dismissSource: (() -> Unit)? = null,
    ): Boolean {
        if (isEnabled(context) && ShareSheetOverlayWindow.show(context, payload, beforeSend = dismissSource)) {
            return true
        }
        // 没开开关 / 无障碍宿主不在：退回老路。系统面板必须由我们先把浮层收掉才能露出来。
        runCatching { dismissSource?.invoke() }
        return openSystemChooser(context, payload)
    }

    fun openSystemChooser(context: Context, payload: ShareSheetPayload): Boolean {
        val chooser = Intent.createChooser(payload.intent(), null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrElse {
            Log.w(TAG, "system chooser failed", it)
            false
        }
    }
}
