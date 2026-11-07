plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.slideindex.app.clipboard"
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
 * 剪贴板数据层（P1 阶段）。
 *
 * 目标：把「历史条目 / 内容解析 / 存储 / 图片缓存」从 `:app` 抽出来，
 * 让 `filebrowser` / `history` / `pickresult` 等面板直接依赖模块，而不是 `:app`。
 *
 * 当前只搬不依赖 `:app` 的纯数据与解析代码；特权写入 / 无障碍读取仍留在 `:app`，
 * 后续通过接口注入。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
