plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.slideindex.app.baselineprofile"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // 必须与 :app 的 flavor 维度同名同值，否则变体匹配不上，generate*BaselineProfile 任务不会出现。
    flavorDimensions += "bundle"
    productFlavors {
        create("full") {
            dimension = "bundle"
        }
        create("lite") {
            dimension = "bundle"
        }
    }

    // benchmark / nonMinifiedRelease 变体由 androidx.baselineprofile 插件自动创建（两侧同名），
    // 这里只声明与 :app 对齐的 flavor 维度。

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

baselineProfile {
    // 生成/测量必须跑在真机上：本仓库引擎包只带 arm64-v8a，模拟器上 OCR/翻译不可用，
    // 且 journey 依赖真实的无障碍服务与悬浮窗权限。
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
