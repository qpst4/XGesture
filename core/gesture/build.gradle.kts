plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.gesture"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
