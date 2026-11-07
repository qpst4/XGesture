plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.overlay.panels"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

/**
 * 面板迁移的第一个真实组（阶段 B2：`backpanel`）。
 *
 * 依赖取舍：
 * - 面板需要 `AppSettings` 与设置扩展函数 → 直接依赖 `:feature:settings`（feature→feature，
 *   先换出 `:app`，以后再谈把设置模型抽 `:core:model`）；
 * - 主题配色走 `:core:design` 的 `AppThemeSpec` 版本；
 * - 窗口/Compose 宿主与尺寸工具走 `:core:overlay-host`。
 *
 * 包名保持 `com.slideindex.app.overlay.backpanel`（split package），调用方 import 不变。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:gesture"))
    implementation(project(":core:overlay-host"))
    implementation(project(":core:design"))
    implementation(project(":feature:settings"))

    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3.pinned)
    implementation(libs.dynamicanimation)
    implementation(libs.miuix.ui)
    implementation(libs.materialkolor)
}
