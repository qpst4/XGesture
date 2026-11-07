package com.slideindex.app.ui.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.design.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏搜索图标：未展开时点击展开；已展开且无内容时收起；有内容时清空。
 */
@Composable
fun MiuixExpandableSearchIconAction(
    expanded: Boolean,
    query: String,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    val searchLabel = stringResource(R.string.search_hint)
    IconButton(
        onClick = {
            if (expanded) {
                if (query.isNotBlank()) {
                    onQueryChange("")
                } else {
                    onExpandedChange(false)
                }
            } else {
                onExpandedChange(true)
            }
        },
    ) {
        Icon(
            imageVector = MiuixIcons.Search,
            contentDescription = searchLabel,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/** 返回键优先收起搜索：有内容先清空，无内容则收起。已处理时返回 true。 */
fun consumeExpandableSearchBack(
    expanded: Boolean,
    query: String,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
): Boolean {
    if (!expanded) return false
    if (query.isNotBlank()) {
        onQueryChange("")
        return true
    }
    onExpandedChange(false)
    return true
}

/**
 * [TopAppBar.bottomContent]：仅带动画展开的 Miuix 搜索框（无 Tab）。
 */
@Composable
fun MiuixExpandableSearchBottomContent(
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    hintResId: Int = R.string.search_hint,
) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(ExpandableSearchFocusDelayMs)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    AnimatedVisibility(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        visible = searchExpanded,
        enter = expandVertically(
            animationSpec = tween(ExpandableSearchEnterMs),
            expandFrom = Alignment.Top,
        ) + fadeIn(animationSpec = tween(ExpandableSearchEnterMs)),
        exit = shrinkVertically(
            animationSpec = tween(ExpandableSearchExitMs),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(animationSpec = tween(ExpandableSearchExitMs)),
    ) {
        MiuixSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            hintResId = hintResId,
            focusRequester = focusRequester,
        )
    }
}

/** Overlay / 自定义顶栏内：与 scaffold bottomContent 搜索条相同的展开动画。 */
@Composable
fun MiuixExpandableSearchFieldStrip(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    hintResId: Int = R.string.search_hint,
) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(ExpandableSearchFocusDelayMs)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    AnimatedVisibility(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        visible = expanded,
        enter = expandVertically(
            animationSpec = tween(ExpandableSearchEnterMs),
            expandFrom = Alignment.Top,
        ) + fadeIn(animationSpec = tween(ExpandableSearchEnterMs)),
        exit = shrinkVertically(
            animationSpec = tween(ExpandableSearchExitMs),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(animationSpec = tween(ExpandableSearchExitMs)),
    ) {
        MiuixSearchField(
            query = query,
            onQueryChange = onQueryChange,
            hintResId = hintResId,
            focusRequester = focusRequester,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/**
 * [TopAppBar.bottomContent]：带动画展开 Miuix 搜索框 + Tab 行（搜索在 Tab 上方）。
 */
@Composable
fun MiuixScaffoldSearchTabBottomContent(
    searchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    tabContent: @Composable () -> Unit,
    hintResId: Int = R.string.search_hint,
) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(ExpandableSearchFocusDelayMs)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        AnimatedVisibility(
            modifier = Modifier.clipToBounds(),
            visible = searchExpanded,
            enter = expandVertically(
                animationSpec = tween(ExpandableSearchEnterMs),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(ExpandableSearchEnterMs)),
            exit = shrinkVertically(
                animationSpec = tween(ExpandableSearchExitMs),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(ExpandableSearchExitMs)),
        ) {
            MiuixSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hintResId = hintResId,
                focusRequester = focusRequester,
                modifier = Modifier.padding(bottom = ExpandableSearchTabSpacing),
            )
        }
        tabContent()
    }
}

private val ExpandableSearchTabSpacing = 8.dp
private const val ExpandableSearchEnterMs = 220
private const val ExpandableSearchExitMs = 200
private const val ExpandableSearchFocusDelayMs = 180L
