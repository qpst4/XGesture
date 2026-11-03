package com.slideindex.app.message

import android.content.Context
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.AccessibilityForegroundResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMessageForegroundPort @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : MessageForegroundPort {
    override fun foregroundPackage(): String? =
        OverlayService.foregroundPackage ?: SlideIndexAccessibilityService.currentForegroundPackage()

    /**
     * 实时前台：tracker 由窗口事件即时更新；窗口树解析带事件失效缓存；
     * `OverlayService.foregroundPackage` 只是最后兜底。
     */
    override fun liveForegroundPackage(): String? =
        SlideIndexAccessibilityService.currentForegroundPackage()
            ?: AccessibilityForegroundResolver.resolve(appContext)
            ?: OverlayService.foregroundPackage
}
