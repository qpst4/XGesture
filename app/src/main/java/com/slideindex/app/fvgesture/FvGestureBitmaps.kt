/*
 * FV「自定义手势」—— 缩略图渲染与偏好键（从原 `FvGestureEditDialog.kt` 拆出来的存活部分）
 *
 * 原来的 View 版录入弹窗（`FvGestureEditDialog` / `FvPrefRow` / `FvGestureConflictDialog`）已经删除：
 * 弹窗改成了 Miuix 样式的 Compose 实现 `ui/FvCustomGestureEditDialog.kt`
 * （原 View 版只把窗口背景设成透明、又没画底卡片，实际显示是"透明浮字"）。
 *
 * 保留在这里的两类东西与 FV 一一对应：
 *   - `gestureToBitmap` / `mirrorBitmap`：列表缩略图，数值照抄 FV（见 05-ui-spec.md）
 *   - `FvGesturePrefs`：FV 的两个偏好键（`addMirrorGesture` / `showGestureTracking`）
 */
package com.slideindex.app.fvgesture

import android.content.Context
import android.gesture.Gesture
import android.graphics.Bitmap

/**
 * 偏好封装（FV: `com.fooview.android.r.N()`）。键名与 FV 一致，便于以后迁移/对照：
 * - `addMirrorGesture`：录入弹窗「同时添加对称手势」的记忆位（点确定时写一次，和 FV 一样）
 * - `showGestureTracking`：运行时轨迹浮层开关，由 overlay 那侧读取
 */
object FvGesturePrefs {
    const val PREFS_NAME = "fv_gesture"

    private const val KEY_ADD_MIRROR = "addMirrorGesture"      // FV 原键名
    private const val KEY_SHOW_TRACKING = "showGestureTracking" // FV 原键名

    fun isAddMirrorGesture(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADD_MIRROR, false)

    fun setAddMirrorGesture(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ADD_MIRROR, value).apply()

    fun isShowGestureTracking(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_TRACKING, false)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * FV: q1.o.i(Gesture, w, h, inset, color, showStart) —— 手势缩略图。
 * 数值（笔宽 8、起点圆半径 64、颜色 #FFFF0000 / #FF689F38）全部照抄 FV，见
 * docs/reverse/fv-custom-gesture/05-ui-spec.md。
 */
fun gestureToBitmap(
    gesture: Gesture,
    widthPx: Int,
    heightPx: Int,
    insetPx: Int,
    color: Int,
    showStart: Boolean,
    context: Context
): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isDither = true
            this.color = color
            style = android.graphics.Paint.Style.STROKE
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeWidth = 8f
        }
        val path = gesture.toPath()
        val rect = android.graphics.RectF()
        path.computeBounds(rect, true)
        val inner = insetPx * 2
        var scale = (widthPx - inner) / rect.width()
        val scaleY = (heightPx - inner) / rect.height()
        if (scale > scaleY) scale = scaleY
        paint.strokeWidth = 8f / scale
        val dx = -rect.left + (widthPx - rect.width() * scale) / 2f
        val dy = -rect.top + (heightPx - rect.height() * scale) / 2f
        path.offset(dx, dy)
        canvas.translate(insetPx.toFloat(), insetPx.toFloat())
        canvas.scale(scale, scale)
        canvas.drawPath(path, paint)
        if (showStart) {
            val pts = gesture.strokes[0].points
            if (pts != null && pts.size > 2) {
                paint.color = FvGesturePanel.COLOR_START_POINT
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(pts[0] + dx, pts[1] + dy, 64f, paint)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * 水平翻转位图 —— 对应 FV 的 `n5.w0.A(Bitmap)`：`Matrix.setScale(-1, 1)` + `postTranslate(w, 0)`。
 * 列表页用它展示「对称手势」的缩略图（镜像沿竖直轴翻 x，水平翻转即可）。
 */
fun mirrorBitmap(source: Bitmap): Bitmap? = try {
    val matrix = android.graphics.Matrix().apply {
        setScale(-1f, 1f)
        postTranslate(source.width.toFloat(), 0f)
    }
    Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
} catch (e: Exception) {
    null
}
