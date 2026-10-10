package com.slideindex.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.cloudstorage.CloudStorageConfig
import com.slideindex.app.cloudstorage.CloudStorageSettings
import com.slideindex.app.cloudstorage.S3StorageConfig
import com.slideindex.app.cloudstorage.WebDavStorageConfig
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.UploadCloud

/** 保留份数的可选档位；0 表示不限制（与 [CloudStorageSettings.retentionCount] 语义一致）。 */
private val RETENTION_OPTIONS = listOf(1, 2, 3, 5, 10, 20, 0)

/**
 * 云存储配置管理。
 *
 * 只负责"有哪些后端 + 保留几份"，具体的字段编辑在 [CloudStorageConfigEditorScreen]。
 */
@Composable
fun CloudStorageSettingsScreen(
    settings: CloudStorageSettings,
    onOpenEditor: (configId: String) -> Unit,
    onSetRetention: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // settingsLazyXxx 是 LazyListScope 扩展、不是 @Composable 上下文，
    // 里面的文案必须在进入 scaffold 之前就取好
    val listSectionTitle = stringResource(R.string.cloud_storage_section_list)
    val retentionSectionTitle = stringResource(R.string.cloud_storage_retention_section)
    val retentionLabels = RETENTION_OPTIONS.map { retentionLabel(it) }
    val retentionHint = stringResource(R.string.cloud_storage_retention_hint)
    val credentialsTip = stringResource(R.string.cloud_storage_credentials_tip)

    SettingsScreenScaffold(
        title = stringResource(R.string.cloud_storage_settings_title),
        subtitle = stringResource(R.string.cloud_storage_settings_subtitle),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "cloud-storage-list", title = listSectionTitle)
        groupedCardItems(
            keyPrefix = "cloud-storage-list",
            items = buildList {
                add(
                    settingsCardScopeItem("cloud-storage-add") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(MiuixIcons.Add, contentDescription = label, modifier = Modifier.size(24.dp))
                            },
                            title = stringResource(R.string.cloud_storage_add),
                            subtitle = stringResource(R.string.cloud_storage_add_subtitle),
                            onClick = { onOpenEditor(NEW_CONFIG_ID) },
                        )
                    },
                )
                settings.configs.forEach { config ->
                    add(
                        settingsCardScopeItem("cloud-storage-${config.id}") {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(MiuixIcons.UploadCloud, contentDescription = label, modifier = Modifier.size(24.dp))
                                },
                                title = config.displayName.ifBlank { config.baseDir },
                                subtitle = configSummary(config, isActive = config.id == settings.activeConfigId),
                                onClick = { onOpenEditor(config.id) },
                            )
                        },
                    )
                }
            },
        )

        settingsLazySmallTitle(
            key = "cloud-storage-retention",
            title = retentionSectionTitle,
        )
        val retentionIndex = RETENTION_OPTIONS.indexOf(settings.retentionCount)
            .takeIf { it >= 0 }
            ?: RETENTION_OPTIONS.indexOf(CloudStorageSettings.DEFAULT_RETENTION_COUNT)
        groupedCardItems(
            keyPrefix = "cloud-storage-retention",
            items = buildList {
                add(
                    settingsCardScopeItem("cloud-storage-retention-count") {
                        SettingDropdownRow(
                            title = stringResource(R.string.cloud_storage_retention_title),
                            subtitle = stringResource(R.string.cloud_storage_retention_subtitle),
                            items = retentionLabels,
                            selectedIndex = retentionIndex,
                            onSelectedIndexChange = { index -> onSetRetention(RETENTION_OPTIONS[index]) },
                        )
                    },
                )
            },
        )
        settingsLazyHint(
            key = "cloud-storage-retention-hint",
            text = retentionHint,
        )
        settingsLazyTipCard(
            key = "cloud-storage-credentials-tip",
            text = credentialsTip,
        )
    }
}

/** 空串表示"新建"：避免为此再引入一个可空类型的导航参数。 */
const val NEW_CONFIG_ID: String = ""

@Composable
private fun retentionLabel(count: Int): String = if (count <= 0) {
    stringResource(R.string.cloud_storage_retention_unlimited)
} else {
    stringResource(R.string.cloud_storage_retention_count, count)
}

@Composable
private fun configSummary(config: CloudStorageConfig, isActive: Boolean): String {
    val type = when (config) {
        is WebDavStorageConfig -> stringResource(R.string.cloud_storage_type_webdav)
        is S3StorageConfig -> stringResource(R.string.cloud_storage_type_s3)
    }
    val baseDir = config.baseDir.ifBlank { "/" }
    return if (isActive) {
        stringResource(R.string.cloud_storage_summary_active, type, baseDir)
    } else {
        stringResource(R.string.cloud_storage_summary, type, baseDir)
    }
}
