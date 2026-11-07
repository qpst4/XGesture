package com.slideindex.app.perf

/**
 * 构建标记的接线点。
 *
 * 探针 / 诊断代码原先直接读 `:app` 的 `BuildConfig`，这让整个 `perf` / `diagnostic` 包无法下沉到模块。
 * 现在由 `SlideIndexApp` 启动时把 `BuildConfig` 的字段写进来；默认值按 release 语义。
 */
object PerfBuildFlags {

    @Volatile
    var debugBuild: Boolean = false

    @Volatile
    var appVersionName: String = ""

    @Volatile
    var appVersionCode: Int = 0
}
