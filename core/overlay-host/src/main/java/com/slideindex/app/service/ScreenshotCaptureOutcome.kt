package com.slideindex.app.service

/**
 * 无障碍截图的结果。
 *
 * 从 `SlideIndexAccessibilityService` 顶部抽出：面板模块（`:feature:overlay-panels`）
 * 需要判断截图结果，但不能依赖 `:app` 的服务实现。
 */
sealed interface ScreenshotCaptureOutcome {

    data class Success(val bitmap: android.graphics.Bitmap) : ScreenshotCaptureOutcome

    /** 无障碍服务未连接，或系统低于 API 30。 */
    data object ServiceUnavailable : ScreenshotCaptureOutcome

    /** 系统限流：距上一次截图太近。 */
    data object Throttled : ScreenshotCaptureOutcome

    /** 其它失败，[errorCode] 为系统返回码。 */
    data class Failed(val errorCode: Int) : ScreenshotCaptureOutcome
}
