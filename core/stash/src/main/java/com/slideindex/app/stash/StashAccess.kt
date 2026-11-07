package com.slideindex.app.stash

/**
 * 浮层进程访问数据层的静态入口。
 *
 * 这些仓库都是 Hilt `@Singleton`，但浮层（无障碍服务进程）没有稳定的注入点，
 * 所以沿用既有做法：仓库在 `init` 里把自己挂到这里。
 */
object StashAccess {
    @Volatile
    var repository: StashRepository? = null

    /** 标签定义 / 标签绑定 / 完成态 / 追加内容（独立于 `index.json`，见 `StashMetaRepository`）。 */
    @Volatile
    var metaRepository: StashMetaRepository? = null
}
