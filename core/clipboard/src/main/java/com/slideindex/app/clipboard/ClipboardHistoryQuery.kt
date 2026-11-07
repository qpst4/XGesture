package com.slideindex.app.clipboard

import kotlinx.coroutines.flow.StateFlow

/**
 * 剪贴板历史的只读查询接口（P1）。
 *
 * 面板（`history` / `filebrowser` / `pickresult`）只依赖这个接口，
 * 不再直接摸 `:app` 的 `ClipboardHistoryRepository`。
 *
 * 写入、特权读取、截图监听仍留在 `:app`。
 */
interface ClipboardHistoryQuery {

    /** 历史总条数（会随写入变化）。 */
    val entryCount: StateFlow<Int>

    /** 变更版本号，可用于刷新缓存。 */
    val revision: StateFlow<Long>

    /** 按 keyset 分页读取；[createdBeforeMs] 为上一页的 [ClipboardHistoryPage.nextCursor]。 */
    suspend fun loadPage(
        createdBeforeMs: Long? = null,
        limit: Int,
        filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All,
    ): ClipboardHistoryPage

    /** 统计满足过滤条件的条数。 */
    suspend fun count(filter: ClipboardHistoryFilter = ClipboardHistoryFilter.All): Int

    /** 全文搜索。 */
    suspend fun search(query: String, limit: Int = 50): List<ClipboardEntry>

    /** 追平最新一条（`:app` 侧可能有外部写入需要同步；默认无操作）。 */
    suspend fun catchUpLatest(
        triggerContext: android.content.Context? = null,
        skipWhenListening: Boolean = false,
    ) = Unit

    /** 删除一条（`:app` 负责真正落库）。 */
    suspend fun delete(id: String) = Unit

    /** 最近一条（用于面板首屏）。 */
    suspend fun peekLatest(): ClipboardEntry?
}

/**
 * 模块内的服务定位点：`:app` 在启动时注入实现，面板侧读取。
 *
 * 未注入时所有读取返回空数据（降级，不崩）。
 */
object ClipboardService {

    @Volatile
    var query: ClipboardHistoryQuery? = null
}

/**
 * 前台包名解析：`:app` 注入（依赖无障碍服务），模块内不直接引用。
 */
object ClipboardForeground {

    @Volatile
    var resolvePackage: ((android.content.Context) -> String?)? = null
}