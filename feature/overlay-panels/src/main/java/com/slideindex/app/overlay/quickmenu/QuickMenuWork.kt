package com.slideindex.app.overlay.quickmenu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 与 Compose 组合**无关**的作用域。
 *
 * 起因是一个真机 bug：选图/截屏在外部 Activity 返回后"没反应"。根因是收尾工作（解码、写历史）
 * 跑在 `rememberCoroutineScope()` 上——那是**组合作用域**，而拉起外部 Activity 时窗口可能被系统
 * 摘掉、组合随之销毁，scope 一取消，后续工作就永远不执行了。
 *
 * 所以凡是"要跨越外部 Activity 活下来"的工作都必须走这里。它用 [SupervisorJob]，一个失败不会
 * 拖死其它任务。
 */
object QuickMenuWork {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
