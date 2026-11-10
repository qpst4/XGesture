package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherLabels
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.isCloneTarget
import com.slideindex.app.launcher.showsMacroBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.launcher.showsShortcutBadge
import com.slideindex.app.overlay.CloneBadgeOverlay
import com.slideindex.app.overlay.MacroBadgeOverlay
import com.slideindex.app.overlay.ShellCommandBadgeOverlay
import com.slideindex.app.overlay.ShortcutBadgeOverlay
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.settings.QuickLauncherDisplaySettings
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.util.QuickLauncherIconResolver

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun QuickLauncherPageGrid(
    modifier: Modifier = Modifier,
    pageStart: Int,
    columns: Int,
    rows: Int,
    pageSize: Int,
    items: List<QuickLauncherItem>,
    appsByPackage: Map<String, AppInfo>,
    iconBitmapCache: Map<Int, android.graphics.Bitmap?>,
    actionIconTintArgb: Int,
    editMode: Boolean,
    dragFromGlobal: Int,
    dragSlotGlobal: Int,
    mergeTargetGlobal: Int = -1,
    iconSizeDp: Int = QuickLauncherDisplaySettings.DEFAULT_ICON_SIZE_DP,
    iconShape: Int = QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT,
    showLabels: Boolean = true,
    cellHeightDp: Dp = 80.dp,
    shellCommands: List<ShellCommand> = emptyList(),
    onEnterFolder: ((Int) -> Unit)? = null,
) {
    val displayMapping = remember(items.size, dragFromGlobal, dragSlotGlobal, mergeTargetGlobal, pageStart, pageSize) {
        QuickLauncherGridLogic.displayMappingForPage(
            itemCount = items.size,
            dragFrom = dragFromGlobal,
            dragSlotGlobal = dragSlotGlobal,
            pageStart = pageStart,
            pageSize = pageSize,
            mergeTargetGlobal = mergeTargetGlobal,
        )
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0 until columns) {
                    val cellIndex = row * columns + col
                    if (cellIndex >= pageSize) continue
                    Box(modifier = Modifier.weight(1f).height(cellHeightDp)) {
                        val originalIndex = displayMapping.getOrNull(cellIndex)
                        val item = originalIndex?.let { items.getOrNull(it) }
                        if (originalIndex == null || item == null) {
                            QuickLauncherEmptyGridCell()
                        } else {
                            val isMergeTarget = mergeTargetGlobal >= 0 && originalIndex == mergeTargetGlobal
                            val enterable = onEnterFolder != null && item.isFolder && !isMergeTarget
                            QuickLauncherGridCell(
                                item = item,
                                appsByPackage = appsByPackage,
                                iconBitmap = iconBitmapCache[originalIndex],
                                actionIconTintArgb = actionIconTintArgb,
                                showEditBadge = editMode && dragFromGlobal != originalIndex && !isMergeTarget,
                                isMergeTarget = isMergeTarget,
                                iconSizeDp = iconSizeDp,
                                iconShape = iconShape,
                                showLabels = showLabels,
                                shellCommands = shellCommands,
                                onClick = if (enterable) {
                                    { onEnterFolder(originalIndex) }
                                } else {
                                    null
                                },
                                showEnterAffordance = enterable,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickLauncherEmptyGridCell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    )
}

@Composable
internal fun QuickLauncherGridCell(
    modifier: Modifier = Modifier,
    item: QuickLauncherItem,
    appsByPackage: Map<String, AppInfo>,
    iconBitmap: android.graphics.Bitmap? = null,
    actionIconTintArgb: Int = android.graphics.Color.WHITE,
    showEditBadge: Boolean = false,
    isMergeTarget: Boolean = false,
    iconSizeDp: Int = QuickLauncherDisplaySettings.DEFAULT_ICON_SIZE_DP,
    iconShape: Int = QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT,
    showLabels: Boolean = true,
    activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
    shellCommands: List<ShellCommand> = emptyList(),
    onClick: (() -> Unit)? = null,
    showEnterAffordance: Boolean = false,
) {
    val context = LocalContext.current
    val label = quickLauncherGridLabel(context, item, appsByPackage)
    val actionIconTint = remember(actionIconTintArgb) { androidx.compose.ui.graphics.Color(actionIconTintArgb) }
    val action = remember(item.payload, item.type) {
        if (item.type == QuickLauncherItemType.ACTION) {
            QuickLauncherItemCodec.parseActionPayload(item.payload)
        } else {
            null
        }
    }
    val resolvedIconBitmap = iconBitmap
    val showShortcutBadge = item.showsShortcutBadge()
    val showShellCommandBadge = !showShortcutBadge && item.showsShellCommandBadge(shellCommands)
    val showMacroBadge = !showShortcutBadge && !showShellCommandBadge && item.showsMacroBadge()
    val isClone = remember(item, com.slideindex.app.macro.MacroStore.revision) {
        item.isCloneTarget(context)
    }
    val iconSize = iconSizeDp.coerceIn(
        QuickLauncherDisplaySettings.MIN_ICON_SIZE_DP,
        QuickLauncherDisplaySettings.MAX_ICON_SIZE_DP,
    ).dp
    val iconClip = remember(iconShape) { quickLauncherIconClipShape(iconShape) }
    val cellBackground = if (isMergeTarget) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val cellBorderModifier = if (isMergeTarget) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(cellBorderModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(cellBackground)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isMergeTarget) {
                        scaleX = 0.92f
                        scaleY = 0.92f
                    }
                }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedIconBitmap != null) {
                    Image(
                        bitmap = resolvedIconBitmap.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier
                            .size(iconSize)
                            .clip(iconClip),
                    )
                } else if (action != null) {
                    com.slideindex.app.ui.ActionIconSlot(
                        action = action,
                        sizeDp = iconSize.value.toInt(),
                        modifier = Modifier.clip(iconClip),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(iconClip)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                if (showShortcutBadge) {
                    ShortcutBadgeOverlay(iconSize = iconSize)
                } else if (showShellCommandBadge) {
                    ShellCommandBadgeOverlay(iconSize = iconSize)
                } else if (showMacroBadge) {
                    MacroBadgeOverlay(iconSize = iconSize)
                }
                if (isClone) {
                    CloneBadgeOverlay(
                        iconSize = iconSize,
                        circular = iconShape == QuickLauncherDisplaySettings.ICON_SHAPE_CIRCLE,
                    )
                }
            }
            if (showLabels) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (showEditBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "−",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (showEnterAffordance) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

internal fun quickLauncherGridLabel(
    context: android.content.Context,
    item: QuickLauncherItem,
    appsByPackage: Map<String, AppInfo>,
): String = QuickLauncherLabels.resolveLabel(context, item, appsByPackage)

internal fun quickLauncherIconClipShape(iconShape: Int): Shape =
    when (QuickLauncherDisplaySettings.coerceIconShape(iconShape)) {
        QuickLauncherDisplaySettings.ICON_SHAPE_CIRCLE -> CircleShape
        QuickLauncherDisplaySettings.ICON_SHAPE_ADAPTIVE -> RoundedCornerShape(percent = 30)
        else -> RectangleShape
    }
