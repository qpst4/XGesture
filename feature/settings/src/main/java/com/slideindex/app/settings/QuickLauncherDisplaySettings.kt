package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.overlay.layout.PanelIconShape
import kotlin.math.roundToInt

data class QuickLauncherDisplaySettings(
    val backgroundOpacityPercent: Int = DEFAULT_BACKGROUND_OPACITY_PERCENT,
    val iconSizeDp: Int = DEFAULT_ICON_SIZE_DP,
    val iconShape: Int = ICON_SHAPE_DEFAULT,
    val panelIconShape: PanelIconShape = PanelIconShape.FOLLOW_GLOBAL,
    /**
     * 是否在面板图标下方显示名称。
     *
     * 关掉后只显示图标，格子里的图标改为垂直居中，面板与文件夹都生效；
     * 名称仍用于无障碍朗读与搜索，不影响点击命中区域。
     */
    val showLabels: Boolean = true,
    val blurRadiusDp: Int = DEFAULT_BLUR_RADIUS_DP,
    /**
     * 是否显示面板底部那条操作栏（+ 添加 / − 编辑 / ⇄ 切面板）。
     *
     * 关掉后：普通使用态面板下方干净，进入编辑态只能走「设置 → 扩展中心 → 快速启动器」里的编辑器。
     * 但**编辑态本身仍然强制显示**——否则用户在面板内进了编辑态就再也没有「✓」可以退出来。
     */
    val showToolbar: Boolean = true,
    /**
     * 右侧面板是否左右**镜像**（默认开，维持历史设计）。
     *
     * 开着：右侧呼出时格子从右往左铺、翻页手势也跟着镜像，看起来就是左侧面板的镜像。
     * 关掉：不管从哪一侧呼出，格子一律从左往右铺，翻页也按从左往右的语义
     *（往左拖 → 下一页），和设置页预览、以及多数启动器的习惯一致。
     *
     * 注意：这里只影响「内容排版」；面板落在屏幕哪一边、从哪边滑出来仍由触发侧决定。
     */
    val mirrorOnRightSide: Boolean = true,
) {
    companion object {
        const val MIN_BACKGROUND_OPACITY_PERCENT = 0
        const val MAX_BACKGROUND_OPACITY_PERCENT = 100
        /** 默认背景不透明度 63%。 */
        const val DEFAULT_BACKGROUND_OPACITY_PERCENT = 63

        const val MIN_ICON_SIZE_DP = 28
        const val MAX_ICON_SIZE_DP = 48
        /** 对齐 overlay 硬编码 `host.dp(38f)` */
        const val DEFAULT_ICON_SIZE_DP = 38

        /** 0 = 不模糊，仅显示纯色背景。 */
        const val MIN_BLUR_RADIUS_DP = 0
        const val MAX_BLUR_RADIUS_DP = 150
        /** 默认模糊强度 16 dp。 */
        const val DEFAULT_BLUR_RADIUS_DP = 16

        /** 不二次裁剪，保留系统绘制结果。 */
        const val ICON_SHAPE_DEFAULT = 0
        /** @deprecated 已移除；持久化值 1 会迁移为 [ICON_SHAPE_DEFAULT]。 */
        const val ICON_SHAPE_ROUNDED_LEGACY = 1
        const val ICON_SHAPE_CIRCLE = 2
        const val ICON_SHAPE_ADAPTIVE = 3

        fun coerceIconShape(shape: Int): Int = when (shape) {
            ICON_SHAPE_CIRCLE, ICON_SHAPE_ADAPTIVE -> shape
            else -> ICON_SHAPE_DEFAULT
        }

        fun legacyBackgroundOpacityPercent(panelOpacity: Float): Int {
            val alpha = (225f * panelOpacity).toInt().coerceIn(150, 225)
            return (alpha * 100f / 255f).roundToInt()
                .coerceIn(MIN_BACKGROUND_OPACITY_PERCENT, MAX_BACKGROUND_OPACITY_PERCENT)
        }

        fun backgroundAlphaArgb(backgroundOpacityPercent: Int): Int =
            (255f * backgroundOpacityPercent.coerceIn(
                MIN_BACKGROUND_OPACITY_PERCENT,
                MAX_BACKGROUND_OPACITY_PERCENT,
            ) / 100f).roundToInt().coerceIn(0, 255)

        fun fromPreferences(prefs: Preferences): QuickLauncherDisplaySettings {
            val storedOpacity = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_BACKGROUND_OPACITY_PERCENT]
            return QuickLauncherDisplaySettings(
                backgroundOpacityPercent = storedOpacity?.coerceIn(
                    MIN_BACKGROUND_OPACITY_PERCENT,
                    MAX_BACKGROUND_OPACITY_PERCENT,
                ) ?: DEFAULT_BACKGROUND_OPACITY_PERCENT,
                iconSizeDp = (prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ICON_SIZE_DP]
                    ?: DEFAULT_ICON_SIZE_DP)
                    .coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP),
                iconShape = coerceIconShape(
                    prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ICON_SHAPE] ?: ICON_SHAPE_DEFAULT,
                ),
                panelIconShape = PanelIconShape.fromName(
                    prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ICON_SHAPE_NAME]
                ),
                showLabels = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_SHOW_LABELS] ?: true,
                blurRadiusDp = (prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_BLUR_RADIUS_DP]
                    ?: DEFAULT_BLUR_RADIUS_DP)
                    .coerceIn(MIN_BLUR_RADIUS_DP, MAX_BLUR_RADIUS_DP),
                showToolbar = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_SHOW_TOOLBAR] ?: true,
                mirrorOnRightSide = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_MIRROR_ON_RIGHT_SIDE] ?: true,
            )
        }
    }
}
