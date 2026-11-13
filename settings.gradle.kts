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

        // Miuix 未发布快照（GitHub Packages，需要带 read:packages 的 token）。
        // 本地： $env:GITHUB_TOKEN = gh auth token
        // 或：   ~/.gradle/gradle.properties 里配 gpr.user / gpr.key
        val gprKey = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
        if (gprKey.isPresent) {
            maven {
                name = "GitHubPackagesMiuix"
                url = uri("https://maven.pkg.github.com/compose-miuix-ui/miuix")
                credentials {
                    // GitHub Packages 的 Maven 只校验 token，username 随便填非空值即可。
                    username = providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .getOrElse("x-access-token")
                    password = gprKey.get()
                }
            }
        }
    }
}

rootProject.name = "XGesture"
include(":app")
include(":baselineprofile")
include(":core:common")
include(":core:autofill")
include(":core:gesture")
include(":core:notification")
include(":core:monitoring")
include(":core:clipboard")
include(":core:stash")
include(":core:search")
include(":core:overlay-layout")
include(":core:overlay-host")
include(":core:design")
include(":core:shortcuts")
include(":feature:settings")
include(":feature:overlay-panels")
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
