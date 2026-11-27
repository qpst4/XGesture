package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.MiuixGlassSegmentedTabRow
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.LocalMiuixContentUnderBar
import com.slideindex.app.ui.miuix.LocalMiuixPageBackdrop
import com.slideindex.app.ui.miuix.LocalMiuixScreenBackdrop
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow

internal enum class NotificationFilterTab {
    HISTORY,
    ACTIVE,
    HIDDEN,
}

@Composable
internal fun NotificationHistoryTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val tabs = listOf(
        stringResource(R.string.notification_filter_tab_history),
        stringResource(R.string.notification_filter_tab_active),
        stringResource(R.string.notification_filter_history_hidden),
    )
    // 对齐 Mishka ProviderScreen 的用法：显式传「内容层」backdrop（不是栏上下文那层），
    // 模糊不可用时退回 miuix 轮廓 tab，避免半透明底没有模糊导致文字透底。
    // 水平只补 4dp：底栏内容槽位自带 12dp，合计 16dp 与列表卡片对齐（Mishka 那里是 12dp 且槽位无 padding）。
    val backdrop = LocalMiuixScreenBackdrop.current ?: LocalMiuixPageBackdrop.current
    // 对齐 miuix 示例：内容滚到栏下（canScrollBackward）才升起玻璃；空列表退回纯色，
    // 避免没有可采样内容时走 shader 出现越界伪影。
    val contentUnderBar = LocalMiuixContentUnderBar.current.value
MiuixGlassSegmentedTabRow(
    tabs = tabs,
    selectedTabIndex = selectedTab,
    onTabSelected = onTabSelected,
    modifier = Modifier,
    contentHorizontalPadding = 4.dp,
    verticalPadding = 4.dp,
)
}

internal fun LazyListScope.notificationHistoryFilterBarItems(
    listenerEnabled: Boolean,
    onGrantListenerAccess: () -> Unit,
) {
    if (!listenerEnabled) {
        item(key = "permission_card") {
            NotificationHistoryPermissionCard(
                onGrant = onGrantListenerAccess,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
internal fun NotificationHistoryPermissionCard(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.notification_history_permission_title),
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.notification_history_permission_desc),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.notification_history_permission_grant))
            }
        }
    }
}
