package com.slideindex.app.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动耗时"尺子"。原 `:macrobenchmark` 模块只有这一个文件，接线基线配置时并入本模块，
 * 省掉一个永远不编译的模块；同一台真机、同一次 `connectedCheck` 里既出 profile 又出数字。
 *
 * 跑法：`gradlew :baselineprofile:connectedFullBenchmarkReleaseAndroidTest`（需要真机）
 *
 * `startupBaselineProfile` 读的是设备上已安装的 profile（`Partial` 编译模式），
 * 因此要对比优化效果时，先装一次带 `baseline-prof.txt` 的包再跑。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = "com.slideindex.app",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
