package com.slideindex.app.overlay.pickresult

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.theme.LocalAppDarkTheme

@Composable
fun PickResultSmartChipsRow(
    text: String?,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val entities = remember(text) {
        PickResultSmartParser.parseSmartEntities(text)
    }

    AnimatedVisibility(
        visible = entities.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val context = LocalContext.current
        val isDark = LocalAppDarkTheme.current

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entities, key = { it.entityKey }) { entity ->
                PickResultSmartChipItem(
                    entity = entity,
                    isDark = isDark,
                    onClick = {
                        entity.execute(context, onCopyText)
                    }
                )
            }
        }
    }
}

@Composable
private fun PickResultSmartChipItem(
    entity: PickResultSmartEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val containerBg = if (isDark) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    }
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val actionPrefix = stringResource(entity.actionPrefixResId)

    Row(
        modifier = Modifier
            .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
            .background(containerBg, CircleShape)
            .border(
                width = 0.5.dp,
                color = if (isDark) androidx.compose.ui.graphics.Color(0x30FFFFFF) else androidx.compose.ui.graphics.Color(0x18000000),
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = entity.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "$actionPrefix: ${entity.label}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = contentColor,
            maxLines = 1
        )
    }
}
