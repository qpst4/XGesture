@file:OptIn(ExperimentalMaterial3Api::class)

package com.slideindex.app.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton

@Composable
fun UpdateDialog(
    show: Boolean,
    state: UpdateViewModel.UiState,
    onDismissRequest: () -> Unit,
    onIgnore: () -> Unit,
    onConfirm: () -> Unit,
    onInstall: () -> Unit,
    onMoveToBackground: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val isUpToDate = state.phase == UpdateViewModel.UpdatePhase.UpToDate
    val titleRes = when (state.phase) {
        UpdateViewModel.UpdatePhase.UpToDate -> R.string.update_already_latest_title
        UpdateViewModel.UpdatePhase.Failed -> R.string.update_download_failed_title
        else -> R.string.update_available_title
    }

    MiuixBottomSheet(
        show = show,
        title = stringResource(titleRes),
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(onClick = onOpenRelease) {
                MiuixIcon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.update_view_on_github),
                )
            }
        },
    ) {
        if (isUpToDate) {
            UpToDateContent(
                currentVersion = UpdateChecker.displayVersion(BuildConfig.VERSION_NAME),
                onDismiss = onDismissRequest,
            )
        } else {
            NewUpdateContent(
                state = state,
                onIgnore = onIgnore,
                onConfirm = onConfirm,
                onInstall = onInstall,
                onMoveToBackground = onMoveToBackground,
            )
        }
    }
}

@Composable
private fun UpToDateContent(
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.update_already_latest_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = currentVersion,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}

@Composable
private fun NewUpdateContent(
    state: UpdateViewModel.UiState,
    onIgnore: () -> Unit,
    onConfirm: () -> Unit,
    onInstall: () -> Unit,
    onMoveToBackground: () -> Unit,
) {
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val maxNotesHeight = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.36f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        // 版本对比胶囊 Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.update_version_current,
                        UpdateChecker.displayVersion(BuildConfig.VERSION_NAME),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.update_version_latest,
                        UpdateChecker.displayVersion(state.version),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 更新说明卡片 Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.about_release_notes_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 更新说明内容卡片（支持 新增/变更/修复 分组；旧版扁平 notes 自动兼容）
        val noteGroups = remember(state.notes) { UpdateChecker.parseUpdateNotes(state.notes) }
        if (noteGroups.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                text = "—",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .heightIn(max = maxNotesHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                noteGroups.forEachIndexed { groupIndex, group ->
                    if (group.items.isEmpty()) return@forEachIndexed
                    if (groupIndex > 0) Spacer(modifier = Modifier.height(10.dp))
                    val title = group.title
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    group.items.forEachIndexed { index, item ->
                        Text(
                            text = "${UpdateChecker.listItemPrefix(index + 1, locale)}$item",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 底部动作区
        when (state.phase) {
            UpdateViewModel.UpdatePhase.Downloading -> {
                DownloadingContent(progress = state.progress)
                PrimaryButtonRow(
                    text = stringResource(R.string.update_move_to_background),
                    onClick = onMoveToBackground,
                )
            }
            UpdateViewModel.UpdatePhase.Downloaded -> {
                PrimaryButtonRow(
                    text = stringResource(R.string.update_install_now),
                    onClick = onInstall,
                )
            }
            UpdateViewModel.UpdatePhase.Failed -> {
                PrimaryButtonRow(
                    text = stringResource(R.string.update_retry),
                    onClick = onConfirm,
                )
            }
            UpdateViewModel.UpdatePhase.NewVersion -> {
                ActionRow(onIgnore = onIgnore, onConfirm = onConfirm)
            }
            UpdateViewModel.UpdatePhase.UpToDate -> {
                // 已在上层独立分支渲染
            }
        }
    }
}

@Composable
private fun ActionRow(onIgnore: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = onIgnore,
        ) {
            Text(
                text = stringResource(R.string.update_ignore_version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = onConfirm,
        ) {
            Text(
                text = stringResource(R.string.update_now),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PrimaryButtonRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            onClick = onClick,
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DownloadingContent(progress: Int) {
    val animatedFraction by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        label = "downloadProgress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.update_downloading_label),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$progress%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
