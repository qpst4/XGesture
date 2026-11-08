package com.slideindex.app.overlay.pickresult

import android.content.Context
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.FloatBallPickResult

/**
 * 取词/取图结果面板的端口（`:app` 的 `FloatBallPickResultPanel`）。
 *
 * `SmartScreenshotOcr` 等截图链路要用到"先弹面板、再异步补扫码/OCR 结果"，
 * 这里把这些能力抽成接口，面板模块不再直接依赖 `:app` 的那个 1360 行面板。
 */
interface PickResultHost {

    /** 弹出结果面板。 */
    fun show(
        context: Context,
        anchorX: Float,
        anchorY: Float,
        result: FloatBallPickResult,
        textMode: PickResultTextMode,
    )

    /** 当前展示令牌（用于判断异步结果是否还属于同一次展示）。 */
    fun showToken(): Int

    /** 该令牌对应的面板是否仍然在显示。 */
    fun isStillShowing(token: Int): Boolean

    /** 追加扫码结果。 */
    fun updateBarcodeResults(results: List<BarcodeScanResult>, token: Int)

    /** 更新 OCR 文本。 */
    fun updateOcrText(text: String, switchToOcr: Boolean, textMode: PickResultTextMode)

    /** 结束 OCR 等待态。 */
    fun finishOcrPending()

    /** 展示 OCR 错误。 */
    fun showOcrError(context: Context, reason: String)
}

/** 模块内服务定位点；未注入时截图链路直接降级（不弹面板）。 */
object PickResultHostAccess {

    @Volatile
    var host: PickResultHost? = null
}
