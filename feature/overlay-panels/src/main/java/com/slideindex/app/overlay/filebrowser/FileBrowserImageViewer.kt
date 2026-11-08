package com.slideindex.app.overlay.filebrowser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.slideindex.app.overlay.share.ShareSheetLauncher
import com.slideindex.app.overlay.share.ShareSheetPayload
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.slideindex.app.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 卡片内的图片查看器：缩放/旋转/前后切换/分享；Root 图片先导出到 cache。 */
@Composable
internal fun FileBrowserImageViewer(
    entry: FileBrowserEntry,
    access: FileBrowserAccessInfo?,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onDetails: (() -> Unit)?,
    onEdit: (Bitmap) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(entry.path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(entry.path) { mutableStateOf(false) }
    var scale by remember(entry.path) { mutableStateOf(1f) }
    var offset by remember(entry.path) { mutableStateOf(Offset.Zero) }
    var rotation by remember(entry.path) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset += panChange
    }
    LaunchedEffect(entry.path) {
        bitmap = withContext(Dispatchers.IO) {
            loadBitmap(context, entry, access)
        }
        failed = bitmap == null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        when {
            failed -> Text(
                text = stringResource(R.string.file_browser_media_image_failed),
                color = MaterialTheme.colorScheme.error,
            )
            bitmap == null -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            else -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(transformState),
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    RoundedCornerShape(12.dp),
                ),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
        }
        bitmap?.let { loaded ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        RoundedCornerShape(14.dp),
                    )
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPrevious != null) {
                    TextButton(onClick = onPrevious) {
                        Text(stringResource(R.string.file_browser_media_previous))
                    }
                }
                if (onNext != null) {
                    TextButton(onClick = onNext) {
                        Text(stringResource(R.string.file_browser_media_next))
                    }
                }
                TextButton(onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }) { Text("1:1") }
                TextButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                    Text(stringResource(R.string.file_browser_media_rotate))
                }
                TextButton(onClick = {
                    shareBitmap(context, loaded)
                }) { Text(stringResource(R.string.file_browser_share)) }
                TextButton(onClick = { onEdit(loaded) }) {
                    Text(stringResource(R.string.file_browser_edit))
                }
                if (onDetails != null) {
                    TextButton(onClick = onDetails) {
                        Text(stringResource(R.string.file_browser_details_title))
                    }
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.file_browser_close)) }
            }
        }
    }
}

private suspend fun loadBitmap(
    context: Context,
    entry: FileBrowserEntry,
    access: FileBrowserAccessInfo?,
): Bitmap? {
    val direct = File(entry.path)
    val file = if (direct.isFile && direct.canRead()) {
        direct
    } else if (access?.mode == AccessMode.ROOT) {
        FileBrowserExport.exportToCache(context, entry.path)
    } else {
        null
    } ?: return null
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
}

private fun shareBitmap(context: Context, bitmap: Bitmap) {
    Log.d("FileBrowserShare", "shareBitmap start")
    Toast.makeText(context, context.getString(R.string.file_browser_sharing), Toast.LENGTH_SHORT).show()
    try {
        val dir = File(context.cacheDir, "filebrowser").apply { mkdirs() }
        val file = File(dir, "share_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.d("FileBrowserShare", "written ${file.absolutePath} ${file.length()}")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        Log.d("FileBrowserShare", "uri=$uri")
        // 旧实现：这里先 dismiss 再拉起系统 chooser —— 悬浮窗（TYPE_APPLICATION_OVERLAY）会盖住
        // 系统分享面板。现在统一交给 launcher：开关打开时在浮层里点选目标直发，开关关闭时
        // 由 launcher 先收起本浮窗再拉系统面板。
        ShareSheetLauncher.share(
            context = context,
            payload = ShareSheetPayload.Image(uri = uri, mimeType = "image/png"),
            dismissSource = { FileBrowserOverlayWindow.dismiss() },
        )
        Log.d("FileBrowserShare", "share dispatched")
    } catch (t: Throwable) {
        Log.e("FileBrowserShare", "share failed", t)
        runCatching {
            Toast.makeText(
                context,
                context.getString(R.string.file_browser_share_failed, t.message ?: t.toString()),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
