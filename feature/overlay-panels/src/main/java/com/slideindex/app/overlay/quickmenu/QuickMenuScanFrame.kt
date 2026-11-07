package com.slideindex.app.overlay.quickmenu

import com.slideindex.app.barcode.BarcodePoint
import kotlin.math.roundToInt

/**
 * 相机"定格"要用的两段纯计算。
 *
 * 抽成纯函数是为了可单测：这两处都是"错了不会崩、但结果会静默错位/变色"的地方——
 * 标记和图对不上、颜色发绿，靠肉眼很难判断是哪里错的。
 */
object QuickMenuScanFrame {

    /**
     * 顺时针旋转 [rotationDegrees] 度后，归一化坐标的映射。
     *
     * 相机分析帧的朝向和"人看到的画面"通常差 90°，所以定格时必须**把位图和标记一起转**，
     * 否则标记会整体错位（这类错位看起来像"识别不准"，实际是坐标没转）。
     *
     * @param rotationDegrees 只支持 90 的整数倍（相机方向永远是 90 的倍数）；其它值按不旋转处理。
     */
    fun rotatePoint(point: BarcodePoint, rotationDegrees: Int): BarcodePoint {
        // 允许传负数（例如 -90），先归一到 0..359
        val degrees = ((rotationDegrees % 360) + 360) % 360
        return when (degrees) {
            90 -> BarcodePoint(x = 1f - point.y, y = point.x)
            180 -> BarcodePoint(x = 1f - point.x, y = 1f - point.y)
            270 -> BarcodePoint(x = point.y, y = 1f - point.x)
            else -> point
        }
    }

    fun rotatePoints(points: List<BarcodePoint>, rotationDegrees: Int): List<BarcodePoint> =
        points.map { rotatePoint(it, rotationDegrees) }

    /** 位图顺时针旋转 [degrees] 度后，宽高是否需要互换（90/270 需要）。 */
    fun swapsDimensions(degrees: Int): Boolean {
        val normalized = ((degrees % 360) + 360) % 360
        return normalized == 90 || normalized == 270
    }

    // ---------------------------------------------------------------- 显示朝向与取景对齐

    /** 画面尺寸（宽 × 高）。 */
    data class FrameSize(val width: Int, val height: Int)

    /** 画面里的矩形区域（显示朝向的坐标）。 */
    data class FrameRect(val x: Int, val y: Int, val width: Int, val height: Int)

    /** 显示朝向、且已按屏幕比例裁剪过的亮度图（紧凑排布）。 */
    data class DisplayLuminance(val pixels: ByteArray, val width: Int, val height: Int)

    /** 显示朝向下的画面尺寸（旋转 90/270 时宽高互换）。 */
    fun displaySize(width: Int, height: Int, rotationDegrees: Int): FrameSize =
        if (swapsDimensions(rotationDegrees)) FrameSize(height, width) else FrameSize(width, height)

    /**
     * 预览实际能看到的那块区域（显示朝向坐标）。
     *
     * **这是"定住的图和看到的画面不一样"的根因所在**：预览会把相机流按屏幕比例**中心裁剪**后铺满
     * 屏幕，而分析帧是传感器比例（通常 4:3）。两者取景不同 —— 于是"你以为框住的东西"和"被扫描、
     * 被定格的画面"根本不是同一块。把这块区域算出来，检测和定格都只用它，两边才一致。
     *
     * @param displayAspect 屏幕宽/高（竖屏小于 1）
     */
    fun displayCrop(displayWidth: Int, displayHeight: Int, displayAspect: Float): FrameRect {
        if (displayWidth <= 0 || displayHeight <= 0 || displayAspect <= 0f) {
            return FrameRect(0, 0, displayWidth.coerceAtLeast(0), displayHeight.coerceAtLeast(0))
        }
        var width = displayWidth
        var height = (displayWidth / displayAspect).roundToInt()
        if (height > displayHeight) {
            height = displayHeight
            width = (displayHeight * displayAspect).roundToInt()
        }
        width = width.coerceIn(1, displayWidth)
        height = height.coerceIn(1, displayHeight)
        return FrameRect((displayWidth - width) / 2, (displayHeight - height) / 2, width, height)
    }

    /**
     * 把传感器朝向的 Y 平面，**一次遍历**直接读成"显示朝向 + 按屏幕比例中心裁剪"的亮度图。
     *
     * 之所以不先转整图再裁：那样要多一次全图重排（每帧都要跑），而且旋转与裁剪分两处实现很容易
     * 出现"图转了、坐标没转"的静默错位。这里旋转与裁剪合成一次取值，检测与定格共用同一块画面。
     *
     * @param rowStride Y 平面的行距（硬件对齐，通常大于 width），直接用它索引传进来的原始缓冲。
     * @param sampleStep 抽样步长：检测传 >1 降采样（逐帧要省），定格传 1 取全分辨率。
     * @param into 可选的复用缓冲（大小需正好等于 `输出宽 × 输出高`）。预览每帧都要刷新，不能每帧新建
     *   数组——那是每秒十几 MB 的垃圾，会把 GC 拖成肉眼可见的卡顿。尺寸不符时自动新建。
     */
    fun displayLuminance(
        luminance: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        rotationDegrees: Int,
        displayAspect: Float,
        sampleStep: Int = 1,
        into: ByteArray? = null,
    ): DisplayLuminance {
        val step = sampleStep.coerceAtLeast(1)
        val display = displaySize(width, height, rotationDegrees)
        val crop = displayCrop(display.width, display.height, displayAspect)
        val outWidth = (crop.width / step).coerceAtLeast(1)
        val outHeight = (crop.height / step).coerceAtLeast(1)
        val output = if (into != null && into.size == outWidth * outHeight) {
            into
        } else {
            ByteArray(outWidth * outHeight)
        }
        if (width <= 0 || height <= 0 || rowStride < width || luminance.isEmpty()) {
            output.fill(0)
            return DisplayLuminance(output, outWidth, outHeight)
        }

        val degrees = ((rotationDegrees % 360) + 360) % 360
        for (outY in 0 until outHeight) {
            val displayY = crop.y + outY * step
            val outRow = outY * outWidth
            for (outX in 0 until outWidth) {
                val displayX = crop.x + outX * step
                // 显示坐标 → 传感器坐标（顺时针旋转 degrees 的逆向）
                val sourceX: Int
                val sourceY: Int
                when (degrees) {
                    90 -> {
                        sourceX = displayY
                        sourceY = height - 1 - displayX
                    }
                    180 -> {
                        sourceX = width - 1 - displayX
                        sourceY = height - 1 - displayY
                    }
                    270 -> {
                        sourceX = width - 1 - displayY
                        sourceY = displayX
                    }
                    else -> {
                        sourceX = displayX
                        sourceY = displayY
                    }
                }
                if (sourceX < 0 || sourceY < 0 || sourceX >= width || sourceY >= height) continue
                val index = sourceY * rowStride + sourceX
                if (index < luminance.size) output[outRow + outX] = luminance[index]
            }
        }
        return DisplayLuminance(output, outWidth, outHeight)
    }

    // ---------------------------------------------------------------- 稳定度

    /** 稳定度判据用的粗网格边长。 */
    const val MOTION_GRID_SIZE = 8

    /**
     * 把亮度图压成 `gridSize × gridSize` 的**均值网格**，用来判断画面稳没稳。
     *
     * 只压均值、不逐像素比：手持抖动在粗网格上就看得出来，而这样做的代价几乎为零
     * （提取亮度时我们已经拿到了整幅数据）。
     *
     * 保留它的理由（而不是照搬库那样完全不判稳定）：**定格的那一张必须是清楚的**。
     * 手机还在动时抓到的那一帧是糊的，模糊帧不仅认不出码，还会把"糊"当成"识别不准"。
     * 它只要求"这一帧和上一帧差不多"，所以最多多等一帧（约 33ms），用户感觉不到。
     */
    fun gridSignature(
        luminance: ByteArray,
        width: Int,
        height: Int,
        gridSize: Int = MOTION_GRID_SIZE,
    ): IntArray {
        if (gridSize <= 0) return IntArray(0)
        // ⚠️ 退化输入返回**空数组**而不是"全 0 网格"：全 0 网格会让 meanAbsDiff 算出 0，
        // 也就是"完全静止"——坏帧反而能通过稳定门槛。空数组会被判成 MAX_VALUE（不稳），才是安全的。
        if (width <= 0 || height <= 0 || luminance.size < width * height) return IntArray(0)

        val sums = IntArray(gridSize * gridSize)
        val counts = IntArray(gridSize * gridSize)
        // 列 → 所属格子一次性算好。**不能**把除法放进逐像素循环里：1280×720 就是每帧 184 万次
        // 整数除法（手机上是几十毫秒），这正是"每帧都要跑的东西"绝不能有的开销。
        val cellColumns = IntArray(width) { it * gridSize / width }
        for (row in 0 until height) {
            val cellRowBase = (row * gridSize / height) * gridSize
            val rowBase = row * width
            for (column in 0 until width) {
                val index = cellRowBase + cellColumns[column]
                sums[index] += luminance[rowBase + column].toInt() and 0xFF
                counts[index]++
            }
        }
        for (index in sums.indices) {
            if (counts[index] > 0) sums[index] /= counts[index]
        }
        return sums
    }

    /**
     * 两张网格签名的平均绝对差（0..255）：越小越稳。
     *
     * 尺寸不一致时返回 [Float.MAX_VALUE]（**默认判为"不稳"**，宁可多等一帧，也不要在没对准时触发）。
     */
    fun meanAbsDiff(a: IntArray, b: IntArray): Float {
        if (a.isEmpty() || a.size != b.size) return Float.MAX_VALUE
        var sum = 0L
        for (index in a.indices) {
            sum += kotlin.math.abs(a[index] - b[index])
        }
        return sum.toFloat() / a.size
    }

    /**
     * 单个像素的 YUV → ARGB（BT.601、全范围）。
     *
     * [u] / [v] 传的是**原始无符号字节值**（0..255，即 128 表示无色度差），内部再减 128。
     * 有意不做色域（limited/full range）判定：Camera2 的 `YUV_420_888` 在不同机型上口径不一，
     * 定格画面只用于"看清是哪个码"，轻微对比度差可以接受；为此引入机型分支得不偿失。
     */
    fun yuvToArgb(y: Int, u: Int, v: Int): Int {
        val luma = y.coerceIn(0, 255)
        val chromaU = u.coerceIn(0, 255) - 128
        val chromaV = v.coerceIn(0, 255) - 128
        val r = (luma + 1.402f * chromaV).roundToInt().coerceIn(0, 255)
        val g = (luma - 0.344136f * chromaU - 0.714136f * chromaV).roundToInt().coerceIn(0, 255)
        val b = (luma + 1.772f * chromaU).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
