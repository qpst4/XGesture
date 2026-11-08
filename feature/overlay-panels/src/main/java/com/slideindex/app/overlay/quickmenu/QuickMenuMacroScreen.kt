package com.slideindex.app.overlay.quickmenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.gesture.GestureAction

private const val TAG = "QuickMenuMacro"

/**
 * 快捷菜单里的「运行宏」页：列出现有宏，点一条就跑到它的目标 App。
 *
 * 只取 id 与名字（不引入宏的数据类型），保持与宏模块的耦合最小。
 */
@Composable
internal fun QuickMenuMacroScreen(onRan: () -> Unit) {
    val context = LocalContext.current
    // 组合期同步读取：prefs 已在进程内缓存（开销极小），这样首帧就有数据，
    // 不会出现"先显示空态、下一帧才出现列表"。
    val macroList = remember(context) {
        runCatching {
            com.slideindex.app.macro.MacroStore.all(context).map { it.id to it.name }
        }.getOrDefault(emptyList())
    }

    if (macroList.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.quick_menu_macro_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(macroList, key = { it.first }) { item ->
            val macroId = item.first
            val macroName = item.second
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 跑起来后把快捷菜单收起来（之前跑完菜单还挂在屏幕上）
                        val started = com.slideindex.app.overlay.quickmenu.QuickMenuHostAccess.host?.runMacro(context, macroId) == true
                        if (started) onRan() else onRan()
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(text = macroName, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
