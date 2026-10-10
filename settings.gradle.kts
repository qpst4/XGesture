pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "XGesture"
include(":app")
include(":core:common")
include(":core:autofill")
include(":core:gesture")
include(":core:notification")
include(":core:monitoring")
include(":core:overlay-layout")
include(":feature:settings")
include(":feature:otp")
include(":feature:notification")
include(":feature:apps")
include(":feature:shake")
include(":feature:message")
include(":vendor:ppocr-sdk")
include(":core:ocr")
include(":core:translate")
include(":core:native-engine")
include(":core:cloud-storage")
