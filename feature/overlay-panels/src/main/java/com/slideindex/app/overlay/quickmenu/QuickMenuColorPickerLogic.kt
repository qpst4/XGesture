package com.slideindex.app.overlay.quickmenu

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 「屏幕取色」的纯计算与持久化。
 *
 * 抽成两个纯件的理由和 `QuickMenuScanFrame` / `QuickMenuColorMath` 一样：
 *
 * 1. `QuickMenuColorPickerGeometry` —— 视图坐标 ↔ 位图像素、以及放大镜该取哪一块源像素。
 *    这类换算**错了不会崩**，只会"准星指着这儿、颜色却是隔壁那颗"，在 6 倍放大下差一个像素
 *    都很难用肉眼判断是谁算错了。因此做成纯函数 + 单测。
 * 2. `RecentColorsCodec` —— 取色历史是"看一眼"的东西，数据损坏或超量都不该让页面打不开。
 *
 * 放大镜的绘制仍然走 `DrawScope.drawImage`（和 `QuickMenuToolScreens` 里
 * `ScanImageWithMarkers`「按原亮度重画标记区」同一套手法），这里只负责把矩形算出来。
 */
object QuickMenuColorPickerGeometry {

    /**
     * 容器尺寸（像素）。
     *
     * 全屏取色时容器是**整块屏幕**，和截图的宽高比不一定相等（状态栏、导航栏、刘海都会让窗口
     * 和位图差几个像素），所以映射不再能当作纯缩放——先由 [fitRect] 算出图实际画在哪块，
     * 再用 [fitOffsetToPixel] 换算。
     */
    data class Viewport(val width: Int, val height: Int)

    /** 位图尺寸（像素）。 */
    data class BitmapSize(val width: Int, val height: Int)

    /** 取样结果：像素坐标 + 对应的容器内坐标。 */
    data class SamplePoint(val x: Int, val y: Int, val viewX: Float, val viewY: Float)

    /** 放大镜要画的源矩形（位图像素坐标，已裁剪到位图内）。 */
    data class LoupeSource(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * 放大镜里**准星那一颗**像素的落点：`dstOffset` 是目标偏移，`crosshairX/Y` 是那颗像素中心
     * 在放大区里的坐标（放大区左上角为原点）。`scale` 是放大倍数。
     *
     * `dstOffset` 是 `drawImage(srcOffset, srcSize, dstOffset, dstSize)` 里的目标偏移；
     * 倍数是 `dstSize.width / srcSize.width`（两轴相同，因为放大区是正方形）。
     */
    data class LoupeContent(
        val src: LoupeSource,
        val dstOffsetX: Int,
        val dstOffsetY: Int,
        val crosshairX: Float,
        val crosshairY: Float,
        val scale: Float,
    )

    /** 水平放大倍数。 */
    fun scaleX(viewport: Viewport, bitmap: BitmapSize): Float =
        viewport.width.toFloat() / bitmap.width.toFloat()

    /** 垂直放大倍数。 */
    fun scaleY(viewport: Viewport, bitmap: BitmapSize): Float =
        viewport.height.toFloat() / bitmap.height.toFloat()

    /**
     * 容器内坐标 → 位图像素。
     *
     * 两个方向的倍数**分别算**（而不是只算一个方向）：容器与位图同比例时两者相等，
     * 万一某处尺寸取错，这里至少还兜得住一侧，不会两个轴一起偏。结果一律夹到位图内，
     * 因为拖动可以拖出边界（手指到容器外面）。
     */
    fun viewOffsetToPixel(
        viewX: Float,
        viewY: Float,
        viewport: Viewport,
        bitmap: BitmapSize,
    ): SamplePoint {
        val safeX = if (viewport.width > 0 && bitmap.width > 0) {
            (viewX / viewport.width * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        } else {
            0
        }
        val safeY = if (viewport.height > 0 && bitmap.height > 0) {
            (viewY / viewport.height * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        } else {
            0
        }
        return SamplePoint(x = safeX, y = safeY, viewX = viewX, viewY = viewY)
    }

    /**
     * 位图按 `ContentScale.Fit` 铺进 [viewport] 后的实际矩形（视图像素坐标）。
     *
     * 全屏取色层铺的是**整块屏幕**：窗口含状态栏/导航栏/刘海，尺寸和截图不一定逐像素相等，
     * 宽高比一差就会出黑边。取色必须知道"图到底画在哪块"，否则准星和放大镜都会偏。
     *
     * 退化尺寸（0 宽/0 高）返回全零矩形，调用方据此跳过换算，不能除零。
     */
    data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

    fun fitRect(viewport: Viewport, bitmap: BitmapSize): FitRect {
        if (viewport.width <= 0 || viewport.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) {
            return FitRect(left = 0f, top = 0f, width = 0f, height = 0f)
        }
        val scale = minOf(
            viewport.width.toFloat() / bitmap.width.toFloat(),
            viewport.height.toFloat() / bitmap.height.toFloat(),
        )
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        // 居中：黑边两两平分（`ContentScale.Fit` 的行为）。
        return FitRect(
            left = (viewport.width - width) / 2f,
            top = (viewport.height - height) / 2f,
            width = width,
            height = height,
        )
    }

    /**
     * 视图坐标 → 位图像素，按 [fitRect] 的实际绘制矩形换算。
     *
     * 手指落在黑边里时夹到图的边缘（和 [viewOffsetToPixel] 夹到位图内是同一个意思：
     * 越界 `getPixel` 会抛，而且"拖出图片"本来就是取边缘那颗）。
     */
    fun fitOffsetToPixel(
        viewX: Float,
        viewY: Float,
        viewport: Viewport,
        bitmap: BitmapSize,
    ): SamplePoint {
        val rect = fitRect(viewport, bitmap)
        if (rect.width <= 0f || rect.height <= 0f) {
            return SamplePoint(x = 0, y = 0, viewX = viewX, viewY = viewY)
        }
        val safeX = ((viewX - rect.left) / rect.width * bitmap.width)
            .roundToInt()
            .coerceIn(0, bitmap.width - 1)
        val safeY = ((viewY - rect.top) / rect.height * bitmap.height)
            .roundToInt()
            .coerceIn(0, bitmap.height - 1)
        return SamplePoint(x = safeX, y = safeY, viewX = viewX, viewY = viewY)
    }

    /**
     * 放大镜要取的源矩形：以 [x]/[y] 为中心、跨度 [span] 个像素，裁剪到位图内。
     *
     * 三条不变量（都由单测钉住）：
     *
     * 1. **不补黑边**：补黑会让人以为那里本来就是黑的，从而取错颜色——这是"边缘取色不准"最常见
     *    的观感来源。
     * 2. **宽度尽量保持 [span]**：缩宽度会让放大倍数在贴边时跳动，手指一往边上挪，镜子里所见的
     *    大小就变。只有位图本身比跨度还小时才缩（[maxSpanFor]）。
     * 3. **先夹起点、再由起点加跨度算终点**（`left = x - span/2`，`right = left + span`），
     *    不能两头各自夹一次（`x - span/2` 与 `x + span/2 + 1`）：后者在偶数跨度下会取到
     *    `span + 1` 颗像素，倍数跟着差一档，而且"6 像素窗口"这个设定就名不副实了。
     *
     * ⚠️ 调用方应传**奇数**跨度（[alignedSpanAndLens] 会保证）：`left = x - span/2` 在奇数下
     * 恰好把采样点放在正中间那颗，这是 [loupeContent] 能让准星精确居中的前提。
     */
    fun loupeSource(x: Int, y: Int, span: Int, bitmap: BitmapSize): LoupeSource {
        if (bitmap.width <= 0 || bitmap.height <= 0) return LoupeSource(0, 0, 0, 0)
        val width = span.coerceIn(1, bitmap.width)
        val height = span.coerceIn(1, bitmap.height)
        val left = (x - width / 2).coerceIn(0, bitmap.width - width)
        val top = (y - height / 2).coerceIn(0, bitmap.height - height)
        return LoupeSource(x = left, y = top, width = width, height = height)
    }

    /**
     * 这个位图**允许**的最大跨度。
     *
     * UI 用它把用户设定夹一次：把跨度保证在"不超过镜子边长、也不超过位图"的范围内，
     * 于是 [loupeContent] 里 `dstOffset = 0` 永远放得下整块源矩形，不需要在贴边时靠夹取
     * 掩盖溢出（早先那样做会让目标框超出镜子，被圆形裁剪切掉一半，看起来像"图跑偏了"）。
     */
    fun maxSpanFor(bitmap: BitmapSize, lensSidePx: Int): Int =
        minOf(bitmap.width, bitmap.height, lensSidePx.coerceAtLeast(1)).coerceAtLeast(1)

    /**
     * 对准镜子尺寸：把 [lensSidePx] 上取整到 [span] 的整数倍。
     *
     * 这样做换来三件确定的好处（单独靠夹取边界都做不到）：
     *
     * 1. 放大倍数 `scale = 镜子边长 / 跨度` 成为**整数**；
     * 2. 每个源像素在镜子里占**整数个屏幕像素**，像素网格是等宽的，不会一半粗一半细；
     * 3. 位图放大后**正好等于**镜子边长，于是不会溢出镜子、也不会留下空白。
     *
     * 配合 [alignedSpanAndLens] 强制出的奇数跨度，[loupeContent] 的"铺满镜子"与"准星精确居中"
     * 就能同时成立、一个像素都不差。代价只是镜子比请求值大不到一个跨度（264 → 272 这个量级），
     * 肉眼不可辨。
     */
    fun alignLensSide(lensSidePx: Int, span: Int): Int {
        val pixels = span.coerceAtLeast(1)
        val side = lensSidePx.coerceAtLeast(pixels)
        // 上取整到 pixels 的整数倍。
        return ((side + pixels - 1) / pixels) * pixels
    }

    /**
     * 把源矩形按 `scale = lensSide / src.width` 放大后摆进镜子，并让**准星指向真正取到的那颗像素**。
     *
     * 推导（镜子坐标，左上角为原点）：
     *
     * ```
     * 准星像素中心 = dstOffset + (x - src.x) * scale + scale/2
     * 让它等于 side/2  →  dstOffset = side/2 - (x - src.x) * scale - scale/2
     * ```
     *
     * ## 两件事同时成立的关键：跨度取奇数
     *
     * "位图铺满镜子"要求 `dstOffset ∈ [side - 位图宽, 0]`，而 `scale` 就是按 `side / src.width`
     * 定的，位图宽恒等于 `side` —— 区间收缩成一点 `{0}`，也就是**目标偏移只能是 0**。
     * 既然 `dstOffset` 被钉死为 0，准星就完全由"采样点在窗口里的位置"决定：
     *
     * - **跨度是奇数**时，[loupeSource] 把采样点放在窗口正中间（`(span-1)/2` 颗在左），
     *   于是 `(x - src.x) * scale = (span/2) * scale = side/2 - scale/2`（`scale = side/span`），
     *   代回去得到 **准星 = side/2，精确落在镜子正中**。铺满与居中**同时成立，一个像素都不差**。
     * - 跨度是偶数时采样点左边有 `span/2` 颗、右边只有 `span/2 - 1` 颗，窗口关于它不对称，
     *   准星会固定偏右/偏下 `scale/2`（`side/2 + scale/2`）。所以 [alignedSpanAndLens]
     *   强制把跨度取成奇数，从源头上消掉这半颗像素。
     *
     * ## 贴到图边缘时准星为什么会偏
     *
     * 窗口被夹回位图内（[loupeSource] 不补黑边）后，采样点不再是窗口中间那颗，准星就会朝那一侧
     * 的镜边移。这是**应有的**：图外面没有像素，放大镜只能把"采样点一侧有多少邻域"如实画出来，
     * 偏移本身就在告诉用户"已经到边了"。此时仍然保证下面两条不变量。
     *
     * ## 始终成立的不变量
     *
     * 1. **位图严丝合缝铺满镜子**（`dstOffset` 被夹进 `[side - 位图宽, 0]`，实际就是 0），
     *    镜内不会出现"没有内容的缝"。
     * 2. **准星坐标 == 那颗像素实际被画到的中心**（用最终 `dstOffset` 复算），所以
     *    "十字线指着哪颗 = 取到哪颗"永远成立。
     * 3. 采样那一颗像素完整落在镜子内。
     */
    fun loupeContent(x: Int, y: Int, span: Int, lensSide: Int, bitmap: BitmapSize): LoupeContent {
        val source = loupeSource(x, y, span, bitmap)
        val side = lensSide.coerceAtLeast(1)
        if (source.width <= 0 || source.height <= 0) {
            return LoupeContent(source, 0, 0, side / 2f, side / 2f, 1f)
        }
        // ⚠️ scale 用源矩形**实际宽度**：贴边时窗口会被夹（宽度可能小于跨度），按实际宽度算
        // 放大后的位图才和镜子一样大，不会溢出、也不会留下空白。
        val scale = side.toFloat() / source.width.toFloat()
        val offsetX = (x - source.x) * scale
        val offsetY = (y - source.y) * scale
        // 铺满镜子的合法区间：`dstOffset ∈ [side - 放大后边长, 0]`。放大后边长恒等于 side，
        // 于是下界就是 0 本身 —— 目标偏移被钉死为 0，位图严丝合缝铺满镜子、一定不露缝。
        // 仍然写出通式（而不是直接写 0），这样即使将来 scale 的定义变了也不会静默溢出。
        val minX = (side - (source.width * scale).roundToInt()).coerceAtMost(0)
        val minY = (side - (source.height * scale).roundToInt()).coerceAtMost(0)
        val dstX = (side / 2f - offsetX - scale / 2f).roundToInt().coerceIn(minX, 0)
        val dstY = (side / 2f - offsetY - scale / 2f).roundToInt().coerceIn(minY, 0)
        // 准星 = 取样像素中心在放大区里的坐标（用取整后的 dstOffset 复算，与实际绘制一致）。
        val crosshairX = dstX + offsetX + scale / 2f
        val crosshairY = dstY + offsetY + scale / 2f
        return LoupeContent(source, dstX, dstY, crosshairX, crosshairY, scale)
    }

    /**
     * 一次性把界面要用的"跨度 + 镜子边长"算出来（跨度为**奇数**，且镜子边长是它的整数倍）。
     *
     * UI 只该调这个，两条规则缺一不可：
     *
     * 1. **镜子边长上取整到跨度的整数倍** → 放大倍数 `scale` 是整数：每个源像素占整数个屏幕
     *    像素，网格等宽，位图也正好与镜子等大。分开算（先定镜子再反推跨度）会引入取整误差，
     *    正是"倍数不是整数、网格一半粗一半细"的来源。
     * 2. **跨度取奇数** → [loupeSource] 会把采样点放在窗口正中间那颗，于是 [loupeContent] 里
     *    `dstOffset` 既能取 0（铺满镜子）、准星又**精确**落在镜子正中。跨度若是偶数，采样点
     *    左右邻域差一颗、窗口不对称，准星会固定偏 `scale/2`（`side/2 + scale/2`）。
     *
     * 代价只是跨度可能比首选值差 1（16 → 17）、镜子比请求值大不到一个跨度（264 → 272），
     * 肉眼不可辨；换来的是几何上不再有"近似"。
     */
    fun alignedSpanAndLens(bitmap: BitmapSize, lensSidePx: Int, span: Int, minSpan: Int, maxSpan: Int): Pair<Int, Int> {
        val lower = minSpan.coerceAtLeast(1)
        val upper = maxSpan.coerceAtLeast(lower)
        // 位图可能比跨度还小，这时上界被位图压住（源矩形绝不能越界）。
        val high = upper.coerceAtMost(maxSpanFor(bitmap, lensSidePx)).coerceAtLeast(1)
        var pixels = span.coerceIn(lower, upper).coerceIn(1, high)
        if (pixels % 2 == 0) {
            // 优先往上取一颗（视野略大、更接近"看清周围"，而且还在上界内就仍落在 [min,max] 里）；
            // 上界顶住了才往下取。位图极小时上界本身可能是偶数，往下取一颗一定还是合法跨度。
            pixels = if (pixels + 1 <= high) pixels + 1 else (pixels - 1).coerceAtLeast(1)
        }
        return pixels to alignLensSide(lensSidePx, pixels)
    }

    /**
     * 放大倍数：`放大镜边长(px) / 源区跨度(px)`。
     *
     * **手感由"跨度"决定，不是由这个倍数决定**：放大镜的物理尺寸固定（96dp），所以源区跨度
     * 就等于"镜子里能看到多大一块屏幕"，这个物理视野在任何 DPI 上都一致；高 DPI 屏上同样的
     * 96dp 里有更多屏幕像素，于是倍数更大、看得更细——这不需要为每种密度分别调数字。
     *
     * ⚠️ 只用于展示/调试：真正决定绘制的是 [alignedSpanAndLens] 给出的整数倍数，
     * 界面不要反过来用这个值去推跨度（那会引入取整误差）。
     */
    fun zoomFor(loupeSidePx: Float, span: Int, minSpan: Int, maxSpan: Int): Float {
        val safeMin = minSpan.coerceAtLeast(1)
        val safeMax = maxSpan.coerceAtLeast(safeMin)
        val pixels = span.coerceIn(safeMin, safeMax)
        if (loupeSidePx <= 0f) return 1f
        return loupeSidePx / pixels
    }
}

/**
 * 取色历史（最近若干条）的序列化与收敛规则。
 *
 * 和 `QuickMenuHistoryCodec` 同一套思路：存纯数据、读取一律兜底，损坏或超量都不该让页面打不开。
 */
object RecentColorsCodec {

    /** 历史最多留多少条。12 条正好在一行/两行色块里看全，再多就只是"翻记录"了。 */
    const val MAX_ENTRIES = 12

    /**
     * 去重（同样的颜色只留最近一次）、丢掉全透明（bitmap 里 alpha=0 的位置取出来没有意义）、
     * 截断到 [MAX_ENTRIES]。顺序保持"最近的在前"，由调用方保证。
     */
    fun sanitize(colors: List<Int>): List<Int> =
        colors.asSequence()
            .filter { it ushr 24 != 0 }
            .distinct()
            .take(MAX_ENTRIES)
            .toList()

    /** 记录一次取色：置顶 + [sanitize]。 */
    fun record(current: List<Int>, argb: Int): List<Int> = sanitize(listOf(argb) + current)
}

/**
 * 取色历史的持久化（功能级 DataStore，遵循本仓库 `UpdatePreferencesStore` 的既有约定）。
 *
 * 数据由一个 JSON 数组字符串承载，避免为 12 个条目声明一堆键。所有读取都做了兜底：
 * DataStore 抛错或数据损坏时返回空列表，页面照常可用（只是没有历史）。
 */
object QuickMenuColorHistoryStore {

    private val KEY = stringPreferencesKey("quick_menu_color_history")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(context: Context): List<Int> {
        val raw = runCatching { context.quickMenuColorPickerDataStore.data.first()[KEY] }.getOrNull()
            ?: return emptyList()
        val colors = runCatching { json.decodeFromString<List<Int>>(raw) }.getOrDefault(emptyList())
        return RecentColorsCodec.sanitize(colors)
    }

    /** 追加一条并返回更新后的列表（供 UI 立即刷新）。 */
    suspend fun record(context: Context, argb: Int): List<Int> {
        var updated: List<Int> = emptyList()
        runCatching {
            context.quickMenuColorPickerDataStore.edit { prefs ->
                val current = prefs[KEY]
                    ?.let { raw -> runCatching { json.decodeFromString<List<Int>>(raw) }.getOrDefault(emptyList()) }
                    .orEmpty()
                updated = RecentColorsCodec.record(RecentColorsCodec.sanitize(current), argb)
                prefs[KEY] = json.encodeToString(updated)
            }
        }
        return updated
    }
}

private val Context.quickMenuColorPickerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_index_quick_menu_color_picker",
)

/**
 * 「取色速度」的持久化：准星位移 / 手指位移的倍率。
 *
 * 1.0 是"拖多远走多远"，手指的抖动会原样传给准星；0.5 让精度翻倍（手指走 2px、准星走 1px），
 * 0.1 是给"一颗像素一颗像素挪"的极端档位（手指走 10px、准星走 1px）。档位固定成这五个，
 * 读盘时只接受这几个值——这个数只影响手感，数据脏了就该回默认，而不是把倍率变成 0.31 这种。
 *
 * 和取色历史共用一个功能级 DataStore，不额外开文件。
 */
object QuickMenuColorGainStore {

    private val KEY = floatPreferencesKey("quick_menu_color_gain")

    /** 默认倍率：0.5 = 手指走 2px、准星走 1px。 */
    const val DEFAULT = 0.5f

    /** 可选的档位，按从小到大排（UI 点一下切下一档）。 */
    val STEPS = floatArrayOf(0.1f, 0.3f, 0.5f, 0.7f, 1f)

    private fun sanitize(value: Float): Float =
        STEPS.firstOrNull { abs(it - value) < 0.001f } ?: DEFAULT

    suspend fun read(context: Context): Float {
        val raw = runCatching { context.quickMenuColorPickerDataStore.data.first()[KEY] }.getOrNull()
            ?: return DEFAULT
        return sanitize(raw)
    }

    suspend fun write(context: Context, gain: Float) {
        runCatching {
            context.quickMenuColorPickerDataStore.edit { prefs ->
                prefs[KEY] = sanitize(gain)
            }
        }
    }
}

/**
 * 取色要额外提供的值格式。
 *
 * `#RRGGBB` / `#AARRGGBB` / `rgb(...)` / `hsl(...)` 都在 [QuickMenuColorMath] 里（已单测），
 * 这里只补它没有的 HSV，并统一成"格式名 → 值"的列表，让 UI 一行一个、各自一个复制按钮。
 */
object QuickMenuColorFormats {

    /** HSV：`hsv(h, s%, v%)`，写法和 [QuickMenuColorMath.toHsl] 的 `hsl(...)` 保持一致。 */
    fun toHsv(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val maxChannel = max(r, max(g, b))
        val minChannel = minOf(r, g, b)
        val delta = maxChannel - minChannel

        val hue = when {
            delta == 0 -> 0f
            maxChannel == r -> 60f * (((g - b).toFloat() / delta) % 6f)
            maxChannel == g -> 60f * (((b - r).toFloat() / delta) + 2f)
            else -> 60f * (((r - g).toFloat() / delta) + 4f)
        }
        val normalizedHue = ((hue % 360f) + 360f) % 360f
        val saturation = if (maxChannel == 0) 0f else delta.toFloat() / maxChannel
        val value = maxChannel / 255f

        return "hsv(${normalizedHue.roundToInt()}, ${(saturation * 100).roundToInt()}%, " +
            "${(value * 100).roundToInt()}%)"
    }

    /** 五种格式的名称与取值。名称是通用缩写（不是本地化文案），所以不占字符串资源。 */
    fun all(argb: Int): List<Pair<String, String>> = listOf(
        "HEX" to QuickMenuColorMath.toHex(argb),
        "HEX(A)" to QuickMenuColorMath.toHexWithAlpha(argb),
        "RGB" to QuickMenuColorMath.toRgb(argb),
        "HSL" to QuickMenuColorMath.toHsl(argb),
        "HSV" to toHsv(argb),
    )
}
