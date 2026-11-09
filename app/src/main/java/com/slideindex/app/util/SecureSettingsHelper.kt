package com.slideindex.app.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.slideindex.app.overlay.OverlayStatePort
import com.slideindex.app.service.SlideIndexAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SecureSettingsHelper {

    const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS

    /**
     * 重绑时两次写之间的默认间隔：覆盖安装/被杀之后，系统需要观察到一次真正的「解绑」。
     *
     * 真机实测（本仓库注释与线上反馈一致）：60ms 会被 ContentObserver 合并成一次写，
     * 等于没解绑；3s 稳定生效。这里取 2.5s 折中——既稳定，又不至于让恢复流程等太久。
     */
    const val DEFAULT_REBIND_GAP_MS = 2_500L
    private const val MIN_REBIND_GAP_MS = 800L

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun adbGrantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} $PERMISSION"

    fun grantViaPrivilegedShell(context: Context): Boolean {
        if (!TaskManagerUtil.hasPrivilegedAccess()) return false
        val packageName = context.packageName
        val granted = TaskManagerUtil.runShellCommand("pm", "grant", packageName, PERMISSION)
        return granted && hasWriteSecureSettings(context)
    }

    fun grantViaShizuku(context: Context): Boolean = grantViaPrivilegedShell(context)

    /**
     * Re-enables this app's accessibility service via Secure settings when permission is granted.
     * Returns true when accessibility is enabled after the call.
     */
    fun ensureAccessibilityEnabled(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        if (PermissionHelper.isAccessibilityServiceEnabled(context)) return true

        val component = ComponentName(context, SlideIndexAccessibilityService::class.java)
        val serviceId = component.flattenToString()
        val shortId = component.flattenToShortString()

        val resolver = context.contentResolver
        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val services = enabledServices.split(':')
            .filter { it.isNotBlank() }
            .toMutableSet()
        val alreadyListed = services.any {
            it.equals(serviceId, ignoreCase = true) ||
                it.equals(shortId, ignoreCase = true)
        }
        if (!alreadyListed) {
            services.add(serviceId)
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                TextUtils.join(":", services),
            )
        }
        Settings.Secure.putInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            1,
        )
        return PermissionHelper.isAccessibilityServiceEnabled(context)
    }

    /**
     * Toggles our accessibility entry in Secure settings so the system re-binds the service.
     * Use when settings show enabled but [SlideIndexAccessibilityService] is not connected yet.
     *
     * 两次写之间必须有足够间隔（[gapMs]）：AccessibilityManagerService 的 ContentObserver 会把挨在
     * 一起的两次写合并成一次，等于没有解绑，系统也就不会重新绑定（实测 60ms 无效、3s 有效）。
     * 间隔在 IO 线程上阻塞等待，调用方保持挂起，不阻塞主线程。
     */
    suspend fun nudgeAccessibilityRebind(
        context: Context,
        gapMs: Long = DEFAULT_REBIND_GAP_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        nudgeAccessibilityRebindBlocking(context, gapMs)
    }

    private fun nudgeAccessibilityRebindBlocking(context: Context, gapMs: Long): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return false
        // 进程无关的连接判定：本文件也可能在非 overlay 进程被调用（那里 isConnected() 恒 false）。
        if (OverlayStatePort.isServiceConnected()) return true

        val component = ComponentName(context, SlideIndexAccessibilityService::class.java)
        val serviceId = component.flattenToString()
        val shortId = component.flattenToShortString()
        val resolver = context.contentResolver
        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val others = enabledServices.split(':')
            .filter { it.isNotBlank() }
            .filterNot {
                it.equals(serviceId, ignoreCase = true) ||
                    it.equals(shortId, ignoreCase = true)
            }

        // ⚠️ 只摘掉自己这一条，**不碰** ACCESSIBILITY_ENABLED 全局开关：
        // 把它置 0 会顺手把用户开着的其它无障碍服务一起关掉，重绑失败时更是灾难。
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            TextUtils.join(":", others),
        )

        // 给系统留出观察到「解绑」的时间：间隔太短会被 ContentObserver 合并，服务不会重绑。
        try {
            Thread.sleep(gapMs.coerceAtLeast(MIN_REBIND_GAP_MS))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        val restored = others.toMutableSet()
        restored.add(serviceId)
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            TextUtils.join(":", restored),
        )
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        return PermissionHelper.isAccessibilityServiceEnabled(context)
    }
}
