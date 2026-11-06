plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.baselineprofile) apply false
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.Test

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }

    // Robolectric 4.x 的 FileDescriptor 拦截器要反射访问 jdk.internal.access.SharedSecrets，
    // JDK 25 的模块封装默认不放行；不补这两个 --add-opens 的话，凡是走
    // ApplicationSharedMemory 初始化的用例（如 DragFlowSessionTest）都会在 beforeTest 阶段抛
    // "Failed to interact with raw FileDescriptor internals"。
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
    }
}

/**
 * 一键跑单测：`:app` 只跑 fullDebug，其余模块各跑自己的 debug 单测。
 *
 * 不用 `./gradlew test`：AGP 会为 app 的 8 个变体（2 flavor × debug/release/
 * nonMinifiedRelease/benchmarkRelease）各跑一遍同一套 1,300+ 用例。lite 与 full 的差异
 * 只有内置引擎 assets，其余变体跑的是同一份逻辑代码，重复跑只会把 CI 时间翻几倍。
 *
 * `:baselineprofile` 是 com.android.test 模块，没有 unit test 组件，排除。
 */
tasks.register("unitTests") {
    group = "verification"
    description = "Runs :app (fullDebug) and every library module's debug unit tests."
    dependsOn(":app:testFullDebugUnitTest")
    // 只挑"叶子模块"（:core:* / :feature:* / :vendor:ppocr-sdk）：`:vendor` 只是分组目录，
    // 没有 build 文件也没有 test 任务；`:app` 与 `:baselineprofile` 是宽度 1 的工程，
    // 前者单独指定、后者是 com.android.test 模块（没有 unit test 组件）。
    // 以后新增 `:core:*` / `:feature:*` 模块会自动纳入。
    subprojects
        .filter { it.path.count { ch -> ch == ':' } >= 2 }
        .forEach { module -> dependsOn("${module.path}:testDebugUnitTest") }
}
