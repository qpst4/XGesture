plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.slideindex.app.search"
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
 * 搜索的纯数据/索引层。
 *
 * 先搬"不碰 overlay / service / privilege"的部分：图片搜索、文件索引、计算器、
 * 搜索历史、系统设置索引等。启动器（`SearchEngineLauncher` 等）与快捷方式索引仍留 `:app`。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:settings"))
    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
