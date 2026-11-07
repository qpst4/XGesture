plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.slideindex.app.settings"
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

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:design"))
    implementation(project(":core:cloud-storage"))
    implementation(project(":core:gesture"))
    implementation(project(":core:notification"))
    implementation(project(":core:overlay-layout"))
    implementation(project(":core:translate"))
    implementation(libs.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)

    // 主题层（ModuleTheme / OverlayAwareModuleTheme / Miuix 毛玻璃）——
    // 面板模块都依赖 :feature:settings，把主题放这里面板才能用。
    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3.pinned)
    implementation(libs.compose.material.icons)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.material)
    // 与 :app 对齐 appcompat 版本（material 会拉 1.7.0，本地缓存里只有 1.8.0）。
    implementation(libs.appcompat)

    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
