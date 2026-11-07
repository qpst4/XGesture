package com.slideindex.app.settings

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 设置流的接线点：`:app` 在启动时把设置仓库的 `Flow<AppSettings>` 接进来。
 *
 * 为什么需要它：主题层（[com.slideindex.app.ui.theme.OverlayAwareModuleTheme]）已经下沉到
 * `:feature:settings`，而 DI 门面（`OverlayDependencyAccess`）还在 `:app`。模块不能依赖 `:app`，
 * 所以反过来由 `:app` 注入这条流。
 *
 * 未接入时返回 null，调用方按 `AppSettings()` 默认值渲染（与旧行为一致）。
 */
object SettingsFlowHolder {

    @Volatile
    var provider: ((Context) -> Flow<AppSettings>?)? = null

    fun settingsFlow(context: Context): Flow<AppSettings>? = provider?.invoke(context)
}
