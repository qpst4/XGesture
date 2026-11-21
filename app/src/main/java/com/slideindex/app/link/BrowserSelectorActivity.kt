package com.slideindex.app.link

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.link.LinkAppIcon
import com.slideindex.app.ui.link.linkAppNameCache
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 未命中任何分发规则且未配置兜底浏览器时的多引擎选择弹窗。
 */
class BrowserSelectorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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
        fun queryBrowsers(context: Context): List<BrowserAppOption> {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
            val resolved = runCatching {
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }.getOrDefault(emptyList())

            val ownPkg = context.packageName
            return resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ownPkg) return@mapNotNull null
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { pkg }
                    linkAppNameCache[pkg] = label
                    BrowserAppOption(packageName = pkg, label = label)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
}

data class BrowserAppOption(
    val packageName: String,
    val label: String,
)

@Composable
private fun BrowserSelectorSheet(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var browsers by remember { mutableStateOf<List<BrowserAppOption>>(emptyList()) }
    var setAsFallback by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        browsers = withContext(Dispatchers.IO) {
            BrowserSelectorActivity.queryBrowsers(context)
        }
    }

    MiuixBottomSheet(
        show = true,
        title = "选择打开浏览器",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (browsers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(text = "未检测到已安装的外部浏览器", style = MiuixTheme.textStyles.body2)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = browsers,
                        key = { it.packageName },
                    ) { browser ->
                        CardSegment(
                            isFirst = true,
                            isLast = true,
                            outerHorizontalPadding = 0.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = browser.label,
                                summary = browser.packageName,
                                startAction = {
                                    Box(modifier = Modifier.padding(end = 12.dp)) {
                                        LinkAppIcon(packageName = browser.packageName, size = 36.dp)
                                    }
                                },
                                onClick = {
                                    scope.launch {
                                        if (setAsFallback) {
                                            LinkRuleRepository.setFallbackBrowser(context, browser.packageName)
                                        }
                                        LinkLauncher.launch(
                                            context = context,
                                            url = url,
                                            targetPackage = browser.packageName,
                                            excludeFromRecents = false,
                                        )
                                        LinkDispatchHistoryRepository.record(
                                            context = context,
                                            rawUrl = url,
                                            finalUrl = url,
                                            matchedRuleName = "用户手动选择",
                                            targetPackage = browser.packageName,
                                            isSuccess = true,
                                        )
                                        onDismiss()
                                    }
                                },
                            )
                        }
                    }
                }

                // 设为默认备选浏览器复选框
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setAsFallback = !setAsFallback }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        state = if (setAsFallback) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                        onClick = { setAsFallback = !setAsFallback },
                    )
                    MiuixText(
                        text = "设为默认备选浏览器（以后未匹配规则直接由此打开）",
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
