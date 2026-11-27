package com.slideindex.app.ui.miuix

/**
 * Mishka-style settings page hint: neutral Card at the top of a sub-page list.
 * See Mishka [RestartRequiredHint](https://github.com/YuKongA/Mishka).
 */
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassCardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsPageHintCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .padding(top = 12.dp, bottom = 6.dp)
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    // 玻璃浮层内走 GlassCard；普通页面保持原 Card。
    if (LocalMiuixGlassSurface.current) {
        GlassCard(modifier = cardModifier, cornerRadius = GlassCardDefaults.CornerRadius, content = body)
    } else {
        Card(modifier = cardModifier, content = body)
    }
}
