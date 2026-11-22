package com.slideindex.app.xposed.bridge

import android.content.Context

/** 缓存模块状态探测结果，供设置页读取（与 EdgeX 的激活态存储同思路）。 */
object ModuleBridgeStatusStore {
  private const val PREFS_NAME = "module_bridge_status"
  private const val KEY_ACTIVE = "active"
  private const val KEY_STATE = "state"
  private const val KEY_DETAIL = "detail"
  private const val KEY_UPDATED_AT = "updated_at"

  /**
   * 写入某个通道的状态回执。
   *
   * 系统框架通道沿用原有偏好键（旧数据继续可用），电话进程通道加后缀分槽存放。
   */
  fun write(
    context: Context,
    active: Boolean,
    state: String,
    detail: String,
    channel: String = ModuleHookBridgeContract.CHANNEL_SYSTEM,
  ) {
    val suffix = keySuffix(channel)
    val stored = prefs(context)
    val apkPrefix = ModuleHookBridgeContract.STATUS_DETAIL_APK_PREFIX
    val installedApk = context.applicationContext.applicationInfo.sourceDir
    // 首次从旧实现升级时，滞留的旧 receiver 仍可能发回回执；不能覆盖已确认的新代状态。
    if (ModuleStatusFields.field(stored.getString(KEY_DETAIL + suffix, "").orEmpty(), apkPrefix) == installedApk &&
      ModuleStatusFields.field(detail, apkPrefix) != installedApk
    ) return
    stored.edit()
      .putBoolean(KEY_ACTIVE + suffix, active)
      .putString(KEY_STATE + suffix, state)
      .putString(KEY_DETAIL + suffix, detail)
      .putLong(KEY_UPDATED_AT + suffix, System.currentTimeMillis())
      .apply()
  }

  fun read(
    context: Context,
    channel: String = ModuleHookBridgeContract.CHANNEL_SYSTEM,
  ): Snapshot {
    val suffix = keySuffix(channel)
    val prefs = prefs(context)
    return Snapshot(
      active = prefs.getBoolean(KEY_ACTIVE + suffix, false),
      state = prefs.getString(KEY_STATE + suffix, "").orEmpty(),
      detail = prefs.getString(KEY_DETAIL + suffix, "").orEmpty(),
      updatedAtMs = prefs.getLong(KEY_UPDATED_AT + suffix, 0L),
    )
  }

  private fun keySuffix(channel: String): String =
    if (channel == ModuleHookBridgeContract.CHANNEL_SYSTEM) "" else "_$channel"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  data class Snapshot(
    val active: Boolean,
    /** 模块三态：`ready` / `armed` / `not-ready`；旧模块可能为空串。 */
    val state: String,
    val detail: String,
    val updatedAtMs: Long,
  )
}
