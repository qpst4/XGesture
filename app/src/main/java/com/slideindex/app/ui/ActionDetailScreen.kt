package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import com.slideindex.app.R
import com.slideindex.app.external.ExternalInvocationCatalog
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.ui.gesturepicker.actionHasDetailParams
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

/**
 * 动作详情页：基本信息（简介 / 深链 / 立即执行）+ 设置（配置入口或"无需配置"）。
 * 界面只负责展示；执行与跳转由调用方注入，避免把 overlay 依赖带进设置页。
 */
@Composable
fun ActionDetailScreen(
    action: GestureAction,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onOpenConfig: (() -> Unit)?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val label = gestureActionLabel(action)
    val description = gestureActionDescription(action)
    val deeplink = "${ExternalInvocationCatalog.SCHEME}://action?type=${action.type.name}"
    // SettingsLazyScreenScaffold 的 content 是 LazyListScope（非 @Composable），小标题必须提前取好。
    val basicSectionTitle = stringResource(R.string.action_detail_basic_section)
    val settingsSectionTitle = stringResource(R.string.action_detail_settings_section)

    SettingsLazyScreenScaffold(
        title = label,
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "action-detail-basic",
            title = basicSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "action-detail-basic",
            items = listOf(
                settingsCardScopeItem("info") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!description.isNullOrBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = stringResource(R.string.action_detail_deeplink),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = deeplink,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(deeplink))
                                    Toast.makeText(context, R.string.action_detail_deeplink_copied, Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.action_detail_deeplink),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRun,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_detail_run))
                        }
                    }
                },
            ),
        )

        settingsLazySmallTitle(
            key = "action-detail-settings",
            title = settingsSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "action-detail-settings",
            items = listOf(
                settingsCardScopeItem("settings") {
                    if (onOpenConfig != null) {
                        SettingNavigationRow(
                            icon = { l -> Icon(gestureActionTypeOutlinedIcon(action.type), contentDescription = l) },
                            title = stringResource(R.string.action_detail_open_config),
                            subtitle = stringResource(R.string.action_detail_config_hint),
                            onClick = onOpenConfig,
                        )
                    } else {
                        // 内容型动作（打开链接/模拟按键/执行 Shell 命令）没有全局配置页：
                        // 参数在绑定时现场填，这里只说明归属，不再给假入口。
                        val hintRes = if (actionHasDetailParams(action.type)) {
                            R.string.action_detail_config_on_bind
                        } else {
                            R.string.action_detail_no_config
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(hintRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            ),
        )
    }
}
