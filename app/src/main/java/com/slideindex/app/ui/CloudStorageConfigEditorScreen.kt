package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.cloudstorage.CloudStorageConfig
import com.slideindex.app.cloudstorage.S3StorageConfig
import com.slideindex.app.cloudstorage.WebDavStorageConfig
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.viewmodel.CloudConnectionTestState
import java.util.UUID
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DEFAULT_BASE_DIR = "/cebian"
private const val TYPE_WEBDAV_INDEX = 0
private const val TYPE_S3_INDEX = 1

/**
 * 单个云存储后端的编辑页。
 *
 * 类型选择只有两项，且**不区分"阿里云 OSS / 标准 S3"**：
 * OSS 兼容 S3 API 且支持 SigV4，原生端点会在请求前自动改写为 S3 兼容端点
 * （见 `ObjectStorageEndpoint`），所以用户不必像用 ClipShare 那样先选对存储类型。
 */
@Composable
fun CloudStorageConfigEditorScreen(
    initial: CloudStorageConfig?,
    isActiveTarget: Boolean,
    connectionTest: CloudConnectionTestState,
    onTestConnection: (CloudStorageConfig) -> Unit,
    onDismissConnectionTest: () -> Unit,
    onSave: (config: CloudStorageConfig, makeActive: Boolean) -> Unit,
    onRemove: (configId: String) -> Unit,
    onBack: () -> Unit,
) {
    val isNew = initial == null
    // 配置 id 必须跨重组稳定：新建时在首次组合生成一次就够了
    val configId = remember { initial?.id ?: UUID.randomUUID().toString() }
    val initialWebDav = initial as? WebDavStorageConfig
    val initialS3 = initial as? S3StorageConfig
    // 配置来自 DataStore 流，首帧可能还没读出来（initial 为 null）：
    // 以 id 为 key，等真正的配置到达后再把表单种子值填一遍，
    // 否则编辑已有配置时会先渲染空表单、并把用户的配置覆盖成空。
    val seedKey = initial?.id
    val initialUserAgent = initialWebDav?.userAgent ?: initialS3?.userAgent

    // mutableIntStateOf 而不是 mutableStateOf：避免 lint 的 AutoboxingStateCreation
    var typeIndex by remember(seedKey) {
        mutableIntStateOf(if (initialS3 != null) TYPE_S3_INDEX else TYPE_WEBDAV_INDEX)
    }
    var displayName by remember(seedKey) { mutableStateOf(initial?.displayName.orEmpty()) }
    var baseDir by remember(seedKey) {
        mutableStateOf(initial?.baseDir?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_DIR)
    }
    var userAgent by remember(seedKey) { mutableStateOf(initialUserAgent.orEmpty()) }
    var makeActive by remember(seedKey) { mutableStateOf(isActiveTarget) }
    var confirmRemove by remember { mutableStateOf(false) }

    var server by remember(seedKey) { mutableStateOf(initialWebDav?.server.orEmpty()) }
    var username by remember(seedKey) { mutableStateOf(initialWebDav?.username.orEmpty()) }
    var password by remember(seedKey) { mutableStateOf(initialWebDav?.password.orEmpty()) }

    var endpoint by remember(seedKey) { mutableStateOf(initialS3?.endpoint.orEmpty()) }
    var accessKey by remember(seedKey) { mutableStateOf(initialS3?.accessKey.orEmpty()) }
    var secretKey by remember(seedKey) { mutableStateOf(initialS3?.secretKey.orEmpty()) }
    var bucket by remember(seedKey) { mutableStateOf(initialS3?.bucket.orEmpty()) }
    var region by remember(seedKey) { mutableStateOf(initialS3?.region.orEmpty()) }
    var pathStyle by remember(seedKey) { mutableStateOf(initialS3?.pathStyle ?: false) }

    val isWebDav = typeIndex == TYPE_WEBDAV_INDEX

    fun buildConfig(): CloudStorageConfig = if (isWebDav) {
        WebDavStorageConfig(
            id = configId,
            displayName = displayName.trim(),
            baseDir = baseDir.trim(),
            server = server.trim(),
            username = username.trim(),
            password = password,
            userAgent = userAgent.trim().takeIf { it.isNotEmpty() },
        )
    } else {
        S3StorageConfig(
            id = configId,
            displayName = displayName.trim(),
            baseDir = baseDir.trim(),
            endpoint = endpoint.trim(),
            accessKey = accessKey.trim(),
            secretKey = secretKey.trim(),
            bucket = bucket.trim(),
            region = region.trim().takeIf { it.isNotEmpty() },
            pathStyle = pathStyle,
            userAgent = userAgent.trim().takeIf { it.isNotEmpty() },
        )
    }

    val valid = displayName.isNotBlank() && baseDir.isNotBlank() && if (isWebDav) {
        server.trim().startsWith("http") && username.isNotBlank() && password.isNotBlank()
    } else {
        endpoint.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank() && bucket.isNotBlank()
    }

    // settingsLazyXxx 是 LazyListScope 扩展、不是 @Composable 上下文，文案要提前取好
    val typeSectionTitle = stringResource(R.string.cloud_storage_editor_type_section)
    val fieldsSectionTitle = stringResource(R.string.cloud_storage_editor_fields_section)
    val baseDirHint = stringResource(R.string.cloud_storage_base_dir_hint)
    val credentialsTip = stringResource(R.string.cloud_storage_credentials_tip)
    val typeLabels = listOf(
        stringResource(R.string.cloud_storage_type_webdav),
        stringResource(R.string.cloud_storage_type_s3),
    )

    SettingsScreenScaffold(
        title = stringResource(
            if (isNew) R.string.cloud_storage_editor_title_new else R.string.cloud_storage_editor_title_edit,
        ),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "cloud-storage-editor-type", title = typeSectionTitle)
        groupedCardItems(
            keyPrefix = "cloud-storage-editor-type",
            items = buildList {
                add(
                    settingsCardScopeItem("cloud-storage-editor-type-pick") {
                        SettingDropdownRow(
                            title = stringResource(R.string.cloud_storage_editor_type_label),
                            items = typeLabels,
                            selectedIndex = typeIndex,
                            onSelectedIndexChange = { typeIndex = it },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("cloud-storage-editor-active") {
                        SettingSwitchRow(
                            title = stringResource(R.string.cloud_storage_make_active),
                            subtitle = stringResource(R.string.cloud_storage_make_active_subtitle),
                            checked = makeActive,
                            enabled = true,
                            onCheckedChange = { makeActive = it },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "cloud-storage-editor-fields", title = fieldsSectionTitle)
        LazySettingsItem(key = "cloud-storage-editor-fields-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixLabeledTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = stringResource(R.string.cloud_storage_field_name),
                    )
                    if (isWebDav) {
                        MiuixLabeledTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = stringResource(R.string.cloud_storage_field_server),
                        )
                        MiuixLabeledTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = stringResource(R.string.cloud_storage_field_username),
                        )
                        MiuixLabeledTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(R.string.cloud_storage_field_password),
                        )
                    } else {
                        MiuixLabeledTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = stringResource(R.string.cloud_storage_field_endpoint),
                        )
                        Text(
                            text = stringResource(R.string.cloud_storage_endpoint_hint),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        MiuixLabeledTextField(
                            value = accessKey,
                            onValueChange = { accessKey = it },
                            label = stringResource(R.string.cloud_storage_field_access_key),
                        )
                        MiuixLabeledTextField(
                            value = secretKey,
                            onValueChange = { secretKey = it },
                            label = stringResource(R.string.cloud_storage_field_secret_key),
                        )
                        MiuixLabeledTextField(
                            value = bucket,
                            onValueChange = { bucket = it },
                            label = stringResource(R.string.cloud_storage_field_bucket),
                        )
                        MiuixLabeledTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = stringResource(R.string.cloud_storage_field_region),
                        )
                        Text(
                            text = stringResource(R.string.cloud_storage_region_hint),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    MiuixLabeledTextField(
                        value = baseDir,
                        onValueChange = { baseDir = it },
                        label = stringResource(R.string.cloud_storage_field_base_dir),
                    )
                    MiuixLabeledTextField(
                        value = userAgent,
                        onValueChange = { userAgent = it },
                        label = stringResource(R.string.cloud_storage_field_user_agent),
                    )
                }
            }
        }
        settingsLazyHint(key = "cloud-storage-editor-base-dir-hint", text = baseDirHint)

        if (!isWebDav) {
            groupedCardItems(
                keyPrefix = "cloud-storage-editor-advanced",
                items = buildList {
                    add(
                        settingsCardScopeItem("cloud-storage-editor-path-style") {
                            SettingSwitchRow(
                                title = stringResource(R.string.cloud_storage_field_path_style),
                                subtitle = stringResource(R.string.cloud_storage_path_style_hint),
                                checked = pathStyle,
                                enabled = true,
                                onCheckedChange = { pathStyle = it },
                            )
                        },
                    )
                },
            )
        }

        LazySettingsItem(key = "cloud-storage-editor-actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { onTestConnection(buildConfig()) },
                        enabled = valid && !connectionTest.testing,
                    ) {
                        if (connectionTest.testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(stringResource(R.string.cloud_storage_test_connection))
                    }
                }
                Button(
                    onClick = { onSave(buildConfig(), makeActive) },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.cloud_storage_save))
                }
                if (!valid) {
                    Text(
                        text = stringResource(R.string.cloud_storage_invalid_form),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                if (!isNew) {
                    Button(
                        onClick = { confirmRemove = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_storage_delete),
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        settingsLazyTipCard(key = "cloud-storage-editor-credentials-tip", text = credentialsTip)
    }

    MiuixConfirmDialog(
        show = connectionTest.message != null,
        onDismissRequest = onDismissConnectionTest,
        title = stringResource(
            if (connectionTest.success == true) {
                R.string.cloud_storage_connection_ok_title
            } else {
                R.string.cloud_storage_connection_failed_title
            },
        ),
        message = connectionTest.message,
        onConfirm = onDismissConnectionTest,
        dismissText = stringResource(android.R.string.cancel),
    )

    MiuixConfirmDialog(
        show = confirmRemove,
        onDismissRequest = { confirmRemove = false },
        title = stringResource(R.string.cloud_storage_delete_confirm_title),
        message = stringResource(R.string.cloud_storage_delete_confirm_message),
        onConfirm = {
            confirmRemove = false
            onRemove(configId)
        },
        confirmText = stringResource(R.string.cloud_storage_delete),
        dismissText = stringResource(android.R.string.cancel),
    )
}
