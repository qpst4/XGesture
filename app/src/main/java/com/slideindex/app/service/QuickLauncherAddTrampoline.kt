package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.overlay.PanelSide

/**
 * Opens the quick-launcher add picker in a transparent [Activity] so the Miuix page
 * (settings-style scaffold, list scrolling) uses the normal window touch pipeline.
 *
 * The Activity hosts the **same** pages as the settings entry
 * (`QuickLauncherAddFlowScreen` + `ExtensionSettingsViewModel`); this trampoline only
 * tracks the "add flow active" lifecycle so the overlay panel can hide/resume.
 */
object QuickLauncherAddTrampoline {
    @Volatile
    private var active = false
    private var onPrepare: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    fun isActive(): Boolean = active

    /**
     * 打开透明 Activity 的添加入口。
     *
     * @return true = Activity 已在途；false = 启动失败（OEM 后台启动限制等），
     *   调用方应回退到浮层 sheet。失败时也会走一次 [onDismiss]，让面板恢复。
     */
    fun launch(
        context: Context,
        panelId: String,
        panelSide: PanelSide,
        onPrepare: () -> Unit,
        onDismiss: () -> Unit,
    ): Boolean {
        cancelPending()
        active = true
        this.onPrepare = onPrepare
        this.onDismiss = onDismiss
        onPrepare()
        return runCatching {
            context.startActivity(
                QuickLauncherAddTrampolineActivity.createIntent(
                    context = context,
                    panelId = panelId,
                    panelSide = panelSide,
                )
            )
        }.fold(
            onSuccess = { true },
            onFailure = {
                deliverDismiss()
                false
            },
        )
    }

    internal fun deliverDismiss() {
        if (!active) return
        active = false
        val dismiss = onDismiss
        clearCallbacks()
        dismiss?.invoke()
    }

    fun cancelPending() {
        active = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        onPrepare = null
        onDismiss = null
    }
}
