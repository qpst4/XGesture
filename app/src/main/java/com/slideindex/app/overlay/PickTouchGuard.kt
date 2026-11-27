package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * 取词期间的「第二根手指」挡层。
 *
 * ## 为什么必须有这一层
 *
 * 触摸派发里，**新按下的手指是独立命中测试的**（`FLAG_SPLIT_TOUCH` 只决定"新手指能不能落到别的窗"，
 * 而不是"一定落到接住第一根手指的那个窗"）。所以取词时接住主指的那些窗口都很窄：
 *
 * | 通道 | 主指接在哪 | 第二根手指落在屏幕中间时 |
 * |---|---|---|
 * | 边缘区域取词 | 贴边触钮条 `EdgeTouchCaptureView` | 命中下面的 App（能点开关、滑动列表） |
 * | 悬浮指针取词 | 指针触摸层（一个直径几十 px 的圆窗） | 同上 |
 *
 * 也就是说：**窄窗完全收不到第二根手指**，光在 `onTouchEvent` 里加 `ACTION_POINTER_DOWN` 分支不会有任何效果。
 * 要在全屏任意位置感知"又有一根手指按下"，只能临时挂一层全屏可触摸窗口。
 *
 * ## 行为
 *
 * - 只在取词会话期间挂（[arm] / [disarm] 成对调用），并带 [MAX_ARM_MS] 保险丝，避免事件丢失时留下
 *   一个吞掉整屏触摸的常驻窗（这类"全屏窗卡死"在本项目里踩过多次）。
 * - 收到 `ACTION_DOWN` 即回调一次：对取词来说，这一下**不可能**是主指（主指的流在别的窗口里），
 *   所以它就是"另一根手指"。
 * - 这一层的触摸全部吞掉，不再漏给下层 App —— 顺带修掉"取词中第二根手指把下面的开关点了"的问题。
 */
internal object PickTouchGuard {
    private const val TAG = "PickTouchGuard"

    /** 保险丝：万一 UP/CANCEL 丢了，最多挡这么久就自己撤掉，绝不常驻。 */
    private const val MAX_ARM_MS = 15_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var owner: Any? = null
    private var guardView: View? = null
    private var windowManager: WindowManager? = null
    private var onExtraFinger: (() -> Unit)? = null

    private val autoDisarm = Runnable {
        android.util.Log.w(TAG, "auto disarm: pick session outlived $MAX_ARM_MS ms")
        detach()
    }

    fun isArmedBy(candidate: Any): Boolean = owner === candidate && guardView != null

    /**
     * 挂上挡层。同一时刻只服务一个 owner（取词本身是互斥的）；重复调用只更新回调。
     *
     * @return true 表示挡层确实可用。
     */
    fun arm(owner: Any, context: Context?, onExtraFinger: () -> Unit): Boolean {
        this.onExtraFinger = onExtraFinger
        if (guardView != null) {
            this.owner = owner
            mainHandler.removeCallbacks(autoDisarm)
            mainHandler.postDelayed(autoDisarm, MAX_ARM_MS)
            return true
        }
        if (context == null) return false
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false

        val guard = View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    this@PickTouchGuard.onExtraFinger?.invoke()
                }
                // 全部吞掉：取词期间不该有任何触摸漏到下面的 App。
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.captureOverlayWindowType(context),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = "XG:pick-guard"
            gravity = Gravity.TOP or Gravity.START
            OverlayWindowTypes.applyFullScreen(this)
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }

        val added = runCatching { wm.addView(guard, params) }
            .onFailure { android.util.Log.w(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            this.onExtraFinger = null
            return false
        }
        guardView = guard
        windowManager = wm
        this.owner = owner
        mainHandler.removeCallbacks(autoDisarm)
        mainHandler.postDelayed(autoDisarm, MAX_ARM_MS)
        return true
    }

    /** 只有当前 owner 才能撤，避免两条取词通道互相拆台。 */
    fun disarm(owner: Any) {
        if (this.owner !== owner) return
        detach()
    }

    /**
     * 无条件撤除。只给"整个浮层正在拆窗"这类收尾用（两条取词通道互斥，不会误伤别人）。
     *
     * 存在的意义：异常收尾（外部点击、熄屏、窗口被系统摘掉）时 owner 收不到终止事件，
     * 光靠 15s 保险丝会让整屏白挡十几秒。
     */
    fun forceDisarmAll() {
        if (guardView == null) return
        android.util.Log.i(TAG, "force disarm (overlay teardown)")
        detach()
    }

    private fun detach() {
        mainHandler.removeCallbacks(autoDisarm)
        val view = guardView
        val wm = windowManager
        guardView = null
        windowManager = null
        onExtraFinger = null
        owner = null
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { android.util.Log.w(TAG, "removeView failed", it) }
        }
    }
}
