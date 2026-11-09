plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.slideindex.app.overlay.panels"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("boolean", "IME_DIAG", "false")
        buildConfigField("String", "APPLICATION_ID", "\"com.slideindex.app\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(project(":core:shortcuts"))
    implementation(project(":core:clipboard"))
    implementation(project(":core:search"))
    implementation(project(":core:translate"))
    implementation(project(":core:notification"))
    implementation(libs.reorderable)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(project(":core:stash"))
    implementation(project(":core:ocr"))
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.savedstate)
    implementation(project(":core:overlay-host"))
    implementation(project(":core:overlay-layout"))
    implementation(project(":core:design"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:apps"))

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3.pinned)
    implementation(libs.compose.material.icons)
    implementation(libs.dynamicanimation)
    implementation(libs.androidx.palette)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.zxing.core)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.materialkolor)
}
