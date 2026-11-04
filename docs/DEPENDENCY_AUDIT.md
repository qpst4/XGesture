# XGesture 全量依赖审计 (2026-09-21)

> 范围：Gradle 全模块解析依赖 + 版本目录 + 插件/工具链 + GitHub Actions + 原生引擎包。
> 传递依赖来自 `lite/fullReleaseRuntimeClasspath` 等可解析配置的 **实际解析结果**。

## 1. 构建工具链

| 组件 | 当前 | 说明 |
|------|------|------|
| Gradle Wrapper | 9.7.1 | `gradle-wrapper.properties` |
| AGP | 9.4.1 | `[versions].agp` |
| Kotlin | 2.4.20 | `[versions].kotlin` |
| KSP | 2.3.12 | `[versions].ksp` |
| Hilt | 2.60.1 | `[versions].hilt` |
| foojay-resolver | 1.0.0 | `settings.gradle.kts` |
| compileSdk / targetSdk | 37.1 / 37 | `app/build.gradle.kts` |
| NDK 默认 | 29.0.13599879 | `app/build.gradle.kts` |
| minSdk | 31 | `[versions].minSdk` |

## 2. Gradle 模块（settings 已 include）

- `:app`
- `:core:common`
- `:core:autofill`
- `:core:gesture`
- `:core:notification`
- `:core:monitoring`
- `:core:overlay-layout`
- `:feature:settings`
- `:feature:otp`
- `:feature:notification`
- `:feature:apps`
- `:feature:shake`
- `:feature:message`
- `:vendor:ppocr-sdk`
- `:core:ocr`
- `:core:translate`
- `:core:native-engine`

未 include 但存在目录：`baselineprofile`、`macrobenchmark`

## 3. GitHub Actions

| Action | Workflow 引用 | Latest Release |
|--------|---------------|----------------|
| `actions/checkout` | `v7` | v7.0.1 |
| `actions/setup-java` | `v6` | v6.0.1 |
| `gradle/actions/setup-gradle` | `v6` | ? |
| `actions/upload-artifact` | `v7` | v7.0.1 |
| `actions/download-artifact` | `v8` | v8.0.1 |
| `actions/setup-python` | `v7` | v7.0.0 |
| `softprops/action-gh-release` | `v3` | v3.0.3 |

CI 环境：`java-version: 25`，`python-version: 3.14.7`（release 工作流）。

## 4. 原生引擎包（`native_engine_packs.json`）

- catalog version: **3**
- **ocr-engine** revision=5 url=`https://github.com/qpst4/XGesture/releases/download/v1.9.23/ocr-engine-arm64-v5.zip`
- **translate-engine** revision=- url=`https://github.com/qpst4/XGesture/releases/download/v1.6.0/translate-engine-arm64-v1.zip`

## 5. Maven 坐标全量（直接 + 传递，按 group:artifact 去重）

| group:artifact | 解析到的版本 | 目录声明 | 状态 | Maven release | Maven 最新稳定 | 出现于 |
|----------------|-------------|----------|------|---------------|----------------|--------|
| `androidx.activity:activity` | 1.12.2, 1.13.0, 1.5.1, 1.6.0, 1.8.0 | - | 落后稳定版 1.13.0 | 1.14.0-alpha02 | 1.13.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.activity:activity-compose` | 1.13.0, 1.7.0, 1.8.0, 1.8.2 | activity-compose | 已对齐稳定版 | 1.14.0-alpha02 | 1.13.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.activity:activity-ktx` | 1.13.0, 1.7.0, 1.7.1 | - | 已对齐稳定版 | 1.14.0-alpha02 | 1.13.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.annotation:annotation` | 1.0.0, 1.1.0, 1.10.0, 1.2.0, 1.3.0, 1.7.0, 1.7.1, 1.8.0, 1.8.1, 1.9.1 | - | 落后稳定版 1.10.0 | 1.11.0-rc01 | 1.10.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.annotation:annotation-experimental` | 1.0.0, 1.3.0, 1.3.1, 1.4.1, 1.5.1 | - | 落后稳定版 1.6.0 | 1.6.0 | 1.6.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.annotation:annotation-jvm` | 1.10.0 | - | 已对齐稳定版 | 1.11.0-rc01 | 1.10.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.appcompat:appcompat` | 1.2.0, 1.6.1, 1.7.0, 1.8.0 | appcompat | 落后稳定版 1.8.0 | 1.8.0 | 1.8.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.appcompat:appcompat-resources` | 1.6.1, 1.8.0 | - | 落后稳定版 1.8.0 | 1.8.0 | 1.8.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.arch.core:core-common` | 2.1.0, 2.2.0 | - | 落后稳定版 2.2.0 | 2.2.0 | 2.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.arch.core:core-runtime` | 2.1.0, 2.2.0 | - | 落后稳定版 2.2.0 | 2.2.0 | 2.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.autofill:autofill` | 1.0.0 | - | 落后稳定版 1.3.0 | 1.3.0 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.benchmark:benchmark-macro-junit4` | 1.5.0 | androidx-benchmark-macro-junit4 | 已对齐稳定版 | 1.5.0 | 1.5.0 | 仅版本目录 |
| `androidx.cardview:cardview` | 1.0.0 | - | 已对齐稳定版 | 1.0.0 | 1.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.collection:collection` | 1.0.0, 1.1.0, 1.4.2, 1.4.5, 1.5.0, 1.6.0 | - | 落后稳定版 1.6.0 | 1.7.0-alpha01 | 1.6.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.collection:collection-jvm` | 1.4.2, 1.6.0 | - | 落后稳定版 1.6.0 | 1.7.0-alpha01 | 1.6.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.collection:collection-ktx` | 1.4.2, 1.6.0 | - | 落后稳定版 1.6.0 | 1.7.0-alpha01 | 1.6.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.animation:animation` | 1.10.0, 1.11.4, 1.12.0, 1.13.0-alpha03, 1.7.1, 1.7.2 | compose-animation | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.animation:animation-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.animation:animation-core` | 1.12.0, 1.13.0-alpha03, 1.2.1, 1.7.2 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.animation:animation-core-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.foundation:foundation` | 1.10.0, 1.11.4, 1.12.0, 1.13.0-alpha01, 1.13.0-alpha03, 1.7.0, 1.7.1 | compose-foundation | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.foundation:foundation-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.foundation:foundation-layout` | 1.10.0, 1.12.0, 1.13.0-alpha03, 1.7.2 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.foundation:foundation-layout-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3.adaptive:adaptive` | 1.2.0, 1.4.0-alpha02 | compose-material3-adaptive | 落后稳定版 1.3.0 | 1.4.0-alpha02 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3.adaptive:adaptive-android` | 1.4.0-alpha02 | - | 已对齐稳定版 | 1.4.0-alpha02 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3` | 1.3.1, 1.4.0, 1.5.0-alpha28 | compose-material3 | 落后稳定版 1.4.0 | 1.5.0-alpha28 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | 1.5.0-alpha28 | compose-material3-adaptive-navigation-suite | 已对齐稳定版 | 1.5.0-alpha28 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3-adaptive-navigation-suite-android` | 1.5.0-alpha28 | - | 已对齐稳定版 | 1.5.0-alpha28 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3-android` | 1.5.0-alpha28 | - | 已对齐稳定版 | 1.5.0-alpha28 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3-ripple` | 1.5.0-alpha28 | - | 已对齐稳定版 | 1.5.0-alpha28 | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3-ripple-android` | 1.5.0-alpha28 | - | 已对齐稳定版 | 1.5.0-alpha28 | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3-window-size-class` | 1.5.0-alpha22, 1.5.0-alpha28 | - | 有预发布更新 1.5.0-alpha28 | 1.5.0-alpha28 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material3:material3-window-size-class-android` | 1.5.0-alpha28 | - | 已对齐稳定版 | 1.5.0-alpha28 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material:material-icons-core` | 1.7.8 | - | 已对齐稳定版 | 1.7.8 | 1.7.8 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material:material-icons-core-android` | 1.7.8 | - | 已对齐稳定版 | 1.7.8 | 1.7.8 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material:material-icons-extended` | 1.7.8 | compose-material-icons | 已对齐稳定版 | 1.7.8 | 1.7.8 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material:material-icons-extended-android` | 1.7.8 | - | 已对齐稳定版 | 1.7.8 | 1.7.8 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material:material-ripple` | 1.12.0, 1.12.0-beta01, 1.13.0-alpha03 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.material:material-ripple-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime` | 1.11.0, 1.11.2, 1.11.4, 1.12.0, 1.13.0-alpha03, 1.7.0, 1.7.2, 1.8.1, 1.8.2 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime-annotation` | 1.13.0-alpha03, 1.9.0 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime-annotation-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime-retain` | 1.12.0, 1.13.0-alpha03 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime-retain-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime-saveable` | 1.11.0, 1.12.0, 1.13.0-alpha03, 1.7.0, 1.7.2 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.runtime:runtime-saveable-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui` | 1.0.1, 1.10.0, 1.11.0, 1.11.2, 1.12.0, 1.13.0-alpha03, 1.6.0, 1.7.0, 1.7.2, 1.8.2 | compose-ui | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-geometry` | 1.10.0, 1.12.0, 1.13.0-alpha03 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-geometry-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-graphics` | 1.10.0, 1.12.0, 1.13.0-alpha03 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-graphics-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-test-junit4` | BOM | compose-ui-test-junit4 | BOM/无版本 | 1.13.0-alpha03 | 1.12.1 | 仅版本目录 |
| `androidx.compose.ui:ui-text` | 1.10.0, 1.12.0, 1.13.0-alpha03 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-text-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-tooling` | 1.13.0-alpha03 | compose-ui-tooling | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath) |
| `androidx.compose.ui:ui-tooling-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath) |
| `androidx.compose.ui:ui-tooling-data` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath) |
| `androidx.compose.ui:ui-tooling-data-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath) |
| `androidx.compose.ui:ui-tooling-preview` | 1.13.0-alpha03 | compose-ui-tooling-preview | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-tooling-preview-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-unit` | 1.10.0, 1.12.0, 1.13.0-alpha03, 1.8.1 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-unit-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-util` | 1.10.0, 1.11.4, 1.12.0, 1.13.0-alpha03, 1.7.1, 1.8.1 | - | 落后稳定版 1.12.1 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose.ui:ui-util-android` | 1.13.0-alpha03 | - | 已对齐稳定版 | 1.13.0-alpha03 | 1.12.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.compose:compose-bom-alpha` | 2026.09.00 | compose-bom-alpha | 已对齐稳定版 | 2026.09.00 | 2026.09.00 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.concurrent:concurrent-futures` | 1.0.0, 1.1.0 | - | 落后稳定版 1.3.0 | 1.4.0-alpha01 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.concurrent:concurrent-futures-ktx` | 1.1.0 | - | 落后稳定版 1.3.0 | 1.4.0-alpha01 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.constraintlayout:constraintlayout` | 2.2.1 | - | 落后稳定版 2.2.2 | 2.2.2 | 2.2.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.constraintlayout:constraintlayout-core` | 1.1.1 | - | 落后稳定版 1.1.2 | 1.1.2 | 1.1.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.coordinatorlayout:coordinatorlayout` | 1.1.0 | - | 落后稳定版 1.3.0 | 1.3.0 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.core:core` | 1.0.0, 1.1.0, 1.12.0, 1.13.0, 1.13.1, 1.16.0, 1.19.0, 1.2.0, 1.3.0, 1.3.2, 1.6.0, 1.7.0, 1.8.0, 1.9.0 | - | 落后稳定版 1.19.0 | 1.19.0 | 1.19.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.core:core-ktx` | 1.1.0, 1.10.0, 1.13.0, 1.13.1, 1.18.0, 1.19.0, 1.2.0, 1.5.0, 1.8.0 | core-ktx | 落后稳定版 1.19.0 | 1.19.0 | 1.19.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 34 处 |
| `androidx.core:core-viewtree` | 1.0.0 | - | 已对齐稳定版 | 1.0.0 | 1.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.cursoradapter:cursoradapter` | 1.0.0 | - | 已对齐稳定版 | 1.0.0 | 1.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.customview:customview` | 1.0.0, 1.1.0, 1.2.0 | - | 落后稳定版 1.2.0 | 1.2.0 | 1.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 | - | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.databinding:viewbinding` | 9.4.1 | - | 已对齐稳定版 | 9.5.0-alpha06 | 9.4.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.datastore:datastore` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-android` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-core` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-core-android` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-core-okio` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-core-okio-jvm` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-preferences` | 1.2.1 | datastore-preferences | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-preferences-android` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-preferences-core` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-preferences-core-android` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-preferences-external-protobuf` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.datastore:datastore-preferences-proto` | 1.2.1 | - | 已对齐稳定版 | 1.3.0-alpha11 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 14 处 |
| `androidx.documentfile:documentfile` | 1.0.0 | - | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.drawerlayout:drawerlayout` | 1.0.0, 1.1.1 | - | 落后稳定版 1.2.0 | 1.2.0 | 1.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0, 1.1.0 | dynamicanimation | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.emoji2:emoji2` | 1.2.0, 1.3.0, 1.4.0 | - | 落后稳定版 1.6.0 | 1.7.0-rc01 | 1.6.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.emoji2:emoji2-views-helper` | 1.2.0, 1.4.0 | - | 落后稳定版 1.6.0 | 1.7.0-rc01 | 1.6.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.exifinterface:exifinterface` | 1.0.0 | - | 落后稳定版 1.4.2 | 1.4.2 | 1.4.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 6 处 |
| `androidx.fragment:fragment` | 1.0.0, 1.1.0, 1.2.5, 1.3.6, 1.5.1, 1.5.4 | - | 落后稳定版 1.9.0 | 1.9.0 | 1.9.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.graphics:graphics-path` | 1.0.1, 1.1.0 | - | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.graphics:graphics-shapes` | 1.0.1, 1.1.0 | - | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.graphics:graphics-shapes-android` | 1.1.0 | - | 已对齐稳定版 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.hilt:hilt-lifecycle-viewmodel` | 1.4.0 | - | 已对齐稳定版 | 1.4.0 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.hilt:hilt-lifecycle-viewmodel-compose` | 1.4.0 | - | 已对齐稳定版 | 1.4.0 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.hilt:hilt-navigation-compose` | 1.4.0 | hilt-navigation-compose | 已对齐稳定版 | 1.4.0 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.interpolator:interpolator` | 1.0.0 | - | 已对齐稳定版 | 1.0.0 | 1.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.legacy:legacy-support-core-utils` | 1.0.0 | - | 已对齐稳定版 | 1.0.0 | 1.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-common` | 2.11.0, 2.5.1, 2.6.1, 2.6.2, 2.8.7, 2.9.0, 2.9.2, 2.9.4 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.lifecycle:lifecycle-common-java8` | 2.11.0, 2.6.1 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-livedata` | 2.0.0, 2.11.0, 2.6.2 | - | 落后稳定版 2.11.0 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 22 处 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.11.0, 2.5.1, 2.6.2 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 22 处 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-process` | 2.11.0, 2.4.1, 2.6.2 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.lifecycle:lifecycle-runtime` | 2.0.0, 2.11.0, 2.5.1, 2.6.1, 2.6.2, 2.8.7, 2.9.0, 2.9.4 | - | 落后稳定版 2.11.0 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0, 2.8.7, 2.9.0, 2.9.4 | lifecycle-runtime-compose | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-runtime-compose-android` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.11.0, 2.6.1, 2.8.7 | lifecycle-runtime | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-service` | 2.11.0, 2.6.2 | lifecycle-service | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.0.0, 2.11.0, 2.5.1, 2.6.1, 2.6.2, 2.8.7, 2.9.0, 2.9.2, 2.9.4 | - | 落后稳定版 2.11.0 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 22 处 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0, 2.8.2, 2.9.0, 2.9.1 | lifecycle-viewmodel-compose | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.11.0, 2.6.1, 2.8.7, 2.9.2 | lifecycle-viewmodel | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.11.0, 2.5.1, 2.6.1, 2.6.2, 2.9.0 | lifecycle-viewmodel-savedstate | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 22 处 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.11.0 | - | 已对齐稳定版 | 2.12.0-alpha03 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.loader:loader` | 1.0.0 | - | 落后稳定版 1.2.0 | 1.2.0 | 1.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.localbroadcastmanager:localbroadcastmanager` | 1.0.0 | - | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigation:navigation-common` | 2.9.0 | - | Maven release=2.10.1 | 2.10.1 | 2.10.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigation:navigation-common-android` | 2.9.0 | - | Maven release=2.10.1 | 2.10.1 | 2.10.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigation:navigation-compose` | 2.9.0 | - | Maven release=2.10.1 | 2.10.1 | 2.10.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigation:navigation-compose-android` | 2.9.0 | - | Maven release=2.10.1 | 2.10.1 | 2.10.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigation:navigation-runtime` | 2.9.0 | - | Maven release=2.10.1 | 2.10.1 | 2.10.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigation:navigation-runtime-android` | 2.9.0 | - | Maven release=2.10.1 | 2.10.1 | 2.10.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigationevent:navigationevent` | 1.0.0, 1.1.0, 1.1.1, 1.1.2 | - | 落后稳定版 1.1.2 | 1.2.0-alpha04 | 1.1.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigationevent:navigationevent-android` | 1.1.2 | - | 已对齐稳定版 | 1.2.0-alpha04 | 1.1.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigationevent:navigationevent-compose` | 1.0.0, 1.1.1, 1.1.2 | navigationevent-compose | 落后稳定版 1.1.2 | 1.2.0-alpha04 | 1.1.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.navigationevent:navigationevent-compose-android` | 1.1.2 | - | 已对齐稳定版 | 1.2.0-alpha04 | 1.1.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.palette:palette` | 1.0.0 | - | 已对齐稳定版 | 1.1.0-alpha01 | 1.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.palette:palette-ktx` | 1.0.0 | androidx-palette | 已对齐稳定版 | 1.1.0-alpha01 | 1.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.print:print` | 1.0.0 | - | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.profileinstaller:profileinstaller` | 1.3.0, 1.4.0, 1.4.1 | profileinstaller | 落后稳定版 1.4.1 | 1.4.1 | 1.4.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.recyclerview:recyclerview` | 1.1.0, 1.2.1 | - | 落后稳定版 1.4.0 | 1.4.0 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.resourceinspection:resourceinspection-annotation` | 1.0.1 | - | 已对齐稳定版 | 1.0.1 | 1.0.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.room:room-common` | 2.7.0 | - | 落后稳定版 2.8.5 | 2.8.5 | 2.8.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.room:room-common-jvm` | 2.7.0 | - | 落后稳定版 2.8.5 | 2.8.5 | 2.8.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.room:room-runtime` | 2.7.0 | - | 落后稳定版 2.8.5 | 2.8.5 | 2.8.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.room:room-runtime-android` | 2.7.0 | - | 落后稳定版 2.8.5 | 2.8.5 | 2.8.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.savedstate:savedstate` | 1.2.0, 1.2.1, 1.3.0, 1.3.3, 1.4.0, 1.5.0 | savedstate | 落后稳定版 1.5.0 | 1.6.0-alpha03 | 1.5.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.savedstate:savedstate-android` | 1.5.0 | - | 已对齐稳定版 | 1.6.0-alpha03 | 1.5.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.savedstate:savedstate-compose` | 1.3.0, 1.3.3, 1.4.0, 1.5.0 | - | 落后稳定版 1.5.0 | 1.6.0-alpha03 | 1.5.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.savedstate:savedstate-compose-android` | 1.5.0 | - | 已对齐稳定版 | 1.6.0-alpha03 | 1.5.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.savedstate:savedstate-ktx` | 1.2.1, 1.3.0, 1.3.1, 1.5.0 | - | 落后稳定版 1.5.0 | 1.6.0-alpha03 | 1.5.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.sqlite:sqlite` | 2.5.0 | - | 落后稳定版 2.7.1 | 2.8.0-alpha01 | 2.7.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.sqlite:sqlite-android` | 2.5.0 | - | 落后稳定版 2.7.1 | 2.8.0-alpha01 | 2.7.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.sqlite:sqlite-framework` | 2.5.0 | - | 落后稳定版 2.7.1 | 2.8.0-alpha01 | 2.7.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.sqlite:sqlite-framework-android` | 2.5.0 | - | 落后稳定版 2.7.1 | 2.8.0-alpha01 | 2.7.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.startup:startup-runtime` | 1.0.0, 1.1.1, 1.2.0 | - | 落后稳定版 1.2.0 | 1.2.0 | 1.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.test.ext:junit` | 1.3.0 | androidx-test-ext-junit | 已对齐稳定版 | 1.3.0 | 1.3.0 | 仅版本目录 |
| `androidx.test.uiautomator:uiautomator` | 2.4.0 | androidx-test-uiautomator | 已对齐稳定版 | 2.4.0 | 2.4.0 | 仅版本目录 |
| `androidx.test:runner` | 1.7.0 | androidx-test-runner | 已对齐稳定版 | 1.7.0 | 1.7.0 | 仅版本目录 |
| `androidx.tracing:tracing` | 1.0.0, 1.2.0, 1.3.0 | - | 落后稳定版 2.0.2 | 2.0.2 | 2.0.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.tracing:tracing-android` | 1.3.0 | - | 落后稳定版 2.0.2 | 2.0.2 | 2.0.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.tracing:tracing-ktx` | 1.2.0, 1.3.0 | - | 落后稳定版 2.0.2 | 2.0.2 | 2.0.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.transition:transition` | 1.5.0 | - | 落后稳定版 1.7.1 | 1.7.1 | 1.7.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.vectordrawable:vectordrawable` | 1.1.0 | - | 落后稳定版 1.2.0 | 1.2.0 | 1.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.vectordrawable:vectordrawable-animated` | 1.1.0 | - | 落后稳定版 1.2.0 | 1.2.0 | 1.2.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | - | 落后稳定版 1.2.1 | 1.2.1 | 1.2.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `androidx.viewpager2:viewpager2` | 1.0.0 | - | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.viewpager:viewpager` | 1.0.0 | - | 落后稳定版 1.1.0 | 1.1.0 | 1.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `androidx.webkit:webkit` | 1.17.0 | webkit | 已对齐稳定版 | 1.18.0-alpha01 | 1.17.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.window:window` | 1.0.0, 1.5.0 | - | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.window:window-core` | 1.4.0, 1.5.0 | - | 落后稳定版 1.5.1 | 1.6.0-alpha05 | 1.5.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.window:window-core-android` | 1.5.0 | - | 落后稳定版 1.5.1 | 1.6.0-alpha05 | 1.5.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.work:work-runtime` | 2.11.2 | - | 已对齐稳定版 | 2.12.0-rc01 | 2.11.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `androidx.work:work-runtime-ktx` | 2.11.2 | work-runtime-ktx | 已对齐稳定版 | 2.12.0-rc01 | 2.11.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.belerweb:pinyin4j` | 2.5.1 | tinypinyin | 已对齐稳定版 | 2.5.1 | 2.5.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 22 处 |
| `com.github.ajalt.colormath:colormath` | 3.6.1 | - | 落后稳定版 3.7.0 | 3.7.0 | 3.7.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.github.ajalt.colormath:colormath-jvm` | 3.6.1 | - | 落后稳定版 3.7.0 | 3.7.0 | 3.7.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.github.Chainfire:libsuperuser` | 1.1.1 | libsuperuser | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.github.skydoves:colorpicker-compose` | 1.3.0 | colorpicker-compose | 已对齐稳定版 | 1.3.0 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.github.skydoves:colorpicker-compose-android` | 1.3.0 | - | 已对齐稳定版 | 1.3.0 | 1.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.google.android.datatransport:transport-api` | 2.2.1 | - | 落后稳定版 4.1.1 | 4.1.1 | 4.1.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.android.datatransport:transport-backend-cct` | 2.3.3 | - | 落后稳定版 4.1.1 | 4.1.1 | 4.1.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.android.datatransport:transport-runtime` | 2.2.5, 2.2.6 | - | 落后稳定版 4.1.1 | 4.1.1 | 4.1.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.android.gms:play-services-base` | 18.1.0, 18.5.0 | - | 落后稳定版 18.11.0 | 18.11.0 | 18.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.android.gms:play-services-basement` | 18.1.0, 18.4.0 | - | 落后稳定版 18.12.0 | 18.12.0 | 18.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.android.gms:play-services-mlkit-text-recognition-chinese` | 16.0.1 | mlkit-text-recognition-chinese-unbundled | 已对齐稳定版 | 16.0.1 | 16.0.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 6 处 |
| `com.google.android.gms:play-services-mlkit-text-recognition-common` | 19.1.0 | - | 已对齐稳定版 | 19.1.0 | 19.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 6 处 |
| `com.google.android.gms:play-services-tasks` | 18.0.2, 18.2.0 | - | 落后稳定版 18.4.1 | 18.4.1 | 18.4.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.android.material:material` | 1.14.0 | material | 已对齐稳定版 | 1.14.0 | 1.14.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.google.android.odml:image` | 1.0.0-beta1 | - | 已对齐稳定版 | 1.0.0-beta1 | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 6 处 |
| `com.google.code.findbugs:jsr305` | 3.0.2 | - | 已对齐稳定版 | 3.0.2 | 3.0.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.dagger:dagger` | 2.60.1 | - | 已对齐稳定版 | 2.60.1 | 2.60.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.dagger:dagger-lint-aar` | 2.60.1 | - | 已对齐稳定版 | 2.60.1 | 2.60.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.dagger:hilt-android` | 2.59, 2.60.1 | hilt-android | 落后稳定版 2.60.1 | 2.60.1 | 2.60.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 22 处 |
| `com.google.dagger:hilt-android-compiler` | 2.60.1 | hilt-compiler | 已对齐稳定版 | 2.60.1 | 2.60.1 | 仅版本目录 |
| `com.google.dagger:hilt-core` | 2.60.1 | - | 已对齐稳定版 | 2.60.1 | 2.60.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.errorprone:error_prone_annotations` | 2.15.0 | - | 落后稳定版 2.50.0 | 2.50.0 | 2.50.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.google.firebase:firebase-annotations` | 16.0.0 | - | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.firebase:firebase-components` | 16.1.0 | - | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.firebase:firebase-encoders` | 16.1.0 | - | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.firebase:firebase-encoders-json` | 17.1.0 | - | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.guava:listenablefuture` | 1.0 | - | 落后稳定版 9999.0-empty-to-avoid-conflict-with-guava | 9999.0-empty-to-avoid-conflict-with-guava | 9999.0-empty-to-avoid-conflict-with-guava | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `com.google.mlkit:common` | 18.11.0, 18.5.0, 18.6.0 | - | 已对齐稳定版 | 18.11.0 | 18.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `com.google.mlkit:language-id` | 17.0.6 | mlkit-language-id | 已对齐稳定版 | 17.0.6 | 17.0.6 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 16 处 |
| `com.google.mlkit:language-id-common` | 16.1.0 | - | 已对齐稳定版 | 16.1.0 | 16.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 16 处 |
| `com.google.mlkit:translate` | 17.0.3 | mlkit-translate | 已对齐稳定版 | 17.0.3 | 17.0.3 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 16 处 |
| `com.google.mlkit:vision-common` | 17.3.0 | - | 已对齐稳定版 | 17.3.0 | 17.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 6 处 |
| `com.google.mlkit:vision-interfaces` | 16.3.0 | - | 已对齐稳定版 | 16.3.0 | 16.3.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 6 处 |
| `com.google.zxing:core` | 3.5.4 | zxing-core | 已对齐稳定版 | 3.5.4 | 3.5.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.materialkolor:material-color-utilities` | 5.0.1 | - | 已对齐稳定版 | 5.0.1 | 5.0.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.materialkolor:material-color-utilities-android` | 5.0.1 | - | 已对齐稳定版 | 5.0.1 | 5.0.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.materialkolor:material-kolor` | 5.0.1 | materialkolor | 已对齐稳定版 | 5.0.1 | 5.0.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.materialkolor:material-kolor-android` | 5.0.1 | - | 已对齐稳定版 | 5.0.1 | 5.0.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.microsoft.onnxruntime:onnxruntime-android` | 1.30.0 | onnxruntime-android | 已对齐稳定版 | 1.30.0 | 1.30.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 8 处 |
| `com.mikepenz:multiplatform-markdown-renderer` | 0.45.0 | markdown-renderer | 已对齐稳定版 | 0.45.0 | 0.45.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.mikepenz:multiplatform-markdown-renderer-android` | 0.45.0 | - | 已对齐稳定版 | 0.45.0 | 0.45.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.mikepenz:multiplatform-markdown-renderer-m3` | 0.45.0 | markdown-renderer-m3 | 已对齐稳定版 | 0.45.0 | 0.45.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.mikepenz:multiplatform-markdown-renderer-m3-android` | 0.45.0 | - | 已对齐稳定版 | 0.45.0 | 0.45.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `com.squareup.okhttp3:mockwebserver` | 5.5.0 | okhttp-mockwebserver | 已对齐稳定版 | 5.5.0 | 5.5.0 | 仅版本目录 |
| `com.squareup.okhttp3:okhttp` | 3.0.0, 5.5.0 | okhttp | 落后稳定版 5.5.0 | 5.5.0 | 5.5.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `com.squareup.okhttp3:okhttp-android` | 5.5.0 | - | 已对齐稳定版 | 5.5.0 | 5.5.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `com.squareup.okio:okio` | 3.18.1, 3.9.1 | - | 落后稳定版 3.18.2 | 3.18.2 | 3.18.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `com.squareup.okio:okio-jvm` | 3.18.1 | - | 落后稳定版 3.18.2 | 3.18.2 | 3.18.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `cz.adaptech.tesseract4android:tesseract4android` | 4.9.0 | tesseract4android | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 6 处 |
| `dev.chrisbanes.haze:haze` | 1.7.3 | haze | 已对齐稳定版 | 2.0.0-rc02 | 1.7.3 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `dev.chrisbanes.haze:haze-android` | 1.7.3 | - | 已对齐稳定版 | 2.0.0-rc02 | 1.7.3 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `dev.drewhamilton.poko:poko-annotations` | 0.23.1 | - | 落后稳定版 0.23.2 | 0.23.2 | 0.23.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `dev.drewhamilton.poko:poko-annotations-jvm` | 0.23.1 | - | 落后稳定版 0.23.2 | 0.23.2 | 0.23.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `dev.rikka.shizuku:aidl` | 13.1.5 | - | 已对齐稳定版 | 13.1.5 | 13.1.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `dev.rikka.shizuku:api` | 13.1.5 | shizuku-api | 已对齐稳定版 | 13.1.5 | 13.1.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `dev.rikka.shizuku:provider` | 13.1.5 | shizuku-provider | 已对齐稳定版 | 13.1.5 | 13.1.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `dev.rikka.shizuku:shared` | 13.1.5 | - | 已对齐稳定版 | 13.1.5 | 13.1.5 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `io.github.libxposed:api` | 102.0.0 | libxposed-api | 未查询到 Maven metadata | - | - | 仅版本目录 |
| `io.github.libxposed:service` | 102.0.0 | libxposed-service | 未查询到 Maven metadata | - | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `jakarta.inject:jakarta.inject-api` | 2.0.1 | - | 落后稳定版 2.0.1.MR | 2.0.1.MR | 2.0.1.MR | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `javax.inject:javax.inject` | 1 | - | 未查询到 Maven metadata | - | 1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 18 处 |
| `junit:junit` | 4.13.2 | junit | 已对齐稳定版 | 4.13.2 | 4.13.2 | 仅版本目录 |
| `org.jetbrains.androidx.lifecycle:lifecycle-common` | 2.11.0, 2.9.5 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-common-jvm` | 2.11.0 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime` | 2.11.0, 2.9.5 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-android` | 2.11.0 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0, 2.9.5, 2.9.6 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-android` | 2.11.0 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` | 2.11.0, 2.9.5 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-android` | 2.11.0 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0, 2.11.0-beta01 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose-android` | 2.11.0 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.11.0, 2.9.5 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.11.0 | - | 已对齐稳定版 | 2.11.0 | 2.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.savedstate:savedstate` | 1.3.5, 1.3.6 | - | 落后稳定版 1.4.0 | 1.4.0 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.androidx.savedstate:savedstate-compose` | 1.3.5, 1.3.6 | - | 落后稳定版 1.4.0 | 1.4.0 | 1.4.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.animation:animation` | 1.12.0, 1.12.0-beta01 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.animation:animation-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.animation:animation-core` | 1.12.0, 1.12.0-beta01 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.animation:animation-core-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.foundation:foundation` | 1.12.0, 1.12.0-beta01 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.foundation:foundation-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.foundation:foundation-layout` | 1.12.0, 1.12.0-beta01 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.foundation:foundation-layout-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.material3:material3` | 1.12.0-alpha03 | - | 落后稳定版 1.9.0 | 1.13.0-alpha01 | 1.9.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.material3:material3-android` | 1.12.0-alpha03 | - | 有预发布更新 1.13.0-alpha01 | 1.13.0-alpha01 | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.material3:material3-window-size-class` | 1.12.0-alpha03 | - | 落后稳定版 1.9.0 | 1.13.0-alpha01 | 1.9.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.material3:material3-window-size-class-android` | 1.12.0-alpha03 | - | 有预发布更新 1.13.0-alpha01 | 1.13.0-alpha01 | - | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.material:material-ripple` | 1.12.0-beta01 | - | 有预发布更新 1.13.0-alpha01 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.material:material-ripple-android` | 1.12.0-beta01 | - | 有预发布更新 1.13.0-alpha01 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.runtime:runtime` | 1.10.0, 1.11.0, 1.12.0, 1.12.0-beta01, 1.9.0, 1.9.3 | - | 落后稳定版 1.12.0 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.runtime:runtime-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.runtime:runtime-saveable` | 1.11.0, 1.12.0, 1.9.2 | - | 落后稳定版 1.12.0 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.runtime:runtime-saveable-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui` | 1.10.0, 1.12.0, 1.12.0-beta01 | - | 落后稳定版 1.12.0 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-geometry` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-geometry-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-graphics` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-graphics-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-text` | 1.12.0, 1.12.0-beta01 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-text-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-unit` | 1.10.0, 1.12.0 | - | 落后稳定版 1.12.0 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-unit-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-util` | 1.10.0, 1.12.0, 1.12.0-beta01 | - | 落后稳定版 1.12.0 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.compose.ui:ui-util-android` | 1.12.0 | - | 已对齐稳定版 | 1.13.0-alpha01 | 1.12.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.kotlin:kotlin-bom` | 1.8.22 | - | 落后稳定版 2.4.20 | 2.4.20 | 2.4.20 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 1.2.50, 1.3.71, 1.6.21, 1.7.10, 1.8.10, 1.8.20, 1.8.22, 2.0.0, 2.0.21, 2.1.0, 2.1.10, 2.1.20, 2.1.21, 2.2.10, 2.2.20, 2.2.21, 2.3.0, 2.3.20, 2.3.21, 2.4.0, 2.4.10, 2.4.20 | - | 落后稳定版 2.4.20 | 2.4.20 | 2.4.20 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 37 处 |
| `org.jetbrains.kotlin:kotlin-stdlib-common` | 1.8.22, 2.4.20 | - | 落后稳定版 2.4.20 | 2.4.20 | 2.4.20 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7` | 1.8.0, 1.8.20, 1.8.22 | - | 落后稳定版 2.4.20 | 2.4.20 | 2.4.20 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 8 处 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8` | 1.8.0, 1.8.20, 1.8.22 | - | 落后稳定版 2.4.20 | 2.4.20 | 2.4.20 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 8 处 |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | 0.5.1 | - | 落后稳定版 0.5.2 | 0.5.2 | 0.5.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm` | 0.5.1 | - | 落后稳定版 0.5.2 | 0.5.2 | 0.5.2 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0, 1.6.4, 1.7.3, 1.8.1, 1.9.0 | kotlinx-coroutines-android | 已对齐稳定版 | 1.11.0 | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 30 处 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.11.0, 1.9.0 | - | 已对齐稳定版 | 1.11.0 | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 30 处 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0, 1.3.4, 1.7.3, 1.9.0 | - | 已对齐稳定版 | 1.11.0 | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 30 处 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.11.0, 1.9.0 | - | 已对齐稳定版 | 1.11.0 | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 30 处 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | kotlinx-coroutines-test | 已对齐稳定版 | 1.11.0 | 1.11.0 | 仅版本目录 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.11.0 | - | 已对齐稳定版 | 1.12.0-RC | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.11.0, 1.7.3 | - | 已对齐稳定版 | 1.12.0-RC | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.11.0 | - | 已对齐稳定版 | 1.12.0-RC | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0, 1.7.3 | kotlinx-serialization-json | 已对齐稳定版 | 1.12.0-RC | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.11.0 | - | 已对齐稳定版 | 1.12.0-RC | 1.11.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 20 处 |
| `org.jetbrains:annotations` | 13.0, 23.0.0 | - | 落后稳定版 26.1.0 | 26.1.0 | 26.1.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 36 处 |
| `org.jetbrains:markdown` | 0.7.9 | - | Maven release=0.7.14 | 0.7.14 | 0.7.14 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jetbrains:markdown-jvm` | 0.7.9 | - | Maven release=0.7.14 | 0.7.14 | 0.7.14 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.jspecify:jspecify` | 1.0.0 | - | 落后稳定版 1.0.1 | 1.0.1 | 1.0.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 28 处 |
| `org.lsposed.hiddenapibypass:hiddenapibypass` | 6.1 | hiddenapibypass | 已对齐稳定版 | 6.1 | 6.1 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `org.opencv:opencv` | 5.0.0.1 | opencv-android | Maven release=5.0.0 | 5.0.0 | 5.0.0 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 8 处 |
| `org.robolectric:robolectric` | 4.17 | robolectric | 已对齐稳定版 | 4.17 | 4.17 | 仅版本目录 |
| `top.yukonga.miuix.kmp:miuix-blur-android` | 0.9.4 | miuix-blur | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-core` | 0.9.4 | - | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-core-android` | 0.9.4 | - | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-icons-android` | 0.9.4 | miuix-icons | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-nav-android` | 0.9.4 | miuix-nav | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-preference-android` | 0.9.4 | miuix-preference | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-shader` | 0.9.4 | - | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-shader-android` | 0.9.4 | miuix-shader | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-squircle` | 0.9.4 | - | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-squircle-android` | 0.9.4 | - | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-ui` | 0.9.4 | - | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |
| `top.yukonga.miuix.kmp:miuix-ui-android` | 0.9.4 | miuix-ui | 已对齐稳定版 | 0.9.4 | 0.9.4 | :app (fullDebugRuntimeClasspath)<br>:app (fullReleaseRuntimeClasspath)<br>:app (liteDebugRuntimeClasspath)<br>…共 4 处 |

## 6. 统计摘要

- 去重 `group:artifact` 坐标数：**321**
- Gradle 解析条目（含传递依赖、多配置重复计数）：**11528**
- 版本目录显式库：**69**

## 7. 如何重新生成

```powershell
powershell -ExecutionPolicy Bypass -File scripts/collect-gradle-deps.ps1
python scripts/generate-dependency-audit.py
```

说明：表中「落后稳定版」含大量**传递依赖**带来的旧版本并存，通常随直接依赖升级而收敛，不必逐项强制对齐。

