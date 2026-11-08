package com.slideindex.app.clipboardfloat

import android.content.Context
import com.slideindex.app.clipboard.ClipboardEntry
import kotlinx.coroutines.flow.Flow

/**
 * 剪贴板浮窗的数据源端口。
 *
 * 历史仓库（`ClipboardHistoryRepository`）是 `:app` 的 dagger 单例，模块侧只消费这 3 个入口。
 */
interface ClipboardFloatHistorySource {
    /** 历史变更版本号。 */
    val revision: Flow<Long>

    /** 取最新一页（新的在前）。 */
    suspend fun loadPage(limit: Int): List<ClipboardEntry>

    /** 搜索历史。 */
    suspend fun search(query: String): List<ClipboardEntry>
}

/** 模块内服务定位点：`:app` 启动时注入。 */
object ClipboardFloatHistoryAccess {

    @Volatile
    var source: ((Context) -> ClipboardFloatHistorySource?)? = null
}
