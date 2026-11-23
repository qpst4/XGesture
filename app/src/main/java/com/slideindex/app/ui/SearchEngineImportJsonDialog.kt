package com.slideindex.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField

/**
 * 粘贴 JSON 导入配置的通用弹窗；文字页和图片页共用。
 */
@Composable
fun SearchEngineImportJsonDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.search_engine_import_config_title),
        confirmEnabled = text.isNotBlank(),
        onConfirm = {
            val raw = text.trim()
            if (raw.isBlank()) {
                Toast.makeText(
                    context,
                    R.string.search_engine_import_json_empty,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                onImport(raw)
                text = ""
            }
        },
    ) {
        Text(
            text = stringResource(R.string.search_engine_import_config_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MiuixLabeledTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.search_engine_import_config_hint),
            singleLine = false,
            minLines = 6,
            maxLines = 12,
        )
        TextButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? ClipboardManager
                val clipText = clipboard?.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    .orEmpty()
                if (clipText.isNotBlank()) text = clipText
            },
        ) {
            Text(stringResource(R.string.search_engine_import_paste_action))
        }
    }
}
