plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.overlay.host"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

/**
 * 浮层宿主的基础设施（第一刀）。
 *
 * 这里只放"与业务无关、只跟 WindowManager / View 打交道"的东西：
 * 租约兜底、窗口类型、面板根布局、系统手势排除。它们以前埋在 `:app` 的 overlay 包里，
 * 想单独测一下租约逻辑得拖起整个 App。
 *
 * 包名保持 `com.slideindex.app.overlay` 不变（split package，与 `:core:common` 里的
 * PanelSide 同款做法），这样调用方一行 import 都不用改。
 */
dependencies {
    implementation(project(":core:common"))
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
