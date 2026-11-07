package com.slideindex.app.perf

/**
 * 构建标记的接线点。
 *
 * 探针代码原先直接读 `:app` 的 `BuildConfig.DEBUG`，这让整个 `perf` 包无法下沉到模块。
 * 现在由 `SlideIndexApp` 启动时把 `BuildConfig.DEBUG` 写进来；默认 false（release 语义）。
 */
object PerfBuildFlags {

    @Volatile
    var debugBuild: Boolean = false
}
