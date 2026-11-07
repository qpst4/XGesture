package com.slideindex.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.slideindex.app.overlay.OverlayHostRuntime

object PermissionHelper {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** True while camera access is granted, including the "only while in use" grant. */
    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 本应用是否持有「通知使用权」（Notification access）。
     *
     * 与 [hasNotificationPermission] 是两回事：那个是本应用**发**通知的权限，
     * 这个是**读/清**系统通知栏的权限，阅读消息、清空通知都依赖它。
     */
    fun hasNotificationAccess(context: Context): Boolean = runCatching {
        androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }.getOrDefault(false)

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife") // Keep-alive is required for edge overlays / shake detection
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }

    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the system "ignore battery optimizations" prompt, with a settings fallback. */
    fun requestBatteryOptimizationAccess(context: Context): Boolean {
        if (isBatteryOptimizationExempt(context)) return true
        val direct = batteryOptimizationIntent(context)
        if (context.packageManager.resolveActivity(direct, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return runCatching { context.startActivity(direct) }.isSuccess
        }
        return runCatching {
            context.startActivity(batteryOptimizationSettingsIntent())
        }.isSuccess
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(context: Context): Intent {
        val direct = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(direct, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return direct
        }
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Returns true if already granted; otherwise opens the system settings screen. */
    fun requestUsageAccess(context: Context): Boolean {
        if (hasUsageAccess(context)) return true
        return runCatching { context.startActivity(usageAccessSettingsIntent(context)) }.isSuccess
    }

    /** True when edge overlays should use [android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]. */
    fun isAccessibilityServiceEnabledForOverlays(context: Context): Boolean =
        isAccessibilityServiceEnabled(context)

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) != 1
        ) {
            return false
        }
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val serviceClass = OverlayHostRuntime.accessibilityServiceClass ?: return false
        val component = ComponentName(context, serviceClass)
        return enabled.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) ||
                it.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    /** Prefer starting from an [android.app.Activity] without [Intent.FLAG_ACTIVITY_NEW_TASK]. */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.isNotificationPolicyAccessGranted
    }

    fun notificationPolicySettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Returns true if already granted; otherwise opens the system settings screen. */
    fun requestNotificationPolicyAccess(context: Context): Boolean {
        if (hasNotificationPolicyAccess(context)) return true
        context.startActivity(notificationPolicySettingsIntent())
        return false
    }

    /** Returns true if already granted; otherwise opens the system settings screen. */
    fun requestWriteSettingsAccess(context: Context): Boolean {
        if (canWriteSettings(context)) return true
        context.startActivity(writeSettingsIntent(context))
        return false
    }

    fun hasAppListPermission(context: Context): Boolean {
        return runCatching {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                context.packageManager.getInstalledApplications(0)
            }
            apps.size > 1
        }.getOrDefault(false)
    }

    fun appListSettingsIntent(context: Context): Intent {
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivity(miuiIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return miuiIntent
        }
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun requestAppListPermission(context: Context) {
        runCatching {
            context.startActivity(appListSettingsIntent(context))
        }
    }
}
