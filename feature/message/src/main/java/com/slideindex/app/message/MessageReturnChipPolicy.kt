package com.slideindex.app.message

/** 显示返回胶囊时，目标应用此刻的前台状态。 */
enum class ReturnChipForeground {
    TARGET,
    OTHER,
    UNKNOWN,
}

/**
 * 阅读消息后是否该显示「返回上一应用」胶囊（纯逻辑，便于单测）。
 *
 * 不做全屏检测：普通阅读本身就是全屏打开，小窗阅读是否显示由调用方另判
 * （来源应用仍可见 = 小窗成功 = 不显示）。
 *
 * 只拦这几种：来源为空、来源与目标同包、锁屏、目标不在前台。
 * 前台未知（无障碍未连接）时放行，由调用方决定兜底时机。
 */
fun shouldShowReturnChip(
    sourcePackage: String?,
    targetPackage: String,
    foreground: ReturnChipForeground,
    screenLocked: Boolean,
): Boolean {
    if (sourcePackage.isNullOrBlank()) return false
    if (sourcePackage == targetPackage) return false
    if (screenLocked) return false
    if (foreground == ReturnChipForeground.OTHER) return false
    return true
}
