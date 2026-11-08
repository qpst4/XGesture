package com.slideindex.app.overlay.searchpanel

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.overlay.panels.R

@Composable
fun SearchPanelActionPillsRow(
    visible: Boolean,
    keyboardSwitchText: String?,
    showPhoneCallAction: Boolean,
    phoneQuery: String,
    onKeyboardSwitchToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (keyboardSwitchText != null) {
                SearchPanelKeyboardSwitchPill(
                    text = keyboardSwitchText,
                    onClick = onKeyboardSwitchToggle,
                )
            }
            if (showPhoneCallAction) {
                Spacer(modifier = Modifier.size(8.dp))
                SearchPanelPhoneCallPill(phoneQuery = phoneQuery)
            }
        }
    }
}

@Composable
private fun SearchPanelKeyboardSwitchPill(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SearchPanelPhoneCallPill(
    phoneQuery: String,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    var dialAppIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val icon = runCatching {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                context.packageManager.resolveActivity(dialIntent, 0)
                    ?.loadIcon(context.packageManager)
                    ?.toBitmap()
                    ?.asImageBitmap()
            }.getOrNull()
            if (icon != null) {
                dialAppIcon = icon
            }
        }
    }

    Surface(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = "tel:${Uri.encode(phoneQuery.trim())}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val currentDialIcon = dialAppIcon
            if (currentDialIcon != null) {
                Icon(
                    painter = BitmapPainter(currentDialIcon),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.search_panel_phone_call),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

fun String.isPhoneNumberQuery(): Boolean =
    isNotEmpty() &&
        if (first() == '+') {
            length > 1 && drop(1).all(Char::isDigit)
        } else {
            all(Char::isDigit)
        }
