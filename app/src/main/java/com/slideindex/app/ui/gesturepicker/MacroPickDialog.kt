package com.slideindex.app.ui.gesturepicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.macro.Macro
import com.slideindex.app.macro.MacroStore
import com.slideindex.app.macro.MacroTargetIconResolver
import com.slideindex.app.overlay.CloneBadgeOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 悬浮面板（快速启动器 / 圆环启动器 / 蜂窝）里的宏选择层。
 *
 * ⚠️ 这些面板自己就是**悬浮窗**（window type 2038），**不能**在里面弹平台 Dialog
 * （`WindowDialog`/`Dialog` 都是 type 2，系统会抛 "Window type mismatch" 直接闪退）。
 * 所以这里改成"画在同一个界面里"的覆盖层。
 */
@Composable
fun MacroPickOverlay(
    onDismissRequest: () -> Unit,
    onPick: (Macro) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前已绑定（或正在编辑）的宏 id：列表里给它上色，换宏时不用自己找。 */
    selectedMacroId: String? = null,
) {
    val context = LocalContext.current
    val macros = remember { MacroStore.all(context) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismissRequest),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.macro_action_run_macro),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (macros.isEmpty()) {
                Text(
                    text = stringResource(R.string.macro_pick_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    macros.forEach { macro ->
                        MacroPickerRow(
                            macro = macro,
                            selected = macro.id == selectedMacroId,
                            showSummary = false,
                            onClick = { onPick(macro) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「运行宏」需要先选一条宏：这是动作选择器里的二级选择弹窗。
 *
 * 只读一次存储（打开时读），选中后把整条宏回调出去 —— 绑定只保存宏 id（见 `GestureAction.RunMacro`），
 * 所以宏之后改名/改步骤都不需要重新绑定。
 *
 * ⚠️ 只能在**普通 Activity 窗口**里用；悬浮面板里请用 [MacroPickOverlay]。
 */
@Composable
fun MacroPickDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onPick: (Macro) -> Unit,
    /** 当前已绑定（或正在编辑）的宏 id：列表里给它上色，换宏时不用自己找。 */
    selectedMacroId: String? = null,
) {
    if (!show) return
    val context = LocalContext.current
    val macros = remember(show) { MacroStore.all(context) }

    WindowDialog(
        show = true,
        title = stringResource(R.string.macro_action_run_macro),
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            if (macros.isEmpty()) {
                Text(
                    text = stringResource(R.string.macro_pick_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    macros.forEach { macro ->
                        MacroPickerRow(
                            macro = macro,
                            selected = macro.id == selectedMacroId,
                            showSummary = true,
                            onClick = { onPick(macro) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                minHeight = 44.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** 一行宏选择项的数据：目标 App 图标 / 名称 / 是否分身。 */
private data class MacroPickerVisual(
    val icon: ImageBitmap? = null,
    val targetLabel: String? = null,
    val isClone: Boolean = false,
)

/**
 * 宏选择列表的一行：目标 App 图标 + 宏名（+ 目标 App 名 · 动作数）。
 *
 * 图标来自 [MacroTargetIconResolver]（全局图标覆盖 → 系统图标，按宏表代次缓存），
 * 解码在 IO 线程；拿不到图标（占位宏 / 目标已卸载）时回退通用播放键，不留空白。
 * 这里**不能**用任何依赖 `LocalAppDependencies` 的可组合：本列表同时用于浮层合成。
 */
@Composable
private fun MacroPickerRow(
    macro: Macro,
    selected: Boolean,
    showSummary: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val displayName = macro.name.ifBlank { Macro.FALLBACK_NAME }
    val visual by produceState(
        initialValue = MacroPickerVisual(),
        macro.id,
        macro.targetPackage,
        MacroStore.revision,
    ) {
        value = withContext(Dispatchers.IO) {
            val target = AppTarget.parse(macro.targetPackage)
            val packageName = target.packageName.takeIf { it.isNotBlank() }
                ?: return@withContext MacroPickerVisual()
            MacroPickerVisual(
                icon = MacroTargetIconResolver.bitmap(context, macro.id, 96)?.asImageBitmap(),
                targetLabel = runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrNull()?.takeIf { it.isNotBlank() },
                isClone = target.isClone,
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            val icon = visual.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (visual.isClone) {
                CloneBadgeOverlay(iconSize = 28.dp, circular = false)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (showSummary) {
                Text(
                    text = stringResource(
                        R.string.macro_row_summary,
                        visual.targetLabel ?: displayName,
                        macro.actionCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
