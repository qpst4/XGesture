plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.shortcuts"
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
 * 快捷方式 / 活动快捷方式的共用层。
 *
 * 为什么单独一个模块：这些工具既需要 `:core:gesture`（`GestureAction` / `QuickLauncherItem`），
 * 又需要 `:core:common`（`ActivityShortcut` / `ShortcutKind`）——而 `:core:gesture` 已经依赖
 * `:core:common`，放不进 `:core:common`（会成环）。
 *
 * 面板（`:feature:overlay-panels`）与 `:app` 都依赖它。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:gesture"))
    implementation(libs.core.ktx)
}
