package com.slideindex.app.util

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FreeWindowLayoutDp
import com.slideindex.app.settings.resolvedFreeWindowLayout
import com.slideindex.app.settings.resolvedFreeWindowLayoutDp
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.usesNubiaFreeformIdentifier
import com.slideindex.app.settings.usesSunshineBroadcast

import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowManager
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.FreeWindowShareProxyActivity
import com.slideindex.app.settings.FreeWindowMode
import kotlin.math.roundToInt

object FreeWindowLauncher {
    /**
     * "这次分享请求来自本应用"的标识。
     *
     * 壳 Activity 是导出的（系统分享流程才能拉起来），所以它必然也会收到别的应用发来的真实分享。
     * 没有这个标识就不处理——不然后果是把别人分享的正文当包名去启动。
     */
    const val EXTRA_FREEWINDOW_REQUEST = "com.slideindex.app.extra.FREEWINDOW_REQUEST"

    private const val TAG = "FreeWindowLauncher"
    private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
    private const val NUBIA_FREEFORM_INTENT_IDENTIFIER = "_WindowReply"
    private const val HUAWEI_FREEFORM_STACK_ID = 2
    private const val MIUI_PORTRAIT_SCALE = 0.7f
    private const val MIUI_LANDSCAPE_SCALE = 0.555f

    /** 米窗（Sunshine）包名与它约定的两条广播。 */
    private const val SUNSHINE_PACKAGE = "com.sunshine.freeform"
    private const val ACTION_SUNSHINE_START_FREEFORM = "com.sunshine.freeform.start_freeform"
    private const val ACTION_SUNSHINE_START_BY_MI_FREEFORM = "com.sunshine.freeform.start_by_mi_freeform"
    private const val EXTRA_SUNSHINE_PACKAGE_NAME = "packageName"
    private const val EXTRA_SUNSHINE_ACTIVITY_NAME = "activityName"

    /** 小窗底部安全边距：状态栏高度与 20mm（≈126dp）取大者，避免压住手势条 / 输入法区。 */
    private const val BOTTOM_SAFE_MARGIN_MM = 20f

    fun launch(context: Context, intent: Intent, settings: AppSettings, fullscreen: Boolean) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!fullscreen && settings.freeWindowEnabled) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (fullscreen || !settings.freeWindowEnabled) {
            context.startActivity(intent)
            return
        }

        val mode = settings.resolvedFreeWindowMode()
        if (mode.usesSunshineBroadcast() && launchSunshineFreeform(context, intent)) {
            return
        }
        if (mode.usesNubiaFreeformIdentifier()) {
            launchNubiaFreeform(context, intent)
            return
        }

        if (mode == FreeWindowMode.ORIGINOS) {
            if (launchOriginOsShareProxy(context, intent)) {
                return
            }
        }

        val bundle = launchOptionsBundle(context, settings) ?: Bundle()
        runCatching {
            context.startActivity(intent, bundle)
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "startActivity failed", error)
        }
    }

    /**
     * 以小窗启动某个应用（照搬 SideGesture「应用小窗(7.0+)」的做法）：
     * 解析该包的 launcher Activity → `Intent(ACTION_MAIN + CATEGORY_LAUNCHER)` → 一次
     * `startActivity(intent, ActivityOptions)`。
     *
     * 与 [launch] 的差别只有 flags：这里只带 NEW_TASK，不叠加
     * REORDER_TO_FRONT / SINGLE_TOP，也不叠加 MULTIPLE_TASK —— 复用还是新建任务交给系统；
     * 不校验、不重试、不搬移已有任务。
     */
    fun launchPackageInFreeWindow(
        context: Context,
        packageName: String,
        settings: AppSettings,
    ): Boolean {
        if (packageName.isBlank()) return false
        val component = resolveLauncherComponent(context, packageName) ?: return false
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            this.component = component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val mode = settings.resolvedFreeWindowMode()
        if (mode.usesSunshineBroadcast() && launchSunshineFreeform(context, intent)) {
            return true
        }
        if (mode.usesNubiaFreeformIdentifier()) {
            launchNubiaFreeform(context, intent)
            return true
        }
        if (mode == FreeWindowMode.ORIGINOS && launchOriginOsShareProxy(context, intent)) {
            return true
        }

        val bundle = launchOptionsBundle(context, settings) ?: return false
        return runCatching {
            context.startActivity(intent, bundle)
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "launchPackageInFreeWindow($packageName) failed", error)
        }.isSuccess
    }

    /** ACTION_MAIN + CATEGORY_LAUNCHER 查询该包的 launcher Activity（对齐 SideGesture）。 */
    private fun resolveLauncherComponent(context: Context, packageName: String): ComponentName? {
        val query = Intent(Intent.ACTION_MAIN).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val className = runCatching {
            context.packageManager
                .queryIntentActivitiesCompat(query, PackageManager.MATCH_ALL)
                .firstOrNull()
                ?.activityInfo
                ?.name
        }.getOrNull()
        return className
            ?.takeIf { it.isNotBlank() }
            ?.let { ComponentName.createRelative(packageName, it) }
    }

    private fun launchOriginOsShareProxy(context: Context, targetIntent: Intent): Boolean {
        return runCatching {
            context.startActivity(buildOriginOsShareChooser(context, targetIntent))
            true
        }.getOrElse { error ->
            android.util.Log.e(TAG, "OriginOS share proxy launch failed", error)
            false
        }
    }

    /**
     * 构造"假分享"：接收方是本应用的透明壳，真正的目标应用藏在 [Intent.EXTRA_INTENT] 里。
     *
     * 这一步不申请任何窗口参数。OriginOS 的「小窗分享」会把**这次分享的接收方**开成小窗，
     * 壳再从小窗里把目标拉起来，目标于是继续落在小窗里——窗口形态完全交给系统决定。
     *
     * 发送方必须是**系统分享流程**（`Intent.createChooser`），直接 `startActivity` 一条 SEND
     * 不算分享目标，系统不会套用「小窗分享」。
     */
    fun buildOriginOsShareChooser(context: Context, targetIntent: Intent): Intent {
        val forwarded = normalizeForwardedIntent(targetIntent)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            component = ComponentName(context, FreeWindowShareProxyActivity::class.java)
            type = "text/plain"
            putExtra(EXTRA_FREEWINDOW_REQUEST, true)
            putExtra(Intent.EXTRA_INTENT, forwarded)
            // 形状上像一次真分享：EXTRA_TEXT 带目标包名。系统/壳都靠它做兜底解析。
            putExtra(
                Intent.EXTRA_TEXT,
                forwarded.`package` ?: forwarded.component?.packageName
                    ?: resolveTargetPackage(context, forwarded).orEmpty(),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 转发给目标的 Intent 只保留"普通启动"的形状：`NEW_TASK` + `SINGLE_TOP`。
     *
     * 去掉 `REORDER_TO_FRONT`（会把已有任务直接提到前台，等于明确要求"不要新窗口"）与
     * `MULTIPLE_TASK`；其余 flag 原样保留（深链、URI 授权一类都还要用）。
     */
    private fun normalizeForwardedIntent(source: Intent): Intent = Intent(source).apply {
        flags = (flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) and
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT.inv() and
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK.inv()
    }

    /** 没有显式包名时（例如纯 URL 的 VIEW）用解析结果补一个，只用于填充 EXTRA_TEXT 与壳的校验。 */
    private fun resolveTargetPackage(context: Context, intent: Intent): String? = runCatching {
        context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    }.getOrNull()

    private fun launchNubiaFreeform(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier = NUBIA_FREEFORM_INTENT_IDENTIFIER
        }
        runCatching {
            context.startActivity(intent, Bundle())
        }.onFailure { error ->
            android.util.Log.e("FreeWindowLauncher", "nubia freeform startActivity failed", error)
        }
    }

    /**
     * 米窗（Sunshine）开小窗：向米窗发两条广播，请它代为把目标应用拉进小窗。
     *
     * 与「一次 ActivityOptions 启动」的差别：
     * - 米窗未安装（或广播发不出去）时返回 false，调用方**回落**到自由窗口的兜底模式值，
     *   而不是"看起来成功、实际什么都没发生"；
     * - 广播即发即忘，米窗装上之后这一次到底有没有开出小窗，这里无法校验。
     */
    private fun launchSunshineFreeform(context: Context, targetIntent: Intent): Boolean {
        val packageName = targetPackageOf(targetIntent)
        if (packageName.isNullOrBlank()) return false
        if (!isPackageInstalled(context, SUNSHINE_PACKAGE)) return false
        val activityName = targetIntent.component?.className
        return runCatching {
            context.sendBroadcast(
                Intent(ACTION_SUNSHINE_START_FREEFORM).apply {
                    setPackage(SUNSHINE_PACKAGE)
                    putExtra(EXTRA_SUNSHINE_PACKAGE_NAME, packageName)
                    activityName?.takeIf { it.isNotBlank() }
                        ?.let { putExtra(EXTRA_SUNSHINE_ACTIVITY_NAME, it) }
                },
            )
            context.sendBroadcast(
                Intent(ACTION_SUNSHINE_START_BY_MI_FREEFORM).apply {
                    setPackage(SUNSHINE_PACKAGE)
                    putExtra(EXTRA_SUNSHINE_PACKAGE_NAME, packageName)
                    activityName?.takeIf { it.isNotBlank() }
                        ?.let { putExtra(EXTRA_SUNSHINE_ACTIVITY_NAME, it) }
                },
            )
            true
        }.getOrElse { error ->
            android.util.Log.e(TAG, "sunshine freeform broadcast failed pkg=$packageName", error)
            false
        }
    }

    /**
     * 目标包名的两个来源：显式 `setPackage`，或组件所属包名。
     * （`Intent.setComponent` 不会回填 `package`，只读 `intent.package` 会拿到 null。）
     */
    private fun targetPackageOf(intent: Intent): String? =
        intent.`package` ?: intent.component?.packageName

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
    }.isSuccess

    fun launchOptionsBundle(context: Context, settings: AppSettings): Bundle? {
        if (!settings.freeWindowEnabled) return null
        val options = ActivityOptions.makeBasic()
        val mode = settings.resolvedFreeWindowMode()
        applyWindowingMode(options, mode.windowingMode)
        if (mode == FreeWindowMode.MAGICOS) {
            applyStackId(options, HUAWEI_FREEFORM_STACK_ID)
        }
        options.setLaunchBounds(launchBounds(context, settings))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val backgroundStartMode = when {
                Build.VERSION.SDK_INT >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                else -> @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            options.pendingIntentBackgroundActivityStartMode = backgroundStartMode
        }
        val bundle = options.toBundle() ?: Bundle()
        if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
            bundle.putInt(KEY_WINDOWING_MODE, mode.windowingMode)
        }
        return bundle
    }

    /**
     * 启动用的 launch bounds，坐标语义对齐 LinkGo-X 的 `WindowConfig.calculateBounds`：
     * 用户配置的百分比 = **真机上看得见的尺寸**，补偿系数只作用在发给系统的逻辑矩形上
     * （小米的 freeform 会按 `original_scale` 把逻辑尺寸缩到约 0.7 再显示，见
     * `MiuiMultiWindowUtils` 的 freeform args 档位表），所以逻辑尺寸 = 可见尺寸 / 补偿。
     *
     * 与 LinkGo-X 的唯一差别在这里：LinkGo-X 允许逻辑矩形溢出屏幕、也不夹 left/top，
     * 结果小米的 fit-in-display（AOSP `LaunchParamsUtil.adjustBoundsToFitInDisplayArea`）
     * 会把窗口整体推回屏内 —— 一旦溢出就贴左，位置配置等于失效。本实现先把**可见**
     * 尺寸夹进屏幕，再反推逻辑尺寸，位置百分比因此始终生效。
     *
     * 另外三条就地生效的规则：
     * - **屏幕尺寸与朝向**取自 WindowManager（见 [resolveDisplaySize]），不读会被旋转落下的
     *   `resources.configuration`；横屏算错宽高会让逻辑矩形整段出屏、被系统无视。
     * - **底部安全边距**：可用高度先扣掉 `max(状态栏高度, 20mm)`，再把可见高与上边距夹进去，
     *   避免小窗压住手势条与输入法起始区；配置本来就放得下时结果不变。
     * - **可达上限**：逻辑矩形不能溢出屏幕，所以可见尺寸上限 = 屏幕 × 补偿系数（小米竖屏 70%、
     *   横屏 55.5%）。设置页与编辑器用 [deliverableLimitFraction] 把这个上限提前告诉用户，
     *   而不是等到启动时静默夹掉。
     * - **跟随触发位置**：开启后水平贴到触发那一侧、竖直以触发高度居中；拿不到触发信息
     *   （或已过期）时原样使用配置值。见 [FreeWindowTriggerAnchor]。
     */
    /**
     * 启动用的 launch bounds，完全对齐 GestureEVO 的 FreeFormUtils.getRect 算法：
     *
     * 1. 物理 dp 尺寸体系：横屏与竖屏完全独立配置宽度、高度、边距。
     * 2. 安全边距：底部预留 max(状态栏高度, 20mm≈126dp)，确保不遮挡手势横条与输入法区。
     * 3. 小米 MIUI 13+ / HyperOS 专项补偿：
     *    - 小米自由小窗底层以左上角为锚点缩放，竖屏强制 0.70x（补偿 1.42f），横屏普通手机 0.55x（补偿 1.8f），
     *      大屏/折叠屏展开态维持 1.42f；launchBounds 宽高乘以补偿系数，left 与 top 原样作为起点。
     * 4. 跟随触发位置：开启后水平贴到触控侧（计算 edgeMargin）、竖直以触发高度居中。
     * 5. 非 MIUI 系统：调用 fitRectOnScreen 保证矩形完全收容在可见屏幕内。
     */
    fun launchBounds(context: Context, settings: AppSettings): Rect {
        val display = resolveDisplaySize(context)
        val screenWidth = display.widthPx
        val screenHeight = display.heightPx
        val isLandscape = display.isLandscape
        val isPortrait = !isLandscape

        val statusBarHeight = statusBarHeightPx(context)
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f

        // 20mm 安全边距换算成 px (20mm / 25.4mm/in * 160dp/in ≈ 126dp)
        val minBottomMarginPx = (126 * density).toInt()
        val targetBottomMargin = maxOf(statusBarHeight, minBottomMarginPx)

        val layout = settings.resolvedFreeWindowLayoutDp(isLandscape)
        val followTrigger = settings.freeWindowFollowTriggerSide

        // 1. 约束宽度：确保窗口宽度至少 100px，且不超过屏幕宽度
        val maxAllowedW = screenWidth.coerceAtLeast(100)
        val w = (layout.widthDp * density).toInt().coerceIn(100, maxAllowedW)

        // 2. 约束高度与底部边距：确保在任何情况下窗口底部绝不侵入 20mm 安全边距
        val bottomMargin = targetBottomMargin
        val maxAllowedH = (screenHeight - statusBarHeight - bottomMargin).coerceAtLeast(100)
        val h = (layout.heightDp * density).toInt().coerceIn(100, maxAllowedH)

        val anchor = if (followTrigger) FreeWindowTriggerAnchor.peek() else null
        val left = resolveLeft(isPortrait, layout, density, w, screenWidth, followTrigger, anchor)
        val top = resolveTop(layout, density, h, screenHeight, statusBarHeight, bottomMargin, followTrigger, anchor)

        android.util.Log.d(
            TAG,
            "getRect: left=$left, top=$top, w=$w, h=$h, followTrigger=$followTrigger, isPortrait=$isPortrait",
        )

        // 3. 小米 MIUI 13+ / HyperOS 专用缩放补偿（对齐 GestureEVO）
        if (FreeWindowRomHelper.isMiui13Plus(context)) {
            val compensation = FreeWindowRomHelper.calculateMiuiCompensation(
                context, screenWidth, screenHeight, isPortrait,
            )
            val launchW = (w * compensation).toInt().coerceAtLeast(100)
            val launchH = (h * compensation).toInt().coerceAtLeast(100)
            // MIUI 以左上角为锚点缩放，所以 targetLeft 与 targetTop 直接作为起始边界
            val miuiBounds = Rect(left, top, left + launchW, top + launchH)
            android.util.Log.d(TAG, "MIUI compensation=$compensation launchBounds → $miuiBounds")
            return miuiBounds
        }

        // 4. 其他系统：保证窗口在屏幕可见区域内
        val bounds = Rect(left, top, left + w, top + h)
        val finalBounds = fitRectOnScreen(bounds, screenWidth, screenHeight, statusBarHeight, bottomMargin)
        android.util.Log.d(TAG, "Standard launchBounds → $finalBounds")
        return finalBounds
    }

    private fun resolveLeft(
        isPortrait: Boolean,
        layout: FreeWindowLayoutDp,
        density: Float,
        widthPx: Int,
        screenWidth: Int,
        followTrigger: Boolean,
        anchor: FreeWindowTriggerAnchor.Anchor?,
    ): Int {
        val maxLeft = (screenWidth - widthPx).coerceAtLeast(0)
        val rawSavedLeft = (layout.leftDp * density).toInt()
        val savedLeft = rawSavedLeft.coerceIn(0, maxLeft)
        if (!followTrigger || anchor == null) return savedLeft

        // 判断触发侧（左还是右）
        val rawX = anchor.rawX
        val isLeft = when {
            anchor.side == PanelSide.LEFT -> true
            anchor.side == PanelSide.RIGHT -> false
            rawX != null && screenWidth > 0 -> rawX < screenWidth / 2f
            else -> return savedLeft
        }

        val edgeMargin = calculateEdgeMargin(
            isPortrait = isPortrait,
            savedEdgeMarginDp = layout.edgeMarginDp,
            rawSavedLeft = rawSavedLeft,
            widthPx = widthPx,
            screenWidth = screenWidth,
            maxLeft = maxLeft,
            density = density,
        )

        return if (isLeft) {
            edgeMargin.coerceIn(0, maxLeft)
        } else {
            (screenWidth - widthPx - edgeMargin).coerceIn(0, maxLeft)
        }
    }

    private fun calculateEdgeMargin(
        isPortrait: Boolean,
        savedEdgeMarginDp: Int,
        rawSavedLeft: Int,
        widthPx: Int,
        screenWidth: Int,
        maxLeft: Int,
        density: Float,
    ): Int {
        if (savedEdgeMarginDp > 0) {
            return (savedEdgeMarginDp * density).toInt()
        }
        val defaultPortraitLeft = (70 * density).toInt()
        val defaultLandscapeLeft = (550 * density).toInt()
        val defaultLandscapeEdgeMargin = (90 * density).toInt()

        if (isPortrait && rawSavedLeft == defaultPortraitLeft) {
            return defaultPortraitLeft
        }
        if (!isPortrait && rawSavedLeft == defaultLandscapeLeft) {
            return defaultLandscapeEdgeMargin
        }

        if (rawSavedLeft <= maxLeft) {
            val rightInset = (screenWidth - rawSavedLeft - widthPx).coerceAtLeast(0)
            return minOf(rawSavedLeft, rightInset)
        }

        val fallbackMargin = if (isPortrait) defaultPortraitLeft else defaultLandscapeEdgeMargin
        return minOf(fallbackMargin, maxLeft / 2)
    }

    private fun resolveTop(
        layout: FreeWindowLayoutDp,
        density: Float,
        heightPx: Int,
        screenHeight: Int,
        statusBarHeight: Int,
        bottomMargin: Int,
        followTrigger: Boolean,
        anchor: FreeWindowTriggerAnchor.Anchor?,
    ): Int {
        val maxTop = (screenHeight - heightPx - bottomMargin).coerceAtLeast(statusBarHeight)
        val defaultTop = ((layout.topDp * density).toInt() + statusBarHeight)
            .coerceIn(statusBarHeight, maxTop)
        if (!followTrigger || anchor == null || anchor.rawY <= 0f) return defaultTop

        // 垂直居中对齐触发手势 Y
        val targetTop = (anchor.rawY - heightPx / 2f).toInt()
        return targetTop.coerceIn(statusBarHeight, maxTop)
    }

    private fun fitRectOnScreen(
        rect: Rect,
        screenWidth: Int,
        screenHeight: Int,
        statusBarHeight: Int,
        bottomMargin: Int,
    ): Rect {
        val width = rect.width().coerceIn(1, screenWidth.coerceAtLeast(1))
        val maxAllowedH = (screenHeight - statusBarHeight - bottomMargin).coerceAtLeast(100)
        val height = rect.height().coerceIn(1, maxAllowedH)
        val left = rect.left.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val maxTop = (screenHeight - height - bottomMargin).coerceAtLeast(statusBarHeight)
        val top = rect.top.coerceIn(statusBarHeight, maxTop)
        return Rect(left, top, left + width, top + height)
    }

    /** 状态栏高度；拿不到时按 0 处理，安全边距退回 20mm。 */
    private fun statusBarHeightPx(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id <= 0) return 0
        return runCatching { context.resources.getDimensionPixelSize(id) }.getOrDefault(0)
    }

    private fun millimetersToPixels(metrics: DisplayMetrics, millimeters: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, millimeters, metrics).toInt()

    /**
     * 真实屏幕尺寸与当前朝向。
     *
     * **刻意不读** `context.resources.displayMetrics` / `resources.configuration`：小窗的调用方既有
     * overlay 的 WindowContext、也有 applicationContext（通知链路），后者的 configuration 在
     * 「没有可见 Activity」时可能不随旋转更新 —— 横屏若仍按竖屏宽高算，逻辑矩形必然出屏、
     * 被系统整段无视（真机反馈：横屏尺寸完全不生效）。口径与 LinkGo-X / GestureEVO 一致：
     * 物理边长取 `maximumWindowMetrics`，朝向取显示 rotation（见 [OverlayScreenMetrics]）。
     */
    private data class DisplaySize(val widthPx: Int, val heightPx: Int, val isLandscape: Boolean)

    private fun resolveDisplaySize(context: Context): DisplaySize {
        val fallback = context.resources.displayMetrics
        val physical = context.getSystemService(WindowManager::class.java)
            ?.let { runCatching { it.maximumWindowMetrics.bounds }.getOrNull() }
        val rawWidth = physical?.width()?.takeIf { it > 0 } ?: fallback.widthPixels.coerceAtLeast(1)
        val rawHeight = physical?.height()?.takeIf { it > 0 } ?: fallback.heightPixels.coerceAtLeast(1)
        val isLandscape = OverlayScreenMetrics.snapshot(context).isLandscape
        val minD = minOf(rawWidth, rawHeight)
        val maxD = maxOf(rawWidth, rawHeight)
        return DisplaySize(
            widthPx = if (isLandscape) maxD else minD,
            heightPx = if (isLandscape) minD else maxD,
            isLandscape = isLandscape,
        )
    }

    /**
     * 系统二次缩放后的「可靠区间」比例（≤1）：小米标准模式 = 补偿系数（竖 0.7 / 横 0.555），其余 1.0。
     *
     * **只给设置页做提示用**：不限制用户能配多大、更不改写已存配置。这个值本身还是"未在真机验证过的
     * 假设"，把它当成硬上限去夹取（无论夹启动参数还是夹编辑器）都可能把用户本来能用的配置改小。
     */
    fun deliverableLimitFraction(
        mode: FreeWindowMode,
        isLandscape: Boolean,
        isXiaomi: Boolean = isXiaomiDevice(),
    ): Float = resolveScaleCompensation(mode, isLandscape, isXiaomi)

    private fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
            Build.BRAND.contains("xiaomi", ignoreCase = true) ||
            Build.BRAND.contains("redmi", ignoreCase = true)

    private fun resolveScaleCompensation(settings: AppSettings, isLandscape: Boolean): Float =
        resolveScaleCompensation(settings.resolvedFreeWindowMode(), isLandscape, isXiaomiDevice())

    private fun resolveScaleCompensation(
        mode: FreeWindowMode,
        isLandscape: Boolean,
        isXiaomi: Boolean,
    ): Float = if (mode == FreeWindowMode.STANDARD && isXiaomi) {
        if (isLandscape) MIUI_LANDSCAPE_SCALE else MIUI_PORTRAIT_SCALE
    } else {
        1f
    }

    private fun applyWindowingMode(options: ActivityOptions, mode: Int) {
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, mode)
        } catch (_: Exception) {
            // Hidden API unavailable; bundle fallback applied in launch().
        }
    }

    private fun applyStackId(options: ActivityOptions, stackId: Int) {
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchStackId",
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, stackId)
        } catch (_: Exception) {
            // Ignored on platforms without setLaunchStackId
        }
    }
}
