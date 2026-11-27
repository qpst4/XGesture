package com.slideindex.app.ui.link

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.link.LinkDispatchRule
import com.slideindex.app.link.LinkMatchType
import com.slideindex.app.link.LinkResolveStrategy
import com.slideindex.app.link.LinkRuleIconStorage
import com.slideindex.app.shortcut.port.ApkTools
import com.slideindex.app.ui.SegmentedControl
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.LocalMiuixSheetHeaderPadding
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ImageOutline
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal val linkAppIconCache = ConcurrentHashMap<String, ImageBitmap>()
internal val linkAppNameCache = ConcurrentHashMap<String, String>()

@Composable
internal fun LinkAppIcon(
    packageName: String,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }.coerceAtLeast(1)
    var bitmap by remember(packageName, sizePx) { mutableStateOf(linkAppIconCache[packageName]) }

    LaunchedEffect(packageName, sizePx) {
        if (packageName.isBlank()) return@LaunchedEffect
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
                        ?: ApkTools(context).getAppIcon(packageName)
                    drawable?.toBitmap(sizePx, sizePx)?.asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                linkAppIconCache[packageName] = loaded
                bitmap = loaded
            }
        }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Glass.ImageOutline,
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 链接规则头像视图，解耦规则头像与目标应用：
 * 1. 自定义图片：优先从私有沙盒加载；
 * 2. 指定应用图标：显示指定包名的图标；
 * 3. 默认联动：目标应用有配置则显示目标应用图标，否则显示 Miuix 玻璃占位图标。
 */
@Composable
internal fun LinkRuleIcon(
    iconType: String = LinkDispatchRule.ICON_TYPE_DEFAULT,
    iconValue: String = "",
    targetPackage: String = "",
    isRedispatch: Boolean = false,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }.coerceAtLeast(1)

    if (iconType == LinkDispatchRule.ICON_TYPE_CUSTOM && iconValue.isNotBlank()) {
        var customBitmap by remember(iconValue, sizePx) {
            mutableStateOf(LinkRuleIconStorage.loadBitmap(context, iconValue, sizePx)?.asImageBitmap())
        }
        LaunchedEffect(iconValue, sizePx) {
            if (customBitmap == null) {
                val loaded = withContext(Dispatchers.IO) {
                    LinkRuleIconStorage.loadBitmap(context, iconValue, sizePx)?.asImageBitmap()
                }
                if (loaded != null) {
                    customBitmap = loaded
                }
            }
        }
        val bmp = customBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "规则图标",
                modifier = modifier
                    .size(size)
                    .clip(shape),
                contentScale = ContentScale.Crop,
            )
            return
        }
    }

    val resolvedPackage = when {
        isRedispatch -> ""
        iconType == LinkDispatchRule.ICON_TYPE_APP && iconValue.isNotBlank() -> iconValue
        else -> targetPackage
    }
    val isInstalled = remember(resolvedPackage) {
        resolvedPackage.isNotBlank() && runCatching {
            context.packageManager.getPackageInfo(resolvedPackage.substringBefore('/'), 0)
        }.isSuccess
    }

    if (resolvedPackage.isNotBlank() && isInstalled) {
        LinkAppIcon(
            packageName = resolvedPackage,
            size = size,
            shape = shape,
            modifier = modifier,
        )
    } else if (isRedispatch) {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_hub_primary),
                contentDescription = "重分发规则图标",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(size * 0.72f),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = "未安装应用",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                modifier = Modifier.padding(size / 5),
            )
        }
    }
}

private data class InstalledAppOption(
    val packageName: String,
    val label: String,
)

@Composable
fun LinkRuleEditDialog(
    show: Boolean,
    originalRule: LinkDispatchRule? = null,
    onDismissRequest: () -> Unit,
    onSave: (LinkDispatchRule) -> Unit,
    onDelete: ((LinkDispatchRule) -> Unit)? = null,
) {
    if (!show) return

    val context = LocalContext.current
    var name by remember(originalRule) { mutableStateOf(originalRule?.name.orEmpty()) }
    var pattern by remember(originalRule) { mutableStateOf(originalRule?.pattern.orEmpty()) }
    var targetPackage by remember(originalRule) { mutableStateOf(originalRule?.targetPackage.orEmpty()) }
    var component by remember(originalRule) { mutableStateOf(originalRule?.component.orEmpty()) }
    var template by remember(originalRule) { mutableStateOf(originalRule?.template.orEmpty()) }
    var extractPattern by remember(originalRule) { mutableStateOf(originalRule?.extractPattern.orEmpty()) }
    var matchType by remember(originalRule) { mutableStateOf(originalRule?.matchType ?: LinkMatchType.CONTAINS) }
    var excludeFromRecents by remember(originalRule) { mutableStateOf(originalRule?.excludeFromRecents ?: false) }

    val initialResolveShort = remember(originalRule) {
        originalRule?.resolveShortLink == true
    }
    var resolveShortLink by remember(originalRule) { mutableStateOf(initialResolveShort) }
    var resolveStrategy by remember(originalRule) {
        val strat = originalRule?.resolveStrategy ?: LinkResolveStrategy.NONE
        mutableStateOf(
            if (strat == LinkResolveStrategy.NONE) LinkResolveStrategy.DIRECT else strat
        )
    }
    var isEnabled by remember(originalRule) { mutableStateOf(originalRule?.isEnabled ?: true) }
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var targetAppDisplayName by remember(targetPackage) {
        mutableStateOf(linkAppNameCache[targetPackage].orEmpty())
    }

    LaunchedEffect(targetPackage) {
        if (targetPackage.isBlank()) {
            targetAppDisplayName = ""
            return@LaunchedEffect
        }
        val cached = linkAppNameCache[targetPackage]
        if (!cached.isNullOrBlank()) {
            targetAppDisplayName = cached
            return@LaunchedEffect
        }
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(targetPackage, 0)).toString()
            }.getOrNull()
        }
        if (!resolved.isNullOrBlank()) {
            linkAppNameCache[targetPackage] = resolved
            targetAppDisplayName = resolved
        } else {
            targetAppDisplayName = targetPackage
        }
    }

    MiuixBottomSheet(
        show = true,
        title = if (originalRule == null) "新建规则" else "编辑规则",
        onDismissRequest = onDismissRequest,
        scrollUnderHeader = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(LocalMiuixSheetHeaderPadding.current)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. 匹配条件
            SmallTitle(
                text = "匹配条件",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            )

            MiuixLabeledTextField(
                value = name,
                onValueChange = { name = it },
                label = "规则名称",
                useLabelAsPlaceholder = false,
                singleLine = true,
            )

            MiuixText(
                text = "匹配方式",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )

            SegmentedControl(
                items = listOf(LinkMatchType.CONTAINS, LinkMatchType.REGEX, LinkMatchType.EXACT),
                selected = matchType,
                labelRes = { type ->
                    when (type) {
                        LinkMatchType.CONTAINS -> R.string.link_rule_match_contains
                        LinkMatchType.REGEX -> R.string.link_rule_match_regex
                        LinkMatchType.EXACT -> R.string.link_rule_match_exact
                    }
                },
                onSelect = { matchType = it },
            )

            MiuixLabeledTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = "匹配内容",
                useLabelAsPlaceholder = false,
                singleLine = true,
            )

            MiuixText(
                text = "填写域名、网址片段或正则表达式",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp),
            )

            // 2. 打开目标（严格对齐 gevo bottom_sheet_edit_link_rule.xml：目标应用点击选择 + 链接改写模板）
            SmallTitle(
                text = "打开目标",
                insideMargin = PaddingValues(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 2.dp),
            )

            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = 0.dp,
            ) {
                BasicComponent(
                    modifier = Modifier.fillMaxWidth(),
                    title = "目标应用",
                    summary = when {
                        targetPackage.isBlank() && resolveShortLink && resolveStrategy == LinkResolveStrategy.REDISPATCH ->
                            "未选择（仅解析短链并重新匹配其他规则）"
                        targetPackage.isBlank() ->
                            "点击选择应用"
                        targetAppDisplayName.isNotBlank() && targetAppDisplayName != targetPackage ->
                            "$targetAppDisplayName\n$targetPackage"
                        else ->
                            targetPackage
                    },
                    startAction = if (targetPackage.isNotBlank()) {
                        {
                            Box(modifier = Modifier.padding(end = 12.dp)) {
                                LinkAppIcon(packageName = targetPackage, size = 36.dp)
                            }
                        }
                    } else null,
                    onClick = { showAppPickerSheet = true },
                )
            }

            MiuixLabeledTextField(
                value = template,
                onValueChange = { template = it },
                label = "链接改写模板",
                useLabelAsPlaceholder = false,
                singleLine = true,
            )

            MiuixText(
                text = "留空则直接打开原链接；正则匹配时可用 \$0–\$9 引用分组，或使用 {url}、{url_b64_enc}、{1}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp),
            )

            if (extractPattern.isNotBlank() || template.contains("{1}")) {
                MiuixLabeledTextField(
                    value = extractPattern,
                    onValueChange = { extractPattern = it },
                    label = "参数提取正则（提取 {1} 分组）",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                )
            }

            // 3. 处理方式（短链还原与策略）
            SmallTitle(
                text = "处理方式",
                insideMargin = PaddingValues(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 2.dp),
            )

            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "解析短链",
                        summary = "打开前先跟随重定向取得真实地址",
                        onClick = {
                            val next = !resolveShortLink
                            resolveShortLink = next
                            if (next && resolveStrategy == LinkResolveStrategy.NONE) {
                                resolveStrategy = LinkResolveStrategy.DIRECT
                            }
                        },
                        endActions = {
                            Switch(
                                checked = resolveShortLink,
                                onCheckedChange = { checked ->
                                    resolveShortLink = checked
                                    if (checked && resolveStrategy == LinkResolveStrategy.NONE) {
                                        resolveStrategy = LinkResolveStrategy.DIRECT
                                    }
                                },
                            )
                        },
                    )
                    if (resolveShortLink) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiuixText(
                                text = "解析策略",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            SegmentedControl(
                                items = listOf(LinkResolveStrategy.DIRECT, LinkResolveStrategy.REDISPATCH),
                                selected = if (resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                                    LinkResolveStrategy.REDISPATCH
                                } else {
                                    LinkResolveStrategy.DIRECT
                                },
                                labelRes = { strat ->
                                    when (strat) {
                                        LinkResolveStrategy.REDISPATCH -> R.string.link_rule_resolve_redispatch
                                        else -> R.string.link_rule_resolve_direct
                                    }
                                },
                                onSelect = { resolveStrategy = it },
                            )
                        }
                    }
                }
            }

            // 4. 高级设置
            SmallTitle(
                text = "高级设置",
                insideMargin = PaddingValues(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 2.dp),
            )

            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "不显示在最近任务",
                        summary = "打开目标应用时不保留在多任务列表",
                        onClick = { excludeFromRecents = !excludeFromRecents },
                        endActions = {
                            Switch(
                                checked = excludeFromRecents,
                                onCheckedChange = { excludeFromRecents = it },
                            )
                        },
                    )
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "启用规则",
                        onClick = { isEnabled = !isEnabled },
                        endActions = {
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { isEnabled = it },
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 底部按钮区（编辑态左侧删除，右侧保存；新建态通栏保存）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (originalRule != null && onDelete != null) {
                    GlassDialogButton(
                        role = GlassDialogButtonRole.Normal,
                        onClick = {
                            onDelete(originalRule)
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        MiuixText(
                            text = "删除",
                            style = MiuixTheme.textStyles.button,
                            maxLines = 1,
                        )
                    }
                }
                GlassDialogButton(
                    role = GlassDialogButtonRole.Primary,
                    onClick = {
                        val trimmedPattern = pattern.trim()
                        val trimmedPkg = targetPackage.trim()
                        if (trimmedPattern.isEmpty()) {
                            Toast.makeText(context, "请填写匹配内容", Toast.LENGTH_SHORT).show()
                            return@GlassDialogButton
                        }
                        if (matchType == LinkMatchType.REGEX) {
                            val validRegex = runCatching { Regex(trimmedPattern, RegexOption.IGNORE_CASE) }.isSuccess
                            if (!validRegex) {
                                Toast.makeText(context, "正则表达式无效", Toast.LENGTH_SHORT).show()
                                return@GlassDialogButton
                            }
                        }
                        val effectiveStrategy = if (resolveShortLink) {
                            if (resolveStrategy == LinkResolveStrategy.NONE) LinkResolveStrategy.DIRECT else resolveStrategy
                        } else {
                            LinkResolveStrategy.NONE
                        }
                        if (trimmedPkg.isEmpty() && effectiveStrategy != LinkResolveStrategy.REDISPATCH) {
                            Toast.makeText(context, "请选择目标应用", Toast.LENGTH_SHORT).show()
                            return@GlassDialogButton
                        }
                        val resultRule = (originalRule ?: LinkDispatchRule(id = UUID.randomUUID().toString())).copy(
                            name = name.trim().ifEmpty { trimmedPattern },
                            pattern = trimmedPattern,
                            targetPackage = trimmedPkg,
                            component = component.trim().takeIf { it.isNotEmpty() },
                            template = template.trim().takeIf { it.isNotEmpty() },
                            extractPattern = extractPattern.trim().takeIf { it.isNotEmpty() },
                            matchType = matchType,
                            excludeFromRecents = excludeFromRecents,
                            resolveShortLink = resolveShortLink,
                            resolveStrategy = effectiveStrategy,
                            isEnabled = isEnabled,
                        )
                        onSave(resultRule)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    MiuixText(
                        text = "保存",
                        style = MiuixTheme.textStyles.button,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    LinkTargetAppPickerBottomSheet(
        show = showAppPickerSheet,
        selectedPackage = targetPackage,
        allowClearForRedispatch = resolveShortLink && resolveStrategy == LinkResolveStrategy.REDISPATCH,
        onDismissRequest = { showAppPickerSheet = false },
        onSelectApp = { pkg, appLabel ->
            if (pkg != targetPackage) {
                component = ""
            }
            targetPackage = pkg
            targetAppDisplayName = appLabel
            showAppPickerSheet = false
        },
    )
}

@Composable
internal fun LinkTargetAppPickerBottomSheet(
    show: Boolean,
    selectedPackage: String,
    title: String = "选择目标应用",
    allowClearForRedispatch: Boolean = false,
    onDismissRequest: () -> Unit,
    onSelectApp: (packageName: String, label: String) -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledAppOption>>(emptyList()) }

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        isLoading = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = runCatching { pm.queryIntentActivities(mainIntent, 0) }.getOrDefault(emptyList())
            val list = resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
                    linkAppNameCache[pkg] = label
                    InstalledAppOption(packageName = pkg, label = label)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            apps = list
        }
        isLoading = false
    }

    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) apps
        else apps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    MiuixBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiuixLabeledTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "搜索应用名称或包名",
                useLabelAsPlaceholder = false,
                singleLine = true,
            )

            if (allowClearForRedispatch && selectedPackage.isNotBlank()) {
                GlassDialogButton(
                    role = GlassDialogButtonRole.Normal,
                    onClick = { onSelectApp("", "") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MiuixText(
                        text = "清空目标应用（仅作为短链重分发）",
                        style = MiuixTheme.textStyles.button,
                        maxLines = 1,
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    outerHorizontalPadding = 0.dp,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isCurrent = app.packageName == selectedPackage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectApp(app.packageName, app.label) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LinkAppIcon(packageName = app.packageName, size = 40.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    MiuixText(
                                        text = app.label,
                                        style = MiuixTheme.textStyles.body1,
                                        color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    )
                                    MiuixText(
                                        text = app.packageName,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                if (isCurrent) {
                                    MiuixText(
                                        text = "已选",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
