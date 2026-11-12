package com.slideindex.app.overlay.share

import android.content.ComponentName
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

    /**
     * 总开关从设置快照读（[com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsSnapshot]，
     * `:app` 启动时已绑定）。不再单开专用钩子：专用槽位一旦漏绑定，就会静默退化成"永远关"，
     * 而这里没有任何兜底能看出来 —— v1.64.0 的内置分享面板正是这样失效的。
     */
    fun isEnabled(context: Context): Boolean =
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsSnapshot
            ?.invoke(context)
            ?.shareSheetEnabled == true

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
        val enabled = isEnabled(context)
        if (enabled && ShareSheetOverlayWindow.show(context, payload, beforeSend = dismissSource)) {
            return true
        }
        if (!enabled) {
            // 开关为关时不再一言不发：曾经因为"静默回退"完全看不出面板为什么没出现。
            Log.d(TAG, "share sheet disabled in settings, falling back to system chooser")
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

    /**
     * 直发到默认目标（取词面板文字 / 图片分享键的"点一下就走"）。
     *
     * 没设默认、目标被卸载 / 禁用、或启动失败都返回 false —— 调用方据此退回 [share]
     * （弹面板），所以这里**失败时不要先收起来源浮窗**。
     */
    fun shareToDefault(
        context: Context,
        payload: ShareSheetPayload,
        dismissSource: (() -> Unit)? = null,
    ): Boolean {
        if (!ShareSheetDefaults.supports(payload.kind)) return false
        val target = ShareSheetDefaults.resolve(context, payload.kind) ?: return false
        return shareToTarget(context, payload, target, dismissSource)
    }

    /** 直发到指定目标：补 `component` 后 StartActivity；成功启动返回 true。 */
    fun shareToTarget(
        context: Context,
        payload: ShareSheetPayload,
        target: ShareDefaultTarget,
        dismissSource: (() -> Unit)? = null,
    ): Boolean {
        val intent = payload.intent().apply {
            component = ComponentName(target.packageName, target.activityClassName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 目标 App 起来后必须压在浮层上面：先用调用方给的 lambda 收起来源浮窗。
        runCatching { dismissSource?.invoke() }
        return runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "default target launch failed: ${target.key}", it) }
            .isSuccess
    }
}
