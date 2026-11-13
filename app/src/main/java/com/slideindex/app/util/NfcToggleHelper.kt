package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import java.lang.reflect.Method
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * NFC 开关。
 *
 * **为什么不用 `svc nfc`**：它在部分 ROM（真机 MEIZU 21 / Flyme）上执行完**不退出**，
 * 而整条链路是在等进程结束；一旦调用方是主线程，就会卡到输入超时 → ANR。
 * `NfcAdapter.enable()/disable()` 本身是异步的（AOSP 注释：*This call is asynchronous*），
 * 发个请求就返回，不存在这个问题。
 *
 * 调用顺序（任一成功即返回）：
 * 1. 已持有 `WRITE_SECURE_SETTINGS` → 进程内反射调用（最快，且不依赖 Shizuku 常驻）；
 * 2. 没有该权限 → 借 Shizuku / root 授一次（`pm grant`，仓库既有能力），再走 1；
 * 3. 反射不可用（隐藏 API 被拦）→ Shizuku 可用时以 shell 身份直接调 `INfcAdapter`；
 * 4. 都不行 → 返回 [Outcome.NEEDS_SETTINGS]，由调用方打开系统 NFC 设置页。
 *
 * ⚠️ 本类里全是 binder / 反射调用，**不要在主线程调用**。
 */
object NfcToggleHelper {

    private const val TAG = "NfcToggle"

    enum class Outcome {
        /** 已发起切换（NFC 状态经广播异步生效）。 */
        TOGGLED,

        /** 本机没有 NFC 硬件。 */
        UNSUPPORTED,

        /** 没有可用通道，需要用户去系统设置里手动开关。 */
        NEEDS_SETTINGS,
    }

    fun hasHardware(context: Context): Boolean = runCatching {
        context.applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
    }.getOrDefault(false)

    /** 当前是否开启；没有硬件或读不到时返回 null。 */
    fun isEnabled(context: Context): Boolean? {
        if (!hasHardware(context)) return null
        return runCatching {
            context.applicationContext.getSystemService(NfcManager::class.java)
                ?.defaultAdapter
                ?.isEnabled
        }.getOrNull()
    }

    fun toggle(context: Context): Outcome {
        val current = isEnabled(context) ?: return if (hasHardware(context)) {
            Outcome.NEEDS_SETTINGS
        } else {
            Outcome.UNSUPPORTED
        }
        return set(context, !current)
    }

    /**
     * 把 NFC 设成 [enable]（幂等：已经是该状态时直接返回 [Outcome.TOGGLED]）。
     *
     * 与 [toggle] 共用同一条降级链；「打开 / 关闭」动作就是靠这里拿到确定性语义。
     */
    fun set(context: Context, enable: Boolean): Outcome {
        if (!hasHardware(context)) return Outcome.UNSUPPORTED
        val current = isEnabled(context) ?: return Outcome.NEEDS_SETTINGS
        if (current == enable) return Outcome.TOGGLED

        // 1/2. 进程内调用：没有 WRITE_SECURE_SETTINGS 时先借特权补授（反射被拦就别白跑一次 pm grant）。
        if (reflectionAvailable()) {
            if (!SecureSettingsHelper.hasWriteSecureSettings(context)) {
                val granted = runCatching {
                    SecureSettingsHelper.grantViaPrivilegedShell(context)
                }.getOrDefault(false)
                Log.i(TAG, "grant WRITE_SECURE_SETTINGS via privilege: $granted")
            }
            if (SecureSettingsHelper.hasWriteSecureSettings(context) && switchInProcess(context, enable)) {
                Log.i(TAG, "switched in-process: enable=$enable")
                return Outcome.TOGGLED
            }
        }

        // 3. Shizuku：以 shell 身份直接调 NFC 的 binder（不需要授权，也不需要子进程）。
        if (switchViaShizuku(enable)) {
            Log.i(TAG, "switched via Shizuku binder: enable=$enable")
            return Outcome.TOGGLED
        }

        Log.w(TAG, "no channel available: enable=$enable, shizuku=${TaskManagerUtil.hasShizukuPermission()}")
        return Outcome.NEEDS_SETTINGS
    }

    fun notifyUnsupported(context: Context) {
        toast(context, context.getString(R.string.gesture_action_nfc_unsupported))
    }

    /** 没有可用通道时的降级：打开系统 NFC 设置页。 */
    fun openSettings(context: Context): Boolean {
        val appContext = context.applicationContext
        val intent = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appContext.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return false
        }
        return runCatching { appContext.startActivity(intent) }.isSuccess
    }

    /** 反射能不能用：只做一次查找，不触发权限检查（被隐藏 API 拦时返回 false）。 */
    private fun reflectionAvailable(): Boolean = enableMethod != null && disableMethod != null

    private val enableMethod: Method? by lazy {
        runCatching { NfcAdapter::class.java.getMethod("enable").apply { isAccessible = true } }.getOrNull()
    }

    private val disableMethod: Method? by lazy {
        runCatching { NfcAdapter::class.java.getMethod("disable").apply { isAccessible = true } }.getOrNull()
    }

    private fun switchInProcess(context: Context, enable: Boolean): Boolean = runCatching {
        val adapter = context.applicationContext.getSystemService(NfcManager::class.java)
            ?.defaultAdapter
            ?: return@runCatching false
        val method = (if (enable) enableMethod else disableMethod) ?: return@runCatching false
        method.invoke(adapter) as? Boolean ?: false
    }.onFailure { Log.w(TAG, "in-process switch failed", it) }.getOrDefault(false)

    /**
     * 以 shell 身份调用 `INfcAdapter.enable()/disable()`。
     *
     * 走 [ShizukuBinderWrapper]（事务由 Shizuku 进程代发，身份是 shell），
     * 用 `INfcAdapter$Stub.asInterface` 拿代理，避免手写事务码。
     */
    private fun switchViaShizuku(enable: Boolean): Boolean {
        if (!TaskManagerUtil.hasShizukuPermission()) return false
        return runCatching {
            val binder: IBinder = SystemServiceHelper.getSystemService("nfc") ?: return@runCatching false
            val stubClass = Class.forName("android.nfc.INfcAdapter\$Stub")
            val adapter = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
                ?: return@runCatching false
            val method = adapter.javaClass.getMethod(if (enable) "enable" else "disable")
            method.invoke(adapter) as? Boolean ?: false
        }.onFailure { Log.w(TAG, "shizuku switch failed", it) }.getOrDefault(false)
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
