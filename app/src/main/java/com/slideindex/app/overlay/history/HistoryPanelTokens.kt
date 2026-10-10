package com.slideindex.app.overlay.history

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `ui_demo_capsule.2050.html` 的 `:root` token，**逐条照搬**。
 *
 * 为什么不直接用 miuix 的 `MiuixTheme.colorScheme`：设计稿是唯一规格（见 `docs/capsule-refactor-plan.md` §0），
 * 而 miuix 的语义色（`surfaceContainer` / `primaryVariant` …）和 demo 的实际观感差得很远
 * （尤其玻璃卡片的"半透明白渐变 + 白描边 + 顶部高光"）。所以面板这块**自带一套 token**，
 * 值全部来自 demo，浅色/深色两套都抄。
 */
@Immutable
internal data class HistoryTheme(
    val text: Color,
    val sub: Color,
    val hair: Color,
    val line: Color,
    val btnBg: Color,
    val btnBd: Color,
    val appBg: Color,
    val appElev: Color,
    val accent: Color,
    val accentSolid: Color,
    val accentSoft: Color,
    val danger: Color,
    /** `.item.fresh .box { background: var(--g-fill) }` */
    val glassFillTop: Color,
    val glassFillBottom: Color,
    val glassBorder: Color,
    val glassRim: Color,
    /** `.item.fresh .box { box-shadow: 0 14px 30px -18px var(--g-shadow) }` */
    val glassShadow: Color,
    /** `--scrim`：面板左侧那层压暗（呼出时按进度淡入）。 */
    val scrim: Color,
    val isDark: Boolean,
    /**
     * `[data-glass="off"] .g { background: var(--g-solid) }`：
     * 毛玻璃关掉时**面板不能还是半透明**（否则底下的 App 会清清楚楚透出来），
     * 设计稿这时换的是一层**不透明**渐变。浅色 #FFFFFF→#F4F6FB，深色 #1D2029→#171A21。
     */
    val glassSolidTop: Color,
    val glassSolidBottom: Color,
    /**
     * **卡片底色**（`.item .box`）—— **单色**，刻意不用竖向渐变。
     *
     * 刻意**不复用** [glassFillTop]/[glassFillBottom]（面板底也是那一组）：卡片叠在同样白的
     * 面板上等于"白压白"，底色分不出层级、观感发灰（用户反馈"卡片淡、发灰"）。
     * 现在卡片比面板实一档 —— 浅色 90% 白（面板 78%→58%）、深色 65%（面板 58%→52%），
     * 仍是玻璃半透明（**没有**改成不透明，别拿 [glassSolidTop] 那组替）。
     *
     * ⚠️ **为什么必须是单色**（真机截图逐像素量出来的）：描边是"8% 黑"，压在什么底色上就出什么
     * 颜色。渐变卡的顶是纯白、底偏灰蓝，于是**同一张卡的上下两条边天然不同色** —— 实测上边
     * rgb(235,237,238)（亮度 236）、下边 rgb(226,229,234)（亮度 228），差 8 级，肉眼就是
     * "顶边淡、底边深"。改成单色后四条边落在同一个底色上，配色完全一致。
     */
    val cardColor: Color,
    /* ------------------------------------------------------------------
     * 以下不是 demo 的值，是**主动加强对比度**的结果（用户反馈：搜索框/页签行/标签
     * "浅灰基本看不见，费眼睛"）。demo 那几处是"白底白描边 + 4% 灰"，在真实浅色
     * 面板上确实糊成一片。数值按"能看清但不抢眼"定。
     * ------------------------------------------------------------------ */
    /** 小字/次要文字（时间、条数、未选中页签）：比 `--sub` 再深一档。 */
    val subStrong: Color,
    /** 搜索框：底与描边。 */
    val fieldBg: Color,
    val fieldBorder: Color,
    /** 页签分段控件：轨道底/描边 + 选中片的底。 */
    val segTrack: Color,
    val segBorder: Color,
    val pillSelected: Color,
    /** 标签药丸/卡片的描边（demo 是白色描边，在浅色面板上等于没有）。 */
    val chipBorder: Color,
    val cardBorder: Color,
) {
    /** `.chip { background: var(--g-fill) }`、`.fab.open` 也是它。 */
    val glassFill: Brush get() = Brush.verticalGradient(listOf(glassFillTop, glassFillBottom))

    /** 不透明版本（玻璃关闭时的面板底）。 */
    val glassSolid: Brush get() = Brush.verticalGradient(listOf(glassSolidTop, glassSolidBottom))

    /** 卡片底：**单色**（见 [cardColor]；刻意不用渐变 —— 渐变会让上下两条边不同色）。 */
    val cardFill: Brush get() = SolidColor(cardColor)
}

private val LightHistoryTheme = HistoryTheme(
    text = Color(0xFF14161C),
    sub = Color(0xFF666C80),
    hair = Color(0x12121628),
    line = Color(0x17121628),
    btnBg = Color(0x0A121628),
    btnBd = Color(0x21121628),
    appBg = Color(0xFFEEF1F7),
    appElev = Color(0xFFF7F8FC),
    accent = Color(0xFF5B6CF0),
    accentSolid = Color(0xFF4A57D8),
    accentSoft = Color(0x245B6CF0),
    danger = Color(0xFFC43D3F),
    glassFillTop = Color(0xC7FFFFFF),
    glassFillBottom = Color(0x94FFFFFF),
    glassBorder = Color(0xEBFFFFFF),
    glassRim = Color(0xFFFFFFFF),
    glassShadow = Color(0x4D181E34),
    scrim = Color(0x3314182C),
    subStrong = Color(0xFF545A6E),
    fieldBg = Color(0x0D121628),
    fieldBorder = Color(0x24121628),
    segTrack = Color(0x0F121628),
    segBorder = Color(0x14121628),
    pillSelected = Color(0xFFFFFFFF),
    chipBorder = Color(0x1A121628),
    cardBorder = Color(0x14121628),
    isDark = false,
    glassSolidTop = Color(0xFFFFFFFF),
    glassSolidBottom = Color(0xFFF4F6FB),
    cardColor = Color(0xE6FFFFFF),
)

private val DarkHistoryTheme = HistoryTheme(
    text = Color(0xFFECEEF4),
    sub = Color(0xFF9BA1B2),
    hair = Color(0x14FFFFFF),
    line = Color(0x1AFFFFFF),
    btnBg = Color(0x13FFFFFF),
    btnBd = Color(0x30FFFFFF),
    appBg = Color(0xFF0A0B0F),
    appElev = Color(0xFF12141A),
    accent = Color(0xFF6E7CF7),
    accentSolid = Color(0xFF5563E8),
    accentSoft = Color(0x296E7CF7),
    danger = Color(0xFFFF8F8F),
    glassFillTop = Color(0x9434394A),
    glassFillBottom = Color(0x851C202C),
    glassBorder = Color(0x1CFFFFFF),
    glassRim = Color(0x4DFFFFFF),
    glassShadow = Color(0xB3000000),
    scrim = Color(0x3314182C),
    subStrong = Color(0xFFB9BECD),
    fieldBg = Color(0x1FFFFFFF),
    fieldBorder = Color(0x2EFFFFFF),
    segTrack = Color(0x1AFFFFFF),
    segBorder = Color(0x1FFFFFFF),
    pillSelected = Color(0xFF2A2E3A),
    chipBorder = Color(0x24FFFFFF),
    cardBorder = Color(0x1FFFFFFF),
    isDark = true,
    glassSolidTop = Color(0xFF1D2029),
    glassSolidBottom = Color(0xFF171A21),
    cardColor = Color(0xA634394A),
)

@Composable
internal fun historyTheme(): HistoryTheme =
    if (isSystemInDarkTheme()) DarkHistoryTheme else LightHistoryTheme

/** demo `:root` 的圆角刻度。 */
internal object HistoryRadii {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 14.dp
    val lg: Dp = 18.dp
    val xl: Dp = 22.dp
    val xxl: Dp = 26.dp
    val pill: Dp = 999.dp
}

/** demo `:root` 的字号刻度（`--f-tiny` … `--f-body`）。 */
internal object HistoryFontSizes {
    val tiny = 10.5.sp
    val meta = 11.5.sp
    val sm = 12.5.sp
    val body = 13.5.sp
    val titleS = 15.sp
    val title = 19.sp
}

/** demo `:root` 的间距刻度（`--s1` … `--s7`）。 */
internal object HistorySpacing {
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 20.dp
    val s6: Dp = 24.dp
    val s7: Dp = 32.dp

    /** `--hit`：Android 最小触摸目标。 */
    val hit: Dp = 48.dp
}

/** demo 的动画时长刻度（`--d1` … `--d3`，毫秒）。 */
internal object HistoryDurations {
    const val d1 = 140
    const val d2 = 200
    const val d3 = 280
}

/** demo 的两条缓动曲线：`--e-out: cubic-bezier(.22,1,.36,1)`、`--e-io: cubic-bezier(.4,0,.2,1)`。 */
internal object HistoryEasing {
    val out = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val io = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/**
 * `.item .box .acts { margin-left: -(48-22)/2 = -13px }`：让第一个图标的**字形**左缘和正文的 14dp 对齐。
 *
 * ⚠️ Compose 的 `padding` 不接受负值（`IllegalArgumentException: Padding must be non-negative`，
 * 这一条真机上直接闪退过）。所以这里存的是**正的 13dp**，用 `Modifier.offset(x = -HistoryActsOffsetX)`
 * 加在**行首那几个图标**上；行尾的 ⋮ 不动（CSS 的负 margin 只让整行往左变宽，右端仍在原处）。
 */
internal val HistoryActsOffsetX: Dp = 13.dp