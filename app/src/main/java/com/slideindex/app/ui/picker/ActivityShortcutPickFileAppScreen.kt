package com.slideindex.app.ui.picker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class FileTargetAppItem(
    val packageName: String,
    val activityClassName: String,
    val label: String,
    val iconDrawable: Drawable?,
)

@Composable
fun ActivityShortcutPickFileAppScreen(
    mimeType: String,
    isShareMode: Boolean,
    onBack: () -> Unit,
    onSelectApp: (packageName: String, activityClassName: String, appLabel: String) -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<FileTargetAppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(mimeType, isShareMode) {
        loading = true
        val resolved = withContext(Dispatchers.IO) {
            queryTargetApps(context, mimeType, isShareMode)
        }
        items = resolved
        loading = false
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_app_list_title),
        onBack = onBack,
    ) {
        if (loading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        } else if (items.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_apps),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        } else {
            groupedCardItems(
                keyPrefix = "target-apps",
                items = items.map { item ->
                    CardItem(key = "${item.packageName}/${item.activityClassName}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectApp(
                                        item.packageName,
                                        item.activityClassName,
                                        item.label,
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.iconDrawable != null) {
                                val bitmap = remember(item.iconDrawable) {
                                    item.iconDrawable.toBitmap(96, 96).asImageBitmap()
                                }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(40.dp),
                                )
                            } else {
                                Spacer(modifier = Modifier.size(40.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    style = MiuixTheme.textStyles.title4,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${item.packageName}/${item.activityClassName}",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

private fun queryTargetApps(
    context: Context,
    mimeType: String,
    isShareMode: Boolean,
): List<FileTargetAppItem> = runCatching {
    val pm = context.packageManager
    val targetMime = mimeType.ifBlank { "*/*" }
    val intent = if (isShareMode) {
        Intent(Intent.ACTION_SEND).apply {
            type = targetMime
        }
    } else {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://dummy.file/sample"), targetMime)
        }
    }
    val resolveList = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    resolveList.mapNotNull { resolveInfo ->
        val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
        val packageName = activityInfo.packageName ?: return@mapNotNull null
        val activityName = activityInfo.name ?: return@mapNotNull null
        val label = resolveInfo.loadLabel(pm).toString().ifBlank { activityInfo.loadLabel(pm).toString() }
        val icon = resolveInfo.loadIcon(pm) ?: activityInfo.loadIcon(pm)
        FileTargetAppItem(
            packageName = packageName,
            activityClassName = activityName,
            label = label,
            iconDrawable = icon,
        )
    }.distinctBy { "${it.packageName}/${it.activityClassName}" }
}.getOrDefault(emptyList())
