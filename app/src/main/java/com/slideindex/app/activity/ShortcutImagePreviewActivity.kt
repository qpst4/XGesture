package com.slideindex.app.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.util.ImageDecodeGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ShortcutImagePreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI).orEmpty()
        val openInDialog = intent.getBooleanExtra(EXTRA_OPEN_IN_DIALOG, true)
        val clickToDismiss = intent.getBooleanExtra(EXTRA_CLICK_TO_DISMISS, true)
        val orientation = intent.getIntExtra(EXTRA_ORIENTATION, 0)

        when (orientation) {
            1 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        setContent {
            MiuixTheme {
                ShortcutImagePreviewScreen(
                    uriString = uriString,
                    openInDialog = openInDialog,
                    clickToDismiss = clickToDismiss,
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_OPEN_IN_DIALOG = "extra_open_in_dialog"
        const val EXTRA_CLICK_TO_DISMISS = "extra_click_to_dismiss"
        const val EXTRA_ORIENTATION = "extra_orientation"

        fun start(
            context: Context,
            config: ImageShortcutConfig,
        ) {
            val intent = Intent(context, ShortcutImagePreviewActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, config.uri)
                putExtra(EXTRA_OPEN_IN_DIALOG, config.openInDialog)
                putExtra(EXTRA_CLICK_TO_DISMISS, config.clickToDismiss)
                putExtra(EXTRA_ORIENTATION, config.orientation)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun ShortcutImagePreviewScreen(
    uriString: String,
    openInDialog: Boolean,
    clickToDismiss: Boolean,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(uriString) {
        if (uriString.isBlank()) {
            loading = false
            error = true
            return@LaunchedEffect
        }
        loading = true
        error = false
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    uriString.startsWith("http://") || uriString.startsWith("https://") -> {
                        val url = URL(uriString)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.inputStream.use { input ->
                            BitmapFactory.decodeStream(input)
                        }?.let { ImageDecodeGuard.fitForDisplay(it) }
                    }
                    uriString.startsWith("content://") -> {
                        val uri = Uri.parse(uriString)
                        ImageDecodeGuard.decodeUri(
                            context = context,
                            uri = uri,
                            maxSidePx = ImageDecodeGuard.MAX_DISPLAY_SIDE_PX,
                        )
                    }
                    else -> {
                        // 本地路径
                        ImageDecodeGuard.decodeFile(
                            path = uriString,
                            maxSidePx = ImageDecodeGuard.MAX_DISPLAY_SIDE_PX,
                        )
                    }
                }
            }.getOrNull()
        }
        bitmap = loaded
        loading = false
        error = (loaded == null)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformModifier = Modifier.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(0.8f, 5f)
            offset = if (scale > 1f) {
                Offset(offset.x + pan.x, offset.y + pan.y)
            } else {
                Offset.Zero
            }
        }
    }

    val dismissInteraction = remember { MutableInteractionSource() }

    if (openInDialog) {
        // 弹窗展示（图四）：暗色半透明遮罩背景，中间是圆角卡片
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier
                    .padding(32.dp)
                    .wrapContentSize(),
                cornerRadius = 20.dp,
                onClick = if (clickToDismiss) onDismiss else null,
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .wrapContentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(32.dp)
                                    .size(36.dp),
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        error || bitmap == null -> {
                            Text(
                                text = stringResource(R.string.float_ball_action_failed),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                style = MiuixTheme.textStyles.body2,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                        else -> {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                                    .then(transformModifier),
                            )
                        }
                    }
                }
            }
        }
    } else {
        // 全屏展示：沉浸黑底
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    enabled = clickToDismiss,
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                    )
                }
                error || bitmap == null -> {
                    Text(
                        text = stringResource(R.string.float_ball_action_failed),
                        color = Color.LightGray,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                else -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .then(transformModifier),
                    )
                }
            }
        }
    }
}
