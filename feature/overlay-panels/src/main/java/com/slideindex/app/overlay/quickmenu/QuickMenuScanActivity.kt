package com.slideindex.app.overlay.quickmenu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.barcode.ZxingBarcodeScanner
import com.slideindex.app.util.finishWithoutTransition
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.min

/**
 * 实时相机扫码（「快捷菜单 → 识码解析 → 相机扫码」）。
 *
 * **用 CameraX**：预览的比例/旋转/缩放、取景一致、各机型 HAL 的差异，全部由它处理。
 *
 * 这是被实测逼出来的结论。之前手写 Camera2 的经过，值得留在这里当教训：
 * 1. 会话里只挂一路 YUV 分析流、预览自己用软件逐像素 YUV→RGB 画 —— **马赛克（分辨率低）+ 果冻（帧率低）**；
 * 2. 用 SurfaceView 直接接相机预览流 —— **比例一直不对**：该机 YUV 尺寸是 1920×1012（非标准），
 *    而我们能建出的预览 Surface 尺寸相机并不支持，HAL 只能换模式再缩放，`SurfaceView` 又只能把缓冲
 *    拉伸到视图矩形（能拉伸、不能旋转）；
 * 3. 结论：预览这件事该交给库。`PreviewView` 内部就是"从设备支持尺寸里挑 + GPU 变换"，
 *    正是我们手写时反复踩坑的那部分。
 *
 * 数据流：
 *
 * ```
 * CameraX Preview（PreviewView 显示，比例/旋转由库处理）
 *   + ImageAnalysis(YUV_420_888, KEEP_ONLY_LATEST, 目标 1920×1080)
 *       → 每帧取 Y 平面 → 按 imageInfo.rotationDegrees 转成显示朝向 → 降采样
 *       → 便宜的触发检查（单码读取器 + 只主二值化 + 只标准亮度）
 *       → 触发时把**同一帧**按全分辨率转成位图交回面板 → 面板对这张图做一次全格式扫描
 * ```
 *
 * 触发策略：**扫到就定格**，但保留"这一帧和上一帧差不多才抓"的稳定判据 —— 手机还在动时抓到的那一帧是
 * 糊的，而**定格图必须是清楚的**；它最多多等一帧（约 33ms）。
 *
 * ⚠️ 本类**没有自动化测试**（相机必须真机验证）；几何与解码逻辑由 `QuickMenuScanFrame` /
 * `ZxingBarcodeScanner` 的既有测试覆盖。
 */
class QuickMenuScanActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var torchButton: Button
    private lateinit var zoomButton: Button

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    /** 分析器执行器：单线程串行，帧处理顺序可控（解码是重活，不能和预览抢）。 */
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "quick-menu-scan").apply { isDaemon = true }
    }

    private var torchAvailable = false
    private var torchEnabled = false
    private var maxZoomRatio = 1f
    private var zoomStepIndex = 0

    private var frameCount = 0

    /** 分析回调一共来过多少帧（含被守卫挡掉的）：分清"帧没来"与"帧来了被挡掉"。 */
    private var seenFrames = 0

    /** Y 平面数据不足的次数（定频日志用）。 */
    private var rawFailures = 0

    private var delivered = false
    private var decoding = false

    /** 前置等待从"分析首帧到达"算起，不是从 onCreate：否则会被相机启动时间吃掉。 */
    private var previewStartedAtMs = 0L

    /** 上一帧的粗网格亮度签名，用来判"画面稳没稳"。 */
    private var previousSignature: IntArray? = null

    /** 原始 Y 平面的复用缓冲（含行距，逐帧覆写）。 */
    private var rawLuminance: ByteArray? = null

    /** 显示朝向的检测图缓冲（与 [rawLuminance] **必须分开**：同一帧里先拷原始、再写显示朝向）。 */
    private var detectionLuminance: ByteArray? = null

    /** 解码节流：真机日志证明"逐帧解码"是卡顿的唯一来源。 */
    private var lastDecodeAtMs = 0L

    /** 逐帧耗时仪表（累计 [COST_REPORT_FRAMES] 帧打一条平均）：卡顿只能靠测量定位。 */
    private var timedFrames = 0
    private var extractMsSum = 0L
    private var signatureMsSum = 0L
    private var decodeMsSum = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else deliver(emptyList(), null)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())

        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        stopCamera()
        analysisExecutor.shutdown()
        // 用户直接返回（没扫到）时也要把空列表交回去，否则面板的回调会一直挂着。
        if (!delivered) {
            delivered = true
            onDecoded?.invoke(emptyList(), null)
            onDecoded = null
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun buildContentView(): View {
        previewView = PreviewView(this).apply {
            // FIT_CENTER：完整显示整帧画面（留黑边），不裁切 —— 这样"你看到的"与"被定格的"才是同一块。
            scaleType = PreviewView.ScaleType.FIT_CENTER
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    focusAt(event.x, event.y)
                }
                true
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                previewView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                ScanReticleView(this@QuickMenuScanActivity),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        torchButton = controlButton(getString(R.string.quick_menu_scan_torch)) { toggleTorch() }
        zoomButton = controlButton(zoomLabel()) { cycleZoom() }
        val closeButton = controlButton(getString(R.string.quick_menu_scan_close)) { deliver(emptyList(), null) }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(torchButton)
            addView(zoomButton)
            addView(closeButton)
        }
        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply { setMargins(0, 48, 24, 0) },
        )
        return root
    }

    private fun controlButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            alpha = 0.85f
            setOnClickListener { onClick() }
        }

    private fun zoomLabel(): String = "${zoomSteps[zoomStepIndex].toInt()}x"

    // ---------------------------------------------------------------- 相机

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (isFinishing || cameraProvider != null) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull()
            if (provider == null) {
                deliver(emptyList(), null)
                return@addListener
            }
            cameraProvider = provider

            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                // 目标 1920×1080：**定格图就是这一路的帧**，真实像素越多，小码/艺术码越可能读出来。
                .setTargetResolution(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy -> handleFrame(proxy) }

            runCatching {
                provider.unbindAll()
                val bound = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                camera = bound
                torchAvailable = bound.cameraInfo.hasFlashUnit()
                torchButton.visibility = if (torchAvailable) View.VISIBLE else View.GONE
                maxZoomRatio = bound.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            }.onFailure {
                Log.w(TAG, "bindToLifecycle failed", it)
                deliver(emptyList(), null)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        camera = null
    }

    private fun toggleTorch() {
        if (!torchAvailable) return
        torchEnabled = !torchEnabled
        torchButton.alpha = if (torchEnabled) 1f else 0.85f
        runCatching { camera?.cameraControl?.enableTorch(torchEnabled) }
    }

    private fun cycleZoom() {
        zoomStepIndex = (zoomStepIndex + 1) % zoomSteps.size
        zoomButton.text = zoomLabel()
        val ratio = zoomSteps[zoomStepIndex].coerceIn(1f, maxZoomRatio.coerceAtLeast(1f))
        runCatching { camera?.cameraControl?.setZoomRatio(ratio) }
    }

    /**
     * 点击对焦：交给 CameraX 的 `FocusMeteringAction`（自己算坐标那套已经被证明容易出错）。
     * `meteringPointFactory` 用的是 `PreviewView` 自己的显示变换，所以"点哪儿焦哪儿"天然正确。
     */
    private fun focusAt(viewX: Float, viewY: Float) {
        val control = camera?.cameraControl ?: return
        runCatching {
            val point = previewView.meteringPointFactory.createPoint(viewX, viewY)
            val action = FocusMeteringAction
                .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .build()
            control.startFocusAndMetering(action)
        }.onFailure { Log.w(TAG, "focusAt failed", it) }
    }

    // ---------------------------------------------------------------- 帧处理

    /**
     * 分析帧回调（CameraX 在 [analysisExecutor] 上调用）。
     *
     * ⚠️ 必须**始终** `proxy.close()`：不关会让分析流水线停住（表现为画面卡住、再也不出结果）。
     */
    @OptIn(ExperimentalGetImage::class)
    private fun handleFrame(proxy: ImageProxy) {
        try {
            if (delivered || decoding) return
            seenFrames++
            val image = proxy.image
            if (image == null) {
                if (seenFrames <= 3) Log.i(TAG, "分析帧没有 image（format=${proxy.format}）")
                return
            }
            // 放在所有守卫**之前**：卡在"完全不扫码"时，第一要务是分清"帧没来"还是"帧来了但被挡掉"。
            if (seenFrames == 1 || seenFrames % 120 == 0) {
                Log.i(
                    TAG,
                    "分析帧#$seenFrames ${image.width}x${image.height} format=${proxy.format} " +
                        "旋转=${proxy.imageInfo.rotationDegrees} Y行距=${image.planes.firstOrNull()?.rowStride} " +
                        "Y容量=${image.planes.firstOrNull()?.buffer?.remaining()}",
                )
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastDecodeAtMs < DECODE_INTERVAL_MS) return
            lastDecodeAtMs = now
            decoding = true
            frameCount++
            if (previewStartedAtMs == 0L) previewStartedAtMs = now

            // CameraX 直接给出"这一帧要顺时针转多少度才是人看到的方向"，比手算 sensorOrientation 可靠。
            val rotation = proxy.imageInfo.rotationDegrees
            val rawY = copyRawLuminance(image) ?: return
            val rowStride = image.planes.firstOrNull()?.rowStride ?: image.width
            val step = triggerSampleStep(image.width, image.height, rotation)
            val afterExtract = SystemClock.elapsedRealtime()

            // 检测只看"整帧"（PreviewView 是 FIT_CENTER，显示的就是整帧），并降到 TRIGGER_LONG_SIDE 量级：
            // 逐帧成本 ≈ 像素数 × 解码次数，两个因子都要压住。displayAspect 传 0 表示不裁。
            val display = QuickMenuScanFrame.displayLuminance(
                luminance = rawY,
                width = image.width,
                height = image.height,
                rowStride = rowStride,
                rotationDegrees = rotation,
                displayAspect = 0f,
                sampleStep = step,
                into = detectionLuminance,
            )
            detectionLuminance = display.pixels
            if (frameCount == 1 || frameCount % 60 == 0) {
                Log.i(
                    TAG,
                    "帧：流=${image.width}x${image.height} 旋转=$rotation rowStride=$rowStride " +
                        "检测图=${display.width}x${display.height} 抽样=$step",
                )
            }

            // 稳定度：粗网格亮度签名与上一帧比（几乎零成本，抖动在 8×8 上就看得出来）
            val signature = QuickMenuScanFrame.gridSignature(display.pixels, display.width, display.height)
            val motion = previousSignature?.let { QuickMenuScanFrame.meanAbsDiff(it, signature) }
                ?: Float.MAX_VALUE
            previousSignature = signature
            val afterSignature = SystemClock.elapsedRealtime()

            // 触发检查用**最便宜**的组合（单码读取器 + 只主二值化 + 只标准亮度）：真机实测
            // "多码读取器 + 两种二值化"在 1280×960 上要 130~157ms/帧，足以把预览拖卡。
            // 权威的多码识别交给面板在定格图上做。
            val fullPass = frameCount % ALL_FORMAT_EVERY_N_FRAMES == 0
            val results = ZxingBarcodeScanner.scanLuminanceForTrigger(
                luminance = display.pixels,
                width = display.width,
                height = display.height,
                qrOnly = !fullPass,
            )
            reportFrameCost(
                image = image,
                extractMs = afterExtract - now,
                signatureMs = afterSignature - afterExtract,
                decodeMs = SystemClock.elapsedRealtime() - afterSignature,
            )

            // **扫到就定格**：只要这一帧解出了东西、画面是稳的、已过前置等待，立刻抓。
            // 有意不做"同一个码连续 N 帧"：那会让触发取决于"哪个码先被认出"，一屏多个码时结果就会飘。
            val prerollDone = SystemClock.elapsedRealtime() - previewStartedAtMs >= PREROLL_MS
            val steady = motion <= MOTION_THRESHOLD
            if (!prerollDone || !steady || results.isEmpty()) {
                if (frameCount % 30 == 0) {
                    Log.i(TAG, "等待触发：preroll=$prerollDone motion=${motion.toInt()} n=${results.size}")
                }
                return
            }

            // 定格：检测图已经是显示朝向且不裁切，所以**点位不需要再旋转**，它与交回面板的那张图同坐标系。
            val frozen = freezeFrame(image, rotation)
            Log.i(
                TAG,
                "锁定：n=${results.size} motion=${motion.toInt()} 流=${image.width}x${image.height} " +
                    "定格图=${frozen?.width}x${frozen?.height}",
            )
            deliver(results, frozen)
        } catch (error: Exception) {
            Log.w(TAG, "frame decode failed", error)
        } finally {
            decoding = false
            runCatching { proxy.close() }
        }
    }

    /**
     * 把 Y 平面拷进复用缓冲。
     *
     * ⚠️ **不能要求"必须够整块"**：不同机型的 `rowStride`/缓冲容量口径不一致，按理论值去比就会出现
     * "预览正常、却永远不扫码"——每帧都在这里返回 null，而且不报错、不崩溃（真机踩过两次：
     * 一次在软件预览上表现为黑屏，一次在检测上表现为完全不触发）。现在改成**能拿多少用多少**，
     * 缺的部分填 0；只有连一行宽度都拿不到才放弃这一帧。
     */
    private fun copyRawLuminance(image: Image): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val needed = plane.rowStride * height
        if (needed <= 0) return null
        val available = min(needed, plane.buffer.remaining())
        if (available < width) {
            if (rawFailures++ % 30 == 0) {
                Log.i(
                    TAG,
                    "Y 平面数据不足：rowStride=${plane.rowStride} 容量=${plane.buffer.remaining()} " +
                        "需要=$needed 画面=${width}x$height",
                )
            }
            return null
        }
        val data = rawLuminance?.takeIf { it.size == needed } ?: ByteArray(needed).also { rawLuminance = it }
        // 先清掉"这一帧没拷到"的尾部，避免上一帧的残留像素被当成画面
        if (available < needed) java.util.Arrays.fill(data, available, needed, 0.toByte())
        plane.buffer.get(data, 0, available)
        return data
    }

    /** 取一块可复用的亮度缓冲；尺寸变了才重新分配。返回值会被下一帧覆写，调用方不得留存。 */
    private fun luminanceBufferFor(size: Int): ByteArray {
        val existing = rawLuminance
        if (existing != null && existing.size >= size) return existing
        return ByteArray(size).also { rawLuminance = it }
    }

    /**
     * 检测用的抽样步长：把画面长边降到约 [TRIGGER_LONG_SIDE]。
     *
     * 逐帧成本 ≈ 像素数 × 解码次数。像素数由相机流尺寸决定（定格图要真实像素，所以取大的），
     * 检测这边靠抽样把它降回来；定格仍按全分辨率做——那是一次性的。
     */
    private fun triggerSampleStep(width: Int, height: Int, rotationDegrees: Int): Int {
        val display = QuickMenuScanFrame.displaySize(width, height, rotationDegrees)
        val longSide = maxOf(display.width, display.height)
        return ceil(longSide.toDouble() / TRIGGER_LONG_SIDE).toInt().coerceAtLeast(1)
    }

    /**
     * 定格这一帧：YUV → ARGB，再按 [rotationDegrees] 摆正。
     *
     * 不做任何裁切：`PreviewView` 是 FIT_CENTER（显示整帧），分析流给的也是整帧，两边取景一致，
     * 所以定格图 = 用户看到的那块画面。
     */
    private fun freezeFrame(image: Image, rotationDegrees: Int): Bitmap? {
        val source = yuvToBitmap(image) ?: return null
        if (rotationDegrees == 0) return source
        return runCatching {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()
            rotated
        }.getOrElse {
            Log.w(TAG, "freezeFrame rotate failed", it)
            source
        }
    }

    /**
     * `YUV_420_888` → ARGB 位图。
     *
     * 逐像素走 Y/U/V 三个平面，**各自按自己的 `rowStride` / `pixelStride` 取值**——硬件对齐会让
     * 行步长大于宽度，这是 Android 上 YUV 处理最经典的坑；色度是半分辨率，所以列与行都要除以 2。
     */
    private fun yuvToBitmap(image: Image): Bitmap? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val planes = image.planes
        if (planes.size < 3) return null
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        return runCatching {
            val pixels = IntArray(width * height)
            for (row in 0 until height) {
                val yRowBase = row * yPlane.rowStride
                val uvRow = row / 2
                val uRowBase = uvRow * uPlane.rowStride
                val vRowBase = uvRow * vPlane.rowStride
                for (column in 0 until width) {
                    val y = yPlane.buffer.get(yRowBase + column * yPlane.pixelStride).toInt() and 0xFF
                    val uvColumn = column / 2
                    val u = uPlane.buffer.get(uRowBase + uvColumn * uPlane.pixelStride).toInt() and 0xFF
                    val v = vPlane.buffer.get(vRowBase + uvColumn * vPlane.pixelStride).toInt() and 0xFF
                    pixels[row * width + column] = QuickMenuScanFrame.yuvToArgb(y, u, v)
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }.getOrElse {
            Log.w(TAG, "yuvToBitmap failed", it)
            null
        }
    }

    /**
     * 累计若干帧的三段耗时并打一条平均值。
     *
     * 存在的理由：卡顿这类问题**必须靠测量定位**。我曾经两次"推断哪里慢"，结果一次把尺寸提到 1920
     * 造成持续卡顿、一次自己加了逐像素除法——有这条日志就能一眼看出是 extract（取亮度）、
     * signature（帧差）还是 decode（识别）在吃时间。
     */
    private fun reportFrameCost(image: Image, extractMs: Long, signatureMs: Long, decodeMs: Long) {
        timedFrames++
        extractMsSum += extractMs
        signatureMsSum += signatureMs
        decodeMsSum += decodeMs
        if (timedFrames < COST_REPORT_FRAMES) return
        Log.i(
            TAG,
            "逐帧耗时(平均 ${timedFrames} 帧，${image.width}x${image.height})：" +
                "extract=${extractMsSum / timedFrames}ms " +
                "signature=${signatureMsSum / timedFrames}ms " +
                "decode=${decodeMsSum / timedFrames}ms（解码间隔下限 ${DECODE_INTERVAL_MS}ms）",
        )
        timedFrames = 0
        extractMsSum = 0
        signatureMsSum = 0
        decodeMsSum = 0
    }

    /**
     * 交回结果并关掉相机页。
     *
     * 诊断日志是**故意**留的：这类"认出了几个 / 有没有定位点 / 有没有定格图"的事实，
     * 之前只能靠读代码推断，现在一次 logcat 就能定论。
     */
    private fun deliver(results: List<BarcodeScanResult>, image: Bitmap?) {
        if (delivered) return
        delivered = true
        Log.i(
            TAG,
            "deliver: n=${results.size} withPoints=${results.count { it.points.isNotEmpty() }} image=${image != null}",
        )
        mainHandler.post {
            onDecoded?.invoke(results, image)
            onDecoded = null
            if (!isFinishing) finishWithoutTransition()
        }
    }

    /** 取景框：四角括号，只画描边，不挡预览。 */
    private class ScanReticleView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = min(width, height) * RETICLE_SIDE_FRACTION
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            val right = left + side
            val bottom = top + side
            val arm = side * RETICLE_ARM_FRACTION
            // 四个角，各两笔
            canvas.drawLine(left, top, left + arm, top, paint)
            canvas.drawLine(left, top, left, top + arm, paint)
            canvas.drawLine(right, top, right - arm, top, paint)
            canvas.drawLine(right, top, right, top + arm, paint)
            canvas.drawLine(left, bottom, left + arm, bottom, paint)
            canvas.drawLine(left, bottom, left, bottom - arm, paint)
            canvas.drawLine(right, bottom, right - arm, bottom, paint)
            canvas.drawLine(right, bottom, right, bottom - arm, paint)
        }
    }

    companion object {
        private const val TAG = "QuickMenuScan"

        /** 分析流的目标尺寸：**定格图就是这一路的帧**，真实像素越多，小码/艺术码越可能读出来。 */
        private const val ANALYSIS_WIDTH = 1920
        private const val ANALYSIS_HEIGHT = 1080

        /** 检测用的目标长边（抽样把逐帧成本压回来）。 */
        private const val TRIGGER_LONG_SIDE = 960

        private const val ALL_FORMAT_EVERY_N_FRAMES = 5

        /**
         * 两次解码之间的最小间隔（约 12fps）。
         *
         * 预览卡顿唯一的**结构性**解法是降低"每帧干活"的频率：分析流一旦让相机排队，HAL 就开始丢帧，
         * 那时再抠每帧内部的几毫秒都晚了。12fps 的检测对"瞄准后扫描"够用，其余 CPU 全留给预览。
         */
        private const val DECODE_INTERVAL_MS = 80L

        /** 每处理这么多帧打一次耗时平均（见 [reportFrameCost]）。 */
        private const val COST_REPORT_FRAMES = 30

        /**
         * 前置等待：首帧到达后先等这么久再接受触发。
         *
         * 它**不是**"等用户对准"（用户要多久不确定），而是**等 AE/AF 收敛**——刚打开那几帧往往
         * 又暗又糊，容易读错。从"首帧"算起，否则会被相机启动时间吃掉。
         */
        private const val PREROLL_MS = 250L

        /**
         * 帧差稳定阈值（8×8 粗网格的平均绝对差，0..255）。
         *
         * 手持静止时通常是个位数，晃动时会到十几以上。太大则"晃着也触发"，太小则很难触发。
         * 现场调参看 `QuickMenuScan` 日志里的 `motion=` 值即可。
         */
        private const val MOTION_THRESHOLD = 8f

        private const val RETICLE_SIDE_FRACTION = 0.62f
        private const val RETICLE_ARM_FRACTION = 0.22f

        /** 可循环的放大档位（会按设备支持的最大数码变焦夹取）。 */
        private val zoomSteps = floatArrayOf(1f, 2f, 3f)

        /**
         * 结果回调（扫到的**所有**原文；用户取消时为空列表）。
         *
         * 用静态回调而非 ActivityResult API，原因是调用方（快捷菜单面板）跑在 overlay 的
         * ComposeView 里、拿不到 `ActivityResultRegistry`；这与本仓库既有的
         * `SearchPanelImagePickerActivity` 做法一致。区别是这里在投递后**立即清空**引用，
         * 并且 `onDestroy` 兜底交付空列表，不会留下跨 Activity 的悬挂回调。
         */
        private var onDecoded: ((List<BarcodeScanResult>, Bitmap?) -> Unit)? = null

        fun launch(context: Context, onResult: (List<BarcodeScanResult>, Bitmap?) -> Unit) {
            onDecoded = onResult
            val intent = Intent(context, QuickMenuScanActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
