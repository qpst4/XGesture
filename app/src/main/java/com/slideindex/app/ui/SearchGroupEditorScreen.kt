@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.search.SearchEngineValidator
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchGroupDefaults
import com.slideindex.app.settings.SearchGroupKind
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsVerticalReorderList
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.viewmodel.SearchGroupDraft
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchGroupEditorScreen(
    kind: SearchGroupKind,
    draft: SearchGroupDraft,
    allEngines: List<SearchEngineConfig>,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: ((SearchEngineConfig) -> Unit)? = null,
    onDelete: (() -> Unit)?,
    onUpdateDraft: ((SearchGroupDraft) -> SearchGroupDraft) -> Unit,
    onReorderMembers: (List<String>) -> Unit,
    onRemoveMember: (String) -> Unit,
    onAddMember: () -> Unit,
) {
    val isImage = kind == SearchGroupKind.IMAGE_SHARE
    val title = if (draft.isNew) {
        stringResource(
            if (isImage) R.string.search_group_image_add_title else R.string.search_group_add_title,
        )
    } else {
        stringResource(
            if (isImage) R.string.search_group_image_edit_title else R.string.search_group_edit_title,
        )
    }
    val basicSection = stringResource(R.string.search_group_basic_section)
    val membersSection = stringResource(
        if (isImage) R.string.search_group_image_members_section else R.string.search_group_members_section,
    )
    val emptyTitle = stringResource(
        if (isImage) R.string.search_group_image_empty_title else R.string.search_group_empty_title,
    )
    val emptySubtitle = stringResource(
        if (isImage) R.string.search_group_image_empty_subtitle else R.string.search_group_empty_subtitle,
    )
    val addMemberTitle = stringResource(
        if (isImage) R.string.search_group_image_add_member_title else R.string.search_group_add_member_title,
    )
    val addMemberSubtitle = stringResource(
        if (isImage) R.string.search_group_image_add_member_subtitle else R.string.search_group_add_member_subtitle,
    )
    val memberTypeLabel = stringResource(
        if (isImage) R.string.search_group_member_type_image else R.string.search_group_member_type_text,
    )
    val missingLabel = stringResource(R.string.search_group_member_missing)
    val removeLabel = stringResource(R.string.search_group_remove_member)
    val nameEmpty = stringResource(R.string.search_group_name_empty)
    val intervalUnit = stringResource(R.string.search_group_interval_unit)
    val intervalSection = stringResource(R.string.search_group_interval_section)
    val reorderHint = stringResource(R.string.search_group_reorder_hint)
    val previewEngine = remember(
        draft.name,
        draft.memberIds,
        kind,
        nameEmpty,
        draft.iconType,
        draft.iconPath,
        draft.textIcon,
        draft.iconSourcePackage,
    ) {
        SearchEngineConfig(
            id = "search-group-preview",
            name = draft.name.ifBlank { nameEmpty },
            engineType = SearchEngineType.SEARCH_GROUP,
            iconType = draft.iconType,
            iconPath = draft.iconPath,
            textIcon = draft.textIcon,
            iconSourcePackage = draft.iconSourcePackage,
            groupKind = kind,
            groupMemberIds = draft.memberIds,
        )
    }
    val memberEngines = remember(draft.memberIds, allEngines) {
        val byId = allEngines.associateBy { it.id }
        draft.memberIds.map { id ->
            byId[id] ?: SearchEngineConfig(
                id = id,
                name = missingLabel,
                engineType = SearchEngineType.DIRECT_LINK,
                showInPickPanel = false,
            )
        }
    }
    val missingIds = remember(draft.memberIds, allEngines) {
        val known = allEngines.mapTo(HashSet()) { it.id }
        draft.memberIds.filterNot { it in known }.toSet()
    }
    val canSave = draft.name.isNotBlank() && draft.memberIds.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SettingsScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (onShare != null) {
                MiuixTopBarIconButton(
                    onClick = {
                        val engine = draft.toShareEngine(kind)
                        when {
                            draft.name.isBlank() -> Toast.makeText(
                                context,
                                R.string.search_group_name_required,
                                Toast.LENGTH_SHORT,
                            ).show()

                            draft.memberIds.isEmpty() -> Toast.makeText(
                                context,
                                if (isImage) {
                                    R.string.search_group_image_empty_subtitle
                                } else {
                                    R.string.search_group_empty_subtitle
                                },
                                Toast.LENGTH_SHORT,
                            ).show()

                            SearchEngineValidator.validate(engine) -> onShare(engine)
                            else -> Toast.makeText(
                                context,
                                R.string.search_engine_validation_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Share,
                        contentDescription = stringResource(R.string.search_engine_share_subject),
                    )
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onDelete != null) {
                    MiuixTextButton(
                        text = stringResource(R.string.search_group_delete),
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                MiuixButtonCompat(
                    text = stringResource(R.string.search_group_save),
                    enabled = canSave,
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        settingsLazySmallTitle(key = "search-group-basic-title", title = basicSection)
        groupedCardItems(
            keyPrefix = "search-group-basic",
            items = listOf(
                settingsCardItem("search-group-name") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchEngineIconPickerHost(
                            engine = previewEngine,
                            size = 44.dp,
                            onIconPath = { path ->
                                onUpdateDraft {
                                    it.copy(
                                        iconType = if (path != null) {
                                            SearchIconType.URI
                                        } else {
                                            SearchIconType.OTHER
                                        },
                                        iconPath = path,
                                        textIcon = null,
                                        iconSourcePackage = null,
                                    )
                                }
                            },
                            onTextIcon = { text ->
                                onUpdateDraft {
                                    it.copy(
                                        iconType = if (!text.isNullOrBlank()) {
                                            SearchIconType.TEXT
                                        } else {
                                            SearchIconType.OTHER
                                        },
                                        textIcon = text,
                                        iconPath = null,
                                        iconSourcePackage = null,
                                    )
                                }
                            },
                            onIconSourcePackage = { packageName ->
                                onUpdateDraft {
                                    it.copy(
                                        iconType = SearchIconType.OTHER,
                                        iconSourcePackage = packageName,
                                        iconPath = null,
                                        textIcon = null,
                                    )
                                }
                            },
                            onReset = {
                                onUpdateDraft {
                                    it.copy(
                                        iconType = SearchIconType.OTHER,
                                        iconPath = null,
                                        textIcon = null,
                                        iconSourcePackage = null,
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.width(12.dp))
                        com.slideindex.app.ui.miuix.MiuixLabeledTextField(
                            value = draft.name,
                            onValueChange = { name ->
                                onUpdateDraft { it.copy(name = name) }
                            },
                            label = stringResource(R.string.search_group_name_label),
                            modifier = Modifier.weight(1f),
                            useLabelAsPlaceholder = true,
                        )
                    }
                },
            ),
        )

        settingsLazySmallTitle(key = "search-group-members-title", title = membersSection)
        settingsLazyHint(key = "search-group-reorder-hint", text = reorderHint)
        if (draft.memberIds.isEmpty()) {
            groupedCardItems(
                keyPrefix = "search-group-members-empty",
                items = listOf(
                    settingsCardItem("search-group-empty-hint") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MiuixText(
                                text = emptyTitle,
                                style = MiuixTheme.textStyles.title4,
                            )
                            MiuixText(
                                text = emptySubtitle,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    },
                    settingsCardScopeItem("search-group-add-empty") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = label,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            title = addMemberTitle,
                            subtitle = addMemberSubtitle,
                            onClick = onAddMember,
                        )
                    },
                ),
            )
        } else {
            LazySettingsItem(key = "search-group-members-list") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val totalSegments = memberEngines.size + 1
                    SettingsVerticalReorderList(
                        items = memberEngines,
                        key = { it.id },
                        onReorder = { ordered -> onReorderMembers(ordered.map { it.id }) },
                    ) { engine, index, _, _, _ ->
                        BasicComponent(
                            modifier = Modifier.miuixGroupedCardItem(index, totalSegments),
                            title = engine.name,
                            summary = if (engine.id in missingIds) missingLabel else memberTypeLabel,
                            onClick = {},
                            startAction = {
                                SearchEngineIcon(
                                    engine = engine,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            endActions = {
                                MiuixTextButton(
                                    text = removeLabel,
                                    onClick = { onRemoveMember(engine.id) },
                                )
                            },
                        )
                    }
                    BasicComponent(
                        modifier = Modifier.miuixGroupedCardItem(
                            memberEngines.size,
                            totalSegments,
                        ),
                        title = addMemberTitle,
                        summary = addMemberSubtitle,
                        onClick = onAddMember,
                        startAction = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        endActions = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }

        settingsLazySmallTitle(
            key = "search-group-interval-title",
            title = intervalSection,
        )
        groupedCardItems(
            keyPrefix = "search-group-interval",
            items = listOf(
                settingsCardItem("search-group-interval-slider") {
                    MiuixSliderRow(
                        title = stringResource(R.string.search_group_interval_title),
                        summary = stringResource(R.string.search_group_interval_summary),
                        value = draft.intervalMillis.toFloat(),
                        valueRange = SearchGroupDefaults.MIN_INTERVAL_MILLIS.toFloat()..
                            SearchGroupDefaults.MAX_INTERVAL_MILLIS.toFloat(),
                        steps = ((SearchGroupDefaults.MAX_INTERVAL_MILLIS -
                            SearchGroupDefaults.MIN_INTERVAL_MILLIS) /
                            SearchGroupDefaults.INTERVAL_STEP_MILLIS) - 1,
                        label = intervalUnit,
                        formatLabel = { value -> "${value.roundToInt()}$intervalUnit" },
                        onValueChange = { value ->
                            onUpdateDraft {
                                it.copy(
                                    intervalMillis = value.roundToInt().coerceIn(
                                        SearchGroupDefaults.MIN_INTERVAL_MILLIS,
                                        SearchGroupDefaults.MAX_INTERVAL_MILLIS,
                                    ),
                                )
                            }
                        },
                    )
                },
            ),
        )
    }

    if (showDeleteDialog && onDelete != null) {
        MiuixConfirmDialog(
            confirmDestructive = true,
            show = true,
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.search_group_delete_title),
            message = stringResource(R.string.search_group_delete_message, draft.name),
            confirmText = stringResource(R.string.search_group_delete),
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }
}

private fun SearchGroupDraft.toShareEngine(kind: SearchGroupKind): SearchEngineConfig {
    val iconType = when {
        iconSourcePackage != null -> SearchIconType.OTHER
        iconPath != null -> SearchIconType.URI
        !textIcon.isNullOrBlank() -> SearchIconType.TEXT
        else -> SearchIconType.OTHER
    }
    return SearchEngineConfig(
        id = groupId.ifBlank { "search-group-preview" },
        name = name.trim(),
        engineType = SearchEngineType.SEARCH_GROUP,
        iconType = iconType,
        iconPath = iconPath.takeIf { iconType == SearchIconType.URI },
        textIcon = textIcon.takeIf { iconType == SearchIconType.TEXT },
        iconSourcePackage = iconSourcePackage.takeIf { iconType == SearchIconType.OTHER },
        showInPickPanel = enabled,
        groupKind = kind,
        groupMemberIds = memberIds,
        groupIntervalMillis = intervalMillis.coerceIn(
            SearchGroupDefaults.MIN_INTERVAL_MILLIS,
            SearchGroupDefaults.MAX_INTERVAL_MILLIS,
        ),
    )
}

@Composable
private fun MiuixButtonCompat(    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    top.yukonga.miuix.kmp.basic.Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = modifier,
    ) {
        MiuixText(text = text, style = MiuixTheme.textStyles.button)
    }
}
