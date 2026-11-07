package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/** 编辑栏动作按钮（`.actbtn`）；原先在 app 的 `HistoryPanelEditBar` 里。 */
@Composable
fun HistoryEditActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    /** 已生效的状态（如"已设提醒"）：描边与文字用主题色。 */
    active: Boolean = false,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.sm)
    val container = when {
        primary -> theme.accentSolid
        active -> theme.accentSoft
        else -> theme.btnBg
    }
    val content = when {
        primary -> Color.White
        danger -> theme.danger
        active -> theme.accent
        else -> theme.text
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(
                if (primary) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = if (active) theme.accent.copy(alpha = 0.55f) else theme.btnBd,
                        shape = shape,
                    )
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .height(52.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = TextStyle(fontSize = HistoryFontSizes.meta),
            color = content,
            modifier = Modifier.padding(start = 5.dp),
            maxLines = 1,
        )
    }
}
