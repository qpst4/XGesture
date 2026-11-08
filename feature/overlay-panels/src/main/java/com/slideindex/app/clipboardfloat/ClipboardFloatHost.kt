package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.content.Context

/**
 * `clipboardfloat`（输入法上方剪贴板浮窗）与 `:app` 的接线点。
 *
 * 浮窗的生命周期、无障碍服务实例都在 `:app` 的 `service` 包里；模块侧只消费这 4 个入口。
 */
interface ClipboardFloatHost {
    /** 当前无障碍服务实例；未连接时为 null。 */
    fun accessibilityService(): AccessibilityService?

    /** 键盘弹起时显示浮窗。 */
    fun showForIme(context: Context, imeTop: Int, showChip: Boolean)

    /** 隐藏浮窗。 */
    fun hide(context: Context)

    /** 可编辑控件的外框缓存（由小到大去重前）。 */
    fun editableBounds(service: AccessibilityService): List<android.graphics.Rect>

    /** 在无障碍服务上同步派发一次点击。 */
    fun dispatchTap(service: AccessibilityService, x: Float, y: Float)

    /** 拖拽转移后丢弃浮窗状态。 */
    fun discardAfterDragTransfer()

    /** 当前前台包名（用于屏蔽名单判断）；取不到时为 null。 */
    fun resolveForegroundPackage(service: AccessibilityService): String?

    /** 键盘顶缘变化时更新浮窗位置。 */
    fun updateImeTop(context: Context, top: Int)
}

/** 模块内服务定位点：`:app` 启动时注入。 */
object ClipboardFloatHostAccess {

    @Volatile
    var host: ClipboardFloatHost? = null
}
