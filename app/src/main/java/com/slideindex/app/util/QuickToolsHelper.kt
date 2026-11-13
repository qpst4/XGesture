package com.slideindex.app.util

import android.content.ContentResolver
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
import com.slideindex.app.gesture.ToggleMode
import com.slideindex.app.gesture.resolve
import com.slideindex.app.privilege.PrivilegeGateway

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

    fun toggleWifi(context: Context): Boolean? = applyWifi(context, ToggleMode.TOGGLE)

    /**
     * 把 Wi-Fi 设成 [mode] 要求的状态（打开 / 关闭是幂等的：已经是该状态时直接返回成功）。
     *
     * @return null = 没有可用通道（无特权 / 读不到状态），由调用方决定怎么降级提示。
     */
    fun applyWifi(context: Context, mode: ToggleMode): Boolean? {
        val current = readWifiEnabled(context) ?: return null
        val enable = mode.resolve(current)
        if (current == enable) return true
        if (!TaskManagerUtil.hasPermission()) return null
        val ok = TaskManagerUtil.runShellCommand(
            "svc",
            "wifi",
            if (enable) "enable" else "disable",
        )
        if (ok) return readWifiEnabled(context) ?: enable
        return null
    }

    fun readMobileDataEnabled(context: Context): Boolean? =
        readMobileDataSwitchState(context)

    fun readMobileDataSwitchState(context: Context): Boolean? =
        readMobileData1(context)

    /**
     * @param currentHint 面板当前亮/暗；与 [readMobileData1] 一起决定 enable/disable 方向。
     */
    fun toggleMobileData(context: Context, currentHint: Boolean? = null): Boolean? =
        applyMobileData(context, ToggleMode.TOGGLE, currentHint)

    /** 把移动数据设成 [mode] 要求的状态（打开 / 关闭幂等）。null = 无特权通道 / 读不到状态。 */
    fun applyMobileData(
        context: Context,
        mode: ToggleMode,
        currentHint: Boolean? = null,
    ): Boolean? {
        if (!TaskManagerUtil.hasPermission()) return null
        val switchOn = currentHint ?: readMobileData1(context) ?: return null
        val target = mode.resolve(switchOn)
        if (switchOn == target) return true
        val action = if (target) "enable" else "disable"
        if (!TaskManagerUtil.runShellCommand("svc", "data", action)) return null
        return readMobileData1(context) ?: target
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
        // 与 Wi-Fi / 移动数据一致：root 模式下 shell 读可能拿不到结果（守护进程通道差异），
        // 退回 ContentResolver 直接读 `bluetooth_on`——读全局设置不需要特权。
        return runCatching {
            when (
                Settings.Global.getInt(
                    context.applicationContext.contentResolver,
                    "bluetooth_on",
                    -1,
                )
            ) {
                1 -> true
                0 -> false
                else -> null
            }
        }.getOrNull()
    }

    fun toggleBluetooth(context: Context): Boolean? =
        applyBluetooth(context, ToggleMode.TOGGLE)

    /** 把蓝牙设成 [mode] 要求的状态（打开 / 关闭幂等）。null = 无特权通道 / 读不到状态。 */
    fun applyBluetooth(context: Context, mode: ToggleMode): Boolean? {
        val current = readBluetoothEnabled(context) ?: return null
        val enable = mode.resolve(current)
        if (current == enable) return true
        if (!TaskManagerUtil.hasPermission()) return null
        val ok = runBluetoothShell(enable)
        if (ok) return enable
        return null
    }

    /**
     * 蓝牙切换命令。
     *
     * Root 模式下守护进程的 [TaskManagerUtil.runShellCommand] 走 2.5s 超时，部分 ROM 上
     * `cmd bluetooth_manager` 会超时（表现就是"动作没生效、直接跳设置页"）；这里先用长超时的
     * 命令行通道，失败再退回通用通道。Shizuku 模式保持原路径不变。
     */
    private fun runBluetoothShell(enable: Boolean): Boolean {
        val action = if (enable) "enable" else "disable"
        if (PrivilegeGateway.isRootMode()) {
            val result = TaskManagerUtil.runShellCommandLine(
                command = "cmd bluetooth_manager $action",
                useRoot = true,
                timeoutMs = 10_000L,
            )
            if (result.exitCode == 0) return true
        }
        return TaskManagerUtil.runShellCommand("cmd", "bluetooth_manager", action)
    }

    /** 没有特权时的降级：打开系统设置页。 */
    fun openBluetoothSettings(context: Context): Boolean =
        openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)

    /** 系统「自动同步应用数据」总开关的当前状态；读不到时返回 null。 */
    fun readAutoSyncEnabled(context: Context): Boolean? = runCatching {
        ContentResolver.getMasterSyncAutomatically()
    }.getOrNull()

    /**
     * 把「自动同步」设成 [mode] 要求的状态。
     *
     * 只需普通权限 `READ/WRITE_SYNC_SETTINGS`，不需要 Shizuku / root；
     * 个别 ROM 可能写不进去（返回 null），由调用方降级提示。
     */
    fun applyAutoSync(context: Context, mode: ToggleMode): Boolean? {
        val current = readAutoSyncEnabled(context) ?: return null
        val target = mode.resolve(current)
        if (current == target) return true
        return runCatching {
            ContentResolver.setMasterSyncAutomatically(target)
            true
        }.getOrDefault(false)
    }

    /** 飞行模式当前状态（读全局设置，无需权限）。 */
    fun readAirplaneModeEnabled(context: Context): Boolean? = runCatching {
        Settings.Global.getInt(
            context.applicationContext.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1
    }.getOrNull()

    /**
     * 把飞行模式设成 [mode] 要求的状态（需要 Shizuku / root）。
     *
     * @return null = 没有特权通道 / 命令失败，由调用方降级打开系统设置页。
     */
    fun applyAirplaneMode(context: Context, mode: ToggleMode): Boolean? {
        val current = readAirplaneModeEnabled(context) ?: return null
        val target = mode.resolve(current)
        if (current == target) return true
        if (!TaskManagerUtil.hasPermission()) return null
        val ok = TaskManagerUtil.runShellCommand(
            "cmd",
            "connectivity",
            "airplane-mode",
            if (target) "enable" else "disable",
        )
        if (!ok) return null
        return target
    }

    /** 飞行模式无特权时的降级：打开系统「飞行模式」设置页。 */
    fun openAirplaneModeSettings(context: Context): Boolean =
        openSystemSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    /** 定位总开关当前状态：优先用公开 API（不需要定位权限），读不到再退回 `location_mode`。 */
    fun readLocationEnabled(context: Context): Boolean? {
        val appContext = context.applicationContext
        runCatching {
            appContext.getSystemService(android.location.LocationManager::class.java)
                ?.isLocationEnabled
        }.getOrNull()?.let { return it }
        return runCatching {
            // 直接用字符串键：常量本身在 API 28 起被标记 deprecated。
            Settings.Secure.getInt(appContext.contentResolver, "location_mode", 0) != 0
        }.getOrNull()
    }

    /**
     * 把定位设成 [mode] 要求的状态（需要 Shizuku / root）。
     *
     * 走 `cmd location set-location-enabled true|false`（Android 12+ 的 LocationManagerService
     * 命令行入口）；设不了时返回 null，由调用方降级打开系统定位设置页。
     */
    fun applyLocation(context: Context, mode: ToggleMode): Boolean? {
        val current = readLocationEnabled(context) ?: return null
        val target = mode.resolve(current)
        if (current == target) return true
        val result = TaskManagerUtil.runShellCommandLine(
            command = "cmd location set-location-enabled ${if (target) "true" else "false"}",
            useRoot = PrivilegeGateway.isRootMode(),
            timeoutMs = 10_000L,
        )
        return if (result.exitCode == 0) target else null
    }

    /** 定位无特权 / 命令失败时的降级：打开系统「位置信息」设置页。 */
    fun openLocationSettings(context: Context): Boolean =
        openSystemSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)

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

    /** 把自动旋转设成 [mode] 要求的状态（幂等）；null = 没通道 / 写入未生效。 */
    fun applyAutoRotate(context: Context, mode: ToggleMode): Boolean? {
        val current = readAutoRotateEnabled(context)
        val target = mode.resolve(current)
        if (current == target) return true
        // 走既有的多级通道；能走到这里说明 target 与当前相反，toggle 的目标就是 target。
        val result = toggleAutoRotate(context) ?: return null
        return result == target
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
