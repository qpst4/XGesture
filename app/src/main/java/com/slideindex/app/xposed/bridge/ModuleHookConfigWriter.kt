package com.slideindex.app.xposed.bridge

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.FloatBallScreenMetrics
import com.slideindex.app.overlay.TakeoverExtraRects
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.interceptWindowWidthDp
import com.slideindex.app.settings.maxEdgeTriggerWidthDp
import com.slideindex.app.settings.triggerHandleEdgeWidthDp
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.settings.withRuntimeLandscapeSettings
import com.slideindex.app.util.OverlaySuppression
import java.io.File

/**
 * app 侧配置快照：把接管开关与四边触钮几何落盘并广播给 system_server 模块。
 *
 * 与 EdgeX 一致：文件放在设备保护目录且放宽读取权限（供模块兜底读取），
 * 同时把 JSON 直接随广播下发（模块主要依赖这条通道，重启后从 /data/system 恢复）。
 */
object ModuleHookConfigWriter {
  fun snapshotFile(context: Context): File =
    File(
      context.createDeviceProtectedStorageContext().filesDir,
      ModuleHookBridgeContract.SNAPSHOT_FILE_NAME,
    )

  /**
   * 对外导出的快照路径（`/storage/emulated/0/Android/data/<pkg>/files/`）。
   *
   * 设备保护目录那份在多数机型上电话进程读不到，模块自己的持久化又恒 EACCES，
   * 所以由 app 再导出一份到外部私有目录：写它不需要权限，模块侧能读。
   */
  fun externalSnapshotFile(context: Context): File? =
    context.getExternalFilesDir(null)?.let { dir ->
      File(dir, ModuleHookBridgeContract.SNAPSHOT_FILE_NAME)
    }

  fun buildSnapshot(context: Context, settings: AppSettings): ModuleHookSnapshot {
    val runtime = settings.withRuntimeLandscapeSettings(OverlaySuppression.isLandscape(context))
    return ModuleHookSnapshot(
      version = ModuleHookBridgeContract.SNAPSHOT_VERSION,
      takeoverGroups = takeoverGroups(settings),
      interceptSystemBackGesture = settings.interceptSystemBackGesture,
      navigationMode = navigationMode(context),
      density = context.resources.displayMetrics.density,
      sides = listOf(PanelSide.LEFT, PanelSide.RIGHT, PanelSide.BOTTOM, PanelSide.TOP)
        .map { side -> runtime.toSideSnapshot(side) },
      extraRects = extraRects(context, settings),
      clipboardWhitelist = settings.clipboardLsposedWhitelist.sorted(),
      otp = ModuleHookOtpPolicy(
        captureEnabled = settings.otpLsposedSmsCaptureEnabled,
        blockCodeSmsEnabled = settings.otpBlockCodeSmsEnabled,
        markAsReadEnabled = settings.otpMarkSmsReadEnabled,
        deleteSmsEnabled = settings.otpDeleteSmsAfterExtractEnabled,
        keywordsRegex = settings.otpKeywordsRegex,
        blacklist = settings.otpSmsBlacklist,
      ),
      updatedAtMs = System.currentTimeMillis(),
    )
  }

  /** 触钮之外的接管矩形（角轮盘；悬浮球线条待下一步）。 */
  private fun extraRects(context: Context, settings: AppSettings): List<ModuleHookExtraRect> {
    val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(context)
    return TakeoverExtraRects.build(
      settings = settings,
      screenWidthPx = screenWidthPx,
      screenHeightPx = screenHeightPx,
      density = context.resources.displayMetrics.density,
      isLandscape = OverlaySuppression.isLandscape(context),
    )
  }

  fun takeoverGroups(settings: AppSettings): Int {
    var groups = 0
    if (settings.systemGestureTakeoverTop) groups = groups or ModuleHookBridgeContract.GROUP_TOP
    if (settings.systemGestureTakeoverSides) groups = groups or ModuleHookBridgeContract.GROUP_SIDES
    if (settings.systemGestureTakeoverBottom) groups = groups or ModuleHookBridgeContract.GROUP_BOTTOM
    return groups
  }

  fun navigationMode(context: Context): Int = runCatching {
    Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)
  }.getOrDefault(0)

  /** 写入快照文件；返回 JSON 供广播使用，写盘失败时仍返回 JSON。 */
  fun write(context: Context, settings: AppSettings): String {
    val json = buildSnapshot(context, settings).toJson()
    runCatching { writeSnapshotFile(snapshotFile(context), json) }
    runCatching { externalSnapshotFile(context)?.let { writeSnapshotFile(it, json) } }
    return json
  }

  fun publish(context: Context, settings: AppSettings): String {
    val json = write(context, settings)
    broadcastConfigChanged(context, json)
    return json
  }

  fun writeSnapshotFile(file: File, json: String) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeText(json)
    if (!temp.renameTo(file)) {
      temp.copyTo(file, overwrite = true)
      temp.delete()
    }
    makeHookReadable(file)
  }

  /**
   * 放开快照文件与父目录的读取权限，使 system_server 侧的模块可以兜底读取。
   * 内容仅包含触钮几何与开关，不含任何隐私数据。
   */
  private fun makeHookReadable(file: File) {
    file.setReadable(true, false)
    file.setWritable(true, true)
    file.parentFile?.let { dir ->
      dir.setExecutable(true, false)
      dir.setReadable(true, false)
      dir.parentFile?.setExecutable(true, false)
    }
  }

  private fun broadcastConfigChanged(context: Context, json: String) {
    context.sendBroadcast(
      Intent(ModuleHookBridgeContract.ACTION_CONFIG_CHANGED).apply {
        putExtra(ModuleHookBridgeContract.EXTRA_CONFIG_JSON, json)
      },
    )
  }

  private fun AppSettings.toSideSnapshot(side: PanelSide): ModuleHookSide {
    val handles = triggerHandles(side).map { handle ->
      ModuleHookHandle(
        topFraction = handle.topFraction,
        heightFraction = handle.heightFraction,
        widthDp = triggerHandleEdgeWidthDp(side, handle.id),
      )
    }
    val widthDp = if (handles.isEmpty()) {
      maxEdgeTriggerWidthDp(side)
    } else {
      interceptWindowWidthDp(side)
    }
    return ModuleHookSide(
      sideId = side.toSnapshotSideId(),
      widthDp = widthDp,
      handles = handles,
    )
  }

  private fun PanelSide.toSnapshotSideId(): Int = when (this) {
    PanelSide.LEFT -> ModuleHookBridgeContract.SIDE_LEFT
    PanelSide.RIGHT -> ModuleHookBridgeContract.SIDE_RIGHT
    PanelSide.BOTTOM -> ModuleHookBridgeContract.SIDE_BOTTOM
    PanelSide.TOP -> ModuleHookBridgeContract.SIDE_TOP
  }

}
