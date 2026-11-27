package com.slideindex.app.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.GestureProfile
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 侧滑手势「按应用分档」的档管理页（GitHub issue #26 v2）。
 *
 * 一档 = 一整套独立触钮集合（挂哪些触钮、位置/宽高/外观、各自绑什么手势，竖屏 + 横屏各一套）。
 * 建档时把**当时的全局**整份复制进档，之后不跟随全局；前台是绑定到该档的 App 时才用它。
 *
 * 列表用 [itemsIndexed] 以档 id 为 key：档名允许重名，用档名当 key 会撞。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureAppProfilesScreen(
    enabled: Boolean,
    profiles: List<GestureProfile>,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
    onRemoveProfile: (String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<GestureProfile?>(null) }
    // ⚠️ `settingsLazySmallTitle` 是**非 @Composable** 的 DSL 函数，`stringResource` 必须在外面取好。
    val sectionTitle = stringResource(R.string.gesture_app_profiles_section)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.gesture_app_profiles_title),
        pageHint = stringResource(R.string.gesture_app_profiles_desc),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "gesture-app-profiles-master",
            items = listOf(
                settingsCardScopeItem("enabled") {
                    SettingSwitchRow(
                        title = stringResource(R.string.gesture_app_profiles_enabled),
                        subtitle = stringResource(R.string.gesture_app_profiles_enabled_desc),
                        checked = enabled,
                        enabled = true,
                        onCheckedChange = onEnabledChange,
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "gesture-app-profiles-section",
            title = sectionTitle,
        )

        if (profiles.isEmpty()) {
            managedAppListEmpty(key = "gesture-app-profiles-empty") {
                stringResource(R.string.gesture_app_profiles_empty)
            }
        } else {
            itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                BasicComponent(
                    // ⚠️ 最后一行补 12dp 底部外边距 = 与下面「新建档」那张卡之间的标准间距；
                    // `miuixGroupedCardItem` 默认是 0dp，两张圆角卡贴在一起会出现"双圆角掐腰"。
                    modifier = Modifier.miuixGroupedCardItem(
                        index = index,
                        count = profiles.size,
                        outerBottomPadding = 12.dp,
                    ),
                    title = profile.name,
                    summary = stringResource(
                        R.string.gesture_app_profiles_bound_count,
                        profile.packageNames.size,
                    ),
                    // 与首页那一行同一个图标：从入口到每档形成同一个视觉锚点。
                    startAction = {
                        Icon(
                            imageVector = HomeLeadingIcons.gestureAppProfiles(true),
                            contentDescription = null,
                        )
                    },
                    onClick = { onOpenProfile(profile.id) },
                    endActions = {
                        IconButton(onClick = { deleteTarget = profile }) {
                            Icon(
                                MiuixIcons.Medium.Delete,
                                contentDescription = stringResource(
                                    R.string.gesture_app_profiles_delete,
                                ),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            }
        }

        managedAppListAddRow(
            key = "gesture-app-profiles-add",
            title = { stringResource(R.string.gesture_app_profiles_new) },
            onClick = { showCreateDialog = true },
        )
    }

    MiuixFormDialog(
        show = showCreateDialog,
        onDismissRequest = {
            showCreateDialog = false
            newName = ""
        },
        title = stringResource(R.string.gesture_app_profiles_new_title),
        confirmText = stringResource(R.string.confirm),
        onConfirm = {
            onCreateProfile(newName)
            newName = ""
        },
    ) {
        MiuixLabeledTextField(
            value = newName,
            onValueChange = { newName = it },
            label = stringResource(R.string.gesture_app_profiles_name_hint),
        )
    }

    val target = deleteTarget
    MiuixConfirmDialog(
        confirmDestructive = true,
        show = target != null,
        onDismissRequest = { deleteTarget = null },
        title = stringResource(
            R.string.gesture_app_profiles_delete_confirm_title,
            target?.name.orEmpty(),
        ),
        message = stringResource(R.string.gesture_app_profiles_delete_confirm_message),
        confirmText = stringResource(R.string.gesture_app_profiles_delete),
        onConfirm = { target?.let { onRemoveProfile(it.id) } },
    )
}
