package com.slideindex.app.overlay.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.PanelRuntimeHooks
import com.slideindex.app.overlay.panels.R

/**
 * 分享目标图标：先同步取缓存（命中就直接画），未命中才异步加载。
 *
 * 分享面板的格子、默认目标芯片、分享键角标都用它，避免三处各自解码一遍图标。
 */
@Composable
fun ShareTargetIconImage(
    packageName: String,
    activityClassName: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var bitmap by remember(packageName, activityClassName) {
        mutableStateOf(PanelRuntimeHooks.peekActivityIcon?.invoke(packageName, activityClassName))
    }
    LaunchedEffect(packageName, activityClassName) {
        if (bitmap == null) {
            bitmap = PanelRuntimeHooks.loadActivityIcon?.invoke(context, packageName, activityClassName)
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        // 图标还没加载 / 解码失败也别留空：给一个占位块，布局不跳。
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/**
 * 分享键右下角的默认目标角标（对齐 FV：一眼看出"点下去会发给谁"）。
 *
 * 尺寸刻意很小且贴图标右下角：42dp 的按键区域不会被撑大，圆形 ripple 也切不到它。
 */
@Composable
fun ShareDefaultBadge(
    target: ShareDefaultTarget,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.share_sheet_default_badge)
    Box(
        modifier = modifier
            .size(13.dp)
            .clip(CircleShape)
            // 一圈 surface 色描边：深浅图标底上都能和分享图标分开。
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        ShareTargetIconImage(
            packageName = target.packageName,
            activityClassName = target.activityClassName,
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(CircleShape),
            contentDescription = description,
        )
    }
}

/**
 * 取当前内容形态的默认分享目标（Compose 状态）：面板里设 / 清默认后角标会立刻跟着变。
 *
 * `resolve` 会校验目标仍存在且可启动；校验失败按"没设默认"处理。
 */
@Composable
fun rememberShareDefaultTarget(kind: ShareSheetKind): ShareDefaultTarget? {
    val context = LocalContext.current
    val targets by ShareSheetDefaults.targets.collectAsState()
    LaunchedEffect(Unit) { ShareSheetDefaults.load(context) }
    return remember(targets, kind, context) { ShareSheetDefaults.resolve(context, kind) }
}

/** 默认目标角标在 42dp 按键里的摆放：贴 22dp 图标的右下角，整体仍在圆形按键内。 */
@Composable
fun BoxScope.ShareDefaultBadgeSlot(target: ShareDefaultTarget?) {
    if (target == null) return
    ShareDefaultBadge(
        target = target,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 3.dp, y = 3.dp),
    )
}
