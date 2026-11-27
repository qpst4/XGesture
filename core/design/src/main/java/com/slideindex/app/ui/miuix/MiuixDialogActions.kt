package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 对话框动作的一颗按钮（声明式描述）。
 *
 * @param primary true = 主操作（主色底 / 白字），false = 普通操作。
 */
data class MiuixDialogAction(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val primary: Boolean = false,
)

/**
 * 全 app 统一的**对话框动作行**：材质与排布只在这里决定。
 *
 * 以前这套规则散落在 21 个自定义弹窗 + 77 个确认框里各写各的，才会出现
 * "有的按钮是基础 TextButton、单按钮不横跨、主次色不一致"。
 *
 * - **材质**：玻璃面 → [GlassDialogButton]；否则 → miuix [TextButton]。
 * - **排布**：每颗 `weight(1f)` 等分、间距 12dp；只有一颗时自然横跨整行。
 * - **顺序**：完全按传入的 [actions] 顺序，不重排 —— 需要 `[次要, 取消, 主操作]`
 *   这种既有排布时直接按该顺序传，避免改动既有页面。
 */
@Composable
fun MiuixDialogActionsRow(
    actions: List<MiuixDialogAction>,
    modifier: Modifier = Modifier,
    /** null = 跟随 [LocalMiuixGlassSurface]；显式传入用于非标准宿主。 */
    glass: Boolean? = null,
) {
    if (actions.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        actions.forEach { action ->
            MiuixDialogActionButton(
                text = action.text,
                onClick = action.onClick,
                enabled = action.enabled,
                role = if (action.primary) {
                    GlassDialogButtonRole.Primary
                } else {
                    GlassDialogButtonRole.Normal
                },
                modifier = Modifier.weight(1f),
                glass = glass,
            )
        }
    }
}

/**
 * 便捷重载：`[次要, 第三颗, 主操作]` 顺序。主操作固定在最右。
 */
@Composable
fun MiuixDialogActionsRow(
    modifier: Modifier = Modifier,
    primaryText: String? = null,
    onPrimary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryEnabled: Boolean = true,
    tertiaryText: String? = null,
    onTertiary: (() -> Unit)? = null,
    tertiaryEnabled: Boolean = true,
    glass: Boolean? = null,
) {
    MiuixDialogActionsRow(
        actions = buildList {
            secondaryText?.let { add(MiuixDialogAction(it, onSecondary ?: {}, secondaryEnabled)) }
            tertiaryText?.let { add(MiuixDialogAction(it, onTertiary ?: {}, tertiaryEnabled)) }
            primaryText?.let { add(MiuixDialogAction(it, onPrimary ?: {}, primaryEnabled, primary = true)) }
        },
        modifier = modifier,
        glass = glass,
    )
}

/**
 * 单颗对话框动作按钮：玻璃面走 [GlassDialogButton]（内部用 miuix [MiuixText]，
 * 文字色来自按钮下发的 `LocalContentColor`，不会退化成 material3 默认黑）；
 * 非玻璃面走 miuix [TextButton]。
 */
@Composable
fun MiuixDialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: GlassDialogButtonRole = GlassDialogButtonRole.Normal,
    glass: Boolean? = null,
) {
    if (glass ?: LocalMiuixGlassSurface.current) {
        GlassDialogButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            role = role,
            insideMargin = PaddingValues(horizontal = 12.dp),
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
