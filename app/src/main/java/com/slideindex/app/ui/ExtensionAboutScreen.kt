@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Update
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.ui.icon.AppIconTheme
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixBlurredTopBar
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.effect.BgEffectBackground
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import com.slideindex.app.ui.onboarding.OnboardingPracticeSession
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import java.util.Calendar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ExtensionAboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyNotices: () -> Unit,
    onCheckUpdate: () -> Unit,
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    val scrollProgressState = remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val heroCollapsed by remember { derivedStateOf { scrollProgressState.value == 1f } }
    val titleAlpha by remember {
        derivedStateOf { ((scrollProgressState.value - 0.35f) / 0.65f).coerceIn(0f, 1f) }
    }

    val backdrop = rememberMiuixBlurBackdrop()
    val blurActive = backdrop != null && heroCollapsed
    val barColor = if (heroCollapsed && !blurActive) MiuixTheme.colorScheme.surface else Color.Transparent

    Scaffold(
        topBar = {
            MiuixBlurredTopBar(
                backdrop = backdrop,
                enabled = blurActive,
            ) {
                SmallTopAppBar(
                    title = stringResource(R.string.about_section_title),
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        MiuixBackNavigationIcon(onBack)
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AboutContent(
                innerPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = { scrollProgressState.value },
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onOpenThirdPartyNotices = onOpenThirdPartyNotices,
                onCheckUpdate = onCheckUpdate,
                autoCheckUpdate = autoCheckUpdate,
                onAutoCheckUpdateChange = onAutoCheckUpdateChange
            )
        }
    }
}

@Composable
private fun AboutContent(
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: () -> Float,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyNotices: () -> Unit,
    onCheckUpdate: () -> Unit,
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val backdrop = rememberLayerBackdrop()

    val isDark = LocalAppDarkTheme.current
    val blurEnabled = remember(isDark) { isRuntimeShaderSupported() }

    var selectedIconTheme by remember { mutableStateOf(AppIconTheme.getSelected(context)) }
    var showIconPicker by remember { mutableStateOf(false) }

    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1.toInt()), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500.toInt()), BlurBlendMode.Lab)
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a.toInt()), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f.toInt()), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200.toInt()), BlurBlendMode.Lab)
            )
        }
    }

    var logoHeightDp by remember { mutableStateOf(300.dp) }

    val versionProgress = { ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f) }
    val projectNameProgress = { ((scrollProgress() - 0.20f) / 0.15f).coerceIn(0f, 1f) }
    val iconProgress = { ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f) }

    val scrollPadding = PaddingValues(
        top = innerPadding.calculateTopPadding(),
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection)
    )
    val logoPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + 32.dp,
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection)
    )

    val projectUrl = stringResource(R.string.about_project_url_desc)
    val weblateUrl = stringResource(R.string.about_weblate_url)
    val qqGroupUrl = stringResource(R.string.about_qq_group_url)
    val telegramChatUrl = stringResource(R.string.about_telegram_chat_url)
    val appInfoTitle = stringResource(R.string.about_section_app_info)
    val communityTitle = stringResource(R.string.about_section_community)
    val helpTranslateTitle = stringResource(R.string.about_help_translate_title)
    val openSourceTitle = stringResource(R.string.about_section_open_source)

    BgEffectBackground(
        dynamicBackground = blurEnabled,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        isFullSize = true,
        effectBackground = blurEnabled,
        alpha = { 1f - scrollProgress() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 44.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection)
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(118.dp)
                    .graphicsLayer {
                        val p = iconProgress()
                        alpha = 1 - p
                        scaleX = 1 - (p * 0.05f)
                        scaleY = 1 - (p * 0.05f)
                    }
            ) {
                Image(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (blurEnabled) {
                                Modifier.textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(0.dp),
                                    blurRadius = 120f,
                                    colors = BlurColors(blendColors = logoBlend),
                                    contentBlendMode = BlendMode.DstIn,
                                    enabled = true
                                )
                            } else Modifier
                        ),
                    painter = painterResource(R.drawable.app_logo),
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
                    contentDescription = stringResource(R.string.cd_app_icon)
                )
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .graphicsLayer {
                        val p = projectNameProgress()
                        alpha = 1 - p
                        scaleX = 1 - (p * 0.05f)
                        scaleY = 1 - (p * 0.05f)
                    }
                    .then(
                        if (blurEnabled) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                                enabled = true
                            )
                        } else {
                            Modifier
                        }
                    ),
                text = stringResource(R.string.app_name),
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = versionProgress()
                        alpha = 1 - p
                        scaleX = 1 - (p * 0.05f)
                        scaleY = 1 - (p * 0.05f)
                    },
                text = stringResource(
                    R.string.about_version_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateStartPadding(layoutDirection),
                end = scrollPadding.calculateEndPadding(layoutDirection)
            )
        ) {
            item(key = "logoSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 44.dp + logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + 96.dp
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(
                                top = logoPadding.calculateTopPadding() + 44.dp - scrollPadding.calculateTopPadding()
                            )
                            .size(90.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                showIconPicker = true
                            }
                    )
                }
            }

            settingsLazySmallTitle(key = "app_info_section", title = appInfoTitle)
            groupedCardItems(
                keyPrefix = "about_app_info",
                items = buildList {
                    add(
                        settingsCardScopeItem("check-update") {
                            SettingNavigationRow(
                                icon = { label -> Icon(MiuixIcons.Update, contentDescription = label) },
                                title = stringResource(R.string.about_check_update_title),
                                subtitle = stringResource(R.string.about_check_update_subtitle),
                                onClick = onCheckUpdate
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("auto-check-update") {
                            SettingToggleRow(
                                icon = { label -> Icon(Icons.Outlined.Schedule, contentDescription = label) },
                                title = stringResource(R.string.auto_check_update_title),
                                subtitle = stringResource(R.string.auto_check_update_hint),
                                checked = autoCheckUpdate,
                                onCheckedChange = onAutoCheckUpdateChange
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("release-notes") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.NewReleases, contentDescription = label) },
                                title = stringResource(R.string.about_release_notes_title),
                                subtitle = stringResource(R.string.about_release_notes_subtitle),
                                onClick = {
                                    val uri = (projectUrl + "/releases").toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("replay-onboarding") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.School, contentDescription = label) },
                                title = stringResource(R.string.about_replay_onboarding_title),
                                subtitle = stringResource(R.string.about_replay_onboarding_subtitle),
                                onClick = { OnboardingPracticeSession.open() }
                            )
                        }
                    )
                }
            )

            settingsLazySmallTitle(key = "community_section", title = communityTitle)
            groupedCardItems(
                keyPrefix = "about_community",
                items = buildList {
                    add(
                        settingsCardScopeItem("star-github") {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = label,
                                        tint = Color(0xFFFFB800)
                                    )
                                },
                                title = stringResource(R.string.about_star_github_title),
                                subtitle = stringResource(R.string.about_star_github_desc),
                                onClick = {
                                    val uri = projectUrl.toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("issues-feedback") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.BugReport, contentDescription = label) },
                                title = stringResource(R.string.about_feedback_issues_title),
                                subtitle = stringResource(R.string.about_feedback_issues_desc),
                                onClick = {
                                    val uri = (projectUrl + "/issues").toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("telegram-chat") {
                            SettingNavigationRow(
                                icon = { label -> Icon(MiuixIcons.Community, contentDescription = label) },
                                title = stringResource(R.string.about_telegram_chat_title),
                                subtitle = stringResource(R.string.about_telegram_chat_desc),
                                onClick = {
                                    val uri = telegramChatUrl.toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("qq-group") {
                            SettingNavigationRow(
                                icon = { label -> Icon(MiuixIcons.Community, contentDescription = label) },
                                title = stringResource(R.string.about_qq_group_title),
                                subtitle = stringResource(R.string.about_qq_group_desc),
                                onClick = {
                                    val uri = qqGroupUrl.toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            )
                        }
                    )
                }
            )

            settingsLazySmallTitle(
                key = "community_translate_section",
                title = helpTranslateTitle,
            )
            groupedCardItems(
                keyPrefix = "about_community_translate",
                items = buildList {
                    add(
                        settingsCardScopeItem("help-translate") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.Language, contentDescription = label) },
                                title = stringResource(R.string.about_help_translate_title),
                                subtitle = stringResource(R.string.about_help_translate_desc),
                                onClick = {
                                    val uri = weblateUrl.toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            )
                        }
                    )
                }
            )

            settingsLazySmallTitle(key = "open_source_section", title = openSourceTitle)
            groupedCardItems(
                keyPrefix = "about_open_source",
                items = buildList {
                    add(
                        settingsCardScopeItem("privacy-policy") {
                            PrivacyPolicyEntryCard(onClick = onOpenPrivacyPolicy)
                        }
                    )
                    add(
                        settingsCardScopeItem("open-source-license") {
                            OpenSourceLicenseEntryCard(
                                onClick = {
                                    val uri = (projectUrl + "/blob/HEAD/LICENSE").toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("third-party-notices") {
                            ThirdPartyNoticesEntryCard(onClick = onOpenThirdPartyNotices)
                        }
                    )
                }
            )

            item(key = "about-footer") {
                Text(
                    text = stringResource(
                        R.string.about_copyright_notice,
                        Calendar.getInstance().get(Calendar.YEAR)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            item(key = "bottom-inset") {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }

        // 图标挑选 BottomSheet
        AppIconPickerBottomSheet(
            show = showIconPicker,
            selected = selectedIconTheme,
            onDismissRequest = { showIconPicker = false },
            onSelect = { theme ->
                selectedIconTheme = theme
                AppIconTheme.applyIconTheme(context, theme)
                showIconPicker = false
            }
        )
    }
}

@Composable
private fun AppIconPickerBottomSheet(
    show: Boolean,
    selected: AppIconTheme,
    onDismissRequest: () -> Unit,
    onSelect: (AppIconTheme) -> Unit
) {
    MiuixBottomSheet(
        show = show,
        title = stringResource(R.string.app_icon_theme_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIconTheme.entries.forEach { theme ->
                val isSelected = theme == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .clickable { onSelect(theme) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(theme.iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(theme.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(theme.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
