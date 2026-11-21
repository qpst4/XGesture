package com.slideindex.app.link

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 屏幕识别/全局复制链接雷达控制器（对齐 GestureEVO ScreenContentLinkRadar）。
 *
 * 支持两种挂载模式：
 * 1. 嵌入宿主容器（[hostContainer] != null，如全局复制 `UniversalCopyOverlay` 的根布局）：
 *    不单独新增 `TYPE_ACCESSIBILITY_OVERLAY` 窗口，避免与宿主窗口争抢焦点与触摸，点击链接胶囊直接直达并关闭全局复制；
 * 2. 独立无障碍浮层（[hostContainer] == null）：自动挂载全屏 `TYPE_ACCESSIBILITY_OVERLAY`。
 */
class ScreenContentLinkRadar(
    private val service: AccessibilityService,
    private val hostContainer: ViewGroup? = null,
    private val insertIndex: Int = -1,
    private val preScannedLinks: List<LinkRegion>? = null,
    private val onLinkOpened: () -> Unit,
) {
    private var scope: CoroutineScope? = null
    private var radarView: LinkRadarView? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false

    fun start() {
        stop()
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = jobScope

        jobScope.launch {
            val enabled = LinkRuleRepository.isRadarScreenContentEnabled(service)
            if (!enabled) return@launch

            val links = preScannedLinks ?: withContext(Dispatchers.Default) {
                LinkRadarScanner.scan(service)
            }
            if (scope !== jobScope || links.isEmpty()) return@launch

            showRadarOverlay(links, jobScope)
        }
    }

    private fun showRadarOverlay(links: List<LinkRegion>, jobScope: CoroutineScope) {
        val view = LinkRadarView(service).apply {
            showScreenContentLinks(links)
            onLinkClickListener = { link ->
                jobScope.launch {
                    LinkLauncher.openUrl(service, link.url, link.sourcePackage, link.sourceText)
                    onLinkOpened()
                    stop()
                }
            }
        }
        radarView = view

        val container = hostContainer
        if (container != null) {
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            runCatching {
                if (insertIndex in 0..container.childCount) {
                    container.addView(view, insertIndex, lp)
                } else {
                    container.addView(view, lp)
                }
                isShowing = true
            }
        } else {
            val wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as? WindowManager ?: return
            windowManager = wm
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.TOP or Gravity.START
            }
            runCatching {
                wm.addView(view, params)
                isShowing = true
            }
        }

        // 异步并发预测每个分组的目标 App 图标与标签，并在布局就绪后刷新横幅
        val groups = links.groupBy { it.groupId }
        for ((groupId, groupLinks) in groups) {
            val primaryLink = groupLinks.firstOrNull() ?: continue
            if (groupLinks.size > 1) continue
            jobScope.launch {
                try {
                    val shortRuleName = runCatching {
                        LinkDispatcher.pendingShortLinkRuleName(service, primaryLink.url)
                    }.getOrNull()
                    if (shortRuleName != null && scope === jobScope) {
                        view.post {
                            view.updateActionPrompt(groupId, "正在解析 ($shortRuleName)…", null)
                        }
                    }
                    val preview = previewLinkTarget(
                        service,
                        primaryLink.url,
                        primaryLink.sourcePackage,
                        primaryLink.sourceText,
                    )
                    if (scope !== jobScope) return@launch
                    val label = if (preview != null) "在 ${preview.label} 中打开" else "打开链接"
                    view.post {
                        view.updateActionPrompt(groupId, label, preview?.rawIcon)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        val view = radarView
        val container = hostContainer
        val wm = windowManager
        if (view != null && isShowing) {
            if (container != null) {
                runCatching { container.removeView(view) }
            } else if (wm != null) {
                runCatching { wm.removeView(view) }
            }
        }
        radarView = null
        windowManager = null
        isShowing = false
    }
}
