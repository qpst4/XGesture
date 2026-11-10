package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.macro.MacroStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 动作图标：**优先全局覆盖**（动作类型 / 应用包名），否则回退到动作自带的图标。
 *
 * 全局覆盖可能是字体图标（Lucide/Remix/Tabler）或图片，所以不能简单返回 `ImageVector`，
 * 这里统一用 [ActionIconView] 渲染；没有覆盖时才画默认的矢量图标。
 */
@Composable
fun ActionIconSlot(
    action: GestureAction,
    sizeDp: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 读 revision：图标设置后触发重组（否则 remember 会缓存旧值）
    val revision = GlobalIconStore.revision
    val override = remember(action, revision) {
        GlobalIconStore.actionIcon(context, action.type.name)
            ?: (action as? GestureAction.LaunchApp)?.let {
                GlobalIconStore.appIcon(context, it.packageName)
            }
    }
    // 「运行宏」没有自己的图标，用宏目标 App 的图标（与宏列表那一行一致）。
    // 模板动作（macroId 为空）拿不到目标，保持通用播放键。
    val macroRevision = MacroStore.revision
    val macroId = (action as? GestureAction.RunMacro)?.macroId.orEmpty()
    var macroIcon by remember(action, macroRevision) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(macroId, macroRevision, revision, override) {
        macroIcon = if (override != null || macroId.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                com.slideindex.app.macro.MacroTargetIconResolver
                    .bitmap(context, macroId, 96)
                    ?.asImageBitmap()
            }
        }
    }

    val loadedMacroIcon = macroIcon
    if (override != null) {
        ActionIconView(action = action, iconKey = override, sizeDp = sizeDp, modifier = modifier, showBackground = false)
    } else if (loadedMacroIcon != null) {
        Image(
            bitmap = loadedMacroIcon,
            contentDescription = null,
            modifier = modifier.size(sizeDp.dp),
        )
    } else {
        Box(
            modifier = modifier.size(sizeDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = gestureActionIcon(action, outlined = true),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(sizeDp.dp),
            )
        }
    }
}
