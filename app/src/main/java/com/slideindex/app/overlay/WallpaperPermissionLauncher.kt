package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.service.LaunchTrampolineActivity

/**
 * 壁纸模糊所需的「所有文件访问权限」跳转入口。
 *
 * 以前是一个专用 Trampoline Activity（[WallpaperPermissionTrampolineActivity]），
 * W3-1 合并跳板后只保留这个调用点，Activity 由 [LaunchTrampolineActivity] 统一承担。
 */
object WallpaperPermissionLauncher {

    fun launch(context: Context) {
        runCatching {
            context.startActivity(LaunchTrampolineActivity.createWallpaperPermissionIntent(context))
        }
    }

    /** 没有权限才跳转；已有权限直接回调 true。 */
    fun ensurePermission(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        if (SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)) {
            onResult?.invoke(true)
            return
        }
        launch(context)
    }
}
