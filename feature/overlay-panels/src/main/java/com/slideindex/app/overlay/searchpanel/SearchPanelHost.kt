package com.slideindex.app.overlay.searchpanel

import android.content.Context
import com.slideindex.app.data.AppInfo
import com.slideindex.app.freezer.FreezerAppState

/**
 * 搜索面板与 `:app` 的接线点。
 *
 * 冻结器（freezer）仍在 `:app`，模块侧只消费这几个入口。
 */
interface SearchPanelHost {

    /** 该应用的冻结状态。 */
    fun freezerState(context: Context, app: AppInfo): FreezerAppState

    /** 按 (包名, 分身用户) 取冻结状态（快捷动作条用）。 */
    fun freezerStateOfTarget(context: Context, packageName: String, cloneUserId: Int): FreezerAppState

    /** 启动应用（必要时先解冻再启动）。 */
    suspend fun launchAndRestore(context: Context, app: AppInfo, fullscreen: Boolean): Boolean

    /** 是否被暂停。 */
    fun isPaused(context: Context, app: AppInfo): Boolean

    /** 暂停/恢复（返回是否成功）。 */
    suspend fun setPaused(context: Context, app: AppInfo, paused: Boolean): Boolean

    /** 冻结/解冻（返回是否成功）。 */
    suspend fun setFrozen(context: Context, app: AppInfo, frozen: Boolean): Boolean
}

/** 搜索面板内容（`:app` 的 SearchPanelScreen 组合函数）。 */
fun interface SearchPanelContent {
    @androidx.compose.runtime.Composable
    fun content(
        visibilityState: androidx.compose.animation.core.MutableTransitionState<Boolean>,
        onDismiss: () -> Unit,
    )
}

/** 模块内服务定位点：`:app` 启动时注入。 */
object SearchPanelHostAccess {

    @Volatile
    var host: SearchPanelHost? = null
}

/** 搜索面板内容注入点。 */
object SearchPanelContentAccess {
    @Volatile
    var content: SearchPanelContent? = null
}
