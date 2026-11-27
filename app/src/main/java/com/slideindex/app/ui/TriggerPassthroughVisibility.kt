package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusProbe
import com.slideindex.app.xposed.bridge.ModuleStatusFields

/** 「边缘透传」开关这一行该不该出现 / 能不能点。 */
internal enum class PassthroughRowVisibility {
    /** 不渲染这一行（模块明确未生效，且当前也没在用透传）。 */
    Hidden,

    /** 正常可切换。 */
    Enabled,

    /** 置灰（模块状态还在检测，先别让用户点）。 */
    Disabled,
}

/**
 * 按模块状态决定「边缘透传」开关的可见性。
 *
 * - 已经开着透传：永远显示且可关，避免把人锁死在透传态；
 * - 模块明确未生效：关着就隐藏（日常用户看不到），开着则显示（可关）；
 * - 状态未知：置灰，等探针返回，避免读取失败时入口凭空消失。
 */
internal fun triggerPassthroughVisibility(
    moduleReady: Boolean?,
    passthrough: Boolean,
): PassthroughRowVisibility = when {
    passthrough -> PassthroughRowVisibility.Enabled
    moduleReady == true -> PassthroughRowVisibility.Enabled
    moduleReady == false -> PassthroughRowVisibility.Hidden
    else -> PassthroughRowVisibility.Disabled
}

/**
 * 探测 LSPosed 模块是否可用（仅在 [enabled] 为 true 时真的发探测）。
 *
 * 返回 null = 还在检测。判定条件：模块有响应（Ready / Armed / SwitchedOff），
 * 且回传的模块代码版本与当前 APK 一致（否则要热重载/重启才生效）。
 */
@Composable
internal fun rememberPassthroughModuleReady(enabled: Boolean): Boolean? {
    val context = LocalContext.current
    var ready by remember { mutableStateOf<Boolean?>(if (enabled) null else true) }
    LaunchedEffect(enabled, context) {
        if (!enabled) {
            ready = true
            return@LaunchedEffect
        }
        ready = null
        ModuleBridgeStatusProbe.probe(context) { status, detail ->
            val codeCurrent =
                ModuleStatusFields.codeStateOfDetail(context, detail) == ModuleStatusFields.CodeState.Current
            ready = status != ModuleBridgeStatusProbe.Status.NotReady && codeCurrent
        }
    }
    return ready
}
