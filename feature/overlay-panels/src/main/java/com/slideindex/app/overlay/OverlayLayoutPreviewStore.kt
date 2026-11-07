package com.slideindex.app.overlay

import com.slideindex.app.gesture.TriggerHandleDesign

/**
 * 布局/触钮设置页拖动滑条时的临时预览值；不落盘，结束预览后应清空。
 */
object OverlayLayoutPreviewStore {
    /** 索引页两个滑条：松手时只提交最近拖过的那一个。 */
    enum class IndexSlider { HEIGHT, VERTICAL_POSITION }

    @Volatile
    private var lastIndexSlider: IndexSlider? = null

    @Volatile
    var indexHeightFraction: Float? = null
        private set

    /** 字母索引条的纵向位置比例（0 = 顶、1 = 底），与 [indexHeightFraction] 同一套预览语义。 */
    @Volatile
    var indexVerticalPositionFraction: Float? = null
        private set

    fun previewIndexHeightFraction(fraction: Float) {
        indexHeightFraction = fraction
        lastIndexSlider = IndexSlider.HEIGHT
    }

    fun previewIndexVerticalPositionFraction(fraction: Float) {
        indexVerticalPositionFraction = fraction
        lastIndexSlider = IndexSlider.VERTICAL_POSITION
    }

    /**
     * 已提交、但还没被设置回流确认的预览值。
     *
     * 滑条松手时"写设置"是异步落盘的；如果这时立刻丢掉临时预览值，浮层会先按**旧的**已保存值
     * 重画一遍，等新值落盘回流后再重画一次（用户看到的就是"松手先跳回原位、再跳到正确位置"）。
     * 因此提交时先把值挪到 [committedIndexHeightFraction] / [committedTriggerHandlePreview] 继续顶着，
     * 等回流过来的设置值和它一样（说明已经落盘）再由 [AppSettings.withOverlayLayoutPreview] 丢掉。
     */
    @Volatile
    var committedIndexHeightFraction: Float? = null

    @Volatile
    private var committedIndexHeightAtMs: Long = 0L

    @Volatile
    var committedIndexVerticalPositionFraction: Float? = null

    @Volatile
    private var committedIndexVerticalPositionAtMs: Long = 0L

    data class TriggerHandlePreview(
        val side: PanelSide,
        val handleId: String,
        val edgeWidthDp: Float? = null,
        val topFraction: Float? = null,
        val bottomFraction: Float? = null,
        val shortSwipeDistanceDp: Float? = null,
        val longSwipeDistanceDp: Float? = null,
        val design: TriggerHandleDesign? = null
    )

    @Volatile
    var triggerHandlePreview: TriggerHandlePreview? = null

    @Volatile
    var committedTriggerHandlePreview: TriggerHandlePreview? = null

    @Volatile
    private var committedTriggerHandleAtMs: Long = 0L

    fun clear() {
        indexHeightFraction = null
        indexVerticalPositionFraction = null
        triggerHandlePreview = null
        committedIndexHeightFraction = null
        committedIndexVerticalPositionFraction = null
        committedTriggerHandlePreview = null
    }

    fun clearIndexSliderPreview() {
        indexHeightFraction = null
        indexVerticalPositionFraction = null
        committedIndexHeightFraction = null
        committedIndexVerticalPositionFraction = null
        lastIndexSlider = null
    }

    fun clearTriggerHandlePreview() {
        triggerHandlePreview = null
        committedTriggerHandlePreview = null
    }

    /** 松手提交：临时值转为"待确认"，继续生效直到设置回流追上。 */
    fun commitIndexHeightPreview() {
        val current = indexHeightFraction ?: return
        committedIndexHeightFraction = current
        committedIndexHeightAtMs = android.os.SystemClock.elapsedRealtime()
        indexHeightFraction = null
    }

    fun commitIndexVerticalPositionPreview() {
        val current = indexVerticalPositionFraction ?: return
        committedIndexVerticalPositionFraction = current
        committedIndexVerticalPositionAtMs = android.os.SystemClock.elapsedRealtime()
        indexVerticalPositionFraction = null
    }

    /**
     * 索引页滑条松手：只把**最近拖过的那一个**转成"待确认"，另一个直接丢弃。
     * （两个滑条共用一个预览会话，不区分的话任何一个松手都会让另一个也顶 2 秒。）
     */
    fun commitIndexSliderPreviews() {
        when (lastIndexSlider) {
            IndexSlider.HEIGHT -> {
                indexVerticalPositionFraction = null
                committedIndexVerticalPositionFraction = null
                commitIndexHeightPreview()
            }
            IndexSlider.VERTICAL_POSITION -> {
                indexHeightFraction = null
                committedIndexHeightFraction = null
                commitIndexVerticalPositionPreview()
            }
            null -> Unit
        }
        lastIndexSlider = null
    }

    fun commitTriggerHandlePreview() {
        val current = triggerHandlePreview ?: return
        committedTriggerHandlePreview = current
        committedTriggerHandleAtMs = android.os.SystemClock.elapsedRealtime()
        triggerHandlePreview = null
    }

    /** 超时兜底：设置写入失败时不能永远顶着旧值。 */
    fun activeIndexHeightFraction(nowMs: Long): Float? =
        indexHeightFraction ?: committedIndexHeightFraction?.takeIf {
            nowMs - committedIndexHeightAtMs <= COMMIT_TTL_MS
        }

    fun activeIndexVerticalPositionFraction(nowMs: Long): Float? =
        indexVerticalPositionFraction ?: committedIndexVerticalPositionFraction?.takeIf {
            nowMs - committedIndexVerticalPositionAtMs <= COMMIT_TTL_MS
        }

    fun activeTriggerHandlePreview(nowMs: Long): TriggerHandlePreview? =
        triggerHandlePreview ?: committedTriggerHandlePreview?.takeIf {
            nowMs - committedTriggerHandleAtMs <= COMMIT_TTL_MS
        }

    fun dropCommittedIndexHeight() {
        committedIndexHeightFraction = null
    }

    fun dropCommittedIndexVerticalPosition() {
        committedIndexVerticalPositionFraction = null
    }

    fun dropCommittedTriggerHandle() {
        committedTriggerHandlePreview = null
    }

    fun mergeTriggerHandlePreview(
        side: PanelSide,
        handleId: String,
        edgeWidthDp: Float? = null,
        topFraction: Float? = null,
        bottomFraction: Float? = null,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
        design: TriggerHandleDesign? = null
    ) {
        val existing = triggerHandlePreview?.takeIf { it.side == side && it.handleId == handleId }
        triggerHandlePreview = TriggerHandlePreview(
            side = side,
            handleId = handleId,
            edgeWidthDp = edgeWidthDp ?: existing?.edgeWidthDp,
            topFraction = topFraction ?: existing?.topFraction,
            bottomFraction = bottomFraction ?: existing?.bottomFraction,
            shortSwipeDistanceDp = shortSwipeDistanceDp ?: existing?.shortSwipeDistanceDp,
            longSwipeDistanceDp = longSwipeDistanceDp ?: existing?.longSwipeDistanceDp,
            design = design ?: existing?.design
        )
    }

    /** 提交值顶住的时限；超过它说明设置没能落盘，放手让浮层回到已保存值。 */
    private const val COMMIT_TTL_MS = 2_000L
}
