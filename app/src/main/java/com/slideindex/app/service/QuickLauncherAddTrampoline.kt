package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.PanelSide

/**
 * Opens the quick-launcher add picker in a transparent [Activity] so list scrolling uses the
 * normal window touch pipeline (overlay [ComposeView] + [LazyColumn] is unreliable).
 */
object QuickLauncherAddTrampoline {
    @Volatile
    private var active = false
    private var onPrepare: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var onAdd: ((QuickLauncherItem) -> Unit)? = null
    private var onRemove: ((QuickLauncherItem) -> Unit)? = null

    fun isActive(): Boolean = active

    /**
     * 打开透明 Activity 的添加入口。
     *
     * @return true = Activity 已在途；false = 启动失败（OEM 后台启动限制等），
     *   调用方应回退到浮层 sheet。失败时也会走一次 [onDismiss]，让面板恢复。
     */
    fun launch(
        context: Context,
        panelSide: PanelSide,
        configuredAppPackages: Set<String>,
        configuredShortcutKeys: Set<String>,
        configuredActionKeys: Set<String>,
        onPrepare: () -> Unit,
        onDismiss: () -> Unit,
        onAdd: (QuickLauncherItem) -> Unit,
        onRemove: (QuickLauncherItem) -> Unit
    ): Boolean {
        cancelPending()
        active = true
        this.onPrepare = onPrepare
        this.onDismiss = onDismiss
        this.onAdd = onAdd
        this.onRemove = onRemove
        onPrepare()
        return runCatching {
            context.startActivity(
                QuickLauncherAddTrampolineActivity.createIntent(
                    context = context,
                    panelSide = panelSide,
                    configuredAppPackages = configuredAppPackages,
                    configuredShortcutKeys = configuredShortcutKeys,
                    configuredActionKeys = configuredActionKeys
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

    internal fun onItemAdd(item: QuickLauncherItem) {
        onAdd?.invoke(item)
    }

    internal fun onItemRemove(item: QuickLauncherItem) {
        onRemove?.invoke(item)
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
        onAdd = null
        onRemove = null
    }
}
