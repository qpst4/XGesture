package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.copy.UniversalCopyOverlay
import com.slideindex.app.freezer.FreezerOverlayWindow
import com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow
import com.slideindex.app.overlay.holographic.HolographicLauncherOverlayWindow
import com.slideindex.app.overlay.filebrowser.FileBrowserOverlayWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow
import com.slideindex.app.overlay.volumepanel.VolumePanelOverlayWindow
import com.slideindex.app.translate.overlay.ScreenTranslationController

/**
 * 全屏面板的统一清场入口，两种判据互不复用：
 * - [dismissAllPanels]：熄屏 / 锁屏 / 截图 / 语言切换前「别入镜、别留到解锁后」；
 * - [dismissContentPanelsForSystemNavigation]：Home / 最近任务前「别挡住桌面」。
 */
object GlobalOverlayDismissHelper {
    private const val TAG = "GlobalOverlayDismiss"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var dismissPosted = false

    /**
     * @param lockScreenBoundary 由熄屏/锁屏触发时传 true：悬浮球提醒交由
     *   [FloatIconOverlayWindow.handleLockScreenBoundary] 决定是清除还是保留到解锁后补显；
     *   截图、语言切换等场景保持 false（一律清除）。
     */
    fun dismissAllPanels(lockScreenBoundary: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (dismissPosted) return
            dismissPosted = true
            mainHandler.post {
                dismissPosted = false
                dismissAllPanels(lockScreenBoundary)
            }
            return
        }
        Log.i(TAG, "Dismissing all active overlay panels due to screen off / device lock")

        runCatching { SearchPanelOverlayWindow.dismiss() }
        runCatching { FileBrowserOverlayWindow.dismiss() }
        runCatching { WidgetPickerOverlayWindow.dismiss() }
        runCatching { WidgetPopupOverlayWindow.dismiss() }
        runCatching {
            // 侧边气泡也支持"锁屏保留"：和悬浮球提醒 / 贴边卡同一套分支，不能再无条件 dismiss。
            if (lockScreenBoundary) {
                SideBubbleOverlayWindow.handleLockScreenBoundary()
            } else {
                SideBubbleOverlayWindow.dismiss()
            }
        }
        runCatching { FloatBallPickResultPanel.dismiss() }
        runCatching { RegionalPickOverlay.dismiss() }
        runCatching { OhoQuickToolsOverlayWindow.dismiss() }
        runCatching { HoneycombAppPickerOverlayWindow.dismiss() }
        runCatching { RingLauncherOverlayWindow.dismiss() }
        runCatching { com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.dismiss() }
        runCatching { com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.dismiss() }
        runCatching { com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.dismiss() }
        runCatching { FloatBallImageSearchPanel.dismiss() }
        runCatching { FloatBallStashPanel.dismiss() }
        runCatching { FreezerOverlayWindow.dismiss() }
        runCatching { FloatBallTranslatePanel.dismiss() }
        runCatching {
            if (lockScreenBoundary) {
                FloatIconOverlayWindow.handleLockScreenBoundary()
            } else {
                FloatIconOverlayWindow.dismiss()
            }
        }
        runCatching { CNoticeOverlayWindow.closePanel() }
        runCatching {
            if (lockScreenBoundary) {
                // 贴边卡：按「锁屏后保留」设置决定清掉还是挂起，解锁后补显。
                EdgeCardOverlayWindow.handleLockScreenBoundary()
            } else {
                EdgeCardOverlayWindow.dismiss()
            }
        }
        runCatching { MessageReplyOverlayWindow.dismiss() }
        runCatching { ForegroundActivityInspectorOverlayWindow.dismiss() }
        runCatching { VolumePanelOverlayWindow.dismiss() }
        runCatching { FloatingPointerOverlayWindow.dismiss() }
        runCatching { HolographicLauncherOverlayWindow.dismiss() }
        runCatching { DanmakuOverlayWindow.detach() }
        runCatching { UniversalCopyOverlay.dismiss() }
        runCatching { ScreenTranslationController.dismissIfActive() }
        runCatching { com.slideindex.app.overlay.screenshot.SmartScreenshotOverlay.dismiss() }
        runCatching { com.slideindex.app.clipboardoverlay.ClipboardOverlayWindow.dismiss() }
        runCatching { com.slideindex.app.clipboardoverlay.ClipboardLinkPickerOverlay.dismissImmediate() }
    }

    /**
     * Home / 最近任务触发时的「内容面板清场」。
     *
     * 面板都是独立 overlay 窗，Home 只是把桌面拉到它们**下面**：不主动关，桌面就被盖住，
     * 而且面板还占着焦点（所以现在必须再点一次返回才关得掉）。
     *
     * 判据与 [dismissAllPanels] **不同**：那一份是「别入镜 / 别留到锁屏」，包含提醒类与常驻工具；
     * 这一份是「别挡住桌面」，只关用户正在用的一次性面板。
     *
     * **刻意不在清单里的**：
     * - 常驻工具：悬浮球 / 边缘触钮、屏幕控制条、虚拟指针、触摸锁定、伪熄屏、钉屏、弹幕、录屏台、
     *   历史 / 剪贴板常驻把手与钉住态 —— 关掉等于把用户的工具吞了；
     * - 提醒类：通知中心 / 详情、贴边卡、侧边气泡 —— 关掉属于行为扩张，未确认前不动；
     * - 前台检查器、剪贴板浮窗 —— 跨应用常驻，有自己的生命周期；
     * - 音量 / 亮度滑动指示条 —— 画在边缘手势层里，不是独立窗，且自带超时收起。
     *
     * 调用点：`SlideIndexAccessibilityGestureInjector` 的 Home / Recents 分支，先清场再
     * `performGlobalAction` —— 所有自有触发源（悬浮球 / 触钮 / 轮盘 / 摇一摇 / 屏幕控制条 / 外部调用）
     * 都汇到那一个漏斗。
     *
     * 每次 Home 都会走到这里，所以除搜索面板外每条都先判 `isShowing`：避免每次都空跑一堆延时收尾。
     */
    fun dismissContentPanelsForSystemNavigation() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissContentPanelsForSystemNavigation() }
            return
        }
        // 搜索面板走专用入口：即便当前是 hide() 的临时隐藏态（选图 / 权限 trampoline 在途），
        // 也要作废那次「回来再 restore」，否则面板会在桌面上诈尸。
        runCatching { SearchPanelOverlayWindow.dismissForSystemNavigation() }

        dismissIfShowing({ FileBrowserOverlayWindow.isShowing }, FileBrowserOverlayWindow::dismiss)
        dismissIfShowing({ WidgetPickerOverlayWindow.isShowing }, WidgetPickerOverlayWindow::dismiss)
        dismissIfShowing({ WidgetPopupOverlayWindow.isShowing }, WidgetPopupOverlayWindow::dismiss)
        dismissIfShowing({ FloatBallPickResultPanel.isShowing }, FloatBallPickResultPanel::dismiss)
        dismissIfShowing({ FloatBallImageSearchPanel.isShowing }, FloatBallImageSearchPanel::dismiss)
        dismissIfShowing({ FloatBallStashPanel.isShowing }, FloatBallStashPanel::dismiss)
        dismissIfShowing({ FloatBallTranslatePanel.isShowing }, FloatBallTranslatePanel::dismiss)
        dismissIfShowing({ UniversalCopyOverlay.isShowing }, UniversalCopyOverlay::dismiss)
        dismissIfShowing({ RegionalPickOverlay.isActive }, RegionalPickOverlay::dismiss)
        dismissIfShowing({ OhoQuickToolsOverlayWindow.isShowing }, OhoQuickToolsOverlayWindow::dismiss)
        dismissIfShowing(
            { HoneycombAppPickerOverlayWindow.isShowing },
            HoneycombAppPickerOverlayWindow::dismiss,
        )
        dismissIfShowing({ RingLauncherOverlayWindow.isShowing }, RingLauncherOverlayWindow::dismiss)
        dismissIfShowing({ HolographicLauncherOverlayWindow.isShowing }, HolographicLauncherOverlayWindow::dismiss)
        dismissIfShowing(
            { com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.isShowing },
            com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow::dismiss,
        )
        dismissIfShowing(
            { com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.isShowing },
            com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay::dismiss,
        )
        dismissIfShowing(
            { com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.isShowing },
            com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow::dismiss,
        )
        dismissIfShowing({ FreezerOverlayWindow.isShowing }, FreezerOverlayWindow::dismiss)
        dismissIfShowing(
            { com.slideindex.app.overlay.screenshot.SmartScreenshotOverlay.isShowing },
            com.slideindex.app.overlay.screenshot.SmartScreenshotOverlay::dismiss,
        )
        dismissIfShowing({ VolumePanelOverlayWindow.isShowing }, VolumePanelOverlayWindow::dismiss)

        // 屏幕翻译自己判活，不需要 isShowing。
        runCatching { ScreenTranslationController.dismissIfActive() }
        // 快捷回复没有 isShowing，但 dismiss 对未显示态是纯空转（全是 null 检查）。
        runCatching { MessageReplyOverlayWindow.dismiss() }
    }

    /** 只在面板真的显示时才调 dismiss：Home 是高频动作，不能让一堆空 dismiss 每次都排延时收尾。 */
    private inline fun dismissIfShowing(isShowing: () -> Boolean, dismiss: () -> Unit) {
        runCatching { if (isShowing()) dismiss() }
    }
}
