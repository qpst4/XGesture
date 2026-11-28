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
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
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
    /** 退出动画播完后回调；调用方在这之后再清数据，退场期间内容不会先变空。 */
    onDismissFinished: (() -> Unit)? = null,
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


    // 不再用 `if (!show) return`：那会把 Dialog 从组合里摘掉，退场动画直接被吃掉。
    // 常驻组合、只把 show 传下去，由 miuix 自己播完进场/退场（Mishka 与 miuix 文档的写法）。
    if (glassBackdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = glassBackdrop,
            title = title,
            onDismissFinished = onDismissFinished,
            content = { MiuixGlassSurfaceScope { body() } },
        )
        return
    }
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
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
    /** 退出动画播完后回调；调用方在这之后再清数据，退场期间内容不会先变空。 */
    onDismissFinished: (() -> Unit)? = null,
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
                    role = GlassDialogButtonRole.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }


    // 不再用 `if (!show) return`：那会把 Dialog 从组合里摘掉，退场动画直接被吃掉。
    // 常驻组合、只把 show 传下去，由 miuix 自己播完进场/退场（Mishka 与 miuix 文档的写法）。
    if (glassBackdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = glassBackdrop,
            title = title,
            onDismissFinished = onDismissFinished,
            content = { MiuixGlassSurfaceScope { body() } },
        )
        return
    }
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
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
    /** 退出动画播完后回调；调用方在这之后再清数据，退场期间内容不会先变空。 */
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    MiuixConfirmDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        onDismissFinished = onDismissFinished,
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
    secondaryConfirmText: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
    secondaryConfirmEnabled: Boolean = true,
) {
    MiuixDialogActionsRow(
        actions = buildList {
            secondaryConfirmText?.let {
                add(MiuixDialogAction(it, onSecondaryConfirm ?: {}, secondaryConfirmEnabled))
            }
            add(MiuixDialogAction(dismissText, onDismissRequest))
            add(MiuixDialogAction(confirmText, onConfirm, confirmEnabled, primary = true))
        },
        glass = glass,
    )
}

/**
 * 对话框单颗动作按钮。
 *
 * 玻璃对话框（`GlassDialog`）**照 miuix 示例的写法内联** `GlassDialogButton`：
 * 内容用 miuix `Text` + `MiuixTheme.textStyles.button`，文字色来自按钮下发的
 * `LocalContentColor`（Primary → onPrimary 白），不会退化成 material3 的默认黑色。
 * 角色只用 `Normal`（取消）与 `Primary`（确认）—— 示例 app 里全部对话框都是这两种，
 * 没有 `Destructive`。
 * 非玻璃路径（浮层窗口里的 `WindowDialog`）仍用 miuix `TextButton`，因为那层是不透明底。
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
        ) {
            MiuixText(
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
