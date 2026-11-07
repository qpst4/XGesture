package com.slideindex.app.overlay

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 取词结果出口。
 *
 * 取词链路目前只有 `onResult` 回调这一种形态（[FloatBallTextPickCoordinator]），而且成功与否
 * 只体现为"面板弹出来了"，外部无法订阅。所有结果最终都会汇总到
 * [FloatBallPickResultPanel.showResult]，所以在这里挂一个可订阅的出口，
 * 比在每条取词链路（悬浮球拖动、边缘区域取词、悬浮指针悬停取词、分享图 OCR…）各改一处更稳。
 *
 * 订阅方：新手引导要判断"用户自己成功取到词了"。
 * 注意：空结果（没取到文字/只有截图）也会从这里经过，订阅方需自行按 `result.text` 过滤。
 */
object FloatBallPickEvents {
    private val mutableResults = MutableSharedFlow<FloatBallPickResult>(extraBufferCapacity = 8)

    val results: SharedFlow<FloatBallPickResult> = mutableResults.asSharedFlow()

    fun publish(result: FloatBallPickResult) {
        mutableResults.tryEmit(result)
    }
}
