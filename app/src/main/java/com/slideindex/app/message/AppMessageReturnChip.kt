package com.slideindex.app.message

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationAppLauncher
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.OverlayToastWindow
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.AccessibilityForegroundResolver
import com.slideindex.app.util.LockScreenState
import com.slideindex.app.util.TaskExclusions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读消息后的「返回上一应用」贴边胶囊。
 *
 * 触发链路：消息提醒里点「阅读」/「打开应用」→ [MessageActionExecutor] 记下打开前的
 * 前台应用 → 等目标应用真正到前台，就在屏幕左边缘浮出胶囊；点击胶囊回到打开消息前的应用。
 *
 * 判定原则（不做全屏检测）：
 * - 普通阅读（[MessageAction.Read]）本身就是全屏打开，目标到前台即可显示；
 * - 小窗阅读（[MessageAction.ReadInSmallWindow]）成功时来源应用仍可见，此时不显示；
 *   小窗失败退化成全屏时来源不可见，照常显示。
 *
 * 显示后是否收起**不看前台包名**，而看目标 App 的窗口还在不在屏幕上；前台包名只在等待阶段
 * 和窗口信息不可用时兜底。前台包名由窗口事件拼出来，切换瞬间会闪（从我们自己的边栏跳过去时
 * 尤其容易闪成自己），拿它判收起会误收。
 */
@Singleton
class AppMessageReturnChip @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : MessageReturnPort {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val chipState = mutableStateOf<ReturnChipState?>(null)

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private var screenOffReceiver: ScreenOffDismissReceiver? = null

    private var pollRunnable: Runnable? = null
    private var autoDismissRunnable: Runnable? = null

    /** 每次 arm / dismiss 自增，用来让旧的重试与轮询任务失效。 */
    private var token = 0
    private var sourcePackage: String? = null
    private var targetPackage: String? = null
    private var smallWindowRead = false

    /** 等目标应用期间最后一次打日志的前台状态，只在状态变化时打，避免刷屏。 */
    private var lastWaitingForeground: ReturnChipForeground? = null

    /** 胶囊显示时刻，用于点击防抖。 */
    private var shownAtMs = 0L

    /** 连续「目标窗口不在屏幕上」的采样次数。 */
    private var missCount = 0

    override fun arm(
        context: Context,
        sourcePackage: String?,
        targetPackage: String,
        settings: MessageSettings,
        smallWindowRead: Boolean,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { arm(context, sourcePackage, targetPackage, settings, smallWindowRead) }
            return
        }
        dismissInternal(reason = "arm-replace")
        if (!settings.returnChipEnabled) return
        if (!isUsableSource(sourcePackage)) {
            Log.d(TAG, "arm skipped source=$sourcePackage target=$targetPackage")
            return
        }
        if (targetPackage.isBlank() || targetPackage == appContext.packageName) return
        val armToken = ++token
        this.sourcePackage = sourcePackage
        this.targetPackage = targetPackage
        this.smallWindowRead = smallWindowRead
        this.lastWaitingForeground = null
        Log.i(TAG, "arm source=$sourcePackage target=$targetPackage smallWindow=$smallWindowRead")
        val startedAt = SystemClock.uptimeMillis()
        mainHandler.postDelayed(
            { waitForTarget(armToken, settings.returnChipAutoDismissSeconds, startedAt) },
            FIRST_DELAY_MS,
        )
    }

    override fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        dismissInternal(reason = "port-dismiss")
    }

    /**
     * 等目标应用到前台：最长等 [MAX_WAIT_MS]，到了就立刻判、立刻显示。
     *
     * 前台拿不到（无障碍没连）时只等 [UNKNOWN_FALLBACK_MS] 就按「阅读默认全屏打开」显示，
     * 不把用户晾在 8 秒之后。
     */
    private fun waitForTarget(armToken: Int, autoDismissSeconds: Int, startedAt: Long) {
        if (armToken != token) return
        val source = sourcePackage ?: return
        val target = targetPackage ?: return
        val elapsed = SystemClock.uptimeMillis() - startedAt
        val foreground = resolveForeground(target)
        if (foreground != lastWaitingForeground) {
            lastWaitingForeground = foreground
            Log.d(
                TAG,
                "wait foreground=$foreground raw=${currentForegroundPackage()} " +
                    "elapsed=${elapsed}ms target=$target",
            )
        }

        val canDecide = when (foreground) {
            ReturnChipForeground.TARGET -> true
            ReturnChipForeground.UNKNOWN -> elapsed >= UNKNOWN_FALLBACK_MS
            ReturnChipForeground.OTHER -> elapsed >= MAX_WAIT_MS
        }
        if (!canDecide) {
            mainHandler.postDelayed(
                { waitForTarget(armToken, autoDismissSeconds, startedAt) },
                POLL_INTERVAL_MS,
            )
            return
        }

        val screenLocked = LockScreenState.isActive(appContext)
        val sourceStillVisible = smallWindowRead && isSourceVisible(source)
        if (!shouldShowReturnChip(
                sourcePackage = source,
                targetPackage = target,
                foreground = foreground,
                screenLocked = screenLocked,
            ) || sourceStillVisible
        ) {
            Log.d(
                TAG,
                "show skipped source=$source target=$target foreground=$foreground " +
                    "locked=$screenLocked smallWindow=$smallWindowRead sourceVisible=$sourceStillVisible",
            )
            dismissInternal(reason = "wait-giveup:$foreground")
            return
        }
        showChip(armToken, autoDismissSeconds)
    }

    private fun showChip(armToken: Int, autoDismissSeconds: Int) {
        if (armToken != token) return
        val source = sourcePackage ?: return
        val label = resolveAppLabel(source)
        chipState.value = ReturnChipState(
            sourcePackage = source,
            label = label,
            icon = resolveAppIcon(source),
        )
        if (!ensureWindow()) {
            chipState.value = null
            dismissInternal(reason = "add-window-failed")
            return
        }
        scheduleAutoDismiss(armToken, autoDismissSeconds)
        startForegroundWatch(armToken)
        Log.i(TAG, "show source=$source target=$targetPackage seconds=$autoDismissSeconds")
    }

    private fun ensureWindow(): Boolean {
        if (composeView != null) return true
        val host = MessageOverlayHost.resolveHostContext(appContext) ?: return false
        val wm = host.getSystemService(WindowManager::class.java) ?: return false
        val owner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(host, owner).apply {
            isClickable = true
            isFocusable = false
            setContent {
                OverlayAwareModuleTheme {
                    chipState.value?.let { state ->
                        ReturnChipContent(state = state) { onChipTapped(state) }
                    }
                }
            }
        }
        val params = buildLayoutParams(host)
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.w(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            OverlayCompose.disposeComposeView(view)
            owner.destroy()
            return false
        }
        windowManager = wm
        composeView = view
        composeOwner = owner
        screenOffReceiver = ScreenOffDismissReceiver { dismissInternal(reason = "screen-off") }.also {
            it.register(host)
        }
        return true
    }

    private fun buildLayoutParams(host: Context): WindowManager.LayoutParams {
        val (_, screenHeightPx) = OverlayScreenMetrics.sizePx(host)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(host),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (screenHeightPx * TOP_Y_FRACTION).roundToInt()
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
            alpha = 1f
        }
    }

    private fun scheduleAutoDismiss(armToken: Int, seconds: Int) {
        if (armToken != token) return
        val delayMs = seconds.coerceIn(0, 60) * 1000L
        if (delayMs <= 0L) return
        val runnable = Runnable {
            if (armToken == token) dismissInternal(reason = "timeout:${seconds}s")
        }
        autoDismissRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * 显示后的收起判定：**看目标 App 的窗口还在不在屏幕上**，而不是看前台包名。
     *
     * 前台包名由无障碍窗口事件拼出来，切换瞬间会闪（从我们自己的边栏跳过去时尤其明显）；
     * 窗口列表是系统当下真实状态，目标窗口还在就说明用户没离开。
     * 连续 [MAX_GONE_SAMPLES] 次看不到目标窗口才收起，容忍一次过渡。
     * 窗口列表拿不到（无障碍断开等）时退回前台包名判断，同样连坐两次才收。
     */
    private fun startForegroundWatch(armToken: Int) {
        val target = targetPackage ?: return
        val startedAt = SystemClock.uptimeMillis()
        shownAtMs = startedAt
        missCount = 0
        val runnable = object : Runnable {
            override fun run() {
                if (armToken != token) return
                if (LockScreenState.isActive(appContext)) {
                    dismissInternal(reason = "lock")
                    return
                }
                // 打开后的头 1 秒会夹着启动动画/系统窗口，先不做判定。
                if (SystemClock.uptimeMillis() - startedAt >= FOREGROUND_GRACE_MS) {
                    val targetOnScreen = isTargetStillOnScreen(target)
                    if (targetOnScreen == null) {
                        val foreground = currentForegroundPackage()
                        if (!foreground.isNullOrBlank() && foreground != target) {
                            missCount++
                        } else {
                            missCount = 0
                        }
                        if (missCount >= MAX_GONE_SAMPLES) {
                            dismissInternal(reason = "foreground:$foreground")
                            return
                        }
                    } else if (targetOnScreen) {
                        missCount = 0
                    } else {
                        missCount++
                        if (missCount >= MAX_GONE_SAMPLES) {
                            dismissInternal(reason = "target-gone:$missCount")
                            return
                        }
                    }
                }
                mainHandler.postDelayed(this, FOREGROUND_POLL_MS)
            }
        }
        pollRunnable = runnable
        mainHandler.postDelayed(runnable, FOREGROUND_POLL_MS)
    }

    /**
     * 目标应用是否还在屏幕上。
     *
     * 返回 null 表示窗口信息不可用（无障碍断开 / 读不到任何应用窗口的包名），
     * 调用方应退回前台包名判断；false 表示列表可读但没有目标窗口。
     */
    private fun isTargetStillOnScreen(target: String): Boolean? {
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return null
        val windows = runCatching { service.windows }.getOrNull() ?: return null
        var readable = false
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val packageName = runCatching { window.root?.packageName?.toString() }
                .getOrNull()
                .orEmpty()
            if (packageName.isEmpty()) continue
            readable = true
            if (packageName == target) return true
        }
        return if (readable) false else null
    }

    private fun onChipTapped(state: ReturnChipState) {
        val sinceShownMs = SystemClock.uptimeMillis() - shownAtMs
        if (sinceShownMs < TAP_GUARD_MS) {
            Log.d(TAG, "tap ignored: ${sinceShownMs}ms after show")
            return
        }
        val source = state.sourcePackage
        dismissInternal(reason = "tap")
        Thread {
            val opened = runCatching {
                NotificationAppLauncher.open(appContext, source)
            }.getOrDefault(false)
            if (!opened) {
                mainHandler.post {
                    OverlayToastWindow.show(
                        appContext,
                        appContext.getString(R.string.message_return_chip_failed, state.label),
                    )
                }
            }
        }.start()
    }

    private fun dismissInternal(reason: String) {
        Log.i(
            TAG,
            "dismiss reason=$reason showing=${composeView != null} " +
                "source=$sourcePackage target=$targetPackage smallWindow=$smallWindowRead",
        )
        token++
        pollRunnable?.let(mainHandler::removeCallbacks)
        pollRunnable = null
        autoDismissRunnable?.let(mainHandler::removeCallbacks)
        autoDismissRunnable = null
        screenOffReceiver?.unregister()
        screenOffReceiver = null

        val view = composeView
        val wm = windowManager
        val owner = composeOwner
        composeView = null
        windowManager = null
        composeOwner = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
                .onFailure { Log.w(TAG, "removeView failed", it) }
        }
        if (view != null) {
            OverlayCompose.teardownOverlayCompose(view, owner)
        } else {
            owner?.destroy()
        }
        chipState.value = null
        sourcePackage = null
        targetPackage = null
        smallWindowRead = false
        lastWaitingForeground = null
        shownAtMs = 0L
        missCount = 0
    }

    private fun resolveForeground(target: String): ReturnChipForeground {
        val foreground = currentForegroundPackage()
        return when {
            foreground.isNullOrBlank() -> ReturnChipForeground.UNKNOWN
            foreground == target -> ReturnChipForeground.TARGET
            else -> ReturnChipForeground.OTHER
        }
    }

    /**
     * 实时前台：先问无障碍 tracker（窗口事件即时更新），再问窗口树解析，
     * 最后才退回 `OverlayService.foregroundPackage` 缓存。
     */
    private fun currentForegroundPackage(): String? =
        SlideIndexAccessibilityService.currentForegroundPackage()
            ?: AccessibilityForegroundResolver.resolve(appContext)
            ?: OverlayService.foregroundPackage

    /** 来源应用此刻是否还留在屏幕上（小窗阅读成功时它是可见窗口）。 */
    private fun isSourceVisible(source: String): Boolean {
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return false
        val windows = runCatching { service.windows }.getOrNull() ?: return false
        return windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                runCatching { window.root?.packageName?.toString() }
                    .getOrNull()
                    .orEmpty() == source
        }
    }

    private fun isUsableSource(sourcePackage: String?): Boolean {
        if (sourcePackage.isNullOrBlank()) return false
        if (sourcePackage in launcherPackages()) return false
        if (sourcePackage in imePackages()) return false
        return NotificationAppLauncher.canOpen(appContext, sourcePackage)
    }

    private fun launcherPackages(): Set<String> {
        val resolved = runCatching {
            appContext.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()
        return TaskExclusions.LAUNCHER_AND_SYSTEM + listOfNotNull(resolved)
    }

    private fun imePackages(): Set<String> = runCatching {
        appContext.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.mapTo(HashSet()) { it.packageName }
    }.getOrNull().orEmpty()

    private fun resolveAppLabel(packageName: String): String = runCatching {
        val info = appContext.packageManager.getApplicationInfo(packageName, 0)
        appContext.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun resolveAppIcon(packageName: String): ImageBitmap? = runCatching {
        appContext.packageManager.getApplicationIcon(packageName)
            .toBitmap(ICON_PX, ICON_PX)
            .asImageBitmap()
    }.getOrNull()

    private companion object {
        const val TAG = "MessageReturnChip"

        /** 点完阅读先缓一拍，避免抢在目标应用启动前面。 */
        const val FIRST_DELAY_MS = 250L

        /** 等目标到前台的轮询间隔。 */
        const val POLL_INTERVAL_MS = 300L

        /** 目标应用最晚等这么久；期间任一次命中就立刻显示。 */
        const val MAX_WAIT_MS = 8_000L

        /** 拿不到前台时的兜底显示时间（无障碍没连等）。 */
        const val UNKNOWN_FALLBACK_MS = 1_000L

        /** 显示后检查「目标窗口还在不在」的间隔与宽限。 */
        const val FOREGROUND_POLL_MS = 500L
        const val FOREGROUND_GRACE_MS = 1_000L

        /** 连续多少次看不到目标窗口才收起（容忍一次过渡）。 */
        const val MAX_GONE_SAMPLES = 2

        /** 显示后这段时间内的点击忽略，避免收窗瞬间的误触。 */
        const val TAP_GUARD_MS = 300L

        /** 胶囊垂直位置：左边缘、屏幕高度 22% 处（参考悬浮菜单的贴边胶囊）。 */
        const val TOP_Y_FRACTION = 0.22f
        const val ICON_PX = 96
    }
}

private data class ReturnChipState(
    val sourcePackage: String,
    val label: String,
    val icon: ImageBitmap?,
)

@Composable
private fun ReturnChipContent(
    state: ReturnChipState,
    onClick: () -> Unit,
) {
    val description = stringResource(
        R.string.message_return_chip_content_description,
        state.label,
    )
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        color = Color(0xE6252525),
        contentColor = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.icon?.let { icon ->
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape),
                )
            }
            Text(
                text = stringResource(R.string.message_return_chip_label),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
