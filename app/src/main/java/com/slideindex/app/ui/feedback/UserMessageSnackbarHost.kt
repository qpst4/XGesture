package com.slideindex.app.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/**
 * 只做 `userMessageBus` → [SnackbarHostState] 的桥接，不负责渲染。
 *
 * 拆出来的原因：渲染位置现在有两个宿主——pager 样式走 `Scaffold(snackbarHost = …)`（偏移由 miuix
 * 按底栏实测高度算），CLASSIC / 侧栏仍放在宿主 Box 里手算 padding。桥接只能有一份，否则同一条
 * 消息会被收集两次、连弹两下。
 */
@Composable
fun UserMessageBusCollector(
    userMessageBus: UserMessageBus,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(userMessageBus) {
        userMessageBus.messages.collect { message ->
            val text = when (message) {
                is UserMessage.Error -> message.text
                is UserMessage.Success -> message.text
            }
            snackbarHostState.showSnackbar(text)
        }
    }
}

@Composable
fun UserMessageSnackbarHost(
    userMessageBus: UserMessageBus,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    UserMessageBusCollector(
        userMessageBus = userMessageBus,
        snackbarHostState = snackbarHostState,
    )
    SnackbarHost(
        state = snackbarHostState,
        modifier = modifier,
    )
}
