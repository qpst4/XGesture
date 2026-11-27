package com.slideindex.app.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.MiuixDialogActionsRow
import com.slideindex.app.ui.miuix.MiuixDialogAction
import com.slideindex.app.ui.miuix.MiuixAdaptiveDialog
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

private const val DEFAULT_SEED_COLOR = 0xFF6750A4.toInt()

@Composable
fun MiuixThemeSeedColorDialog(
    show: Boolean,
    initialColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var picked by remember(show, initialColorArgb) { mutableStateOf(Color(initialColorArgb)) }

    MiuixAdaptiveDialog(
        show = show,
        title = stringResource(R.string.theme_seed_color_dialog_title),
        onDismissRequest = onDismiss,
    ) {
        Column {
            ColorPicker(
                color = picked,
                onColorChanged = { picked = it },
            )
            Spacer(Modifier.height(20.dp))
            MiuixDialogActionsRow(
                actions = listOf(
                    MiuixDialogAction(
                        text = stringResource(R.string.theme_seed_color_reset),
                        onClick = { picked = Color(DEFAULT_SEED_COLOR) },
                    ),
                    MiuixDialogAction(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                    ),
                    MiuixDialogAction(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            onConfirm(picked.toArgb())
                            onDismiss()
                        },
                        primary = true,
                    ),
                ),
            )
        }
    }
}
