plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.slideindex.app.stash"
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
 * 暂存（stash）数据层。
 *
 * `history` 面板的列表/标签/提醒都读它，之前被 `:app` 的 `stash` 包挡住。
 * 这里先搬数据与仓储；与浮窗/服务耦合的 `StashCoordinator`、通知与提醒广播仍留在 `:app`。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:clipboard"))
    implementation(libs.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
