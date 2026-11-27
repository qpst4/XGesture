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
internal interface PickGuardSession {
    /** 取词会话是否还在进行；false 时挡层会立刻撤除。 */
    fun isPickGuardNeeded(): Boolean
}

internal object PickTouchGuard {
    private const val TAG = "PickTouchGuard"

    /**
     * 最后一道保险：万一 liveness 轮询本身失效（owner 被回收、主线程卡死），
     * 最多挡这么久也一定撤掉，绝不留下常驻的整屏可触摸窗。
     *
     * ⚠️ 这个值**不能**当成正常收尾手段。上一版设成 15s，实测取词会话照样活着，
     * 挡层先被撤掉 → 之后按第二根手指就直接漏到下面的 App，正是"时好时坏"的来源。
     */
    private const val MAX_ARM_MS = 60_000L

    /** 会话存活轮询间隔：比 fuse 更早发现会话结束。 */
    private const val LIVENESS_CHECK_MS = 500L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var owner: PickGuardSession? = null
    private var guardView: View? = null
    private var guardParams: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var onExtraFinger: (() -> Unit)? = null

    private val autoDisarm = Runnable {
        android.util.Log.w(TAG, "auto disarm: guard outlived $MAX_ARM_MS ms")
        detach()
    }

    private val livenessCheck = object : Runnable {
        override fun run() {
            val session = owner ?: return
            if (!session.isPickGuardNeeded()) {
                android.util.Log.i(TAG, "session finished → disarm")
                detach()
                return
            }
            reattachIfDetached()
            mainHandler.postDelayed(this, LIVENESS_CHECK_MS)
        }
    }

    /**
     * 会话还在、窗却被摘掉（系统策略 / 被别的层顶掉）时补挂。
     *
     * 这一条是纯粹的防御：本地日志里没观察到系统摘窗，但一旦发生，表现就是
     * "取词中第二根手指又漏到下面的 App"，而且不会有任何 DOWN 日志 —— 只能靠轮询发现。
     */
    private fun reattachIfDetached() {
        val view = guardView ?: return
        if (view.isAttachedToWindow) return
        val wm = windowManager ?: return
        val params = guardParams ?: return
        android.util.Log.w(TAG, "guard detached while session alive → re-attach")
        runCatching { wm.addView(view, params) }
            .onFailure { android.util.Log.w(TAG, "re-attach failed", it) }
    }

    fun isArmedBy(candidate: Any): Boolean = owner === candidate && guardView != null

    /**
     * 挂上挡层。同一时刻只服务一个 owner（取词本身是互斥的）；重复调用只更新回调。
     *
     * @return true 表示挡层确实可用。
     */
    fun arm(owner: PickGuardSession, context: Context?, onExtraFinger: () -> Unit): Boolean {
        this.onExtraFinger = onExtraFinger
        if (guardView != null) {
            this.owner = owner
            scheduleLifetimeChecks()
            return true
        }
        if (context == null) {
            android.util.Log.w(TAG, "arm skipped: context unavailable")
            return false
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            android.util.Log.w(TAG, "arm skipped: WindowManager unavailable")
            return false
        }

        val guard = View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // 关键观测点：被系统（如 Oplus 反诈规则）摘窗时会走到这里。
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    android.util.Log.i(TAG, "guard attached")
                }

                override fun onViewDetachedFromWindow(v: View) {
                    android.util.Log.w(TAG, "guard DETACHED (removed while still needed?)")
                }
            })
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    android.util.Log.i(
                        TAG,
                        "guard DOWN pointerCount=${event.pointerCount} deviceId=${event.deviceId} " +
                            "raw=(${event.rawX},${event.rawY})"
                    )
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
        guardParams = params
        windowManager = wm
        this.owner = owner
        scheduleLifetimeChecks()
        android.util.Log.i(
            TAG,
            "arm owner=${owner.javaClass.simpleName} type=${params.type} " +
                "flags=0x${Integer.toHexString(params.flags)} " +
                "size=${params.width}x${params.height}"
        )
        return true
    }

    /** 轮询会话存活（正常收尾）+ 长保险丝（异常兜底）。 */
    private fun scheduleLifetimeChecks() {
        mainHandler.removeCallbacks(livenessCheck)
        mainHandler.removeCallbacks(autoDisarm)
        mainHandler.postDelayed(livenessCheck, LIVENESS_CHECK_MS)
        mainHandler.postDelayed(autoDisarm, MAX_ARM_MS)
    }

    /** 只有当前 owner 才能撤，避免两条取词通道互相拆台。 */
    fun disarm(owner: PickGuardSession) {
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
        android.util.Log.i(TAG, "detach owner=${owner?.javaClass?.simpleName}")
        mainHandler.removeCallbacks(livenessCheck)
        mainHandler.removeCallbacks(autoDisarm)
        val view = guardView
        val wm = windowManager
        guardView = null
        guardParams = null
        windowManager = null
        onExtraFinger = null
        owner = null
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { android.util.Log.w(TAG, "removeView failed", it) }
        }
    }
}
