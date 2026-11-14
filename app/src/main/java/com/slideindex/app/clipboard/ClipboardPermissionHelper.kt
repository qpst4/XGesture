package com.slideindex.app.clipboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.slideindex.app.util.TaskManagerUtil

object ClipboardPermissionHelper {
    fun mediaReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /**
     * 是否拥有**完整**照片访问权限。
     *
     * Android 14+ 的「选中的照片」只会授 `READ_MEDIA_VISUAL_USER_SELECTED`：能读用户挑中的那几张，
     * **看不到新截图**。截图监听依赖新图入库，所以这里必须按完整权限判断，否则会启动监听却永远收不到事件。
     */
    fun hasMediaReadPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /** 仅「选中的照片」部分访问：截图监听不可用，但设置页要能区分出来提示用户改成完整访问。 */
    fun hasPartialMediaReadPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED &&
            !hasMediaReadPermission(context)

    fun hasReadLogsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    fun adbGrantReadLogsCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} ${Manifest.permission.READ_LOGS}"

    fun grantViaShizuku(context: Context): Boolean {
        if (!TaskManagerUtil.hasPermission()) return false
        val packageName = context.packageName
        val granted = TaskManagerUtil.runShellCommand(
            "pm",
            "grant",
            packageName,
            Manifest.permission.READ_LOGS
        )
        return granted && hasReadLogsPermission(context)
    }
}
