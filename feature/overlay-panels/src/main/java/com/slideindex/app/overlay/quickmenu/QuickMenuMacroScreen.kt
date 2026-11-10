package com.slideindex.app.overlay.quickmenu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.macro.Macro
import com.slideindex.app.overlay.panels.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "QuickMenuMacro"

/**
 * 快捷菜单里的「运行宏」页：列出现有宏，点一条就跑到它的目标 App。
 *
 * 每行显示**宏目标 App 的图标**（走面板统一的 `QuickLauncherIconResolver`），
 * 拿不到图标（占位宏 / 目标已卸载）时回退播放键；图标在 IO 线程解码。
 */
@Composable
internal fun QuickMenuMacroScreen(onRan: () -> Unit) {
    val context = LocalContext.current
    // 组合期同步读取：prefs 已在进程内缓存（开销极小），这样首帧就有数据，
    // 不会出现"先显示空态、下一帧才出现列表"。
    val macros = remember(context) {
        runCatching { com.slideindex.app.macro.MacroStore.all(context) }.getOrDefault(emptyList())
    }

    if (macros.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.quick_menu_macro_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(macros, key = { it.id }) { macro ->
            QuickMenuMacroRow(
                macro = macro,
                onClick = {
                    // 跑起来后把快捷菜单收起来（之前跑完菜单还挂在屏幕上）
                    val started =
                        com.slideindex.app.overlay.quickmenu.QuickMenuHostAccess.host
                            ?.runMacro(context, macro.id) == true
                    if (started) onRan() else onRan()
                },
            )
        }
    }
}

@Composable
private fun QuickMenuMacroRow(
    macro: Macro,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val revision = com.slideindex.app.macro.MacroStore.revision
    val icon by produceState<ImageBitmap?>(
        initialValue = null,
        macro.id,
        revision,
    ) {
        value = withContext(Dispatchers.IO) {
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.quickLauncherIconBitmap
                ?.invoke(
                    QuickLauncherItem.action(GestureAction.RunMacro(macro.id)),
                    emptyMap(),
                    96,
                    context,
                    emptyList(),
                    emptyList(),
                )
                ?.asImageBitmap()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val loadedIcon = icon
        if (loadedIcon != null) {
            Image(
                bitmap = loadedIcon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = macro.name.ifBlank { Macro.FALLBACK_NAME },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
