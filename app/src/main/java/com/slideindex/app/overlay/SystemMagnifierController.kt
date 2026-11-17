package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.MagnificationConfig
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.service.SlideIndexAccessibilityService

/**
 * 放大镜偏好持久化存储。
 */
object MagnifierPrefs {
    const val PREFS_NAME = "magnifier"
    const val KEY_SCALE = "scale"
    const val DEFAULT_SCALE = 2.0f
    const val MIN_SCALE = 1.25f
    const val MAX_SCALE = 5.0f

    val SCALES = floatArrayOf(
        1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3f, 3.5f, 4f, 5f, 6f, 8f,
    )

    fun readScale(context: Context): Float = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SCALE, DEFAULT_SCALE)
    }.getOrDefault(DEFAULT_SCALE)

    fun writeScale(context: Context, value: Float) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_SCALE, value).apply()
        }
    }
}

/**
 * 「放大镜」动作的落地实现：调用**系统无障碍放大**（`MagnificationController`）。
 *
 * 不抓屏、不弹录屏授权；放大画面由系统合成层提供，视频/动画照常实时。
 * 需要无障碍服务声明 `canControlMagnification="true"`（见 accessibility_service_config.xml），
 * 否则系统会静默拒绝。
 *
 * 关闭入口：自己画的顶部小胶囊（点=关闭，长按=切倍率），系统放大本身不提供任何 UI。
 */
object SystemMagnifierController {

    private const val TAG = "SystemMagnifier"

    /** 普通触发（开关式）没有"松手"事件，靠这个兜底，防止用户忘了关。 */
    private const val AUTO_STOP_MS = 60_000L

    /**
     * 全屏与窗口放大模式区分：
     *
     * 窗口模式只放大局部视窗；全屏模式是整屏内容随指针平移。
     * 具体用哪个由触发方式决定（见 [toggle] / [toggleFollow] / [start] 的 fullscreen 参数）。
     */

    /** 放大镜常用缩放倍率阶梯档位。 */
    val SCALES get() = MagnifierPrefs.SCALES

    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    private var controller: AccessibilityService.MagnificationController? = null
    private var active = false
    private var chip: MagnifierChipOverlay? = null
    private var scale = MagnifierPrefs.DEFAULT_SCALE
    private var lastX = 0f
    private var lastY = 0f
    /** 本次会话是否用全屏放大（持续触发 = 全屏跟手；开关式 = 窗口小放大镜）。 */
    private var fullscreen = false
    /** 是否由悬浮指针的光标驱动（摇杆功能环触发）。 */
    private var followCursor = false
    /** 本次会话是否已经用 config 设过"全屏/窗口"模式（之后只靠 setCenter 平移）。 */
    private var modeApplied = false

    fun isActive(): Boolean = active
    fun isFollowingCursor(): Boolean = active && followCursor

    /** 开关式（松手 / 即时触发）：窗口小放大镜。开着就关，关着就在 (x,y) 打开。 */
    fun toggle(x: Float, y: Float) {
        if (active) {
            stop()
        } else {
            start(x, y, fullscreen = false)
            handler.removeCallbacks(autoStop)
            handler.postDelayed(autoStop, AUTO_STOP_MS)
        }
    }

    /**
     * 指针 / 摇杆功能环触发：全屏放大，并由光标位置持续驱动中心。
     * 抬手（UP/CANCEL）时由指针会话调用 [stop] 退出。
     */
    fun startFollow(x: Float, y: Float) {
        followCursor = true
        if (active) {
            fullscreen = true
            update(x, y)
        } else {
            start(x, y, fullscreen = true)
        }
        if (!active) followCursor = false
    }

    /** 指针 / 摇杆触发开关：开着就关，关着就进入跟手全屏。 */
    fun toggleFollow(x: Float, y: Float) {
        if (active) {
            stop()
            return
        }
        startFollow(x, y)
    }

    /** 光标移动时调用；只有指针触发的放大镜才跟随。 */
    fun updateFollow(x: Float, y: Float) {
        if (!active || !followCursor) return
        update(x, y)
    }

    fun start(x: Float, y: Float, fullscreen: Boolean = false) {
        if (active) return
        this.fullscreen = fullscreen
        this.modeApplied = false
        // setMagnificationConfig / MagnificationConfig 需要 API 33；
        // 本功能按 Android 13（API 33）起支持。低版本在这里拦截，避免 NoSuchMethodError。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.i(TAG, "ignored: requires Android 13+")
            SlideIndexAccessibilityService.overlayHostContext()?.let { context ->
                Toast.makeText(
                    context,
                    context.getString(R.string.magnifier_requires_android13),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: run {
            Log.w(TAG, "ignored: accessibility service unavailable")
            return
        }
        val c = runCatching { service.magnificationController }.getOrNull() ?: run {
            Log.w(TAG, "ignored: magnification controller unavailable")
            return
        }
        val context = SlideIndexAccessibilityService.overlayHostContext() ?: service
        scale = readScale(context)
        controller = c
        lastX = x
        lastY = y
        if (!apply(x, y)) {
            controller = null
            return
        }
        active = true
        handler.removeCallbacks(autoStop)
        Log.i(
            TAG,
            "start mode=${if (fullscreen) "fullscreen" else "window"} follow=$followCursor " +
                "x=$x y=$y scale=$scale",
        )
        // 指针跟手模式下不显示胶囊与 Toast：胶囊是个可触摸浮窗，会压住摇杆区域把摇杆拖动吃掉；
        // 且跟手模式是持续手势，松手即退出，无需小圆点关闭提示。
        if (!followCursor) {
            showChrome(context)
            Toast.makeText(
                context,
                context.getString(R.string.magnifier_opened_toast),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun update(x: Float, y: Float) {
        if (!active) return
        // 位置没变就不重复下发：每帧重建 config 也是 binder 调用，没必要。
        if (x == lastX && y == lastY) return
        lastX = x
        lastY = y
        apply(x, y)
    }

    fun stop() {
        handler.removeCallbacks(autoStop)
        followCursor = false
        modeApplied = false
        if (!active) return
        active = false
        chip?.dismiss()
        chip = null
        val c = controller ?: return
        controller = null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                c.resetCurrentMagnification(false)
            } else {
                @Suppress("DEPRECATION")
                c.reset(false)
            }
        }.onFailure { Log.w(TAG, "reset failed", it) }
    }

    private fun showChrome(context: Context) {
        val overlay = MagnifierChipOverlay(
            context = context,
            onClose = { stop() },
            onCycleScale = { cycleScale(context) },
        )
        overlay.show(scaleLabel())
        chip = overlay
    }

    private fun cycleScale(context: Context) {
        val index = SCALES.indexOfFirst { it == scale }.coerceAtLeast(0)
        scale = SCALES[(index + 1) % SCALES.size]
        writeScale(context, scale)
        chip?.update(scaleLabel())
        apply(lastX, lastY)
        Toast.makeText(context, scaleLabel(), Toast.LENGTH_SHORT).show()
    }

    private fun scaleLabel(): String = if (scale % 1f == 0f) {
        scale.toInt().toString()
    } else {
        scale.toString()
    }

    private fun readScale(context: Context): Float = MagnifierPrefs.readScale(context)

    private fun writeScale(context: Context, value: Float) = MagnifierPrefs.writeScale(context, value)

    private fun screenSize(): Pair<Float, Float> {
        val metrics = SlideIndexAccessibilityService.overlayHostContext()?.resources?.displayMetrics
        return (metrics?.widthPixels?.toFloat() ?: 1080f) to
            (metrics?.heightPixels?.toFloat() ?: 2400f)
    }

    /**
     * 下发一次放大配置。
     *
     * 用系统的 `setScale` + `setCenter`（都 animated=false）：
     * 全屏模式下将手指坐标换算为视窗中心平移偏移：
     * center -= (center - screenSize / 2) / scale。
     *
     * 配置里**不调用** `MagnificationConfig.Builder.setActivated(true)`：它是 API 34 才有的
     * 方法，而 Builder 默认就是 true；Android 13 上 `setMagnificationConfig` 本身就会激活。
     */
    private fun apply(x: Float, y: Float): Boolean {
        val c = controller ?: return false
        // 正常路径已在 start() 拦掉 <33；这里再兜一道，既防反射调用，也让 lint 识别版本边界。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val (width, height) = screenSize()
        var cx = x
        var cy = y

        if (fullscreen) {
            cx -= (cx - width / 2f) / scale
            cy -= (cy - height / 2f) / scale
            val scaleOk = runCatching { c.setScale(scale, false) }
                .onFailure { Log.w(TAG, "setScale failed", it) }
                .getOrDefault(false)
            if (!modeApplied) {
                val config = MagnificationConfig.Builder()
                    .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN)
                    .setScale(scale)
                    .setCenterX(cx)
                    .setCenterY(cy)
                    .build()
                modeApplied = runCatching { c.setMagnificationConfig(config, false) }
                    .onFailure { Log.w(TAG, "setMagnificationConfig failed", it) }
                    .getOrDefault(false)
            }
            val centerOk = runCatching { c.setCenter(cx, cy, false) }
                .onFailure { Log.w(TAG, "setCenter failed", it) }
                .getOrDefault(false)
            val ok = scaleOk || centerOk
            if (!ok) {
                Log.w(TAG, "setScale/setCenter rejected; keep active and retry on next move")
            }
            return ok
        } else {
            // 开关式（松手触发 / 直接点击）：局部窗口小放大镜（MODE_WINDOW）。
            // 绝不能调用 setScale/setCenter（它们是系统全局全屏放大老 API，会把窗口模式冲掉）。
            val insetX = width * 0.2f
            cx = when {
                x < insetX -> x + insetX
                x > width - insetX -> x - insetX
                else -> x
            }
            cy = cy.coerceIn(height * 0.1f, height * 0.9f)
            val config = MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(cx)
                .setCenterY(cy)
                .build()
            return runCatching { c.setMagnificationConfig(config, false) }
                .onFailure { Log.w(TAG, "setMagnificationConfig MODE_WINDOW failed", it) }
                .getOrDefault(false)
        }
    }

}
