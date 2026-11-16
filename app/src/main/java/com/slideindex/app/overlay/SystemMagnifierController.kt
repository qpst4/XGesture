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

    /** `MagnificationConfig.MODE_WINDOW` 的系统值。 */
    private const val MODE_WINDOW = 2

    private val SCALES = floatArrayOf(1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f, 6f, 8f)

    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    private var controller: AccessibilityService.MagnificationController? = null
    private var active = false
    private var chip: MagnifierChipOverlay? = null
    private var scale = 2.5f
    private var lastX = 0f
    private var lastY = 0f

    fun isActive(): Boolean = active

    /** 开关式：开着就关，关着就在 (x,y) 打开。 */
    fun toggle(x: Float, y: Float) {
        if (active) {
            stop()
        } else {
            start(x, y)
            handler.removeCallbacks(autoStop)
            handler.postDelayed(autoStop, AUTO_STOP_MS)
        }
    }

    fun start(x: Float, y: Float) {
        if (active) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.i(TAG, "ignored: requires Android 13+")
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
        showChrome(context)
        Toast.makeText(
            context,
            context.getString(R.string.magnifier_opened_toast),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun update(x: Float, y: Float) {
        if (!active) return
        lastX = x
        lastY = y
        apply(x, y)
    }

    fun stop() {
        handler.removeCallbacks(autoStop)
        if (!active) return
        active = false
        chip?.dismiss()
        chip = null
        val c = controller ?: return
        controller = null
        runCatching { c.resetCurrentMagnification(false) }
            .onFailure { Log.w(TAG, "reset failed", it) }
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

    private fun readScale(context: Context): Float = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_SCALE, 2.5f)
    }.getOrDefault(2.5f)

    private fun writeScale(context: Context, value: Float) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_SCALE, value).apply()
        }
    }

    private fun apply(x: Float, y: Float): Boolean {
        val c = controller ?: return false
        val metrics = SlideIndexAccessibilityService.overlayHostContext()?.resources?.displayMetrics
        val width = metrics?.widthPixels ?: 1080
        val height = metrics?.heightPixels ?: 2400
        // 边缘触钮的触发点贴着屏幕边：放大中心往屏内挪 20%，否则看到的是屏幕边。
        val insetX = width * 0.2f
        val cx = when {
            x < insetX -> x + insetX
            x > width - insetX -> x - insetX
            else -> x
        }
        val cy = y.coerceIn(height * 0.1f, height * 0.9f)
        val config = MagnificationConfig.Builder()
            .setMode(MODE_WINDOW)
            .setScale(scale)
            .setCenterX(cx)
            .setCenterY(cy)
            .build()
        val ok = runCatching { c.setMagnificationConfig(config, false) }
            .onFailure { Log.w(TAG, "setMagnificationConfig failed", it) }
            .getOrDefault(false)
        if (!ok) {
            Log.w(TAG, "system rejected magnification config (unsupported on this ROM?)")
            stop()
        }
        return ok
    }

    private const val PREFS = "magnifier"
    private const val KEY_SCALE = "scale"
}
