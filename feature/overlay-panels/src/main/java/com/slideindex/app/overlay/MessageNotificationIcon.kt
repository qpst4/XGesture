package com.slideindex.app.overlay

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R

@Composable
fun MessageNotificationIcon(
    iconBitmap: Bitmap?,
    appIconBitmap: Bitmap?,
    sizeDp: Dp,
    startPaddingDp: Dp = 0.dp,
    endPaddingDp: Dp = 0.dp,
    badgeAlignment: Alignment = Alignment.BottomEnd,
    allowAppIconFallback: Boolean = true,
    fallbackLabel: String? = null,
    iconShape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val displayIcon = iconBitmap ?: if (allowAppIconFallback) appIconBitmap else null
    Box(
        modifier = Modifier.padding(start = startPaddingDp, end = endPaddingDp)
    ) {
        if (displayIcon != null) {
            Image(
                bitmap = displayIcon.asImageBitmap(),
                contentDescription = stringResource(
                    if (iconBitmap != null) R.string.cd_notification_icon else R.string.cd_app_icon
                ),
                modifier = Modifier
                    .size(sizeDp)
                    .clip(iconShape),
                contentScale = contentScale,
            )
            if (iconBitmap != null && appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_app_icon),
                    modifier = Modifier
                        .align(badgeAlignment)
                        .size(sizeDp * 0.34f)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            val label = fallbackLabel?.trim()?.firstOrNull()?.toString()
            if (!label.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .clip(iconShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_notification),
                    contentDescription = stringResource(R.string.cd_notification_icon),
                    modifier = Modifier
                        .size(sizeDp)
                        .clip(iconShape),
                    contentScale = contentScale,
                )
            }
        }
    }
}
