package com.slideindex.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 app 的基线配置（`app/src/main/baseline-prof.txt`）。
 *
 * 跑法（需要一台已授权 USB 调试的 arm64 真机）：
 * ```
 * gradlew :app:generateFullReleaseBaselineProfile --no-configuration-cache
 * ```
 * 产物会由插件自动写回 `app/src/fullRelease/generated/baselineProfiles/{baseline,startup}-prof.txt`
 * （插件按"变体 + 构建类型"分目录，不是 `src/main/`），随 fullRelease 打进 APK。
 * 必须带 `--no-configuration-cache`：AGP 的 connected 测试任务在 Gradle 9 的配置缓存下
 * 无法被序列化（`DeviceProviderInstrumentTestTask` 的 `testedApplicationId` 序列化失败）。
 *
 * 覆盖范围目前只有「冷启动 → 首页首帧」。面板/设置页的 journey 需要可稳定定位的
 * testTag（各面板都是悬浮窗，`By.text` 会随语言变），后续单独补。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = "com.slideindex.app",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            // 只等"本包已经画出界面"，不用文案：设备语言是英文时 `By.text("首页")` 永远等不到。
            device.wait(Until.hasObject(By.pkg("com.slideindex.app")), 5_000)
            device.waitForIdle()
        }
    }
}
