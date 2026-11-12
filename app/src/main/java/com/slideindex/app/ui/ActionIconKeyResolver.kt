package com.slideindex.app.ui

import android.content.Context
import com.slideindex.app.gesture.GestureAction

/**
 * 动作图标的「全局覆盖 key」唯一解析入口。
 *
 * 优先级（越具体越优先）：
 * 1. **启动面板**图标：`GestureAction.QuickLauncher(panelId)` 对应的面板自己设的图标；
 * 2. **动作类型**图标：扩展 → 图标库 → 动作；
 * 3. **应用**图标：扩展 → 图标库 → 应用（只对「打开应用」有意义）。
 *
 * 为什么收敛成一处：这段 `actionIcon ?: appIcon` 原来在 `ActionIconBitmap`（Canvas）、
 * `ActionIconSlot`（Compose）、`GestureSlotActionIcon`（槽位行）各写一份，
 * 加「面板」这一维时只要漏一处，同一个动作在不同入口就会显示不同图标。
 *
 * 返回 null = 没有任何覆盖，调用方继续用动作自带图标。
 */
object ActionIconKeyResolver {

    fun resolve(context: Context, action: GestureAction): String? =
        panelIconKey(context, action)
            ?: GlobalIconStore.actionIcon(context, action.type.name)
            ?: (action as? GestureAction.LaunchApp)?.let {
                GlobalIconStore.appIcon(context, it.packageName)
            }

    private fun panelIconKey(context: Context, action: GestureAction): String? {
        val panelId = (action as? GestureAction.QuickLauncher)
            ?.panelId
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return GlobalIconStore.panelIcon(context, panelId)
    }
}
