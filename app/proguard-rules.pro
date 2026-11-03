# SlideIndex — R8 / ProGuard rules

# ---------------------------------------------------------------------------
# Debugging (keep line numbers in crash reports)
# ---------------------------------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions, *Annotation*

# ---------------------------------------------------------------------------
# Xposed / LSPosed module entry (LibXposed API 102)
# ---------------------------------------------------------------------------
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class com.slideindex.app.xposed.** { *; }
-keep class com.slideindex.app.autofill.** { *; }

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**

# ---------------------------------------------------------------------------
# Kotlin coroutines
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Jetpack Compose (rely on compiler-generated consumer rules; keep only runtime essentials)
-keep,allowobfuscation class androidx.compose.runtime.Composer { *; }
-keep,allowobfuscation class androidx.compose.runtime.ComposerImpl { *; }
-keep,allowobfuscation class androidx.compose.runtime.ComposerKt { *; }
-keepclassmembers class androidx.compose.runtime.** {
    <init>(...);
}
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# DataStore Preferences (protobuf lite)
# ---------------------------------------------------------------------------
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class androidx.datastore.preferences.** {
    <fields>;
}
-dontwarn androidx.datastore.**

# ---------------------------------------------------------------------------
# WorkManager (Room WorkDatabase_Impl is reflectively constructed at startup)
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keep class androidx.work.impl.** { *; }
-keepclassmembers class androidx.work.impl.** {
    <init>(...);
}
-keep class com.slideindex.app.update.UpdateCheckWorker { *; }
-dontwarn androidx.work.**

# ---------------------------------------------------------------------------
# AndroidX App Startup (WorkManagerInitializer runs in InitializationProvider)
# ---------------------------------------------------------------------------
-keep class androidx.startup.** { *; }

# ---------------------------------------------------------------------------
# Shizuku
# ---------------------------------------------------------------------------
-keep class rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface com.slideindex.app.shizuku.ITaskManagerService { *; }
-keep class com.slideindex.app.shizuku.** { *; }
-keep class com.slideindex.app.clipboard.monitor.** { *; }

# ---------------------------------------------------------------------------
# AIDL / Binder stubs
# ---------------------------------------------------------------------------
-keep class * implements android.os.IInterface { *; }
-keepclassmembers class * implements android.os.IInterface {
    public static ** CREATOR;
}

# ---------------------------------------------------------------------------
# Third-party libraries
# ---------------------------------------------------------------------------
-keep class com.github.skydoves.colorpicker.** { *; }
-keep class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**

# Paddle OCR / ONNX Runtime
-keep class com.paddle.ocr.** { *; }
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Google ML Kit & Play Services
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ---------------------------------------------------------------------------
# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}
-keep class com.slideindex.app.SlideIndexApp { *; }
-keep class com.slideindex.app.privilege.root.TaskManagerRootDaemonMain {
    public static void main(java.lang.String[]);
}

-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.content.ContentProvider { *; }

# ---------------------------------------------------------------------------
# Android framework types used by codecs & intents
# ---------------------------------------------------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers enum com.slideindex.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
