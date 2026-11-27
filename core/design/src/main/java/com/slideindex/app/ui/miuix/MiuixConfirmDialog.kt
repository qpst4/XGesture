package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.slideindex.app.design.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    text: @Composable (() -> Unit)? = null,
    confirmText: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit,
    dismissText: String = stringResource(R.string.cancel),
    dismissOnConfirm: Boolean = true,
    secondaryConfirmText: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
    secondaryDismissOnConfirm: Boolean = true,
    /** 确认键是否走破坏性配色（删除 / 清空类操作）。仅玻璃对话框生效。 */
    confirmDestructive: Boolean = false,
) {
    // 玻璃路径的判定要前置：`body` 里的动作行需要知道该用玻璃按钮还是普通 TextButton。
    val glassBackdrop = LocalMiuixPageBackdrop.current
    val glassActions = glassBackdrop != null
    val body: @Composable () -> Unit = {
        Column {
            when {
                text != null -> text()
                message != null -> {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            DialogActionsRow(
                glass = glassActions,
                dismissText = dismissText,
                onDismissRequest = onDismissRequest,
                confirmText = confirmText,
                confirmDestructive = confirmDestructive,
                onConfirm = {
                    onConfirm()
                    if (dismissOnConfirm) {
                        onDismissRequest()
                    }
                },
                secondaryConfirmText = secondaryConfirmText,
                onSecondaryConfirm = onSecondaryConfirm?.let { action ->
                    {
                        action()
                        if (secondaryDismissOnConfirm) {
                            onDismissRequest()
                        }
                    }
                },
            )
        }
    }


    if (!show) return
    if (glassBackdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = glassBackdrop,
            title = title,
            content = body,
        )
        return
    }
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        body()
    }
}

@Composable
fun MiuixFormDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissText: String? = stringResource(R.string.cancel),
    dismissOnConfirm: Boolean = true,
    secondaryConfirmText: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
    secondaryConfirmEnabled: Boolean = true,
    secondaryDismissOnConfirm: Boolean = false,
    /** 确认键是否走破坏性配色（删除 / 清空类操作）。仅玻璃对话框生效。 */
    confirmDestructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glassBackdrop = LocalMiuixPageBackdrop.current
    val glassActions = glassBackdrop != null
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val maxScrollHeight = with(density) {
        val availableHeight = if (imeBottomPx > 0) {
            (windowHeightPx - imeBottomPx) * 0.52f
        } else {
            windowHeightPx * 0.70f
        }
        availableHeight.toDp().coerceAtLeast(140.dp)
    }

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxScrollHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
            Spacer(Modifier.height(16.dp))
            if (dismissText != null) {
                DialogActionsRow(
                    glass = glassActions,
                    dismissText = dismissText,
                    onDismissRequest = onDismissRequest,
                    confirmText = confirmText,
                    confirmEnabled = confirmEnabled,
                    confirmDestructive = confirmDestructive,
                    onConfirm = {
                        onConfirm()
                        if (dismissOnConfirm) {
                            onDismissRequest()
                        }
                    },
                    secondaryConfirmText = secondaryConfirmText,
                    onSecondaryConfirm = onSecondaryConfirm?.let { action ->
                        {
                            action()
                            if (secondaryDismissOnConfirm) {
                                onDismissRequest()
                            }
                        }
                    },
                    secondaryConfirmEnabled = secondaryConfirmEnabled,
                )
            } else {
                DialogActionButton(
                    glass = glassActions,
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        if (dismissOnConfirm) {
                            onDismissRequest()
                        }
                    },
                    enabled = confirmEnabled,
                    role = if (confirmDestructive) {
                        GlassDialogButtonRole.Destructive
                    } else {
                        GlassDialogButtonRole.Primary
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }


    if (!show) return
    if (glassBackdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = glassBackdrop,
            title = title,
            content = body,
        )
        return
    }
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        body()
    }
}

@Composable
fun MiuixScrollableConfirmDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit,
    dismissText: String = stringResource(R.string.cancel),
    content: @Composable () -> Unit,
) {
    MiuixConfirmDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        },
    )
}

@Composable
private fun DialogActionsRow(
    glass: Boolean,
    dismissText: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmDestructive: Boolean = false,
    secondaryConfirmText: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
    secondaryConfirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondaryConfirmText != null && onSecondaryConfirm != null) {
            DialogActionButton(
                glass = glass,
                text = secondaryConfirmText,
                onClick = onSecondaryConfirm,
                enabled = secondaryConfirmEnabled,
                role = GlassDialogButtonRole.Normal,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
        }
        DialogActionButton(
            glass = glass,
            text = dismissText,
            onClick = onDismissRequest,
            role = GlassDialogButtonRole.Normal,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        DialogActionButton(
            glass = glass,
            text = confirmText,
            onClick = onConfirm,
            enabled = confirmEnabled,
            role = if (confirmDestructive) {
                GlassDialogButtonRole.Destructive
            } else {
                GlassDialogButtonRole.Primary
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 对话框单颗动作按钮。
 *
 * 玻璃对话框（`GlassDialog`）走 rc01 的 [GlassDialogButton]：16dp 圆角实心胶囊、50dp 高，
 * 普通/主要/破坏性三种色调由 [GlassDialogButtonRole] 决定，按压反馈同时盖住填充与文字。
 * 非玻璃路径（浮层窗口里的 `WindowDialog`）仍用 miuix `TextButton`，因为那层是不透明底，
 * 玻璃的半透明色调在上面对比度不足。
 */
@Composable
private fun DialogActionButton(
    glass: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: GlassDialogButtonRole = GlassDialogButtonRole.Normal,
) {
    if (glass) {
        GlassDialogButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            role = role,
            // 三键并排时每个按钮只剩 1/3 宽，上游 24dp 的横向内边距会把中文标签挤到换行。
            insideMargin = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.button,
                maxLines = 1,
            )
        }
    } else {
        TextButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = 44.dp),
            minHeight = 44.dp,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = if (role == GlassDialogButtonRole.Primary) {
                ButtonDefaults.textButtonColorsPrimary()
            } else {
                ButtonDefaults.textButtonColors()
            },
        )
    }
}
