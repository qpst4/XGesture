package com.slideindex.app.ocr

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.core.content.ContextCompat
import com.paddle.ocr.model.OCRBox
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.ocr.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class OcrInferenceService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OcrModelRepository,
    private val catalogProvider: OcrModelCatalogProvider,
    private val nativeEnginePackCoordinator: NativeEnginePackCoordinator,
    private val vlmConfigManager: com.slideindex.app.ocr.vlm.VlmOcrConfigManager,
    private val applicationScope: CoroutineScope,
) {
    private companion object {
        private const val TAG = "OcrInferenceService"

        /**
         * 最后一次推理结束后，引擎会话允许继续驻留的时间。
         *
         * ONNX Runtime 的 det/rec session + arena 在真机上实测是几百 MB 级，而取词是"人手点几下"
         * 的交互：这个窗口内保持热会话（"取完看一眼再取"的连点不额外变慢），窗口过去就放掉。
         * 另外两条零代价的释放路径见 [screenOffReceiver] / [memoryTrimCallbacks]。
         */
        private const val IDLE_ENGINE_RELEASE_MS = 30_000L
    }

    private val mutex = Mutex()
    private var loadedModelId: String? = null
    private var loadedEngine: String? = null
    private var paddleOcr: PaddleOCR? = null
    private var openCvInitialized = false

    /** 待执行的空闲释放任务；每次推理进入时取消，推理结束后重新排。 */
    private var idleReleaseJob: Job? = null

    /**
     * 息屏即释放。
     *
     * 息屏之后不可能再取词，会话留着纯属白占；而且空闲释放用的是协程 `delay`，它**不是**唤醒闹钟，
     * 设备进 suspend 之后不保证准点，靠它等息屏释放会拖到下次亮屏。这里直接插队释放。
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                releaseOnDemand("screenOff")
            }
        }
    }

    /**
     * 系统内存吃紧时的释放。只认"本进程正在被压"这几档，`TRIM_MEMORY_BACKGROUND` 之类不接——
     * 本 App 常态就在后台，那种 trim 一来就放会退化成"每次取词都冷建"。
     */
    private val memoryTrimCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
                -> releaseOnDemand("trimMemory($level)")
                else -> Unit
            }
        }

        override fun onLowMemory() {
            releaseOnDemand("lowMemory")
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit
    }

    init {
        runCatching {
            ContextCompat.registerReceiver(
                context,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            context.registerComponentCallbacks(memoryTrimCallbacks)
        }.onFailure { error ->
            Log.w(TAG, "register engine idle hooks failed", error)
        }
    }

    /** 不等空闲窗口，直接作废会话；没有加载过引擎时是空操作。 */
    private fun releaseOnDemand(reason: String) {
        applicationScope.launch {
            runCatching { invalidateEngine() }
                .onFailure { error -> Log.w(TAG, "engine release failed reason=$reason", error) }
        }
    }

    /**
     * PP-OCR line boxes for on-screen find (e.g. screen search). Returns null if model is not PP-OCR.
     *
     * 非 `:engine` 进程优先走跨进程传输（推理在引擎进程执行）；传输未接或失败时本地兜底。
     */
    suspend fun recognizePpOcrLines(modelId: String, bitmap: Bitmap): List<OcrRecognizedLine>? {
        if (!com.slideindex.app.util.AppProcess.isEngine) {
            val remote = OcrRemoteBridge.transport?.recognizePpOcrLines(modelId, bitmap)
            if (remote != null && remote.isSuccess) return remote.getOrNull()
        }
        return recognizePpOcrLinesLocal(modelId, bitmap)
    }

    private suspend fun recognizePpOcrLinesLocal(modelId: String, bitmap: Bitmap): List<OcrRecognizedLine>? {
        val entry = catalogProvider.findModel(modelId) ?: return null
        if (entry.engine != OcrEngines.PPOCR) return null
        if (!repository.isInstalled(modelId)) return null
        return withSessionUse {
            if (!nativeEnginePackCoordinator.ensurePackReady(NativeEnginePackIds.OCR)) {
                return@withSessionUse null
            }
            ensureEngine(modelId, OcrEngines.PPOCR)
            val engine = paddleOcr ?: return@withSessionUse null
            val result = engine.recognize(bitmap)
            OcrRtlText.orderedForReading(
                result.results.map { item ->
                    OcrRecognizedLine(
                        bounds = ocrBoxToRect(item.box),
                        text = item.text,
                    )
                },
            )
        }
    }

    suspend fun recognizeBitmap(modelId: String, bitmap: Bitmap): OcrRecognizeResult {
        if (!com.slideindex.app.util.AppProcess.isEngine) {
            OcrRemoteBridge.transport?.recognizeBitmap(modelId, bitmap)?.let { return it }
        }
        return recognizeBitmapLocal(modelId, bitmap)
    }

    private suspend fun recognizeBitmapLocal(modelId: String, bitmap: Bitmap): OcrRecognizeResult {
        val entry = catalogProvider.findModel(modelId) ?: run {
            Log.w(TAG, "recognize skipped: unknown modelId=$modelId")
            return OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_unknown_model, modelId))
        }
        if (!repository.isInstalled(modelId)) {
            Log.w(TAG, "recognize skipped: model not installed modelId=$modelId")
            return OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_model_not_installed))
        }
        return withSessionUse {
            when (entry.engine) {
                OcrEngines.PPOCR -> {
                    if (!nativeEnginePackCoordinator.ensurePackReady(NativeEnginePackIds.OCR)) {
                        Log.w(TAG, "recognize skipped: OCR native engine pack not ready")
                        return@withSessionUse OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_native_engine_not_ready))
                    }
                    recognizeWithPpOcr(modelId, bitmap)
                }
                OcrEngines.MLKIT_CHINESE -> {
                    ensureEngine(modelId, entry.engine)
                    val text = MlKitTextRecognizer.recognize(bitmap)?.trim().orEmpty()
                    text.toSuccessOrEmptyFailure()
                }
                OcrEngines.TESSERACT -> {
                    if (!nativeEnginePackCoordinator.ensurePackReady(NativeEnginePackIds.OCR)) {
                        return@withSessionUse OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_native_engine_not_ready))
                    }
                    ensureEngine(modelId, entry.engine)
                    val text = TesseractTextRecognizer.recognize(
                        modelId = modelId,
                        dataRoot = repository.modelRoot(modelId),
                        bitmap = bitmap,
                    )?.trim().orEmpty()
                    text.toSuccessOrEmptyFailure()
                }
                OcrEngines.VLM_FORMULA -> {
                    val apiKey = vlmConfigManager.apiKey
                    if (apiKey.isBlank()) {
                        Log.w(TAG, "recognize skipped: VLM API key not configured")
                        return@withSessionUse OcrRecognizeResult.Failure(
                            context.getString(R.string.ocr_error_api_key_not_configured),
                        )
                    }
                    com.slideindex.app.ocr.vlm.VlmFormulaOcrEngine.recognize(
                        context = context,
                        bitmap = bitmap,
                        apiKey = apiKey,
                        baseUrl = vlmConfigManager.baseUrl,
                        model = vlmConfigManager.model,
                        prompt = vlmConfigManager.prompt,
                    )
                }
                else -> OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_unsupported_engine))
            }
        }
    }

    /**
     * 所有推理入口都从这里进：拿锁（与释放互斥）、取消待执行的空闲释放、执行、最后重新排释放。
     *
     * 并发用"持锁"表达——同一时刻只有一段推理在跑；只要还有请求进来，待执行的释放任务就会被取消。
     * 因此空闲任务真正拿到锁时，就说明这段时间确实没人用过引擎，可以放。
     */
    private suspend fun <T> withSessionUse(block: suspend () -> T): T =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                cancelIdleReleaseLocked()
                try {
                    block()
                } finally {
                    scheduleIdleReleaseLocked()
                }
            }
        }

    private fun cancelIdleReleaseLocked() {
        idleReleaseJob?.cancel()
        idleReleaseJob = null
    }

    private fun scheduleIdleReleaseLocked() {
        idleReleaseJob?.cancel()
        idleReleaseJob = applicationScope.launch {
            delay(IDLE_ENGINE_RELEASE_MS)
            mutex.withLock {
                // 释放过程不可被取消：release 打到一半被打断，会留下"会话已关、引用还在"的脏状态。
                withContext(NonCancellable) {
                    releaseLoadedEngine()
                }
            }
        }
    }

    suspend fun release() {
        mutex.withLock {
            cancelIdleReleaseLocked()
            val modelId = loadedModelId
            releaseLoadedEngine()
            MlKitTextRecognizer.close()
            TesseractTextRecognizer.close(modelId)
        }
    }

    suspend fun invalidateIfModelChanged(selectedModelId: String?) {
        mutex.withLock {
            cancelIdleReleaseLocked()
            if (loadedModelId != null && loadedModelId != selectedModelId) {
                releaseLoadedEngine()
            }
        }
    }

    /** OCR 引擎包升级/删除后调用，避免继续复用旧的 PaddleOCR 会话。 */
    fun invalidateEngineBlocking() {
        kotlinx.coroutines.runBlocking(Dispatchers.Default) {
            invalidateEngine()
        }
    }

    /**
     * 挂起版的"无条件作废当前引擎会话"，供协程里调用（别在主线程 runBlocking）。
     *
     * 与 [invalidateIfModelChanged] 的区别：后者只在"已加载模型 ≠ 传入模型"时释放，
     * 删除引擎包时传的恰好是当前选中的模型，于是永远不会释放，留下"包已删、旧会话还在"的脏状态。
     */
    suspend fun invalidateEngine() {
        mutex.withLock {
            cancelIdleReleaseLocked()
            releaseLoadedEngine()
        }
    }

    private suspend fun recognizeWithPpOcr(modelId: String, bitmap: Bitmap): OcrRecognizeResult {
        ensureEngine(modelId, OcrEngines.PPOCR)
        val engine = paddleOcr ?: run {
            Log.w(TAG, "recognize skipped: PaddleOCR engine not initialized modelId=$modelId")
            return OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_paddle_not_initialized))
        }
        val result = engine.recognize(bitmap)
        val text = OcrRtlText.orderedForReading(
            result.results.map { item ->
                OcrRecognizedLine(bounds = ocrBoxToRect(item.box), text = item.text)
            },
        ).joinToString("\n") { item -> item.text }.trim()
        if (text.isEmpty()) {
            Log.i(TAG, "recognize empty: modelId=$modelId boxes=${result.lineCount}")
        }
        return text.toSuccessOrEmptyFailure()
    }

    private suspend fun ensureEngine(modelId: String, engine: String) {
        if (loadedModelId == modelId && loadedEngine == engine) {
            if (engine == OcrEngines.PPOCR && paddleOcr != null) return
            if (engine != OcrEngines.PPOCR) return
        }
        releaseLoadedEngine()
        loadedModelId = modelId
        loadedEngine = engine

        if (engine != OcrEngines.PPOCR) return

        if (!ensureOpenCvReady()) {
            loadedModelId = null
            loadedEngine = null
            return
        }

        val det = repository.detModelFile(modelId)
        val rec = repository.recModelFile(modelId)
        val config = repository.recConfigFile(modelId)
        paddleOcr = try {
            PaddleOCR.createFromFiles(
                context = context,
                config = PaddleOCRConfig(),
                engineConfig = EngineConfig(numThreads = 4),
                detModelFilePath = det.absolutePath,
                recModelFilePath = rec.absolutePath,
                recConfigFilePath = config.absolutePath,
            )
        } catch (error: Throwable) {
            Log.e(TAG, "PaddleOCR init failed modelId=$modelId", error)
            loadedModelId = null
            loadedEngine = null
            null
        }
    }

    private fun ensureOpenCvReady(): Boolean {
        if (openCvInitialized) return true
        openCvInitialized = OpenCVUtils.init(context)
        if (!openCvInitialized) {
            Log.e(TAG, "OpenCV not ready; PP-OCR cannot run")
        }
        return openCvInitialized
    }

    private suspend fun releaseLoadedEngine() {
        paddleOcr?.release()
        paddleOcr = null
        if (loadedEngine == OcrEngines.MLKIT_CHINESE) {
            MlKitTextRecognizer.close()
        }
        if (loadedEngine == OcrEngines.TESSERACT) {
            TesseractTextRecognizer.close(loadedModelId)
        }
        loadedModelId = null
        loadedEngine = null
    }

    private fun String.toSuccessOrEmptyFailure(): OcrRecognizeResult =
        if (isNotBlank()) {
            OcrRecognizeResult.Success(this)
        } else {
            OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_no_text_recognized))
        }

    private fun ocrBoxToRect(box: OCRBox): Rect {
        val xs = box.points.map { it.x }
        val ys = box.points.map { it.y }
        return Rect(
            xs.min().toInt(),
            ys.min().toInt(),
            xs.max().toInt(),
            ys.max().toInt(),
        )
    }
}
