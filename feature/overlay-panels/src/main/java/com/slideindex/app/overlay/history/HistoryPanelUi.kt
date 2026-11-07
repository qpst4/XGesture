package com.slideindex.app.overlay.history

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.settings.StashPanelWidthMode
import com.slideindex.app.stash.StashMetaRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.TextStyle

/** 顶栏工具图标：与 [MiuixExpandableSearchIconAction] 同尺寸、同色。 */
@Composable
fun HistoryPanelToolbarIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 浮窗卡片本体"吃点击"（**空实现，只消费**）。
 *
 * ⚠️ 没有它，点在卡片上的**空白处**（内边距 / 行间距 / 纯文字区）会穿到下面那层
 * "点空白关闭"的遮罩上 —— 用户实测：手指明明落在弹窗里，弹窗却被关掉了。
 * 面板自己早就这么干了（`HistoryPanelScreen` 里面板根节点那个空 `clickable {}`），
 * 三块浮窗当时漏了这一步。
 */

/** 收纳面板列表滚动：MIUI 回弹 + 触底触感。 */
fun Modifier.historyPanelListScrollEffects(): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical(nestedScrollToParent = false)

/** 列表参与顶栏毛玻璃采样。 */
fun Modifier.historyPanelListBackdrop(backdrop: LayerBackdrop?): Modifier =
    historyPanelListScrollEffects().then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)

/**
 * 列表尾部留给右下角 FAB 的空白（只有闪念页签有 FAB）。
 *
 * 54dp（FAB 本体）+ 32dp（它现在的底边偏移）+ 10dp 余量 = 96dp —— 之前是 64dp，
 * 那是 FAB 还贴着屏幕底边时定的，结果最后一张卡的 ⋮ 会被 FAB 压住。
 */
val HistoryListFooterPadding = 96.dp

