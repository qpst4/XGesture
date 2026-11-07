package com.slideindex.app.overlay.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 收纳面板专用色：Miuix 浅色下 background 与 surfaceContainer 均为白，
 * 须用 surface（灰底）+ surfaceContainer（白卡）才能看出卡片边界。
 */
object HistoryPanelColors {
    @Composable
    fun panelChrome(blurActive: Boolean = false): Color {
        val scheme = MiuixTheme.colorScheme
        return if (blurActive) {
            scheme.surfaceContainer.copy(alpha = 0.72f)
        } else {
            scheme.surfaceContainer
        }
    }

    @Composable
    fun listBackground(blurActive: Boolean = false): Color {
        val scheme = MiuixTheme.colorScheme
        return if (blurActive) {
            scheme.surface.copy(alpha = 0.65f)
        } else {
            scheme.surface
        }
    }

    /**
     * 卡片底色。
     *
     * [done]（待办已完成）优先级高于 [starred]：设计稿里完成态是「整张变暗后划掉」，
     * 若同时星标，星标的强调色不该盖过"已完成"的弱化。
     *
     * [group] 是设计稿里**按时间分档**的卡片观感（这是列表观感的大头）：
     * - **今天**（`.item.fresh .box`）：实心卡片 + 亮边 + 投影，最"厚"；
     * - **昨天**（`.item.mid .box`）：半透明；
     * - **更早**（`.item.old .box`）：**完全透明**，只剩文字浮在面板底上。
     */
    @Composable
    fun cardBackground(
        starred: Boolean,
        done: Boolean = false,
        group: HistoryDayGroup? = null,
    ): Color {
        val scheme = MiuixTheme.colorScheme
        return when {
            done -> scheme.surfaceContainer.copy(alpha = 0.55f)
            starred -> scheme.primaryVariant.copy(alpha = 0.35f)
            group == HistoryDayGroup.Earlier -> Color.Transparent
            group == HistoryDayGroup.Yesterday -> scheme.surfaceContainer.copy(alpha = 0.66f)
            else -> scheme.surfaceContainer
        }
    }

    /**
     * 卡片描边（设计稿 `.item.fresh .box { border-color: var(--g-border) }`）：
     * 只有"今天"那档有一条亮边（玻璃感靠它），昨天用普通分隔色，更早不描边。
     */
    @Composable
    fun cardBorder(group: HistoryDayGroup?): Color? {
        val scheme = MiuixTheme.colorScheme
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        return when (group) {
            HistoryDayGroup.Today -> Color.White.copy(alpha = if (isDark) 0.12f else 0.92f)
            HistoryDayGroup.Yesterday -> scheme.dividerLine
            else -> null
        }
    }

    /** 已完成条目的整体不透明度（设计稿 `.item.done .box { opacity: .62 }`）。 */
    const val DONE_CONTENT_ALPHA = 0.62f
}
