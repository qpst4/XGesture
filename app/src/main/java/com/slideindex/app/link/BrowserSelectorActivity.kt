package com.slideindex.app.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.ui.link.LinkAppIcon
import com.slideindex.app.ui.link.linkAppNameCache
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 未命中任何分发规则且未配置兜底浏览器时的多引擎选择弹窗（4列网格 BottomSheet，深度对标 LinkGo 原版）。
 */
class BrowserSelectorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("EXTRA_URL") ?: run {
            finish()
            return
        }

        setContent {
            MiuixTheme {
                BrowserSelectorSheet(
                    url = url,
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        fun queryBrowsers(context: Context, url: String = "https://www.example.com"): List<BrowserAppOption> {
            val pm = context.packageManager
            val ownPkg = context.packageName

            // 1. 探测已安装的所有外部浏览器
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
            val resolvedBrowsers = runCatching {
                pm.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList())

            val browsers = resolvedBrowsers.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPkg) return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
                linkAppNameCache[pkg] = label
                BrowserAppOption(packageName = pkg, label = label, isBrowser = true)
            }.distinctBy { it.packageName }

            // 2. 探测能响应该特定 URL 的特定原生应用（如哔哩哔哩、知乎、贴吧等推荐应用）
            val targetUri = runCatching { Uri.parse(url) }.getOrNull()
            val recommendedApps = if (targetUri != null) {
                val appIntent = Intent(Intent.ACTION_VIEW, targetUri)
                val resolvedApps = runCatching {
                    pm.queryIntentActivities(appIntent, PackageManager.MATCH_DEFAULT_ONLY)
                }.getOrDefault(emptyList())
                val browserPkgs = browsers.map { it.packageName }.toSet()
                resolvedApps.mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ownPkg || pkg in browserPkgs) return@mapNotNull null
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
                    linkAppNameCache[pkg] = label
                    BrowserAppOption(packageName = pkg, label = label, isBrowser = false)
                }.distinctBy { it.packageName }
            } else {
                emptyList()
            }

            // 推荐应用排在前面，外部浏览器按名称排序排在后方
            return (recommendedApps + browsers.sortedBy { it.label.lowercase() }).distinctBy { it.packageName }
        }
    }
}

data class BrowserAppOption(
    val packageName: String,
    val label: String,
    val isBrowser: Boolean = true,
)

@Composable
private fun BrowserSelectorSheet(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<BrowserAppOption>>(emptyList()) }
    var setAsFallback by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        apps = withContext(Dispatchers.IO) {
            BrowserSelectorActivity.queryBrowsers(context, url)
        }
    }

    MiuixBottomSheet(
        show = true,
        title = "选择应用打开",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部：当前截获链接卡片 + 一键复制
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MiuixText(
                            text = "待分发链接",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        MiuixText(
                            text = url,
                            style = MiuixTheme.textStyles.footnote1,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surface)
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("url", url))
                                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制链接",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // 中间：4 列应用网格 (深度对标 LinkGo)
            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(text = "未检测到已安装的外部浏览器或对应应用", style = MiuixTheme.textStyles.body2)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items = apps, key = { it.packageName }) { app ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        if (setAsFallback && app.isBrowser) {
                                            LinkRuleRepository.setFallbackBrowser(context, app.packageName)
                                        }
                                        LinkLauncher.launch(
                                            context = context,
                                            url = url,
                                            targetPackage = app.packageName,
                                            excludeFromRecents = false,
                                        )
                                        LinkDispatchHistoryRepository.record(
                                            context = context,
                                            rawUrl = url,
                                            finalUrl = url,
                                            matchedRuleName = if (app.isBrowser) "手动选择浏览器" else "匹配推荐应用直达",
                                            targetPackage = app.packageName,
                                            isSuccess = true,
                                        )
                                        onDismiss()
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // 48dp 高清应用图标
                            Box(contentAlignment = Alignment.TopEnd) {
                                LinkAppIcon(packageName = app.packageName, size = 48.dp)
                                if (!app.isBrowser) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primary)
                                            .size(10.dp),
                                    )
                                }
                            }
                            // 单行应用标题
                            MiuixText(
                                text = app.label,
                                style = MiuixTheme.textStyles.footnote2,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // 底部：设为默认备选浏览器复选框
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { setAsFallback = !setAsFallback }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        state = if (setAsFallback) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                        onClick = { setAsFallback = !setAsFallback },
                    )
                    Spacer(Modifier.size(8.dp))
                    Column {
                        MiuixText(
                            text = "设为默认备选浏览器",
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MiuixText(
                            text = "点击浏览器后记住该选择，后续未匹配规则将直接由此打开",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
