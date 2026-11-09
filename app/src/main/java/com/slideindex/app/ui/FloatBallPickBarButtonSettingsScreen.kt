package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.PickResultBarButtons
import com.slideindex.app.settings.PickResultImageBarButton
import com.slideindex.app.settings.PickResultTextBarButton
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsVerticalReorderList
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 取词面板底栏按钮的顺序与显隐。
 *
 * 交互照抄桌面长按菜单那页（[LauncherShortcutMenuScreen]）并贴近竞品：
 * - 长按拖动排序；
 * - 右侧开关 = 是否显示在底栏，关掉后仍留在列表里（可再打开），底栏渲染时跳过；
 * - 「恢复默认」把两份列表顺序与隐藏集合一起清空。
 *
 * 顺序只对「纯图标模式」生效；经典样式的位置仍由「复制按钮位置 / 图片工具行位置」决定。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallPickBarButtonSettingsScreen(
    textOrder: List<String>,
    textHidden: Set<String>,
    imageOrder: List<String>,
    imageHidden: Set<String>,
    onTextOrderChange: (List<String>) -> Unit,
    onTextHiddenChange: (Set<String>) -> Unit,
    onImageOrderChange: (List<String>) -> Unit,
    onImageHiddenChange: (Set<String>) -> Unit,
    onRestoreDefaults: () -> Unit,
    onBack: () -> Unit,
) {
    val textButtons = PickResultBarButtons.normalizeText(textOrder)
    val imageButtons = PickResultBarButtons.normalizeImage(imageOrder)
    val hiddenText = PickResultBarButtons.sanitizeHiddenText(textHidden)
    val hiddenImage = PickResultBarButtons.sanitizeHiddenImage(imageHidden)
    val shownLabel = stringResource(R.string.float_ball_pick_bar_shown)
    val hiddenLabel = stringResource(R.string.float_ball_pick_bar_hidden)
    // `SettingsLazyScreenScaffold` 的 content 是 LazyListScope（非 @Composable）：
    // 小标题的文案必须在这里先求值，不能写进 content 的 `settingsLazySmallTitle(title = stringResource(...))`。
    val textTitle = stringResource(R.string.float_ball_pick_bar_text_title)
    val imageTitle = stringResource(R.string.float_ball_pick_bar_image_title)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.float_ball_pick_bar_buttons),
        pageHint = stringResource(R.string.float_ball_pick_bar_hint),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "pick-bar-text-title",
            title = textTitle,
        )
        LazySettingsItem(key = "pick-bar-text-list") {
            SettingsVerticalReorderList(
                items = textButtons,
                key = { it.id },
                onReorder = { reordered -> onTextOrderChange(reordered.map { it.id }) },
            ) { button, _, segmentIndex, segmentCount, dragModifier ->
                SwitchPreference(
                    modifier = dragModifier
                        .then(if (button.id in hiddenText) Modifier.alpha(0.55f) else Modifier)
                        .miuixGroupedCardItem(segmentIndex, segmentCount),
                    title = stringResource(textBarLabelRes(button)),
                    summary = if (button.id in hiddenText) hiddenLabel else shownLabel,
                    checked = button.id !in hiddenText,
                    onCheckedChange = { checked ->
                        onTextHiddenChange(
                            if (checked) hiddenText - button.id else hiddenText + button.id,
                        )
                    },
                )
            }
        }
        settingsLazySmallTitle(
            key = "pick-bar-image-title",
            title = imageTitle,
        )
        LazySettingsItem(key = "pick-bar-image-list") {
            SettingsVerticalReorderList(
                items = imageButtons,
                key = { it.id },
                onReorder = { reordered -> onImageOrderChange(reordered.map { it.id }) },
            ) { button, _, segmentIndex, segmentCount, dragModifier ->
                SwitchPreference(
                    modifier = dragModifier
                        .then(if (button.id in hiddenImage) Modifier.alpha(0.55f) else Modifier)
                        .miuixGroupedCardItem(segmentIndex, segmentCount),
                    title = stringResource(imageBarLabelRes(button)),
                    summary = if (button.id in hiddenImage) hiddenLabel else shownLabel,
                    checked = button.id !in hiddenImage,
                    onCheckedChange = { checked ->
                        onImageHiddenChange(
                            if (checked) hiddenImage - button.id else hiddenImage + button.id,
                        )
                    },
                )
            }
        }
        LazySettingsItem(key = "pick-bar-restore-default") {
            ArrowPreference(
                modifier = Modifier.miuixGroupedCardItem(0, 1),
                title = stringResource(R.string.float_ball_pick_bar_restore_default),
                summary = stringResource(R.string.float_ball_pick_bar_restore_default_desc),
                onClick = onRestoreDefaults,
            )
        }
    }
}

private fun textBarLabelRes(button: PickResultTextBarButton): Int = when (button) {
    PickResultTextBarButton.COPY -> R.string.float_ball_pick_bar_copy
    PickResultTextBarButton.OPEN_LINK -> R.string.float_ball_pick_bar_open_link
    PickResultTextBarButton.SEARCH -> R.string.float_ball_pick_bar_search
    PickResultTextBarButton.TRANSLATE -> R.string.float_ball_pick_bar_translate
    PickResultTextBarButton.SHARE -> R.string.float_ball_pick_bar_share
    PickResultTextBarButton.MORE -> R.string.float_ball_pick_bar_more
}

private fun imageBarLabelRes(button: PickResultImageBarButton): Int = when (button) {
    PickResultImageBarButton.IMAGE_SEARCH -> R.string.float_ball_pick_bar_image_search
    PickResultImageBarButton.SAVE -> R.string.float_ball_pick_bar_save
    PickResultImageBarButton.SHARE -> R.string.float_ball_pick_bar_share
    PickResultImageBarButton.MORE -> R.string.float_ball_pick_bar_more
}
