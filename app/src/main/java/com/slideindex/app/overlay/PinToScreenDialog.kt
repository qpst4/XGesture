package com.slideindex.app.overlay

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.service.PinImagePickerTrampolineActivity
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper

/**
 * 钉到屏幕轻量弹窗：选择【文本便签】或【置顶图片】。
 */
object PinToScreenDialog {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dialogHost: OverlayComposeDialogHost? = null

    fun show(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context) }
            return true
        }

        if (!PermissionHelper.canDrawOverlays(context)) {
            context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            return false
        }

        val hostContext = overlayHostContext() ?: context.applicationContext
        val host = dialogHost ?: OverlayComposeDialogHost(
            context = hostContext,
            fullScreen = false
        ).also { dialogHost = it }

        host.show(
            onBackPressed = {
                host.dismiss()
                true
            }
        ) {
            OverlayAwareModuleTheme {
                PinToScreenDialogContent(
                    context = hostContext,
                    onPinText = { text ->
                        ScreenPinManager.pinText(hostContext, text)
                        host.dismiss()
                    },
                    onPickGalleryImage = {
                        host.dismiss()
                        PinImagePickerTrampolineActivity.launch(hostContext)
                    },
                    onCaptureRegionalScreenshot = {
                        host.dismiss()
                        SlideIndexAccessibilityService.pickFullscreen(
                            context = hostContext,
                            ocrFallbackEnabled = false,
                            ocrModelId = ""
                        )
                    },
                    onDismiss = { host.dismiss() }
                )
            }
        }
        return true
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        dialogHost?.dismiss()
        dialogHost = null
    }
}

@Composable
private fun PinToScreenDialogContent(
    context: Context,
    onPinText: (String) -> Unit,
    onPickGalleryImage: () -> Unit,
    onCaptureRegionalScreenshot: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var textInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (clipText.isNotEmpty()) {
            textInput = clipText
        }
    }

    Card(
        modifier = Modifier
            .padding(16.dp)
            .width(340.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.pin_to_screen_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.pin_to_screen_tab_text)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.pin_to_screen_tab_image)) }
                )
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.pin_to_screen_text_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        shape = RoundedCornerShape(16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                    if (clipText.isNotEmpty()) {
                                        textInput = clipText
                                    } else {
                                        Toast.makeText(context, R.string.pin_to_screen_clipboard_empty, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.pin_to_screen_btn_read_clipboard), fontSize = 12.sp)
                            }

                            if (textInput.isNotEmpty()) {
                                TextButton(onClick = { textInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.pin_to_screen_btn_clear), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = textInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onPinText(trimmed)
                                } else {
                                    Toast.makeText(context, R.string.pin_to_screen_text_empty, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.pin_to_screen_btn_pin))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PinImageOptionCard(
                        icon = Icons.Outlined.Image,
                        title = stringResource(R.string.pin_to_screen_image_pick_gallery),
                        desc = stringResource(R.string.pin_to_screen_image_pick_gallery_desc),
                        onClick = onPickGalleryImage
                    )

                    PinImageOptionCard(
                        icon = Icons.Outlined.Crop,
                        title = stringResource(R.string.pin_to_screen_image_pick_screenshot),
                        desc = stringResource(R.string.pin_to_screen_image_pick_screenshot_desc),
                        onClick = onCaptureRegionalScreenshot
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinImageOptionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
