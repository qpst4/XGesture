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
import com.slideindex.app.design.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
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
) {
    if (!show) return

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
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
                dismissText = dismissText,
                onDismissRequest = onDismissRequest,
                confirmText = confirmText,
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
    content: @Composable () -> Unit,
) {
    if (!show) return

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

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
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
                    dismissText = dismissText,
                    onDismissRequest = onDismissRequest,
                    confirmText = confirmText,
                    confirmEnabled = confirmEnabled,
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
                TextButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        if (dismissOnConfirm) {
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp),
                    minHeight = 44.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
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
    dismissText: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    secondaryConfirmText: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
    secondaryConfirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (secondaryConfirmText != null && onSecondaryConfirm != null) {
            TextButton(
                text = secondaryConfirmText,
                onClick = onSecondaryConfirm,
                enabled = secondaryConfirmEnabled,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp),
                minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        TextButton(
            text = dismissText,
            onClick = onDismissRequest,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp),
            minHeight = 44.dp,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        )
        Spacer(Modifier.width(12.dp))
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp),
            minHeight = 44.dp,
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
