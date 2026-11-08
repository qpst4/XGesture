package com.slideindex.app.overlay.filebrowser

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 快捷菜单首页的「文件」卡片：容量条 + 固定路径 chips。
 *
 * 只做导航和总容量，不做目录递归统计；点卡片进完整文件浏览器。
 */
@Composable
fun FileBrowserQuickCard(
    modifier: Modifier = Modifier,
    onOpen: (String?) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        FileBrowserPreferences.attach(context)
    }
    val prefs = FileBrowserPreferences.current
    val internalRoot = remember(context) { FileBrowserStorage.internalStoragePath() }
    var usage by remember { mutableStateOf<FileBrowserStorageUsage?>(null) }
    var volumes by remember { mutableStateOf(emptyList<FileBrowserVolume>()) }
    LaunchedEffect(internalRoot) {
        usage = withContext(Dispatchers.IO) {
            FileBrowserStorage.read(internalRoot)
        }
        volumes = withContext(Dispatchers.IO) {
            FileBrowserStorage.volumes(context)
        }
    }
    val pins = remember(prefs.pinnedPaths, prefs.hiddenShortcuts, internalRoot) {
        FileBrowserPreferences.quickShortcuts(internalRoot)
    }
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onOpen(internalRoot) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.file_browser_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = usage?.let {
                        stringResource(
                            R.string.file_browser_card_available,
                            FileBrowserFormat.size(it.availableBytes),
                        )
                    } ?: stringResource(R.string.file_browser_card_size_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        usage?.let { current ->
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { current.usedFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.file_browser_card_used_of_total,
                    FileBrowserFormat.size(current.usedBytes),
                    FileBrowserFormat.size(current.totalBytes),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp),
        ) {
            items(pins, key = { it }) { path ->
                FileBrowserPinChip(
                    label = pinLabel(path, internalRoot),
                    icon = pinIcon(path, internalRoot),
                    onClick = { onOpen(path) },
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp),
        ) {
            items(categories(internalRoot), key = { it.path }) { category ->
                FileBrowserPinChip(
                    label = stringResource(category.labelRes),
                    icon = category.icon,
                    onClick = { onOpen(category.path) },
                )
            }
        }

        if (volumes.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 1.dp),
            ) {
                items(volumes, key = { it.path }) { volume ->
                    FileBrowserPinChip(
                        label = volume.label,
                        icon = if (volume.removable) Icons.Outlined.Storage else Icons.Outlined.PhoneAndroid,
                        onClick = { onOpen(volume.path) },
                    )
                }
            }
        }
    }
}

private data class FileBrowserCategory(
    val labelRes: Int,
    val path: String,
    val icon: ImageVector,
)

private fun categories(@Suppress("UNUSED_PARAMETER") internalRoot: String): List<FileBrowserCategory> {
    return listOf(
        FileBrowserCategory(
            R.string.file_browser_category_pictures,
            FileBrowserMediaCategory.PICTURES.path,
            Icons.Outlined.Image,
        ),
        FileBrowserCategory(
            R.string.file_browser_category_music,
            FileBrowserMediaCategory.AUDIO.path,
            Icons.Outlined.MusicNote,
        ),
        FileBrowserCategory(
            R.string.file_browser_category_videos,
            FileBrowserMediaCategory.VIDEOS.path,
            Icons.Outlined.Movie,
        ),
        FileBrowserCategory(
            R.string.file_browser_category_documents,
            FileBrowserMediaCategory.DOCUMENTS.path,
            Icons.Outlined.Description,
        ),
    )
}

@Composable
private fun FileBrowserPinChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun pinLabel(path: String, internalRoot: String): String {
    val root = FileBrowserPaths.normalize(internalRoot, internalRoot)
    return when (path) {
        "/" -> stringResource(R.string.file_browser_crumb_root)
        root -> stringResource(R.string.file_browser_crumb_internal)
        "$root/Android/data" -> stringResource(R.string.file_browser_quick_android_data)
        "$root/Download" -> stringResource(R.string.file_browser_quick_download)
        else -> path.substringAfterLast('/').ifEmpty { path }
    }
}

private fun pinIcon(path: String, internalRoot: String): ImageVector {
    val root = FileBrowserPaths.normalize(internalRoot, internalRoot)
    return when (path) {
        "/" -> Icons.Outlined.Storage
        root -> Icons.Outlined.PhoneAndroid
        "$root/Android/data" -> Icons.Outlined.Android
        "$root/Download" -> Icons.Outlined.Download
        else -> Icons.Outlined.Folder
    }
}
