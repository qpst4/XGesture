package com.slideindex.app.overlay.pickresult

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.ui.theme.LocalAppDarkTheme

/**
 * 取词面板的 LTR 下拉菜单（从 app 的 PickResultPanelChrome 剪出，面板与 app 共用）。
 */
@Composable
fun PickResultDropdownMenuInLtr(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.widthIn(min = 140.dp, max = 280.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = if (isDark) Color(0xFF28282A) else Color(0xFFFFFFFF),
            shadowElevation = 6.dp,
            tonalElevation = 0.dp,
            content = content,
        )
    }
}

/** 下拉菜单里的动作项。 */
@Composable
fun PickResultActionMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier,
) {
    val isDark = LocalAppDarkTheme.current
    val textColor = if (isDark) Color(0xFFECECED) else Color(0xFF1F1F1F)
    val iconTint = if (isDark) Color(0xFFE0E0E6) else Color(0xFF333333)
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = textColor,
                ),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp).then(iconModifier),
                tint = iconTint,
            )
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = onClick,
    )
}