package com.slideindex.app.ui.settings.components

import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.searchpanel.SearchPanelAliasResolver
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchPanelSectionAliasSettings
import com.slideindex.app.ui.miuix.MiuixFormDialog

@Composable
fun AliasPill(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    onClearClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    val contentColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.then(
            if (onClick != null && onClearClick == null) {
                Modifier.clip(shape).clickable(onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = Color.Transparent,
        border = if (showBackground) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                if (showBackground) {
                    PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                } else {
                    PaddingValues(start = 0.dp, top = 2.dp, end = 8.dp, bottom = 2.dp)
                },
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = if (onClick != null && onClearClick != null) {
                    Modifier.clip(shape).clickable(onClick = onClick)
                } else {
                    Modifier
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
            if (onClearClick != null) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.search_panel_section_alias_clear),
                    tint = contentColor,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp)
                        .clip(shape)
                        .clickable(onClick = onClearClick),
                )
            }
        }
    }
}

@Composable
fun SectionAliasCodeDisplay(
    aliasCode: String,
    sectionTitle: String,
    defaultAlias: String,
    sectionAliases: SearchPanelSectionAliasSettings,
    engines: List<SearchEngineConfig>,
    excludeSectionKey: String,
    onAliasChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val displayCode = aliasCode.trim()

    if (showDialog) {
        SectionAliasEditDialog(
            currentCode = displayCode,
            sectionTitle = sectionTitle,
            defaultAlias = defaultAlias,
            sectionAliases = sectionAliases,
            engines = engines,
            excludeSectionKey = excludeSectionKey,
            onSave = { code ->
                onAliasChange(code)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }

    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (displayCode.isNotEmpty()) {
            AliasPill(
                text = displayCode,
                onClick = { showDialog = true },
                onClearClick = {
                    if (displayCode != defaultAlias) {
                        onAliasChange(defaultAlias)
                    }
                }.takeIf { displayCode != defaultAlias },
            )
        } else {
            AliasPill(
                text = stringResource(R.string.search_panel_section_alias_add),
                onClick = { showDialog = true },
                showBackground = false,
            )
        }
    }
}

@Composable
private fun SectionAliasEditDialog(
    currentCode: String,
    sectionTitle: String,
    defaultAlias: String,
    sectionAliases: SearchPanelSectionAliasSettings,
    engines: List<SearchEngineConfig>,
    excludeSectionKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(currentCode) { mutableStateOf(currentCode) }
    val normalized = remember(draft) { SearchPanelAliasResolver.normalizeAliasCode(draft) }
    val isValid = remember(normalized) {
        normalized.isEmpty() || SearchPanelAliasResolver.isValidAliasCode(normalized)
    }
    val conflict = remember(normalized, sectionAliases, engines, excludeSectionKey) {
        sectionAliasConflict(
            code = normalized,
            excludeSectionKey = excludeSectionKey,
            sectionAliases = sectionAliases,
            engines = engines,
        )
    }
    val canSave = isValid && conflict == null
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = if (currentCode.isBlank()) {
            stringResource(R.string.search_panel_section_alias_add_title, sectionTitle)
        } else {
            stringResource(R.string.search_panel_section_alias_edit_title, sectionTitle)
        },
        confirmText = stringResource(R.string.confirm),
        confirmEnabled = canSave,
        onConfirm = {
            onSave(normalized.ifEmpty { defaultAlias })
        },
    ) {
        MiuixLabeledTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(R.string.search_panel_section_alias_label),
            singleLine = true,
            useLabelAsPlaceholder = false,
            supportingText = when {
                !isValid -> stringResource(R.string.search_panel_section_alias_invalid)
                conflict != null -> stringResource(R.string.search_panel_section_alias_conflict)
                else -> stringResource(R.string.search_panel_section_alias_hint)
            },
            isError = !isValid || conflict != null,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

internal fun sectionAliasConflict(
    code: String,
    excludeSectionKey: String,
    sectionAliases: SearchPanelSectionAliasSettings,
    engines: List<SearchEngineConfig>,
): String? {
    val normalized = SearchPanelAliasResolver.normalizeAliasCode(code)
    if (normalized.isEmpty()) return null
    val n = sectionAliases.normalized()
    val occupied = buildMap {
        if (excludeSectionKey != SearchPanelSectionAliasSettings.SECTION_APPS) {
            put(n.apps, SearchPanelSectionAliasSettings.SECTION_APPS)
        }
        if (excludeSectionKey != SearchPanelSectionAliasSettings.SECTION_CONTACTS) {
            put(n.contacts, SearchPanelSectionAliasSettings.SECTION_CONTACTS)
        }
        if (excludeSectionKey != SearchPanelSectionAliasSettings.SECTION_FILES) {
            put(n.files, SearchPanelSectionAliasSettings.SECTION_FILES)
        }
        if (excludeSectionKey != SearchPanelSectionAliasSettings.SECTION_SETTINGS) {
            put(n.settings, SearchPanelSectionAliasSettings.SECTION_SETTINGS)
        }
    }
    if (occupied.containsKey(normalized)) return normalized
    val engineHit = engines.firstOrNull {
        SearchPanelAliasResolver.normalizeAliasCode(it.aliasCode.orEmpty()) == normalized
    }
    return if (engineHit != null) normalized else null
}
