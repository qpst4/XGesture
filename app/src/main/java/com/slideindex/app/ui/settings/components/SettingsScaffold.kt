@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixExpandableSearchBottomContent
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixListScaffold
import com.slideindex.app.ui.miuix.MiuixSettingsScreenScaffold
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack

@Composable
fun SettingsEmbeddedContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SettingsScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    pageHint: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    enableBackHandler: Boolean = true,
    overlayMode: Boolean = false,
    embedded: Boolean = false,
    scrollContent: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    embeddedContentPadding: PaddingValues = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
    /** 悬浮按钮需要预留的底部空间，见 [com.slideindex.app.ui.miuix.MiuixSettingsFabInset]。 */
    fabInset: androidx.compose.ui.unit.Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    if (embedded) {
        error("embedded SettingsScreenScaffold is not supported with LazyListScope content")
    }
    MiuixSettingsScreenScaffold(
        title = title,
        modifier = modifier,
        pageHint = pageHint,
        subtitle = subtitle,
        onBack = onBack,
        enableBackHandler = false,
        overlayMode = overlayMode,
        scrollContent = scrollContent,
        actions = actions,
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        fabInset = fabInset,
        content = content,
    )
}

@Composable
fun SettingsLazyScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    pageHint: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    enableBackHandler: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    userScrollEnabled: Boolean = true,
    /** 悬浮按钮需要预留的底部空间，见 [com.slideindex.app.ui.miuix.MiuixSettingsFabInset]。 */
    fabInset: androidx.compose.ui.unit.Dp = 0.dp,
    /** 外部持有的滚动状态（例如「打开时定位到当前已绑定的那一项」需要给出初始下标）。 */
    listState: LazyListState? = null,
    /** 玻璃弹层槽，透传给 [MiuixListScaffold]。 */
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    MiuixListScaffold(
        title = title,
        modifier = modifier,
        pageHint = pageHint,
        subtitle = subtitle,
        showPageHintCard = true,
        onBack = onBack,
        actions = actions,
        floatingActionButton = floatingActionButton,
        bottomContent = bottomContent,
        userScrollEnabled = userScrollEnabled,
        fabInset = fabInset,
        listState = listState,
        overlay = overlay,
    ) {
        content()
    }
}

@Composable
fun SettingsLazyScreenScaffoldWithExpandableSearch(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pageHint: String? = null,
    subtitle: String? = null,
    hintResId: Int = R.string.search_hint,
    enableBackHandler: Boolean = true,
    extraActions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    /** 悬浮按钮需要预留的底部空间，见 [com.slideindex.app.ui.miuix.MiuixSettingsFabInset]。 */
    fabInset: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val handleBack: () -> Unit = {
        if (
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = onSearchQueryChange,
            )
        ) {
            onBack()
        }
    }
    SettingsLazyScreenScaffold(
        title = title,
        modifier = modifier,
        pageHint = pageHint,
        subtitle = subtitle,
        onBack = handleBack,
        enableBackHandler = enableBackHandler,
        floatingActionButton = floatingActionButton,
        fabInset = fabInset,
        actions = {
            MiuixExpandableSearchIconAction(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = onSearchQueryChange,
            )
            extraActions()
        },
        bottomContent = {
            MiuixExpandableSearchBottomContent(
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                focusRequester = searchFocusRequester,
                hintResId = hintResId,
            )
        },
        content = content,
    )
}

@Composable
fun SettingsCardScope.SettingsHintText(text: String, modifier: Modifier = Modifier) {
    SettingsCardRow(key = text) { _ ->
        MiuixHintText(text, modifier)
    }
}

@Composable
fun SettingsCardScope.SettingsLabeledTextFieldRow(
    key: Any,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    SettingsCardRow(key = key) { _ ->
        MiuixLabeledTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
        )
    }
}