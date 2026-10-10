import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.slideindex.app"
    compileSdk = 37

    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.slideindex.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = 37
        versionCode = 71
        versionName = "1.36.0"

        // 输入法诊断日志（ImeDiagnostics）。默认关：正式包零开销。
        // 出诊断包：`./gradlew :app:assembleFullRelease -PimeDiag=true`（release 构建可用同签名，
        // 用户不必卸载重装）。日志只在"内容变化或满 1s"时输出，避免日志 IO 改变时序与复现率。
        buildConfigField(
            "boolean",
            "IME_DIAG",
            ((findProperty("imeDiag") as String?)?.toBoolean() ?: false).toString(),
        )

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    flavorDimensions += "bundle"
    productFlavors {
        create("full") {
            dimension = "bundle"
        }
        create("lite") {
            dimension = "bundle"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    val defaultNdkVersion = "29.0.13599879"
    val localPropFile = rootProject.file("local.properties")
    val localSdkDir = if (localPropFile.exists()) {
        val props = Properties()
        localPropFile.inputStream().use { props.load(it) }
        props.getProperty("sdk.dir")
    } else {
        System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
    }
    val localNdks = localSdkDir?.let { file(it).resolve("ndk") }?.listFiles()?.filter { it.isDirectory }?.map { it.name }
    ndkVersion = if (localNdks != null && !localNdks.contains(defaultNdkVersion) && localNdks.isNotEmpty()) {
        localNdks.maxOrNull() ?: defaultNdkVersion
    } else {
        defaultNdkVersion
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        debug {
            // 本地快速装机：debug 也用发布签名，可直接覆盖安装在发布包上，不丢数据。
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
        viewBinding = true
    }

    androidResources {
        ignoreAssetsPattern += "dict:.*"
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    sourceSets {
        named("full") {
            assets.directories.add("build/generated/release-assets")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            merges += "META-INF/xposed/*"
        }
        jniLibs {
            excludes += setOf(
                "**/libopencv_java5.so",
                "**/libonnxruntime.so",
                "**/libtesseract.so",
                "**/libleptonica.so",
                "**/libtranslate_jni.so",
                "**/liblanguage_id_l2c_jni.so",
                "**/libslideindex_jieba.so",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val versionName = android.defaultConfig.versionName ?: "unknown"
        val bundleFlavor = variant.productFlavors.firstOrNull { it.first == "bundle" }?.second ?: "unknown"
        variant.outputs.forEach { output ->
            output.outputFileName.set("xgesture-$versionName-$bundleFlavor.apk")
        }
    }
}

private val NATIVE_ENGINE_ABI = "arm64-v8a"

private data class NativeEnginePackSpec(
    val taskName: String,
    val zipName: String,
    val libraries: List<String>,
    val assetPaths: List<String> = emptyList(),
)

private val nativeEnginePackSpecs = listOf(
    NativeEnginePackSpec(
        taskName = "packageOcrEnginePack",
        zipName = "ocr-engine-arm64-v6.zip",
        libraries = listOf(
            "libonnxruntime.so",
            "libopencv_java5.so",
            "libleptonica.so",
            "libtesseract.so",
        ),
    ),
    NativeEnginePackSpec(
        taskName = "packageTranslateEnginePack",
        zipName = "translate-engine-arm64-v1.zip",
        libraries = listOf(
            "libtranslate_jni.so",
            "liblanguage_id_l2c_jni.so",
        ),
    ),
    NativeEnginePackSpec(
        taskName = "packageSegmentationEnginePack",
        zipName = "segmentation-engine-arm64-v1.zip",
        libraries = listOf("libslideindex_jieba.so"),
        assetPaths = listOf(
            "dict/jieba.dict.utf8",
            "dict/hmm_model.utf8",
            "dict/user.dict.utf8",
        ),
    ),
)

val nativeEnginePacksDir = rootProject.layout.buildDirectory.dir("native-engine-packs")
val nativeEnginePackLibDir = layout.buildDirectory.dir("native-engine-pack-libs/$NATIVE_ENGINE_ABI")

val nativeEnginePackArtifacts = configurations.create("nativeEnginePackArtifacts") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val nativeEnginePackArtifactFiles = nativeEnginePackArtifacts.incoming.artifactView {
    lenient(true)
}.files

abstract class CleanupStaleNativeEnginePacksTask : DefaultTask() {
    @get:InputDirectory
    abstract val packsDirectory: DirectoryProperty

    @get:Input
    abstract val expectedZipNames: SetProperty<String>

    @TaskAction
    fun cleanup() {
        val packsDir = packsDirectory.get().asFile
        if (!packsDir.isDirectory) return
        val expected = expectedZipNames.get()
        packsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) && it.name !in expected }
            ?.forEach { it.delete() }
    }
}

abstract class CollectNativeEnginePackLibsTask : DefaultTask() {
    @get:InputFiles
    abstract val artifactFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val cxxIntermediatesDir: DirectoryProperty

    @get:Input
    abstract val nativeEngineAbi: Property<String>

    @TaskAction
    fun collect() {
        val abi = nativeEngineAbi.get()
        val destination = outputDirectory.get().asFile.apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        artifactFiles.files.forEach { artifact ->
            if (artifact.extension.equals("aar", ignoreCase = true)) {
                extractArm64LibsFromAar(artifact, abi, destination)
            }
        }
        val jieba = findLatestJiebaLib(cxxIntermediatesDir.get().asFile, abi)
            ?: error("libslideindex_jieba.so not found; run CMake release build first")
        jieba.copyTo(destination.resolve("libslideindex_jieba.so"), overwrite = true)
    }

    private fun extractArm64LibsFromAar(aar: File, abi: String, destination: File) {
        val jniPrefix = "jni/$abi/"
        ZipFile(aar).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(jniPrefix) && it.name.endsWith(".so") }
                .forEach { entry ->
                    val outFile = destination.resolve(entry.name.substringAfterLast('/'))
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
        }
    }

    private fun findLatestJiebaLib(cxxDir: File, abi: String): File? {
        if (!cxxDir.isDirectory) return null
        val targetSuffix = "/obj/$abi/libslideindex_jieba.so"
        return cxxDir.walkTopDown()
            .filter { file ->
                if (!file.isFile || file.name != "libslideindex_jieba.so") return@filter false
                val path = file.path.replace('\\', '/')
                path.contains("/RelWithDebInfo/") && path.endsWith(targetSuffix)
            }
            .maxByOrNull { it.lastModified() }
    }
}

tasks.register<CollectNativeEnginePackLibsTask>("collectNativeEnginePackLibs") {
    group = "build"
    description = "Extract arm64 native libraries from dependency AARs for engine packs."
    dependsOn("buildCMakeRelWithDebInfo[$NATIVE_ENGINE_ABI]")
    mustRunAfter(tasks.matching { it.name.contains("CMakeDebug") })
    artifactFiles.from(nativeEnginePackArtifactFiles)
    outputDirectory.set(nativeEnginePackLibDir)
    cxxIntermediatesDir.set(layout.buildDirectory.dir("intermediates/cxx"))
    nativeEngineAbi.set(NATIVE_ENGINE_ABI)
}

val packTasks = nativeEnginePackSpecs.map { spec ->
    tasks.register<Zip>(spec.taskName) {
        group = "build"
        description = "Package ${spec.zipName} for bundled offline install."
        dependsOn("collectNativeEnginePackLibs")
        archiveFileName.set(spec.zipName)
        destinationDirectory.set(nativeEnginePacksDir)
        from(nativeEnginePackLibDir) {
            include(spec.libraries)
            into("lib/$NATIVE_ENGINE_ABI")
        }
        spec.assetPaths.forEach { assetPath ->
            val assetFile = file("src/main/assets/$assetPath")
            from(assetFile.parentFile) {
                include(assetFile.name)
                into("assets/${assetFile.parentFile.relativeTo(file("src/main/assets"))}")
            }
        }
    }
}

tasks.register<CleanupStaleNativeEnginePacksTask>("packageNativeEnginePacks") {
    group = "build"
    description = "Package all native engine zips for release APK bundling."
    dependsOn(packTasks)
    packsDirectory.set(nativeEnginePacksDir)
    expectedZipNames.set(nativeEnginePackSpecs.map { it.zipName }.toSet())
}

val bundledNativeEngineGeneratedAssets = layout.buildDirectory.dir("generated/release-assets")

tasks.register("copyBundledNativeEnginePacks") {
    group = "build"
    description = "Copy native engine zip packs into generated app assets for offline-first install."
    dependsOn("packageNativeEnginePacks")
    val packsDirProvider = nativeEnginePacksDir
    val outputDirProvider = bundledNativeEngineGeneratedAssets
    outputs.dir(outputDirProvider)
    doLast {
        val expectedPackIds = listOf("ocr-engine", "translate-engine", "segmentation-engine")
        val zipPattern = Regex("""^(.+)-arm64-v(\d+)\.zip$""")
        val packsDir = packsDirProvider.get().asFile
        val zips = if (!packsDir.isDirectory) {
            emptyList()
        } else {
            packsDir.listFiles()
                .orEmpty()
                .asSequence()
                .filter { it.isFile }
                .mapNotNull { file ->
                    val match = zipPattern.matchEntire(file.name) ?: return@mapNotNull null
                    Triple(match.groupValues[1], match.groupValues[2].toInt(), file)
                }
                .groupBy { it.first }
                .map { (_, versions) -> versions.maxBy { it.second }.third }
                .sortedBy { it.name }
                .toList()
        }
        if (zips.isEmpty()) {
            error("No native engine pack zips found in ${packsDir.absolutePath}")
        }
        val missing = expectedPackIds.filter { packId ->
            zips.none { it.name.startsWith("$packId-arm64-v") }
        }
        if (missing.isNotEmpty()) {
            error("Missing native engine packs: $missing (in ${packsDir.absolutePath})")
        }
        val assetDir = outputDirProvider.get().asFile.resolve("bundled-native-engine")
        assetDir.mkdirs()
        assetDir.listFiles()?.forEach { it.delete() }
        zips.forEach { zip ->
            val packId = zip.name.substringBefore("-arm64-v")
            zip.copyTo(assetDir.resolve("$packId.zip"), overwrite = true)
        }
    }
}

afterEvaluate {
    tasks.matching {
        it.name == "mergeFullReleaseAssets" || it.name == "mergeFullDebugAssets"
    }.configureEach {
        dependsOn("copyBundledNativeEnginePacks")
    }
    tasks.matching {
        it.name.startsWith("generateFullReleaseLint") || it.name == "lintVitalAnalyzeFullRelease"
    }.configureEach {
        dependsOn("copyBundledNativeEnginePacks")
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:autofill"))
    implementation(project(":core:gesture"))
    implementation(project(":core:notification"))
    implementation(project(":core:monitoring"))
    implementation(project(":core:overlay-layout"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:otp"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:apps"))
    implementation(project(":feature:shake"))
    implementation(project(":feature:message"))
    implementation(project(":core:ocr"))
    implementation(project(":core:translate"))
    implementation(project(":core:native-engine"))
    implementation(project(":core:cloud-storage"))

    nativeEnginePackArtifacts(libs.onnxruntime.android)
    nativeEnginePackArtifacts(libs.opencv.android)
    nativeEnginePackArtifacts(libs.tesseract4android)
    nativeEnginePackArtifacts(libs.mlkit.translate)
    nativeEnginePackArtifacts(libs.mlkit.language.id)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation(libs.androidx.palette)
    implementation(libs.profileinstaller)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.work.runtime.ktx)
    implementation(libs.savedstate)
    implementation(libs.activity.compose)
    implementation(libs.webkit)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.datastore.preferences)
    implementation(libs.tinypinyin)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
    implementation(libs.libsuperuser)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3.pinned)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.material)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.dynamicanimation)
    implementation(libs.colorpicker.compose)
    implementation(libs.navigationevent.compose)
    implementation(libs.miuix.nav)
    implementation(libs.haze)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.materialkolor)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // 模块侧（xposed/）代码现在有单测覆盖：libxposed api 是 compileOnly，
    // 单测运行时类路径上也得有，否则加载 ClipboardWhitelistHook 会 NoClassDefFoundError。
    testImplementation(libs.libxposed.api)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
