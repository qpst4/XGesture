plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.slideindex.app.design"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
    }
}

/**
 * 设计层种子（阶段 A1）。
 *
 * 这里只放"配色/主题这种与业务无关、面板和设置页都要用"的东西。它是面板迁移的前置依赖：
 * `:app` 的 overlay 面板要算主题色，如果这段逻辑留在 `:app` 里，面板永远搬不出去。
 *
 * 包名保持 `com.slideindex.app.ui.miuix.theme`（split package），调用方 import 不变。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:gesture"))
    implementation(libs.core.ktx)

    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3.pinned)
    implementation(libs.compose.material.icons)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.materialkolor)
}
