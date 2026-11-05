package com.slideindex.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.search.SearchEngineFaviconFetcher
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.search.SearchEngineValidator
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.overlay.searchpanel.SearchPanelAliasResolver
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixInsetCardComponentMargin
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.miuix.MiuixSwitchRow
import com.slideindex.app.ui.miuix.MiuixTabSettingsCard
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.viewmodel.SearchEngineDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Ok

enum class SearchEngineEditorCategory {
    TEXT,
    IMAGE_SHARE,
}

data class SearchEngineEditorResult(
    val engine: SearchEngineConfig,
    val iconUri: Uri?,
    val savedIconPath: String? = null,
)

@Composable
fun SearchEngineEditorScreen(
    initialEngine: SearchEngineConfig?,
    draft: SearchEngineDraft,
    editorCategory: SearchEngineEditorCategory = SearchEngineEditorCategory.TEXT,
    onBack: () -> Unit,
    onSave: (SearchEngineEditorResult) -> Unit,
    onUpdateDraft: ((SearchEngineDraft) -> SearchEngineDraft) -> Unit,
    onPickApp: (target: String, titleResId: Int, selectedPackageName: String) -> Unit,
    onPickActivity: (packageName: String, selectedClassName: String) -> Unit,
    onPickShareTarget: (selectedPackageName: String, selectedActivityClassName: String) -> Unit,
) {
    val isNew = initialEngine == null
    val name = draft.name
    val isNameMissing = name.isBlank()
    val aliasCode = draft.aliasCode
    val engineType = draft.engineType
    val searchLink = draft.searchLink
    val externJumpLink = draft.externJumpLink
    val externJumpPackage = draft.externJumpPackage
    val targetPackage = draft.targetPackage
    val targetActivity = draft.targetActivity
    val autoInputEnter = draft.autoInputEnter
    val pendingIconUri = draft.pendingIconUri
    val pendingIconPath = draft.pendingIconPath
    val pendingTextIcon = draft.pendingTextIcon
    val pendingIconSourcePackage = draft.pendingIconSourcePackage

    var showTextIconDialog by remember(initialEngine?.id) { mutableStateOf(false) }
    var showIdentityDialog by remember(initialEngine?.id) { mutableStateOf(false) }
    var nameSaveAttempted by remember(initialEngine?.id) { mutableStateOf(false) }
    var isFetchingFavicon by remember(initialEngine?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fetchFaviconFailedMessage = stringResource(R.string.search_engine_fetch_favicon_failed)
    val pickActivityRequiresPackageMessage =
        stringResource(R.string.search_engine_pick_activity_requires_package)
    val isShareImageType = editorCategory == SearchEngineEditorCategory.IMAGE_SHARE ||
        engineType == SearchEngineType.SHARE_IMAGE_TO_APP
    val canFetchFavicon = !isShareImageType &&
        engineType == SearchEngineType.DIRECT_LINK &&
        searchLink.isNotBlank()
    val previewEngine = remember(
        initialEngine?.id,
        name,
        engineType,
        pendingIconUri,
        pendingIconPath,
        pendingTextIcon,
        pendingIconSourcePackage,
    ) {
        buildPreviewEngine(
            initialEngine = initialEngine,
            name = name,
            engineType = engineType,
            hasPendingIconUri = pendingIconUri != null,
            pendingIconPath = pendingIconPath,
            pendingTextIcon = pendingTextIcon,
            iconSourcePackage = pendingIconSourcePackage,
        )
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        discardPendingIconPath(context, pendingIconPath, initialEngine?.iconPath)
        onUpdateDraft {
            it.copy(
                pendingIconPath = null,
                pendingTextIcon = null,
                pendingIconUri = uri,
                pendingIconSourcePackage = null,
            )
        }
    }

    val saveAction: () -> Unit = {
        if (isNameMissing) {
            // 名称是必填项，但入口藏在标题行里，很容易被当成说明文字跳过。
            // 这里直接打开名称弹窗并说明原因，避免"点保存没反应"。
            nameSaveAttempted = true
            showIdentityDialog = true
            Toast.makeText(
                context,
                R.string.search_engine_name_required,
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            val engine = buildEngine(
                initialEngine = initialEngine,
                name = name.trim(),
                aliasCode = aliasCode,
                engineType = engineType,
                searchLink = searchLink.trim(),
                externJumpLink = externJumpLink.trim(),
                externJumpPackage = externJumpPackage.trim(),
                targetPackage = targetPackage.trim(),
                targetActivity = targetActivity.trim(),
                autoInputEnter = autoInputEnter,
                hasPendingIcon = pendingIconUri != null || pendingIconPath != null,
                pendingIconPath = pendingIconPath,
                pendingTextIcon = pendingTextIcon,
                iconSourcePackage = pendingIconSourcePackage,
            )
            val normalizedAlias = SearchPanelAliasResolver.normalizeAliasCode(engine.aliasCode.orEmpty())
            when {
                !SearchPanelAliasResolver.isValidAliasCode(normalizedAlias) -> {
                    Toast.makeText(
                        context,
                        R.string.search_engine_alias_invalid,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                !SearchEngineValidator.validate(engine) -> {
                    Toast.makeText(
                        context,
                        R.string.search_engine_validation_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> {
                    onSave(
                        SearchEngineEditorResult(
                            engine = engine.copy(
                                aliasCode = normalizedAlias.takeIf { it.isNotEmpty() },
                            ),
                            iconUri = pendingIconUri,
                            savedIconPath = pendingIconPath,
                        ),
                    )
                }
            }
        }
    }

    SettingsScreenScaffold(
        title = stringResource(
            if (isNew) R.string.search_engine_add_title else R.string.search_engine_edit_title,
        ),
        onBack = onBack,
        actions = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = saveAction) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = MiuixIcons.Medium.Ok,
                    contentDescription = stringResource(R.string.search_engine_save),
                )
            }
        },
    ) {
        LazySettingsItem(key = "search-engine-editor") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                ),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallTitle(
                        text = stringResource(R.string.search_engine_pick_icon),
                        insideMargin = PaddingValues(bottom = 8.dp),
                    )

                    val nameNeedsAttention = isNameMissing && nameSaveAttempted
                    val nameFieldAccent = when {
                        nameNeedsAttention -> MiuixTheme.colorScheme.error
                        isNameMissing -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.dividerLine
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showIdentityDialog = true }
                            .drawBehind {
                                drawRoundRect(
                                    color = nameFieldAccent,
                                    cornerRadius = CornerRadius(12.dp.toPx()),
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = if (isNameMissing) {
                                            PathEffect.dashPathEffect(
                                                floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                                            )
                                        } else {
                                            null
                                        },
                                    ),
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchEngineEditorIconPreview(
                            previewEngine = previewEngine,
                            pendingIconUri = pendingIconUri,
                            modifier = Modifier.size(48.dp),
                            backgroundColor = MiuixTheme.colorScheme.surfaceVariant,
                            fallbackContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name.ifBlank {
                                    stringResource(R.string.search_engine_name_placeholder)
                                },
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isNameMissing) {
                                    nameFieldAccent
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceContainer
                                },
                            )
                            Text(
                                text = if (isNameMissing) {
                                    stringResource(R.string.search_engine_name_required)
                                } else {
                                    stringResource(R.string.search_engine_edit_identity_hint)
                                },
                                style = MiuixTheme.textStyles.body2,
                                color = if (nameNeedsAttention) {
                                    MiuixTheme.colorScheme.error
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = MiuixIcons.Medium.Edit,
                            contentDescription = stringResource(R.string.search_engine_edit_identity_title),
                            tint = if (isNameMissing) {
                                nameFieldAccent
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (!isShareImageType) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 「用应用图标」时说明图标是跟随某个应用的，图标库改图会自动跟着变。
                            pendingIconSourcePackage?.let { sourcePackage ->
                                MiuixHintText(
                                    text = stringResource(
                                        R.string.search_engine_icon_following_app,
                                        sourcePackage,
                                    ),
                                    horizontalPadding = 0.dp,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                IconSourceButton(
                                    onClick = { iconPicker.launch("image/*") },
                                    enabled = true,
                                    isLoading = false,
                                    icon = MiuixIcons.Image,
                                    label = stringResource(R.string.search_engine_pick_icon),
                                    modifier = Modifier.weight(1f),
                                )
                                IconSourceButton(
                                    onClick = {
                                        onPickApp(
                                            "APP_ICON",
                                            R.string.search_engine_pick_app_icon_title,
                                            "",
                                        )
                                    },
                                    enabled = true,
                                    isLoading = false,
                                    icon = MiuixIcons.GridView,
                                    label = stringResource(R.string.search_engine_pick_app_icon),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                IconSourceButton(
                                    onClick = {
                                        if (isFetchingFavicon) return@IconSourceButton
                                        scope.launch {
                                            isFetchingFavicon = true
                                            val iconPath = SearchEngineFaviconFetcher.fetchAndSave(
                                                context = context,
                                                searchLink = searchLink.trim(),
                                            )
                                            isFetchingFavicon = false
                                            if (iconPath != null) {
                                                discardPendingIconPath(
                                                    context,
                                                    pendingIconPath,
                                                    initialEngine?.iconPath,
                                                )
                                                onUpdateDraft {
                                                    it.copy(
                                                        pendingIconUri = null,
                                                        pendingTextIcon = null,
                                                        pendingIconPath = iconPath,
                                                        pendingIconSourcePackage = null,
                                                    )
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    fetchFaviconFailedMessage,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                    enabled = canFetchFavicon,
                                    isLoading = isFetchingFavicon,
                                    icon = MiuixIcons.Download,
                                    label = stringResource(
                                        if (isFetchingFavicon) {
                                            R.string.search_engine_fetch_favicon_loading
                                        } else {
                                            R.string.search_engine_fetch_favicon
                                        },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                IconSourceButton(
                                    onClick = { showTextIconDialog = true },
                                    enabled = true,
                                    isLoading = false,
                                    icon = MiuixIcons.File,
                                    label = stringResource(R.string.search_engine_text_icon),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            if (isShareImageType) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SmallTitle(
                            text = stringResource(R.string.search_engine_share_image_target_section),
                            insideMargin = PaddingValues(bottom = 8.dp),
                        )
                        val targetSummary = when {
                            targetPackage.isBlank() -> stringResource(R.string.search_engine_share_image_target_not_set)
                            targetActivity.isBlank() -> targetPackage
                            else -> "$targetPackage / ${targetActivity.substringAfterLast('.')}"
                        }
                        MiuixHintText(targetSummary, horizontalPadding = 0.dp)
                        MiuixButton(
                            onClick = { onPickShareTarget(targetPackage, targetActivity) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.search_engine_pick_share_image_target))
                        }
                    }
                }
            } else {
                EditorTypeFields(
                    engineType = engineType,
                    onEngineTypeChange = { onUpdateDraft { d -> d.copy(engineType = it) } },
                    searchLink = searchLink,
                    onSearchLinkChange = { onUpdateDraft { d -> d.copy(searchLink = it) } },
                    externJumpLink = externJumpLink,
                    onExternJumpLinkChange = { onUpdateDraft { d -> d.copy(externJumpLink = it) } },
                    externJumpPackage = externJumpPackage,
                    onExternJumpPackageChange = { onUpdateDraft { d -> d.copy(externJumpPackage = it) } },
                    targetPackage = targetPackage,
                    onTargetPackageChange = { onUpdateDraft { d -> d.copy(targetPackage = it) } },
                    targetActivity = targetActivity,
                    onTargetActivityChange = { onUpdateDraft { d -> d.copy(targetActivity = it) } },
                    autoInputEnter = autoInputEnter,
                    onAutoInputEnterChange = { onUpdateDraft { d -> d.copy(autoInputEnter = it) } },
                    onPickTargetApp = {
                        onPickApp(
                            "TARGET",
                            R.string.search_engine_pick_app_title,
                            targetPackage,
                        )
                    },
                    onPickExternApp = {
                        onPickApp(
                            "EXTERN",
                            R.string.search_engine_pick_app_title,
                            externJumpPackage,
                        )
                    },
                    onPickActivity = {
                        if (targetPackage.isBlank()) {
                            Toast.makeText(
                                context,
                                pickActivityRequiresPackageMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            onPickActivity(targetPackage, targetActivity)
                        }
                    },
                    onPickShareTarget = { onPickShareTarget(targetPackage, targetActivity) },
                )
            }
        }
        }
    }

    if (showTextIconDialog) {
        TextIconDialog(
            initialText = pendingTextIcon.orEmpty().ifBlank { name.take(2) },
            onDismiss = { showTextIconDialog = false },
            onConfirm = { text ->
                discardPendingIconPath(context, pendingIconPath, initialEngine?.iconPath)
                onUpdateDraft {
                    it.copy(
                        pendingIconUri = null,
                        pendingIconPath = null,
                        pendingTextIcon = text,
                        pendingIconSourcePackage = null,
                    )
                }
                showTextIconDialog = false
            },
        )
    }

    if (showIdentityDialog) {
        MiuixFormDialog(
            show = true,
            title = stringResource(R.string.search_engine_edit_identity_title),
            onDismissRequest = { showIdentityDialog = false },
            confirmText = stringResource(R.string.search_engine_save),
            confirmEnabled = name.isNotBlank(),
            onConfirm = { showIdentityDialog = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixLabeledTextField(
                    value = name,
                    onValueChange = { onUpdateDraft { d -> d.copy(name = it) } },
                    label = stringResource(R.string.search_engine_name_hint),
                )
                MiuixLabeledTextField(
                    value = aliasCode,
                    onValueChange = { onUpdateDraft { d -> d.copy(aliasCode = it) } },
                    label = stringResource(R.string.search_engine_alias_hint),
                )
                MiuixHintText(stringResource(R.string.search_engine_alias_support))
            }
        }
    }
}

@Composable
private fun EditorTypeFields(
    engineType: SearchEngineType,
    onEngineTypeChange: (SearchEngineType) -> Unit,
    searchLink: String,
    onSearchLinkChange: (String) -> Unit,
    externJumpLink: String,
    onExternJumpLinkChange: (String) -> Unit,
    externJumpPackage: String,
    onExternJumpPackageChange: (String) -> Unit,
    targetPackage: String,
    onTargetPackageChange: (String) -> Unit,
    targetActivity: String,
    onTargetActivityChange: (String) -> Unit,
    autoInputEnter: Boolean,
    onAutoInputEnterChange: (Boolean) -> Unit,
    onPickTargetApp: () -> Unit,
    onPickExternApp: () -> Unit,
    onPickActivity: () -> Unit,
    onPickShareTarget: () -> Unit,
) {
    val engineTypes = listOf(
        SearchEngineType.DIRECT_LINK to stringResource(R.string.search_engine_editor_tab_link),
        SearchEngineType.JUMP_TO_ACTIVITY to stringResource(R.string.search_engine_editor_tab_activity),
        SearchEngineType.SHARE_TO_APP to stringResource(R.string.search_engine_editor_tab_share),
    )
    val selectedIndex = engineTypes.indexOfFirst { it.first == engineType }.coerceAtLeast(0)

    MiuixTabSettingsCard(
        tabs = engineTypes.map { it.second },
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            if (index in engineTypes.indices) {
                onEngineTypeChange(engineTypes[index].first)
            }
        },
        outerHorizontalPadding = 0.dp,
        outerBottomPadding = 0.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (engineType) {
        SearchEngineType.DIRECT_LINK -> {
            MiuixLabeledTextField(
                value = searchLink,
                onValueChange = onSearchLinkChange,
                label = stringResource(R.string.search_engine_url_link_hint),
            )
            MiuixHintText(
                stringResource(R.string.search_engine_search_link_support),
                horizontalPadding = 0.dp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiuixLabeledTextField(
                    value = targetPackage,
                    onValueChange = onTargetPackageChange,
                    label = stringResource(R.string.search_engine_target_package_hint),
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(onClick = onPickTargetApp) {
                    Text(stringResource(R.string.search_engine_pick_app))
                }
            }

            MiuixSwitchRow(
                title = stringResource(R.string.search_engine_auto_input_enter),
                summary = stringResource(R.string.search_engine_auto_input_enter_desc),
                checked = autoInputEnter,
                insideMargin = MiuixInsetCardComponentMargin,
                onCheckedChange = onAutoInputEnterChange,
            )
            MiuixHintText(
                stringResource(R.string.search_engine_url_link_flow_desc),
                horizontalPadding = 0.dp,
            )
        }

        SearchEngineType.JUMP_TO_ACTIVITY -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiuixLabeledTextField(
                    value = targetPackage,
                    onValueChange = onTargetPackageChange,
                    label = stringResource(R.string.search_engine_target_package_hint),
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(onClick = onPickTargetApp) {
                    Text(stringResource(R.string.search_engine_pick_app))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiuixLabeledTextField(
                    value = targetActivity,
                    onValueChange = onTargetActivityChange,
                    label = stringResource(R.string.search_engine_target_activity_hint),
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(onClick = onPickActivity, enabled = targetPackage.isNotBlank()) {
                    Text(stringResource(R.string.search_engine_pick_activity))
                }
            }

            MiuixSwitchRow(
                title = stringResource(R.string.search_engine_auto_input_enter),
                summary = stringResource(R.string.search_engine_auto_input_enter_desc),
                checked = autoInputEnter,
                insideMargin = MiuixInsetCardComponentMargin,
                onCheckedChange = onAutoInputEnterChange,
            )
            MiuixHintText(
                stringResource(R.string.search_engine_jump_activity_flow_desc),
                horizontalPadding = 0.dp,
            )
        }

        SearchEngineType.EXTERN_JUMP_LINK -> {
            MiuixLabeledTextField(
                value = externJumpLink,
                onValueChange = onExternJumpLinkChange,
                label = stringResource(R.string.search_engine_extern_link_hint),
            )
            MiuixHintText(
                stringResource(R.string.search_engine_url_link_support),
                horizontalPadding = 0.dp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiuixLabeledTextField(
                    value = externJumpPackage,
                    onValueChange = onExternJumpPackageChange,
                    label = stringResource(R.string.search_engine_extern_package_hint),
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(onClick = onPickExternApp) {
                    Text(stringResource(R.string.search_engine_pick_app))
                }
            }

            MiuixSwitchRow(
                title = stringResource(R.string.search_engine_auto_input_enter),
                summary = stringResource(R.string.search_engine_auto_input_enter_desc),
                checked = autoInputEnter,
                insideMargin = MiuixInsetCardComponentMargin,
                onCheckedChange = onAutoInputEnterChange,
            )
            MiuixHintText(
                stringResource(R.string.search_engine_url_link_flow_desc),
                horizontalPadding = 0.dp,
            )
        }

        SearchEngineType.SHARE_TO_APP -> {
            SmallTitle(
                text = stringResource(R.string.search_engine_share_text_target_section),
                insideMargin = PaddingValues(bottom = 8.dp),
            )
            val targetSummary = when {
                targetPackage.isBlank() -> stringResource(R.string.search_engine_share_text_target_not_set)
                targetActivity.isBlank() -> targetPackage
                else -> "$targetPackage / ${targetActivity.substringAfterLast('.')}"
            }
            MiuixHintText(targetSummary, horizontalPadding = 0.dp)
            MiuixButton(
                onClick = onPickShareTarget,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.search_engine_pick_share_text_target))
            }
        }

        SearchEngineType.SHARE_IMAGE_TO_APP,
        SearchEngineType.SHARE_FILE_TO_APP -> Unit
            }
        }
    }
}

private fun buildPreviewEngine(
    initialEngine: SearchEngineConfig?,
    name: String,
    engineType: SearchEngineType,
    hasPendingIconUri: Boolean,
    pendingIconPath: String?,
    pendingTextIcon: String?,
    iconSourcePackage: String?,
): SearchEngineConfig {
    val iconType = when {
        iconSourcePackage != null -> SearchIconType.OTHER
        hasPendingIconUri || pendingIconPath != null -> SearchIconType.URI
        !pendingTextIcon.isNullOrBlank() -> SearchIconType.TEXT
        else -> initialEngine?.iconType ?: SearchIconType.OTHER
    }
    return (initialEngine ?: SearchEngineConfig(
        id = "preview",
        name = name,
        engineType = engineType,
    )).copy(
        name = name,
        engineType = engineType,
        iconType = iconType,
        iconPath = if (iconType == SearchIconType.URI) pendingIconPath ?: initialEngine?.iconPath else null,
        textIcon = if (iconType == SearchIconType.TEXT) {
            pendingTextIcon ?: initialEngine?.textIcon
        } else {
            null
        },
        iconSourcePackage = iconSourcePackage,
    )
}

private fun buildEngine(
    initialEngine: SearchEngineConfig?,
    name: String,
    aliasCode: String,
    engineType: SearchEngineType,
    searchLink: String,
    externJumpLink: String,
    externJumpPackage: String,
    targetPackage: String,
    targetActivity: String,
    autoInputEnter: Boolean,
    hasPendingIcon: Boolean,
    pendingIconPath: String?,
    pendingTextIcon: String?,
    iconSourcePackage: String?,
): SearchEngineConfig {
    val id = initialEngine?.id ?: "engine_${System.currentTimeMillis()}"
    val iconType = when {
        iconSourcePackage != null -> SearchIconType.OTHER
        hasPendingIcon -> SearchIconType.URI
        !pendingTextIcon.isNullOrBlank() -> SearchIconType.TEXT
        else -> initialEngine?.iconType ?: SearchIconType.OTHER
    }
    return SearchEngineConfig(
        id = id,
        name = name,
        aliasCode = aliasCode.takeIf { it.isNotEmpty() },
        engineType = engineType,
        searchLink = if (engineType == SearchEngineType.DIRECT_LINK || engineType == SearchEngineType.EXTERN_JUMP_LINK) searchLink else null,
        externJumpLink = if (engineType == SearchEngineType.EXTERN_JUMP_LINK) externJumpLink.takeIf { it.isNotEmpty() } else null,
        externJumpPackage = if (engineType == SearchEngineType.EXTERN_JUMP_LINK) externJumpPackage.takeIf { it.isNotEmpty() } else null,
        targetPackage = when (engineType) {
            SearchEngineType.JUMP_TO_ACTIVITY,
            SearchEngineType.DIRECT_LINK,
            SearchEngineType.SHARE_TO_APP,
            SearchEngineType.SHARE_IMAGE_TO_APP,
            -> targetPackage.takeIf { it.isNotEmpty() }
            else -> null
        },
        targetActivity = when (engineType) {
            SearchEngineType.JUMP_TO_ACTIVITY,
            SearchEngineType.SHARE_TO_APP,
            SearchEngineType.SHARE_IMAGE_TO_APP,
            -> targetActivity.takeIf { it.isNotEmpty() }
            else -> null
        },
        autoInputEnter = autoInputEnter,
        iconType = iconType,
        iconSourcePackage = iconSourcePackage,
        iconPath = if (iconType == SearchIconType.URI) {
            pendingIconPath ?: initialEngine?.iconPath?.takeIf { initialEngine.iconType == SearchIconType.URI }
        } else {
            null
        },
        textIcon = if (iconType == SearchIconType.TEXT) {
            (pendingTextIcon ?: initialEngine?.textIcon)?.takeIf { it.isNotBlank() }
        } else {
            null
        },
    )
}

private fun discardPendingIconPath(
    context: android.content.Context,
    pendingIconPath: String?,
    savedIconPath: String?,
) {
    if (pendingIconPath != null && pendingIconPath != savedIconPath) {
        SearchEngineIconStorage.deleteIconIfOwned(context, pendingIconPath)
    }
}

private fun searchEngineEditorIconNeedsPlaceholder(engine: SearchEngineConfig): Boolean {
    if (engine.iconType == SearchIconType.TEXT) {
        return engine.textIcon.isNullOrBlank() && engine.name.isBlank()
    }
    return engine.iconPath.isNullOrBlank() && engine.name.isBlank()
}

@Composable
private fun SearchEngineEditorIconPreview(
    previewEngine: SearchEngineConfig,
    pendingIconUri: Uri?,
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color,
    fallbackContentColor: androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current
    var pendingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(pendingIconUri) {
        pendingBitmap = pendingIconUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
        }
    }
    Box(modifier = modifier) {
        val pending = pendingBitmap
        if (pending != null) {
            Image(
                bitmap = pending.asImageBitmap(),
                contentDescription = previewEngine.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else if (searchEngineEditorIconNeedsPlaceholder(previewEngine)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Image,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = fallbackContentColor,
                )
            }
        } else {
            SearchEngineIcon(
                engine = previewEngine,
                modifier = Modifier.fillMaxSize(),
                backgroundColor = backgroundColor,
                fallbackContentColor = fallbackContentColor,
            )
        }
    }
}

@Composable
private fun engineTypeLabel(type: SearchEngineType): String {
    return when (type) {
        SearchEngineType.DIRECT_LINK -> stringResource(R.string.search_engine_type_direct_link)
        SearchEngineType.JUMP_TO_ACTIVITY -> stringResource(R.string.search_engine_type_jump_activity)
        SearchEngineType.EXTERN_JUMP_LINK -> stringResource(R.string.search_engine_type_extern_jump)
        SearchEngineType.SHARE_TO_APP -> stringResource(R.string.search_engine_type_share)
        SearchEngineType.SHARE_IMAGE_TO_APP -> stringResource(R.string.search_engine_type_share_image)
        SearchEngineType.SHARE_FILE_TO_APP -> "文件分享"
    }
}

@Composable
private fun IconSourceButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    MiuixButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
    ) {
        if (isLoading) {
            MiuixCircularProgressIndicator(modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextIconDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember(initialText) { mutableStateOf(initialText) }
    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.search_engine_text_icon_title),
        confirmEnabled = input.trim().isNotEmpty(),
        onConfirm = { onConfirm(input.trim()) },
    ) {
        MiuixLabeledTextField(
            value = input,
            onValueChange = { if (it.length <= 8) input = it },
            label = stringResource(R.string.search_engine_text_icon_hint),
        )
        Text(
            text = stringResource(R.string.search_engine_text_icon_support),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
