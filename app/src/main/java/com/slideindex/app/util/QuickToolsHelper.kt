package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.widget.Toast
import com.slideindex.app.diagnostic.EdgeDiag

object QuickToolsHelper {
    fun readWifiEnabled(context: Context): Boolean? {
        val appContext = context.applicationContext
        if (TaskManagerUtil.hasPermission()) {
            val output = TaskManagerUtil.runShellCommandOutput("settings", "get", "global", "wifi_on")
                .output.trim()
            if (output == "0" || output == "1") return output == "1"
        }
        return runCatching {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.WIFI_ON,
                0,
            ) == 1
        }.getOrNull()
    }

    fun toggleWifi(context: Context): Boolean? {
        val current = readWifiEnabled(context) ?: return null
        val enable = !current
        if (TaskManagerUtil.hasPermission()) {
            val ok = TaskManagerUtil.runShellCommand(
                "svc",
                "wifi",
                if (enable) "enable" else "disable",
            )
            if (ok) return readWifiEnabled(context) ?: enable
        }
        return null
    }

    fun readMobileDataEnabled(context: Context): Boolean? =
        readMobileDataSwitchState(context)

    fun readMobileDataSwitchState(context: Context): Boolean? =
        readMobileData1(context)

    /**
     * @param currentHint 面板当前亮/暗；与 [readMobileData1] 一起决定 enable/disable 方向。
     */
    fun toggleMobileData(context: Context, currentHint: Boolean? = null): Boolean? {
        if (!TaskManagerUtil.hasPermission()) return null
        val switchOn = currentHint ?: readMobileData1(context) ?: return null
        val action = if (switchOn) "disable" else "enable"
        if (!TaskManagerUtil.runShellCommand("svc", "data", action)) return null
        return readMobileData1(context) ?: !switchOn
    }

    /**
     * Flyme 双卡实测：默认上网卡移动数据开关对应 `settings global mobile_data1`（0=关，1=开）。
     * 忽略 `mobile_data`（恒为 1）与 `mobile_data0`（null）。
     */
    private fun readMobileData1(context: Context): Boolean? {
        readMobileData1FromResolver(context)?.let { return it }
        if (TaskManagerUtil.hasPermission()) {
            readMobileData1FromShell()?.let { return it }
        }
        return null
    }

    private fun readMobileData1FromResolver(context: Context): Boolean? {
        val value = runCatching {
            Settings.Global.getInt(
                context.applicationContext.contentResolver,
                MOBILE_DATA_SETTING_KEY,
                -1,
            )
        }.getOrDefault(-1)
        return when (value) {
            1 -> true
            0 -> false
            else -> null
        }
    }

    private fun readMobileData1FromShell(): Boolean? = when (
        TaskManagerUtil.runShellCommandOutput("settings", "get", "global", MOBILE_DATA_SETTING_KEY)
            .output
            .trim()
            .lowercase()
    ) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private const val MOBILE_DATA_SETTING_KEY = "mobile_data1"

    fun readBluetoothEnabled(context: Context): Boolean? {
        if (TaskManagerUtil.hasPermission()) {
            val output = TaskManagerUtil.runShellCommandOutput(
                "settings",
                "get",
                "global",
                "bluetooth_on",
            ).output.trim()
            if (output == "0" || output == "1") return output == "1"
        }
        return null
    }

    fun toggleBluetooth(context: Context): Boolean? {
        val current = readBluetoothEnabled(context) ?: return null
        val enable = !current
        if (TaskManagerUtil.hasPermission()) {
            val ok = TaskManagerUtil.runShellCommand(
                "cmd",
                "bluetooth_manager",
                if (enable) "enable" else "disable",
            )
            if (ok) return enable
        }
        return null
    }

    /** 没有特权时的降级：打开系统设置页。 */
    fun openBluetoothSettings(context: Context): Boolean =
        openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)

    private fun openSystemSettings(context: Context, action: String): Boolean {
        val appContext = context.applicationContext
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appContext.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return false
        }
        return runCatching { appContext.startActivity(intent) }.isSuccess
    }

    fun readAutoRotateEnabled(context: Context): Boolean {
        return runCatching {
            Settings.System.getInt(
                context.applicationContext.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0,
            ) == 1
        }.getOrDefault(false)
    }

    private fun postToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleAutoRotate(context: Context): Boolean? {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val canWrite = PermissionHelper.canWriteSettings(context)
        val hasShizuku = TaskManagerUtil.hasPermission()
        val current = readAutoRotateEnabled(context)
        val targetEnable = !current
        val targetIntValue = if (targetEnable) 1 else 0
        val targetStrValue = if (targetEnable) "1" else "0"

        val diagTag = "AutoRotateDiag"
        val startMsg = "切换自动旋转: 当前=$current -> 目标=$targetEnable, " +
            "canWrite=$canWrite, shizuku=$hasShizuku, device=${Build.MANUFACTURER}/${Build.MODEL}, SDK=${Build.VERSION.SDK_INT}"
        Log.i(diagTag, startMsg)
        EdgeDiag.log("AutoRotate", startMsg)

        // 阶梯 1: 若具有「修改系统设置」权限，优先尝试原生 Settings.System API（速度极快且无需子进程）
        if (canWrite) {
            val putResult = runCatching {
                Settings.System.putInt(
                    resolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    targetIntValue,
                )
            }.getOrDefault(false)

            val postSysNative = readAutoRotateEnabled(context)
            if (putResult && postSysNative == targetEnable) {
                val okMsg = "阶梯1(原生putInt)成功且读回生效: $targetEnable"
                Log.i(diagTag, okMsg)
                EdgeDiag.log("AutoRotate", okMsg)
                return targetEnable
            }
            val failMsg = "阶梯1(原生putInt)未生效: putOk=$putResult, 读回=$postSysNative"
            Log.w(diagTag, failMsg)
            EdgeDiag.log("AutoRotate", failMsg)
        }

        // 阶梯 2: 若有 Shizuku / Root，尝试 WindowManager 权威命令（cmd window set-user-rotation）
        if (hasShizuku) {
            val currentRotation = runCatching {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }.getOrDefault(Surface.ROTATION_0)

            val wmCmd = if (targetEnable) {
                arrayOf("cmd", "window", "set-user-rotation", "free")
            } else {
                arrayOf("cmd", "window", "set-user-rotation", "lock", currentRotation.toString())
            }
            val wmRes = TaskManagerUtil.runShellCommandOutput(*wmCmd)
            val postSysWm = readAutoRotateEnabled(context)
            if (wmRes.exitCode == 0 && postSysWm == targetEnable) {
                val okMsg = "阶梯2(cmd window)成功且读回生效: $targetEnable"
                Log.i(diagTag, okMsg)
                EdgeDiag.log("AutoRotate", okMsg)
                return targetEnable
            }
            val wmFailMsg = "阶梯2(cmd window)执行后读回未变: exit=${wmRes.exitCode}, out='${wmRes.output.trim()}', 读回=$postSysWm"
            Log.w(diagTag, wmFailMsg)
            EdgeDiag.log("AutoRotate", wmFailMsg)

            // 阶梯 2.1: 兼容部分系统别名 wm set-user-rotation
            val legacyWmCmd = if (targetEnable) {
                arrayOf("wm", "set-user-rotation", "free")
            } else {
                arrayOf("wm", "set-user-rotation", "lock", currentRotation.toString())
            }
            val legacyRes = TaskManagerUtil.runShellCommandOutput(*legacyWmCmd)
            val postSysLegacy = readAutoRotateEnabled(context)
            if (legacyRes.exitCode == 0 && postSysLegacy == targetEnable) {
                val okMsg = "阶梯2.1(wm set-user-rotation)成功且读回生效: $targetEnable"
                Log.i(diagTag, okMsg)
                EdgeDiag.log("AutoRotate", okMsg)
                return targetEnable
            }

            // 阶梯 3: 尝试通过 Shell settings put（带 --user current）写入系统表
            val settingsCmd = arrayOf("settings", "put", "--user", "current", "system", Settings.System.ACCELEROMETER_ROTATION, targetStrValue)
            val settingsRes = TaskManagerUtil.runShellCommandOutput(*settingsCmd)
            val postSysSettings = readAutoRotateEnabled(context)
            if (settingsRes.exitCode == 0 && postSysSettings == targetEnable) {
                val okMsg = "阶梯3(settings put --user current)成功且读回生效: $targetEnable"
                Log.i(diagTag, okMsg)
                EdgeDiag.log("AutoRotate", okMsg)
                return targetEnable
            }
            val settingsFailMsg = "阶梯3(settings put)执行后读回未变: exit=${settingsRes.exitCode}, 读回=$postSysSettings"
            Log.w(diagTag, settingsFailMsg)
            EdgeDiag.log("AutoRotate", settingsFailMsg)
        }

        // 最终校验与失败提示
        val finalRead = readAutoRotateEnabled(context)
        val success = finalRead == targetEnable
        val finalMsg = "切换自动旋转流程结束: 成功=$success, 最终读回=$finalRead (期望=$targetEnable)"
        Log.i(diagTag, finalMsg)
        EdgeDiag.log("AutoRotate", finalMsg)

        if (!success) {
            val failReason = if (!canWrite && !hasShizuku) {
                "请授予「修改系统设置」或 Shizuku 权限"
            } else {
                "系统拦截写入 (读回未改变)"
            }
            postToast(appContext, "自动旋转切换失败: $failReason")
        }

        return if (success) targetEnable else null
    }

    fun setOrientation(context: Context, orientation: Int): Boolean {
        val appContext = context.applicationContext
        val rotationVal = when (orientation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 1
            Surface.ROTATION_180 -> 2
            Surface.ROTATION_270 -> 3
            else -> 0
        }
        val canWrite = PermissionHelper.canWriteSettings(context)
        val hasShizuku = TaskManagerUtil.hasPermission()
        val diagTag = "AutoRotateDiag"
        val startMsg = "setOrientation: targetRotation=$rotationVal, canWrite=$canWrite, shizuku=$hasShizuku"
        Log.i(diagTag, startMsg)
        EdgeDiag.log("AutoRotate", startMsg)

        // 阶梯 1: 原生 putInt
        if (canWrite) {
            val nativeOk = runCatching {
                Settings.System.putInt(appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(appContext.contentResolver, Settings.System.USER_ROTATION, rotationVal)
            }.getOrDefault(false)
            if (nativeOk) {
                EdgeDiag.log("AutoRotate", "setOrientation via native putInt 成功: $rotationVal")
                return true
            }
        }

        // 阶梯 2: Shizuku / Root 下通过 cmd window set-user-rotation lock <rotation>
        if (hasShizuku) {
            val wmRes = TaskManagerUtil.runShellCommandOutput("cmd", "window", "set-user-rotation", "lock", rotationVal.toString())
            if (wmRes.exitCode == 0) {
                EdgeDiag.log("AutoRotate", "setOrientation via cmd window 成功: $rotationVal")
                return true
            }
            val legacyRes = TaskManagerUtil.runShellCommandOutput("wm", "set-user-rotation", "lock", rotationVal.toString())
            if (legacyRes.exitCode == 0) {
                EdgeDiag.log("AutoRotate", "setOrientation via wm 成功: $rotationVal")
                return true
            }
            // 阶梯 3: 兜底 settings put
            TaskManagerUtil.runShellCommand("settings", "put", "--user", "current", "system", Settings.System.ACCELEROMETER_ROTATION, "0")
            val ok = TaskManagerUtil.runShellCommand("settings", "put", "--user", "current", "system", Settings.System.USER_ROTATION, rotationVal.toString())
            EdgeDiag.log("AutoRotate", "setOrientation via settings put: ok=$ok")
            if (ok) return true
        }

        postToast(appContext, "屏幕旋转设置失败")
        return false
    }

    fun forcePortrait(context: Context): Boolean = setOrientation(context, Surface.ROTATION_0)

    fun forceLandscape(context: Context): Boolean = setOrientation(context, Surface.ROTATION_90)
}
