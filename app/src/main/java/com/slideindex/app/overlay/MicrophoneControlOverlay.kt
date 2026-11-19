package com.slideindex.app.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.ui.ThinActionIcons
import com.slideindex.app.util.MicrophoneControlHelper
import com.slideindex.app.util.PermissionHelper

object MicrophoneControlOverlay {
    private var host: OverlayComposeDialogHost? = null

    fun show(context: Context) {
        if (!PermissionHelper.canDrawOverlays(context)) {
            runCatching { context.startActivity(PermissionHelper.overlaySettingsIntent(context)) }
            return
        }
        val hostContext = MessageOverlayHost.resolveHostContext(context) ?: context.applicationContext
        val dialogHost = host ?: OverlayComposeDialogHost(
            context = hostContext,
            fullScreen = false,
        ).also { host = it }

        dialogHost.show {
            MicrophoneControlDialogContent(
                context = hostContext,
                onDismiss = { dialogHost.dismiss() },
            )
        }
    }

    fun dismiss() {
        host?.dismiss()
    }
}

@Composable
private fun MicrophoneControlDialogContent(
    context: Context,
    onDismiss: () -> Unit,
) {
    var isMuted by remember { mutableStateOf(MicrophoneControlHelper.isMuted(context)) }

    Card(
        modifier = Modifier
            .padding(16.dp)
            .width(310.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶栏：标题 + 状态 Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = ThinActionIcons.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.mic_menu_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                val statusBg = if (isMuted) Color(0xFFE53935).copy(alpha = 0.15f) else Color(0xFF43A047).copy(alpha = 0.15f)
                val statusFg = if (isMuted) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                val statusText = if (isMuted) stringResource(R.string.mic_status_muted) else stringResource(R.string.mic_status_unmuted)

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusFg,
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 操作按钮区：切换 / 开启 / 静音
            // 1. 切换状态（高亮 FilledButton）
            Button(
                onClick = {
                    MicrophoneControlHelper.toggle(context)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.mic_action_toggle),
                    fontWeight = FontWeight.Medium,
                )
            }

            // 2. 开启麦克风
            FilledTonalButton(
                onClick = {
                    MicrophoneControlHelper.setMute(context, mute = false)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = isMuted,
            ) {
                Text(
                    text = stringResource(R.string.mic_action_unmute),
                    fontWeight = FontWeight.Medium,
                )
            }

            // 3. 静音麦克风
            OutlinedButton(
                onClick = {
                    MicrophoneControlHelper.setMute(context, mute = true)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !isMuted,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (!isMuted) Color(0xFFD32F2F) else Color.Gray,
                ),
            ) {
                Text(
                    text = stringResource(R.string.mic_action_mute),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
