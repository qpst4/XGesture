package com.slideindex.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PixelFormat
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.util.ImageDecodeGuard
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.overlay.history.HistoryCardSnapshot
import com.slideindex.app.overlay.history.recordHistoryCardSnapshot
import com.slideindex.app.overlay.history.rememberHistoryCardSnapshot
import com.slideindex.app.stash.PinNotificationSnapshot
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashPinNotificationHelper
import com.slideindex.app.stash.resolvedContentBlocks
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ScreenPinDropZone {
    NONE,
    STASH,
    DELETE,
    NOTIFICATION,
}

private const val TEXT_PIN_MAX_WIDTH_FRACTION = 0.55f
private const val TEXT_PIN_MAX_HEIGHT_FRACTION = 0.5f
private const val TEXT_PIN_MIN_WIDTH_DP = 96f
private const val TEXT_PIN_MIN_HEIGHT_DP = 44f
/** 与 [ScreenPinContent] 中文本渲染保持一致的字号与内边距。 */
private const val TEXT_PIN_TEXT_SIZE_SP = 15f
private const val TEXT_PIN_CONTENT_PADDING_DP = 12f
private const val TEXT_PIN_SIZE_EPSILON_DP = 2f
private const val RICH_PIN_MAX_HEIGHT_FRACTION = 0.5f
private const val RICH_PIN_IMAGE_MAX_HEIGHT_DP = 220f
private const val PIN_CONTROL_ALPHA_MIN = 0.1f
private const val EDGE_DOCK_SIZE_DP = 50f
private const val EDGE_SNAP_ANIM_MS = 200L
private const val PIN_CONTROL_BAR_MIN_WIDTH_DP = 188f
private const val PIN_CONTROL_BAR_SLOT_HEIGHT_DP = 56f
/** 命中容错：只在贴边标签的**内侧**外扩这么多；值越大越容易和贴边吸附抢手势。 */
private const val DROP_TARGET_HIT_SLOP_DP = 4f
/** 手指在同一投放口里驻留这么久才"武装"（放大 + 实色），松手才认投放。 */
private const val DROP_TARGET_ARM_DELAY_MS = 140L
/**
 * 三个投放口的尺寸；必须和 [ScreenPinDropTargets] 里的 dp 值、[computeFallbackDropBounds] 完全一致。
 * 半隐：顶部/底部只露上/下一半、右侧只露左一半，所以「半隐方向」的尺寸是 **可见尺寸 × 2**。
 */
private const val DROP_TARGET_NOTIFY_WIDTH_DP = 168f
private const val DROP_TARGET_NOTIFY_HEIGHT_DP = 64f
private const val DROP_TARGET_STASH_WIDTH_DP = 136f
private const val DROP_TARGET_STASH_HEIGHT_DP = 104f
private const val DROP_TARGET_DELETE_WIDTH_DP = 176f
private const val DROP_TARGET_DELETE_HEIGHT_DP = 64f
/** 静止态半透明、被手指压住才完全不透明；武装后再放大 —— 两级反馈。 */
private const val DROP_TARGET_IDLE_ALPHA = 0.6f
private const val DROP_TARGET_ARMED_SCALE = 1.1f
/** 入场/退场：从各自贴着的那条边滑进来、滑出去。 */
private const val DROP_TARGET_ENTER_MS = 140
private const val DROP_TARGET_ENTER_SLIDE_DP = 12f
/** 退场动画播完（加一点余量）才把常驻的投放层窗口置为 GONE。 */
private const val DROP_TARGET_EXIT_HIDE_DELAY_MS = DROP_TARGET_ENTER_MS + 40L
/** 通知标题用钉图内容 OCR：大图必须先降采样，标题也限长。 */
private const val NOTIFICATION_OCR_MAX_EDGE_PX = 1280
private const val NOTIFICATION_TITLE_MAX_CHARS = 24

private fun pinOverlayFlags(): Int {
    return WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
}

private fun viewToScreen(view: android.view.View, localX: Float, localY: Float): Pair<Float, Float> {
    val loc = IntArray(2)
    view.getLocationOnScreen(loc)
    return loc[0] + localX to loc[1] + localY
}

private fun overlayBoundsToScreen(overlayView: android.view.View, boundsInOverlay: Rect): Rect {
    if (boundsInOverlay.isEmpty) return boundsInOverlay
    val loc = IntArray(2)
    overlayView.getLocationOnScreen(loc)
    return Rect(
        loc[0] + boundsInOverlay.left,
        loc[1] + boundsInOverlay.top,
        loc[0] + boundsInOverlay.right,
        loc[1] + boundsInOverlay.bottom
    )
}

private data class PinInitialPlacement(
    val x: Int,
    val y: Int,
    val expandedWidthPx: Int = 0,
    val expandedHeightPx: Int = 0
)

private class PinUiState {
    val showControls: MutableState<Boolean> = mutableStateOf(false)
    val contentAlpha: MutableFloatState = mutableFloatStateOf(1f)
    var initialWidthPx: Int = 0
    var initialHeightPx: Int = 0
    var expandedWidthPx: Int = 0
    var expandedHeightPx: Int = 0
    val displayWidthPx = mutableIntStateOf(0)
    val displayHeightPx = mutableIntStateOf(0)
    val isEdgeDocked = mutableStateOf(false)
    var animator: ValueAnimator? = null
}

internal sealed class PinContent {
    data class Text(val body: String) : PinContent()
    data class Image(
        val bitmap: Bitmap,
        val screenRect: Rect? = null,
        val layoutMeta: ScreenshotLayoutMeta? = null
    ) : PinContent()
    data class Rich(val blocks: List<PinDisplayBlock>) : PinContent()
}

internal sealed class PinDisplayBlock {
    data class Text(val body: String) : PinDisplayBlock()
    data class Image(val bitmap: Bitmap) : PinDisplayBlock()
}

/** 钉图内容进入窗口前的统一限幅；原图不回收，调用方可能还在别处持有。 */
private fun PinContent.fitForDisplay(): PinContent = when (this) {
    is PinContent.Text -> this
    is PinContent.Image -> copy(
        bitmap = ImageDecodeGuard.fitForDisplay(bitmap) ?: bitmap
    )
    is PinContent.Rich -> copy(
        blocks = blocks.map { block ->
            when (block) {
                is PinDisplayBlock.Text -> block
                is PinDisplayBlock.Image -> block.copy(
                    bitmap = ImageDecodeGuard.fitForDisplay(block.bitmap) ?: block.bitmap
                )
            }
        }
    )
}

private class PinInstance(
    val id: String,
    val content: PinContent,
    val composeView: ComposeView,
    val owner: OverlayComposeOwner,
    val params: WindowManager.LayoutParams,
    val uiState: PinUiState,
    var offsetX: Int,
    var offsetY: Int
)

/**
 * 钉文字面板：按内容实际占用测量面板尺寸。
 *
 * 与 [ScreenPinContent] 的文本渲染（15sp 字号、12dp 内边距）保持同一口径：
 * 短文本收紧到刚好放得下，长文本在 [TEXT_PIN_MAX_WIDTH_FRACTION] ×
 * [TEXT_PIN_MAX_HEIGHT_FRACTION] 的范围内换行，超出部分仍由面板内部滚动承载。
 *
 * @return 面板内容区宽高（含内边距），单位为像素。
 */
internal fun measureTextPinSizePx(
    text: String,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    scaledDensity: Float,
): Pair<Int, Int> {
    val paddingPx = (TEXT_PIN_CONTENT_PADDING_DP * density).roundToInt().coerceAtLeast(0)
    val maxContentW = (screenWidthPx * TEXT_PIN_MAX_WIDTH_FRACTION).roundToInt()
        .minus(paddingPx * 2)
        .coerceAtLeast(1)
    val maxContentH = (screenHeightPx * TEXT_PIN_MAX_HEIGHT_FRACTION).roundToInt()
        .minus(paddingPx * 2)
        .coerceAtLeast(1)
    val minContentW = (TEXT_PIN_MIN_WIDTH_DP * density).roundToInt().coerceAtLeast(1)
    val minContentH = (TEXT_PIN_MIN_HEIGHT_DP * density).roundToInt().coerceAtLeast(1)
    val epsilonPx = (TEXT_PIN_SIZE_EPSILON_DP * density).roundToInt().coerceAtLeast(0)

    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_PIN_TEXT_SIZE_SP * scaledDensity
    }
    // 先在最大宽度下排版：宽约束越大行数越少，取其中最长行作为自然宽度，
    // 再以该宽度收窄面板不会引入额外换行。
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, maxContentW)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()
    var widestLine = 0f
    for (line in 0 until layout.lineCount) {
        widestLine = maxOf(widestLine, layout.getLineWidth(line))
    }

    val contentW = (widestLine.roundToInt() + epsilonPx).coerceIn(minContentW, maxContentW)
    val contentH = (layout.height + epsilonPx).coerceIn(minContentH, maxContentH)
    return (contentW + paddingPx * 2) to (contentH + paddingPx * 2)
}

/** 投放口贴的安全线：状态栏 / 导航栏 / 刘海所占的那几段不算可投放区。 */
private class DropTargetInsets(
    val topPx: Int = 0,
    val bottomPx: Int = 0,
    val rightPx: Int = 0,
)

private class DropOverlayState {
    var deleteBounds: Rect = Rect()
    var notifyBounds: Rect = Rect()
    var stashBounds: Rect = Rect()
    var insets: DropTargetInsets = DropTargetInsets()

    /** 可投放区（整屏去掉安全线），随 [insets] 一起算好，拖动中不再反复新建。 */
    var usable: Rect = Rect()

    /** 手指当前压住的投放口：立刻点亮（半透明 → 不透明）。 */
    val highlighted = mutableStateOf(ScreenPinDropZone.NONE)

    /** 压住且已驻留够 [DROP_TARGET_ARM_DELAY_MS] 的投放口：放大 + 实色，松手才认。 */
    val armed = mutableStateOf(ScreenPinDropZone.NONE)

    /**
     * 本次拖动是否处于显示中：三个口由它一起驱动（false→true 滑入、true→false 滑出）。
     * 投放层窗口现在常驻，所以退场动画播完之前不能把窗口拆掉。
     */
    val visible = mutableStateOf(false)
}

object ScreenPinManager {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var appContext: Context? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private val pins = linkedMapOf<String, PinInstance>()
    private var dropOverlay: DropOverlayState? = null
    private var dropComposeView: ComposeView? = null
    private var dropOwner: OverlayComposeOwner? = null

    /** 当前正被压住/已武装的投放口，用来实现「松手只认武装过的口」。 */
    private var armedDropZone = ScreenPinDropZone.NONE
    private var armedAtMs = 0L

    /**
     * 驻留到点后把 [DropOverlayState.armed] 置上。
     * 手指停在投放口上不动时不会再有 MOVE 事件，必须靠这个延时兜底。
     */
    private val armDropTargetRunnable = Runnable {
        val overlay = dropOverlay ?: return@Runnable
        val zone = overlay.highlighted.value
        if (zone != ScreenPinDropZone.NONE && zone == armedDropZone) {
            overlay.armed.value = zone
        }
    }

    /** 退场动画播完后把常驻的投放层窗口藏起来；期间若又开始了新拖动，就不藏。 */
    private val hideDropOverlayViewRunnable = Runnable {
        if (dropOverlay?.visible?.value != true) {
            dropComposeView?.visibility = View.GONE
        }
    }

    /** 通知标题的补 OCR（不阻塞发通知）。 */
    private val ocrScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun pinText(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        mainHandler.post { addPin(context, PinContent.Text(trimmed)) }
    }

    fun pinImage(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null
    ) {
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        val rectCopy = screenRect?.let { Rect(it) }
        val metaCopy = layoutMeta?.copy()
        mainHandler.post { addPin(context, PinContent.Image(copy, rectCopy, metaCopy)) }
    }

    private fun pinRich(context: Context, blocks: List<PinDisplayBlock>) {
        if (blocks.isEmpty()) return
        val copies = blocks.mapNotNull { block ->
            when (block) {
                is PinDisplayBlock.Text -> {
                    val trimmed = block.body.trim()
                    if (trimmed.isEmpty()) null else PinDisplayBlock.Text(trimmed)
                }
                is PinDisplayBlock.Image -> {
                    val copy = block.bitmap.copy(block.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        ?: return@mapNotNull null
                    PinDisplayBlock.Image(copy)
                }
            }
        }
        if (copies.isEmpty()) return
        when {
            copies.size == 1 && copies.first() is PinDisplayBlock.Image -> {
                val singleImg = (copies.first() as PinDisplayBlock.Image).bitmap
                mainHandler.post { addPin(context, PinContent.Image(singleImg)) }
            }
            copies.size == 1 && copies.first() is PinDisplayBlock.Text -> {
                val singleText = (copies.first() as PinDisplayBlock.Text).body
                mainHandler.post { addPin(context, PinContent.Text(singleText)) }
            }
            else -> {
                mainHandler.post { addPin(context, PinContent.Rich(copies)) }
            }
        }
    }

    fun pinClipboardEntry(context: Context, entry: ClipboardEntry) {
        val pinBlocks = entry.resolvedContentBlocks().mapNotNull { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> {
                    val trimmed = block.text.trim()
                    if (trimmed.isEmpty()) null else PinDisplayBlock.Text(trimmed)
                }
                ClipboardBlockKind.IMAGE -> {
                    val loaded = ClipboardImageStore.loadBitmap(context, block.fileName) ?: return@mapNotNull null
                    val copy = loaded.copy(loaded.config ?: Bitmap.Config.ARGB_8888, false) ?: return@mapNotNull null
                    if (copy !== loaded) loaded.recycle()
                    PinDisplayBlock.Image(copy)
                }
                // 语音块与未知块钉不到屏上（钉图只画文字与位图）→ 跳过这一块，其余内容照旧。
                ClipboardBlockKind.AUDIO,
                ClipboardBlockKind.UNKNOWN,
                -> null
            }
        }
        if (pinBlocks.isEmpty()) return
        when {
            pinBlocks.size == 1 && pinBlocks.first() is PinDisplayBlock.Image -> {
                val singleImg = (pinBlocks.first() as PinDisplayBlock.Image).bitmap
                mainHandler.post { addPin(context, PinContent.Image(singleImg)) }
            }
            pinBlocks.size == 1 && pinBlocks.first() is PinDisplayBlock.Text -> {
                val singleText = (pinBlocks.first() as PinDisplayBlock.Text).body
                mainHandler.post { addPin(context, PinContent.Text(singleText)) }
            }
            else -> {
                mainHandler.post { addPin(context, PinContent.Rich(pinBlocks)) }
            }
        }
    }

    fun pinFromStashText(context: Context, text: String) = pinText(context, text)
    fun pinFromStashImage(
        context: Context,
        bitmap: Bitmap,
        displayWidthPx: Int? = null,
        displayHeightPx: Int? = null
    ) {
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        mainHandler.post {
            val preferredW = displayWidthPx?.takeIf { it > 0 }
            val preferredH = displayHeightPx?.takeIf { it > 0 }
            if (preferredW != null && preferredH != null) {
                addPin(
                    context,
                    PinContent.Image(copy),
                    placement = PinInitialPlacement(
                        x = defaultPinX(context),
                        y = defaultPinY(context),
                        expandedWidthPx = preferredW,
                        expandedHeightPx = preferredH
                    )
                )
            } else {
                addPin(context, PinContent.Image(copy))
            }
        }
    }

    fun pinStashRich(context: Context, entry: StashEntry) {
        val repo = StashAccess.repository ?: return
        val pinBlocks = entry.resolvedContentBlocks().mapNotNull { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> {
                    val trimmed = block.text.trim()
                    if (trimmed.isEmpty()) null else PinDisplayBlock.Text(trimmed)
                }
                ClipboardBlockKind.IMAGE -> {
                    val loaded = repo.loadBitmapByFileName(block.fileName) ?: return@mapNotNull null
                    val copy = loaded.copy(loaded.config ?: Bitmap.Config.ARGB_8888, false)
                        ?: return@mapNotNull null
                    PinDisplayBlock.Image(copy)
                }
                // 见 `pinClipboardEntry` 的同类分支说明。
                ClipboardBlockKind.AUDIO,
                ClipboardBlockKind.UNKNOWN,
                -> null
            }
        }
        if (pinBlocks.isEmpty()) return
        pinRich(context, pinBlocks)
    }

    fun restoreFromNotification(
        context: Context,
        snapshot: PinNotificationSnapshot,
        imageBitmap: Bitmap?,
        richImageLoader: ((String) -> Bitmap?)? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        mainHandler.post {
            val placement = PinInitialPlacement(
                x = snapshot.x,
                y = snapshot.y,
                expandedWidthPx = snapshot.expandedWidthPx,
                expandedHeightPx = snapshot.expandedHeightPx
            )
            val success = when (snapshot.type) {
                PinNotificationSnapshot.TYPE_IMAGE -> {
                    val source = imageBitmap ?: run {
                        onResult(false)
                        return@post
                    }
                    val bitmap = source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
                    if (bitmap == null) {
                        onResult(false)
                        return@post
                    }
                    addPin(
                        context,
                        PinContent.Image(
                            bitmap = bitmap,
                            screenRect = snapshot.toScreenRect(),
                            layoutMeta = snapshot.toLayoutMeta()
                        ),
                        placement = placement
                    )
                }
                PinNotificationSnapshot.TYPE_TEXT -> {
                    val text = snapshot.text?.trim().orEmpty()
                    if (text.isEmpty()) {
                        onResult(false)
                        return@post
                    }
                    addPin(context, PinContent.Text(text), placement = placement)
                }
                PinNotificationSnapshot.TYPE_RICH -> {
                    val blocks = snapshot.contentBlocks.mapNotNull { block ->
                        when (block.kind) {
                            ClipboardBlockKind.TEXT -> {
                                val trimmed = block.text.trim()
                                if (trimmed.isEmpty()) null else PinDisplayBlock.Text(trimmed)
                            }
                            ClipboardBlockKind.IMAGE -> {
                                val loaded = richImageLoader?.invoke(block.fileName) ?: return@mapNotNull null
                                val copy = loaded.copy(loaded.config ?: Bitmap.Config.ARGB_8888, false)
                                    ?: return@mapNotNull null
                                PinDisplayBlock.Image(copy)
                            }
                            // 语音 / 未知块在钉屏上画不出来 → 跳过（其余内容照常恢复）。
                            ClipboardBlockKind.AUDIO,
                            ClipboardBlockKind.UNKNOWN,
                            -> null
                        }
                    }
                    if (blocks.isEmpty()) {
                        onResult(false)
                        return@post
                    }
                    addPin(context, PinContent.Rich(blocks), placement = placement)
                }
                else -> false
            }
            onResult(success)
        }
    }

    fun dismissAll() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissAll() }
            return
        }
        pins.keys.toList().forEach { removePin(it) }
        destroyDropOverlay()
    }

    private fun addPin(
        context: Context,
        content: PinContent,
        placement: PinInitialPlacement? = null
    ): Boolean {
        val hostContext = overlayHostContext() ?: context.applicationContext
        // 钉图内容可能来自图片编辑器/剪贴板原图，先压到显示预算内再挂窗口。
        val displayContent = content.fitForDisplay()
        ensureScreenOffReceiver(hostContext)
        val wm = windowManager ?: (
            hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        )?.also { windowManager = it } ?: return false

        val id = UUID.randomUUID().toString()
        val owner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(hostContext, owner)
        val metrics = hostContext.resources.displayMetrics
        val contentDisplaySize = when (displayContent) {
            is PinContent.Image -> {
                displayContent.bitmap.density = metrics.densityDpi
                if (placement != null &&
                    placement.expandedWidthPx > 0 &&
                    placement.expandedHeightPx > 0
                ) {
                    placement.expandedWidthPx to placement.expandedHeightPx
                } else {
                    resolvePinImageDisplaySizePx(
                        bitmap = displayContent.bitmap,
                        screenRect = displayContent.screenRect,
                        layoutMeta = displayContent.layoutMeta,
                        screenWidthPx = metrics.widthPixels,
                        screenHeightPx = metrics.heightPixels
                    )
                }
            }
            is PinContent.Text -> {
                // 恢复路径（通知栏/暂存）带显式尺寸时保留用户尺寸，否则按文字内容自适应。
                val explicitW = placement?.expandedWidthPx?.takeIf { it > 0 }
                val explicitH = placement?.expandedHeightPx?.takeIf { it > 0 }
                if (explicitW != null && explicitH != null) {
                    explicitW to explicitH
                } else {
                    measureTextPinSizePx(
                        text = displayContent.body,
                        screenWidthPx = metrics.widthPixels,
                        screenHeightPx = metrics.heightPixels,
                        density = metrics.density,
                        scaledDensity = metrics.density * hostContext.resources.configuration.fontScale,
                    )
                }
            }
            is PinContent.Rich -> {
                val defaultW = (metrics.widthPixels * TEXT_PIN_MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(1)
                val defaultH = (metrics.heightPixels * RICH_PIN_MAX_HEIGHT_FRACTION).roundToInt().coerceAtLeast(1)
                val w = placement?.expandedWidthPx?.takeIf { it > 0 } ?: defaultW
                val h = placement?.expandedHeightPx?.takeIf { it > 0 } ?: defaultH
                w to h
            }
        }
        val uiState = PinUiState()
        uiState.initialWidthPx = contentDisplaySize.first
        uiState.initialHeightPx = contentDisplaySize.second
        uiState.expandedWidthPx = contentDisplaySize.first
        uiState.expandedHeightPx = contentDisplaySize.second
        uiState.displayWidthPx.intValue = contentDisplaySize.first
        uiState.displayHeightPx.intValue = contentDisplaySize.second

        val barMinW = controlBarWidthPx(metrics)
        val (panelW, panelH) = pinPanelSizePx(
            metrics = metrics,
            contentW = contentDisplaySize.first,
            contentH = contentDisplaySize.second,
            edgeDocked = false
        )
        val params = WindowManager.LayoutParams(
            panelW,
            panelH,
            OverlayWindowTypes.overlayWindowType(hostContext),
            pinOverlayFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (placement != null) {
                x = placement.x
                y = placement.y
            } else {
                val imageContent = displayContent as? PinContent.Image
                val placementRect = resolvePinPlacementRect(
                    imageContent?.screenRect,
                    imageContent?.layoutMeta
                )
                if (placementRect != null && !placementRect.isEmpty) {
                    val contentW = contentDisplaySize.first
                    val pW = max(contentW, barMinW)
                    x = placementRect.left - (pW - contentW) / 2
                    y = placementRect.top
                } else {
                    x = (metrics.widthPixels * 0.08f).roundToInt()
                    y = (metrics.heightPixels * 0.22f).roundToInt() + pins.size * 32
                }
            }
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val instance = PinInstance(
            id = id,
            content = displayContent,
            composeView = composeView,
            owner = owner,
            params = params,
            uiState = uiState,
            offsetX = params.x,
            offsetY = params.y
        )
        pins[id] = instance
        bindPinContent(instance)
        val added = runCatching { wm.addView(composeView, params) }.isSuccess
        if (!added) {
            pins.remove(id)
            owner.destroy()
            Toast.makeText(hostContext, R.string.stash_pin_add_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        if (displayContent is PinContent.Text || displayContent is PinContent.Rich) {
            composeView.post {
                val measuredH = composeView.height.coerceAtLeast(1)
                val slotH = controlBarSlotHeightPx(metrics)
                val actualContentH = (measuredH - slotH).coerceAtLeast(60)
                if (actualContentH < uiState.initialHeightPx) {
                    uiState.initialHeightPx = actualContentH
                    uiState.expandedHeightPx = actualContentH
                    uiState.displayHeightPx.intValue = actualContentH
                    applyPinWindowLayout(instance)
                    runCatching { wm.updateViewLayout(instance.composeView, instance.params) }
                }
            }
        }
        appContext = hostContext
        return true
    }

    private fun defaultPinX(context: Context): Int {
        val metrics = context.resources.displayMetrics
        return (metrics.widthPixels * 0.08f).roundToInt()
    }

    private fun defaultPinY(context: Context): Int {
        val metrics = context.resources.displayMetrics
        return (metrics.heightPixels * 0.22f).roundToInt() + pins.size * 32
    }

    private fun bindPinContent(instance: PinInstance) {
        instance.composeView.setContent {
            OverlayAwareModuleTheme {
                // 长按拖拽：把**钉图本身**当拖影（见 HistoryCardSnapshot）。抓图是 suspend 的，先起协程。
                val dragSnapshot = rememberHistoryCardSnapshot()
                val dragScope = rememberCoroutineScope()
                ScreenPinContent(
                    instance = instance,
                    onTap = { onPinTap(instance.id) },
                    onDoubleTap = { togglePinDoubleTapZoom(instance.id) },
                    onZoom = { zoomFactor -> zoomPin(instance.id, zoomFactor) },
                    onLongPressDrag = {
                        dragScope.launch { startPinDrag(instance, dragSnapshot.capture()) }
                    },
                    onDragStart = {
                        if (instance.uiState.isEdgeDocked.value) {
                            undockForDrag(instance)
                        }
                        showDropOverlay()
                    },
                    onDrag = { dx, dy, localX, localY ->
                        dragPin(instance.id, dx, dy)
                        val (screenX, screenY) = viewToScreen(instance.composeView, localX, localY)
                        updateDropHighlight(screenX, screenY)
                    },
                    onDragEnd = { localX, localY ->
                        val (screenX, screenY) = viewToScreen(instance.composeView, localX, localY)
                        finishPinDrag(instance.id, screenX, screenY)
                        hideDropOverlay()
                    },
                    onClose = { removePin(instance.id) },
                    onAlphaChange = { instance.uiState.contentAlpha.floatValue = it },
                    snapshot = dragSnapshot,
                )
            }
        }
    }

    private fun zoomPin(pinId: String, zoomFactor: Float) {
        val instance = pins[pinId] ?: return
        if (instance.uiState.isEdgeDocked.value) return
        val context = appContext ?: return
        val metrics = context.resources.displayMetrics

        val currentW = instance.uiState.displayWidthPx.intValue.coerceAtLeast(1)
        val currentH = instance.uiState.displayHeightPx.intValue.coerceAtLeast(1)
        val aspectRatio = currentW.toFloat() / currentH.toFloat()

        val minW = (80 * metrics.density).roundToInt().coerceAtLeast(1)

        // 放大不设上限：图片要能放大到看清原始像素的小字。
        // 代价是窗口可能大于屏幕，拖窗口即可看局部（窗口本身允许出屏）。
        val newW = (currentW * zoomFactor).roundToInt().coerceAtLeast(minW)
        val newH = (newW / aspectRatio).roundToInt().coerceAtLeast(1)

        if (newW == currentW && newH == currentH) return

        instance.uiState.displayWidthPx.intValue = newW
        instance.uiState.displayHeightPx.intValue = newH
        instance.uiState.expandedWidthPx = newW
        instance.uiState.expandedHeightPx = newH

        applyPinWindowLayout(instance)
        runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
    }

    private fun togglePinDoubleTapZoom(pinId: String) {
        val instance = pins[pinId] ?: return
        if (instance.uiState.isEdgeDocked.value) return

        val baseW = if (instance.uiState.initialWidthPx > 0) instance.uiState.initialWidthPx else instance.uiState.expandedWidthPx
        val baseH = if (instance.uiState.initialHeightPx > 0) instance.uiState.initialHeightPx else instance.uiState.expandedHeightPx
        if (baseW <= 0 || baseH <= 0) return

        val currentW = instance.uiState.displayWidthPx.intValue
        val isZoomedIn = currentW > (baseW * 1.25f)

        val targetW: Int
        val targetH: Int
        if (isZoomedIn) {
            targetW = baseW
            targetH = baseH
        } else {
            val aspectRatio = baseW.toFloat() / baseH.toFloat()
            targetW = (baseW * 1.8f).roundToInt()
            targetH = (targetW / aspectRatio).roundToInt().coerceAtLeast(1)
        }

        animatePinLayout(
            instance = instance,
            targetX = instance.params.x,
            targetY = instance.params.y,
            targetDisplayW = targetW,
            targetDisplayH = targetH
        )
    }

    private fun undockForDrag(instance: PinInstance) {
        instance.uiState.animator?.cancel()
        instance.uiState.isEdgeDocked.value = false
        instance.uiState.showControls.value = false
        if (instance.uiState.expandedWidthPx > 0) {
            instance.uiState.displayWidthPx.intValue = instance.uiState.expandedWidthPx
        }
        if (instance.uiState.expandedHeightPx > 0) {
            instance.uiState.displayHeightPx.intValue = instance.uiState.expandedHeightPx
        }
        applyPinWindowLayout(instance)
        runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
    }

    private fun onPinTap(pinId: String) {
        val instance = pins[pinId] ?: return
        if (instance.uiState.isEdgeDocked.value) {
            restoreFromEdge(instance)
            return
        }
        instance.uiState.showControls.value = !instance.uiState.showControls.value
    }

    /**
     * 长按钉图 → 跨应用拖拽投放。
     *
     * 与「滑动拖动 = 挪窗口」和「拖动时的投放层 = 暂存/通知/删除」区分开：
     * 这里只负责把钉图内容交给系统拖拽框架，拖到别的应用松手放下。
     */
    private fun startPinDrag(instance: PinInstance, snapshot: Bitmap? = null) {
        val context = appContext ?: return
        if (instance.uiState.isEdgeDocked.value) return
        // 先给触感反馈，再做内容落盘，避免长按判定被编码耗时拖后。
        instance.composeView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        instance.uiState.showControls.value = false
        val items = PinDragHelper.itemsOf(instance.content, instance.id)
        val clip = PinDragHelper.buildClip(context, items) ?: return
        PinDragHelper.startDrag(
            view = instance.composeView,
            clipData = clip,
            // [snapshot] = 长按那一刻抓的"钉图本身"，拖影就是这张钉图的真实样子（圆角/投影由拖影侧画）。
            preview = PinDragHelper.previewOf(instance.content).copy(snapshot = snapshot),
        )
    }

    private fun removePin(pinId: String) {
        val instance = pins.remove(pinId) ?: return
        instance.uiState.animator?.cancel()
        val wm = windowManager
        if (wm != null) {
            runCatching { wm.removeView(instance.composeView) }
        }
        instance.owner.destroy()
        recyclePinBitmaps(instance.content)
        appContext?.let { PinDragHelper.releaseCache(it, pinId) }
        if (pins.isEmpty()) {
            unregisterScreenOffReceiver()
            // 没有钉图了：常驻的投放层窗口一起拆掉，别留一个整屏 overlay 挂在那。
            destroyDropOverlay()
        }
    }

    private fun dragPin(pinId: String, dx: Float, dy: Float) {
        val instance = pins[pinId] ?: return
        instance.offsetX += dx.roundToInt()
        instance.offsetY += dy.roundToInt()
        instance.params.x = instance.offsetX
        instance.params.y = instance.offsetY
        runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
    }

    private fun finishPinDrag(pinId: String, fingerX: Float, fingerY: Float) {
        val instance = pins[pinId] ?: return
        // 只有"驻留够时长"的投放口才认投放：手指顺路扫过（其实想贴边吸附）不会被误投。
        val underFinger = resolveDropZone(fingerX, fingerY)
        val accepted = dropOverlay?.armed?.value ?: ScreenPinDropZone.NONE
        val zone = if (underFinger == accepted) accepted else ScreenPinDropZone.NONE
        when (zone) {
            ScreenPinDropZone.STASH -> stashPin(pinId)
            ScreenPinDropZone.DELETE -> removePin(pinId)
            ScreenPinDropZone.NOTIFICATION -> postPinToNotification(pinId)
            ScreenPinDropZone.NONE -> checkEdgeSnap(instance)
        }
    }

    private fun resolveDropZone(fingerX: Float, fingerY: Float): ScreenPinDropZone {
        val overlay = dropOverlay ?: return ScreenPinDropZone.NONE
        val metrics = appContext?.resources?.displayMetrics ?: return ScreenPinDropZone.NONE
        ensureDropBoundsReady(overlay, metrics)
        val usable = overlay.usable
        val overlayView = dropComposeView
        val x = fingerX.roundToInt()
        val y = fingerY.roundToInt()
        val delete = screenDropBounds(overlayView, overlay.deleteBounds)
        val notify = screenDropBounds(overlayView, overlay.notifyBounds)
        val stash = screenDropBounds(overlayView, overlay.stashBounds)
        if (delete != null && hitDropTarget(delete, x, y, metrics, usable)) return ScreenPinDropZone.DELETE
        if (notify != null && hitDropTarget(notify, x, y, metrics, usable)) return ScreenPinDropZone.NOTIFICATION
        if (stash != null && hitDropTarget(stash, x, y, metrics, usable)) return ScreenPinDropZone.STASH
        return ScreenPinDropZone.NONE
    }

    /**
     * Compose 首次上报 bounds 之前的兜底：只算一次。
     * 拖动中每个 MOVE 事件都会走 [resolveDropZone]，不能在那里反复新建 Rect / 状态对象。
     */
    private fun ensureDropBoundsReady(overlay: DropOverlayState, metrics: DisplayMetrics) {
        if (!overlay.notifyBounds.isEmpty) return
        val fallback = computeFallbackDropBounds(metrics, overlay.insets)
        overlay.notifyBounds = fallback.notifyBounds
        overlay.deleteBounds = fallback.deleteBounds
        overlay.stashBounds = fallback.stashBounds
        overlay.usable = fallback.usable
    }

    /** 可投放区：整屏去掉状态栏 / 导航栏 / 刘海那几条安全线。 */
    private fun usableDropArea(metrics: DisplayMetrics, insets: DropTargetInsets): Rect =
        Rect(
            0,
            insets.topPx.coerceIn(0, metrics.heightPixels),
            (metrics.widthPixels - insets.rightPx).coerceIn(0, metrics.widthPixels),
            (metrics.heightPixels - insets.bottomPx).coerceIn(0, metrics.heightPixels)
        )

    private fun screenDropBounds(overlayView: ComposeView?, boundsInOverlay: Rect): Rect? {
        if (boundsInOverlay.isEmpty) return null
        // overlay 窗口就是整屏 (0,0)，Compose 上报的和兜底的矩形都在同一套屏幕坐标里。
        return overlayView?.let { overlayBoundsToScreen(it, boundsInOverlay) }
    }

    /**
     * 命中判定：矩形先和 [usable] 求交（贴进状态栏/导航栏里的部分不算），再只往**屏内**扩
     * [DROP_TARGET_HIT_SLOP_DP]。外扩被夹在可用区里，所以贴着安全线的那条边不会多出一圈误触带。
     */
    private fun hitDropTarget(
        bounds: Rect,
        x: Int,
        y: Int,
        metrics: DisplayMetrics?,
        usable: Rect
    ): Boolean {
        if (bounds.isEmpty) return false
        val slop = metrics?.let { (DROP_TARGET_HIT_SLOP_DP * it.density).roundToInt() } ?: 0
        val left = (bounds.left - slop).coerceAtLeast(usable.left)
        val top = (bounds.top - slop).coerceAtLeast(usable.top)
        val right = (bounds.right + slop).coerceAtMost(usable.right)
        val bottom = (bounds.bottom + slop).coerceAtMost(usable.bottom)
        if (right <= left || bottom <= top) return false
        return x >= left && x < right && y >= top && y < bottom
    }

    private fun stashPin(pinId: String) {
        val instance = pins[pinId] ?: return
        val context = appContext ?: return
        when (val content = instance.content) {
            is PinContent.Text -> {
                StashCoordinator.addText(content.body) { success ->
                    if (success) {
                        Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                        StashCoordinator.openStashPanel(context)
                        removePin(pinId)
                    }
                }
            }
            is PinContent.Image -> {
                val displayW = instance.uiState.expandedWidthPx.takeIf { it > 0 }
                    ?: instance.uiState.displayWidthPx.intValue
                val displayH = instance.uiState.expandedHeightPx.takeIf { it > 0 }
                    ?: instance.uiState.displayHeightPx.intValue
                StashCoordinator.addImage(
                    bitmap = content.bitmap,
                    pinDisplayWidthPx = displayW,
                    pinDisplayHeightPx = displayH
                ) { success ->
                    if (success) {
                        Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                        StashCoordinator.openStashPanel(context)
                        removePin(pinId)
                    }
                }
            }
            is PinContent.Rich -> {
                val combinedText = richCombinedText(content.blocks)
                val firstImage = content.blocks.filterIsInstance<PinDisplayBlock.Image>().firstOrNull()?.bitmap
                when {
                    combinedText.isNotBlank() -> {
                        StashCoordinator.addText(combinedText) { success ->
                            if (success) {
                                Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(context)
                                removePin(pinId)
                            }
                        }
                    }
                    firstImage != null -> {
                        StashCoordinator.addImage(bitmap = firstImage) { success ->
                            if (success) {
                                Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(context)
                                removePin(pinId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun postPinToNotification(pinId: String) {
        val instance = pins[pinId] ?: return
        val context = appContext ?: return
        val content = instance.content
        val snapshot = buildNotificationSnapshot(instance)
        val (title, displayBitmap) = when (content) {
            is PinContent.Image -> {
                context.getString(R.string.stash_pin_notification_title) to content.bitmap
            }
            is PinContent.Text -> {
                content.body.take(36) to textBitmap(context, content.body)
            }
            is PinContent.Rich -> {
                val combined = richCombinedText(content.blocks)
                val preview = content.blocks.filterIsInstance<PinDisplayBlock.Image>().firstOrNull()?.bitmap
                (combined.take(36).ifBlank { context.getString(R.string.stash_pin_notification_title) }) to
                    (preview ?: textBitmap(context, combined))
            }
        }
        StashPinNotificationHelper.postPinSnapshot(context, snapshot, displayBitmap, title)
        Toast.makeText(context, R.string.stash_pin_notification_posted, Toast.LENGTH_SHORT).show()
        // 图片类钉图身上没有文本可带 → 先按通用标题发出去，再异步 OCR 出内容**原地刷新**标题。
        // 拿的是降采样副本：钉图本身紧接着就会被 removePin 回收。
        val ocrSource = content.ocrTitleSourceBitmap()
        removePin(pinId)
        if (ocrSource != null) {
            ocrScope.launch {
                val text = recognizeNotificationTitle(context, ocrSource)
                ocrSource.recycle()
                if (!text.isNullOrBlank()) {
                    StashPinNotificationHelper.updatePinNotificationTitle(context, text)
                }
            }
        }
    }

    /**
     * 需要补 OCR 的钉图内容 → 一份降采样副本；已经有文本的（Text / 带文本块的 Rich）返回 null。
     * 副本由调用方负责 recycle。
     */
    private fun PinContent.ocrTitleSourceBitmap(): Bitmap? {
        if (this is PinContent.Text) return null
        if (this is PinContent.Rich && richCombinedText(blocks).isNotBlank()) return null
        val source = when (this) {
            is PinContent.Image -> bitmap
            is PinContent.Rich -> blocks.filterIsInstance<PinDisplayBlock.Image>().firstOrNull()?.bitmap
            is PinContent.Text -> null
        } ?: return null
        val maxEdge = max(source.width, source.height).coerceAtLeast(1)
        if (maxEdge <= NOTIFICATION_OCR_MAX_EDGE_PX) {
            return runCatching { source.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        }
        val scale = NOTIFICATION_OCR_MAX_EDGE_PX.toFloat() / maxEdge
        val targetW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (source.height * scale).roundToInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(source, targetW, targetH, true) }.getOrNull()
    }

    /**
     * 拿钉图内容 OCR 出一行能当通知标题的文字。
     * 复用取词那条 PP-OCR 链路；模型没选 / 不是 PP-OCR / 识别为空都返回 null（保持通用标题）。
     */
    private suspend fun recognizeNotificationTitle(context: Context, bitmap: Bitmap): String? {
        val modelId = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?.floatBallOcrModelId
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (OcrDependencyAccess.catalogProvider(context)?.findModel(modelId)?.engine != OcrEngines.PPOCR) {
            return null
        }
        val inference = OcrDependencyAccess.inferenceService(context) ?: return null
        val lines = runCatching { inference.recognizePpOcrLines(modelId, bitmap) }.getOrNull() ?: return null
        return lines.asSequence()
            .map { it.text.trim() }
            .firstOrNull { line -> line.any { it.isLetterOrDigit() } }
            ?.take(NOTIFICATION_TITLE_MAX_CHARS)
    }

    private fun buildNotificationSnapshot(instance: PinInstance): PinNotificationSnapshot {
        val rect = (instance.content as? PinContent.Image)?.screenRect
        val layoutMeta = (instance.content as? PinContent.Image)?.layoutMeta
        return when (val content = instance.content) {
            is PinContent.Image -> PinNotificationSnapshot(
                type = PinNotificationSnapshot.TYPE_IMAGE,
                x = instance.params.x,
                y = instance.params.y,
                expandedWidthPx = instance.uiState.expandedWidthPx,
                expandedHeightPx = instance.uiState.expandedHeightPx,
                screenRectLeft = rect?.left,
                screenRectTop = rect?.top,
                screenRectRight = rect?.right,
                screenRectBottom = rect?.bottom,
                layoutScreenWidth = layoutMeta?.screenWidth,
                layoutScreenHeight = layoutMeta?.screenHeight,
                layoutCaptureWidth = layoutMeta?.captureWidth,
                layoutCaptureHeight = layoutMeta?.captureHeight
            )
            is PinContent.Text -> PinNotificationSnapshot(
                type = PinNotificationSnapshot.TYPE_TEXT,
                text = content.body,
                x = instance.params.x,
                y = instance.params.y,
                expandedWidthPx = instance.uiState.expandedWidthPx,
                expandedHeightPx = instance.uiState.expandedHeightPx
            )
            is PinContent.Rich -> PinNotificationSnapshot(
                type = PinNotificationSnapshot.TYPE_TEXT,
                text = richCombinedText(content.blocks),
                x = instance.params.x,
                y = instance.params.y,
                expandedWidthPx = instance.uiState.expandedWidthPx,
                expandedHeightPx = instance.uiState.expandedHeightPx
            )
        }
    }

    private fun richCombinedText(blocks: List<PinDisplayBlock>): String =
        blocks.filterIsInstance<PinDisplayBlock.Text>()
            .joinToString("\n\n") { it.body.trim() }
            .trim()

    private fun recyclePinBitmaps(content: PinContent) {
        when (content) {
            is PinContent.Image -> content.bitmap.recycle()
            is PinContent.Rich -> {
                content.blocks.filterIsInstance<PinDisplayBlock.Image>().forEach { it.bitmap.recycle() }
            }
            is PinContent.Text -> Unit
        }
    }

    private fun textBitmap(context: Context, text: String): Bitmap {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 42f
            color = android.graphics.Color.BLACK
        }
        val bounds = android.graphics.Rect()
        val sample = text.lineSequence().firstOrNull().orEmpty().take(24)
        paint.getTextBounds(sample, 0, sample.length, bounds)
        val bmp = createBitmap(
            bounds.width().coerceAtLeast(1) + 32,
            bounds.height().coerceAtLeast(1) + 32
        )
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawText(sample, 16f, 16f + bounds.height(), paint)
        return bmp
    }

    private fun checkEdgeSnap(instance: PinInstance) {
        val context = appContext ?: return
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val width = currentPanelWidth(instance)
        val height = currentPanelHeight(instance)
        val x = instance.params.x
        val y = instance.params.y

        if (instance.uiState.isEdgeDocked.value) {
            val substantiallyOnScreen = x > -width / 2 &&
                x + width < screenW + width / 2 &&
                y > -height / 2 &&
                y + height < screenH + height / 2
            if (substantiallyOnScreen) {
                restoreFromEdge(instance)
            }
            return
        }

        val threshold = -width / 3
        val rightGap = screenW - (x + width)
        val bottomGap = screenH - (y + height)
        when {
            x < threshold -> snapToEdge(instance, horizontal = true, toStart = true, metrics)
            rightGap < threshold -> snapToEdge(instance, horizontal = true, toStart = false, metrics)
            y < threshold -> snapToEdge(instance, horizontal = false, toStart = true, metrics)
            bottomGap < threshold -> snapToEdge(instance, horizontal = false, toStart = false, metrics)
        }
    }

    private fun snapToEdge(
        instance: PinInstance,
        horizontal: Boolean,
        toStart: Boolean,
        metrics: DisplayMetrics
    ) {
        if (instance.uiState.isEdgeDocked.value) return
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val dockSize = (EDGE_DOCK_SIZE_DP * metrics.density).roundToInt().coerceAtLeast(1)
        val halfDock = dockSize / 2
        val expandedW = instance.uiState.expandedWidthPx.coerceAtLeast(currentPinWidth(instance))
        val expandedH = instance.uiState.expandedHeightPx.coerceAtLeast(currentPinHeight(instance))
        if (instance.uiState.expandedWidthPx <= 0 || instance.uiState.expandedHeightPx <= 0) {
            instance.uiState.expandedWidthPx = expandedW
            instance.uiState.expandedHeightPx = expandedH
        }
        val targetX: Int
        val targetY: Int
        val targetDisplayW: Int
        val targetDisplayH: Int
        val dockedPanelW: Int
        val dockedPanelH: Int
        if (horizontal) {
            targetDisplayW = dockSize
            targetDisplayH = expandedH
            dockedPanelW = dockSize
            dockedPanelH = expandedH
            targetY = instance.params.y.coerceIn(0, (screenH - dockedPanelH).coerceAtLeast(0))
            targetX = if (toStart) -(dockedPanelW - halfDock) else screenW - halfDock
        } else {
            targetDisplayW = expandedW
            targetDisplayH = dockSize
            dockedPanelW = expandedW
            dockedPanelH = dockSize
            targetX = instance.params.x.coerceIn(0, (screenW - dockedPanelW).coerceAtLeast(0))
            targetY = if (toStart) -(dockedPanelH - halfDock) else screenH - halfDock
        }
        instance.uiState.isEdgeDocked.value = true
        instance.uiState.showControls.value = false
        animatePinLayout(instance, targetX, targetY, targetDisplayW, targetDisplayH)
    }

    private fun restoreFromEdge(instance: PinInstance) {
        if (!instance.uiState.isEdgeDocked.value) return
        instance.uiState.isEdgeDocked.value = false
        applyPinWindowLayout(instance)
        val metrics = appContext?.resources?.displayMetrics ?: return
        val panelW = currentPanelWidth(instance)
        val panelH = currentPanelHeight(instance)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val x = instance.params.x
        val y = instance.params.y
        val targetX = when {
            x < 0 -> 0
            x + panelW > screenW -> (screenW - panelW).coerceAtLeast(0)
            else -> x
        }
        val targetY = when {
            y < 0 -> 0
            y + panelH > screenH -> (screenH - panelH).coerceAtLeast(0)
            else -> y
        }
        animatePinLayout(
            instance,
            targetX,
            targetY,
            instance.uiState.expandedWidthPx.coerceAtLeast(1),
            instance.uiState.expandedHeightPx.coerceAtLeast(1)
        )
    }

    private fun animatePinLayout(
        instance: PinInstance,
        targetX: Int,
        targetY: Int,
        targetDisplayW: Int,
        targetDisplayH: Int
    ) {
        instance.uiState.animator?.cancel()
        val startX = instance.params.x
        val startY = instance.params.y
        val startW = instance.uiState.displayWidthPx.intValue.coerceAtLeast(1)
        val startH = instance.uiState.displayHeightPx.intValue.coerceAtLeast(1)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = EDGE_SNAP_ANIM_MS
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                instance.params.x = (startX + (targetX - startX) * t).roundToInt()
                instance.params.y = (startY + (targetY - startY) * t).roundToInt()
                instance.offsetX = instance.params.x
                instance.offsetY = instance.params.y
                instance.uiState.displayWidthPx.intValue =
                    (startW + (targetDisplayW - startW) * t).roundToInt().coerceAtLeast(1)
                instance.uiState.displayHeightPx.intValue =
                    (startH + (targetDisplayH - startH) * t).roundToInt().coerceAtLeast(1)
                applyPinWindowLayout(instance)
                runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    applyPinWindowLayout(instance)
                    runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
                }
            })
        }
        instance.uiState.animator = animator
        animator.start()
    }

    private fun currentPanelWidth(instance: PinInstance): Int {
        val metrics = appContext?.resources?.displayMetrics ?: return instance.params.width
        val contentW = instance.uiState.displayWidthPx.intValue.coerceAtLeast(1)
        return if (instance.uiState.isEdgeDocked.value) {
            contentW
        } else {
            max(contentW, controlBarWidthPx(metrics))
        }
    }

    private fun currentPanelHeight(instance: PinInstance): Int {
        val metrics = appContext?.resources?.displayMetrics ?: return instance.params.height
        val contentH = instance.uiState.displayHeightPx.intValue.coerceAtLeast(1)
        return if (instance.uiState.isEdgeDocked.value) {
            contentH
        } else {
            contentH + controlBarSlotHeightPx(metrics)
        }
    }

    private fun currentPinWidth(instance: PinInstance): Int {
        return instance.uiState.expandedWidthPx.coerceAtLeast(1)
    }

    private fun currentPinHeight(instance: PinInstance): Int {
        return instance.uiState.expandedHeightPx.coerceAtLeast(1)
    }

    private fun controlBarWidthPx(metrics: DisplayMetrics): Int {
        return (PIN_CONTROL_BAR_MIN_WIDTH_DP * metrics.density).roundToInt()
    }

    private fun controlBarSlotHeightPx(metrics: DisplayMetrics): Int {
        return (PIN_CONTROL_BAR_SLOT_HEIGHT_DP * metrics.density).roundToInt()
    }

    private fun pinPanelSizePx(
        metrics: DisplayMetrics,
        contentW: Int,
        contentH: Int,
        edgeDocked: Boolean
    ): Pair<Int, Int> {
        val safeW = contentW.coerceAtLeast(1)
        val safeH = contentH.coerceAtLeast(1)
        return if (edgeDocked) {
            safeW to safeH
        } else {
            max(safeW, controlBarWidthPx(metrics)) to (safeH + controlBarSlotHeightPx(metrics))
        }
    }

    private fun applyPinWindowLayout(instance: PinInstance) {
        val context = appContext ?: return
        val metrics = context.resources.displayMetrics
        val contentW = instance.uiState.displayWidthPx.intValue.coerceAtLeast(1)
        val contentH = instance.uiState.displayHeightPx.intValue.coerceAtLeast(1)
        val (panelW, panelH) = pinPanelSizePx(
            metrics = metrics,
            contentW = contentW,
            contentH = contentH,
            edgeDocked = instance.uiState.isEdgeDocked.value
        )
        instance.params.width = panelW
        instance.params.height = panelH
    }

    /**
     * 投放口贴的三条安全线：状态栏/刘海的上沿、导航栏/刘海的下沿、右侧刘海。
     * 取 `max(可见栏位, 刘海)`：沉浸全屏时贴回物理边缘，有刘海时不会被挖孔吃掉。
     */
    private fun resolveDropTargetInsets(context: Context): DropTargetInsets {
        val wm = context.getSystemService(WindowManager::class.java) ?: return DropTargetInsets()
        val windowInsets = wm.currentWindowMetrics.windowInsets
        val bars = windowInsets.getInsets(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        )
        val cutout = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
        return DropTargetInsets(
            topPx = max(bars.top, cutout.top),
            bottomPx = max(bars.bottom, cutout.bottom),
            rightPx = max(bars.right, cutout.right)
        )
    }

    /**
     * Compose 还没上报 bounds 时用的兜底矩形：尺寸必须和 [ScreenPinDropTargets] 里的一致。
     * 三个口分别贴顶部 / 底部 / 右侧安全线。
     */
    private fun computeFallbackDropBounds(
        metrics: DisplayMetrics,
        insets: DropTargetInsets
    ): DropOverlayState {
        val d = metrics.density
        val notifyW = (DROP_TARGET_NOTIFY_WIDTH_DP * d).roundToInt()
        val notifyH = (DROP_TARGET_NOTIFY_HEIGHT_DP * d).roundToInt()
        val stashW = (DROP_TARGET_STASH_WIDTH_DP * d).roundToInt()
        val stashH = (DROP_TARGET_STASH_HEIGHT_DP * d).roundToInt()
        val deleteW = (DROP_TARGET_DELETE_WIDTH_DP * d).roundToInt()
        val deleteH = (DROP_TARGET_DELETE_HEIGHT_DP * d).roundToInt()
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val top = insets.topPx.coerceIn(0, screenH)
        val bottom = (screenH - insets.bottomPx).coerceIn(0, screenH)
        val right = (screenW - insets.rightPx).coerceIn(0, screenW)
        return DropOverlayState().apply {
            this.insets = insets
            usable = usableDropArea(metrics, insets)
            // 半隐：三个矩形都故意跨过各自的安全线一半，命中时和 [usable] 求交后只剩屏内那半。
            notifyBounds = Rect(
                screenW / 2 - notifyW / 2,
                top - notifyH / 2,
                screenW / 2 + notifyW / 2,
                top + notifyH / 2
            )
            deleteBounds = Rect(
                screenW / 2 - deleteW / 2,
                bottom - deleteH / 2,
                screenW / 2 + deleteW / 2,
                bottom + deleteH / 2
            )
            stashBounds = Rect(
                right - stashW / 2,
                screenH / 2 - stashH / 2,
                right + stashW / 2,
                screenH / 2 + stashH / 2
            )
        }
    }

    /**
     * 让投放层显示出来。窗口本身**常驻**（有钉图期间一直挂着，空闲只 GONE）：
     * 只有第一次拖动会建窗，之后每次拖动只把 [DropOverlayState.visible] 置上，
     * 由它驱动三个口滑入；退场同理见 [hideDropOverlay]。
     */
    private fun showDropOverlay() {
        val context = appContext ?: return
        val wm = windowManager ?: return
        val state = dropOverlay ?: ensureDropOverlayAttached(context, wm) ?: return
        resetDropTargetArm()
        state.highlighted.value = ScreenPinDropZone.NONE
        dropComposeView?.visibility = View.VISIBLE
        state.visible.value = true
    }

    /** 首次拖动时把投放层窗口挂上，之后一直复用。挂不上返回 null（这次没有投放口，但不影响拖动本身）。 */
    private fun ensureDropOverlayAttached(context: Context, wm: WindowManager): DropOverlayState? {
        dropComposeView?.let { return dropOverlay }
        val metrics = context.resources.displayMetrics
        val insets = resolveDropTargetInsets(context)
        val state = computeFallbackDropBounds(metrics, insets)
        val owner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(context, owner)
        composeView.setContent {
            OverlayAwareModuleTheme {
                val highlighted by state.highlighted
                val armed by state.armed
                val visible by state.visible
                ScreenPinDropTargets(
                    highlighted = highlighted,
                    armed = armed,
                    visible = visible,
                    topInsetPx = insets.topPx,
                    bottomInsetPx = insets.bottomPx,
                    rightInsetPx = insets.rightPx,
                    onDeleteBounds = { state.deleteBounds = it },
                    onNotifyBounds = { state.notifyBounds = it },
                    onStashBounds = { state.stashBounds = it }
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            pinOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val added = runCatching { wm.addView(composeView, params) }.isSuccess
        if (!added) {
            owner.destroy()
            return null
        }
        composeView.visibility = View.GONE
        dropOverlay = state
        dropComposeView = composeView
        dropOwner = owner
        return state
    }

    /**
     * 拖动过程中更新投放口的两级状态：
     * 1. [DropOverlayState.highlighted]：手指当前压住的口 → 立刻点亮（有反馈）；
     * 2. [DropOverlayState.armed]：同一口连续压住 [DROP_TARGET_ARM_DELAY_MS] 之后才"武装"（放大 + 实色）。
     *
     * 松手时只认已武装的口（见 [finishPinDrag]）：想贴边吸附时手指顺路扫过投放口不会被误投。
     */
    private fun updateDropHighlight(fingerX: Float, fingerY: Float) {
        val overlay = dropOverlay ?: return
        val zone = resolveDropZone(fingerX, fingerY)
        overlay.highlighted.value = zone
        if (zone == armedDropZone) {
            if (zone != ScreenPinDropZone.NONE &&
                overlay.armed.value != zone &&
                System.currentTimeMillis() - armedAtMs >= DROP_TARGET_ARM_DELAY_MS
            ) {
                overlay.armed.value = zone
            }
            return
        }
        armedDropZone = zone
        armedAtMs = System.currentTimeMillis()
        overlay.armed.value = ScreenPinDropZone.NONE
        mainHandler.removeCallbacks(armDropTargetRunnable)
        if (zone != ScreenPinDropZone.NONE) {
            mainHandler.postDelayed(armDropTargetRunnable, DROP_TARGET_ARM_DELAY_MS)
        }
    }

    /** 拖拽结束 / 取消时清掉驻留状态与延时回调。 */
    private fun resetDropTargetArm() {
        mainHandler.removeCallbacks(armDropTargetRunnable)
        armedDropZone = ScreenPinDropZone.NONE
        armedAtMs = 0L
        dropOverlay?.armed?.value = ScreenPinDropZone.NONE
    }

    private fun hideDropOverlay() {
        resetDropTargetArm()
        dropOverlay?.highlighted?.value = ScreenPinDropZone.NONE
        val state = dropOverlay ?: return
        // 常驻窗口：先播退场动画，播完再 GONE（不销毁，下次拖动直接复用）。
        state.visible.value = false
        mainHandler.removeCallbacks(hideDropOverlayViewRunnable)
        mainHandler.postDelayed(hideDropOverlayViewRunnable, DROP_TARGET_EXIT_HIDE_DELAY_MS)
    }

    /** 真正拆掉常驻窗口：最后一个钉图消失、或【关闭全部钉图】时调用。 */
    private fun destroyDropOverlay() {
        mainHandler.removeCallbacks(hideDropOverlayViewRunnable)
        dropOverlay?.visible?.value = false
        val view = dropComposeView
        val owner = dropOwner
        if (view != null) {
            runCatching { windowManager?.removeView(view) }
        }
        owner?.destroy()
        dropComposeView = null
        dropOwner = null
        dropOverlay = null
    }

    private fun ensureScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismissAll()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun unregisterScreenOffReceiver() {
        val receiver = screenOffReceiver ?: return
        appContext?.let { runCatching { it.unregisterReceiver(receiver) } }
        screenOffReceiver = null
    }
}

@Composable
private fun ScreenPinContent(
    instance: PinInstance,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onZoom: (zoomFactor: Float) -> Unit,
    onLongPressDrag: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (dx: Float, dy: Float, localX: Float, localY: Float) -> Unit,
    onDragEnd: (localX: Float, localY: Float) -> Unit,
    onClose: () -> Unit,
    onAlphaChange: (Float) -> Unit,
    /** 长按拖拽用的"钉图本身"快照入口；null = 拖影退回旧画法（手绘文字/缩略图）。 */
    snapshot: HistoryCardSnapshot? = null
) {
    val showControls by instance.uiState.showControls
    val alpha by instance.uiState.contentAlpha
    val isDocked by instance.uiState.isEdgeDocked
    val displayW = instance.uiState.displayWidthPx.intValue
    val displayH = instance.uiState.displayHeightPx.intValue
    val density = LocalDensity.current
    val slotHeight = PIN_CONTROL_BAR_SLOT_HEIGHT_DP.dp
    val barMinWidth = PIN_CONTROL_BAR_MIN_WIDTH_DP.dp

    var appeared by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appeared = true
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0.8f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        )
    )
    val entryAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200)
    )
    val scrollState = rememberScrollState()
    val supportsContentScroll =
        instance.content is PinContent.Text || instance.content is PinContent.Rich
    var scrollEnabled by remember(instance.id) { mutableStateOf(supportsContentScroll) }
    androidx.compose.runtime.LaunchedEffect(
        scrollState.maxValue,
        scrollState.viewportSize,
        supportsContentScroll
    ) {
        scrollEnabled = when {
            !supportsContentScroll -> false
            scrollState.viewportSize > 0 -> scrollState.maxValue > 0
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = entryAlpha
            }
            .pointerInput(instance.id, isDocked, scrollEnabled) {
                detectPinDragAndTap(
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                    onZoom = onZoom,
                    // 贴边收起时只保留「点击恢复」，长按留给收起的滑块本身。
                    onLongPressDrag = if (isDocked) null else onLongPressDrag,
                    horizontalDragOnly = scrollEnabled && !isDocked
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val content = instance.content) {
            is PinContent.Text -> {
                val contentW = with(density) { displayW.toDp().coerceAtLeast(1.dp) }
                val contentH = with(density) { displayH.toDp().coerceAtLeast(1.dp) }
                Box(
                    modifier = Modifier
                        .size(contentW, contentH)
                        .alpha(alpha)
                        .shadow(6.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        // 整块内容录一份：长按拖拽时钉图原样跟着手指（见 HistoryCardSnapshot）。
                        .then(if (snapshot != null) Modifier.recordHistoryCardSnapshot(snapshot) else Modifier)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = content.body,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState, enabled = scrollEnabled)
                    )
                }
            }
            is PinContent.Image -> {
                val contentW = with(density) { displayW.toDp().coerceAtLeast(1.dp) }
                val contentH = with(density) { displayH.toDp().coerceAtLeast(1.dp) }
                // ⚠️ 这里多包一层 Box 只为一件事：让"录一份"的那层**包住图片本身** ——
                // 直接把修饰符挂在 `Image` 上不一定录得到（图片是 Image 内部画的）。
                Box(
                    modifier = Modifier
                        .size(contentW, contentH)
                        .alpha(alpha)
                        .then(if (snapshot != null) Modifier.recordHistoryCardSnapshot(snapshot) else Modifier)
                ) {
                    Image(
                        bitmap = content.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (content.screenRect != null) {
                            val bmpAspect =
                                content.bitmap.width.toFloat() / content.bitmap.height.coerceAtLeast(1)
                            val boxAspect = displayW.toFloat() / displayH.coerceAtLeast(1)
                            if (kotlin.math.abs(bmpAspect - boxAspect) <= 0.02f) {
                                ContentScale.FillBounds
                            } else {
                                ContentScale.Fit
                            }
                        } else {
                            ContentScale.Fit
                        }
                    )
                }
            }
            is PinContent.Rich -> {
                val contentW = with(density) { displayW.toDp().coerceAtLeast(1.dp) }
                val contentH = with(density) { displayH.toDp().coerceAtLeast(1.dp) }
                Box(
                    modifier = Modifier
                        .size(contentW, contentH)
                        .alpha(alpha)
                        .shadow(6.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        // 整块内容录一份：长按拖拽时钉图原样跟着手指（见 HistoryCardSnapshot）。
                        .then(if (snapshot != null) Modifier.recordHistoryCardSnapshot(snapshot) else Modifier)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState, enabled = scrollEnabled),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        content.blocks.forEach { block ->
                            when (block) {
                                is PinDisplayBlock.Text -> {
                                    Text(
                                        text = block.body,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                is PinDisplayBlock.Image -> {
                                    Image(
                                        bitmap = block.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = RICH_PIN_IMAGE_MAX_HEIGHT_DP.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isDocked) {
            Box(
                modifier = Modifier
                    .widthIn(min = barMinWidth)
                    .height(slotHeight)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showControls) {
                    PinControlBar(
                        alpha = alpha,
                        onAlphaChange = onAlphaChange,
                        onClose = onClose,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PinControlBar(
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(20.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Row(
        modifier = modifier
            .shadow(6.dp, pillShape, clip = false)
            .clip(pillShape)
            .border(0.5.dp, outlineColor, pillShape)
            .background(surfaceColor)
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = alpha,
            onValueChange = onAlphaChange,
            valueRange = PIN_CONTROL_ALPHA_MIN..1f,
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.shell_panel_close),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private sealed interface PinPressOutcome {
    /** 长按成立，本次触控改交给系统拖拽。 */
    object LongPress : PinPressOutcome

    /** 长按不成立，把结束等待的那个事件交回主循环继续处理。 */
    data class Continue(val event: PointerEvent) : PinPressOutcome
}

/**
 * 长按判定：手指按住不动超过 [timeoutMs] 判定为长按。
 *
 * 期间抬手、位移超过 [touchSlop]、或落下第二指都不算长按，
 * 并把触发判定的那个事件原样交回主循环（否则点击 / 双击 / 直接拖动会丢事件）。
 */
private suspend fun AwaitPointerEventScope.awaitLongPressHold(
    anchor: Offset,
    pointerId: PointerId,
    touchSlop: Float,
    timeoutMs: Long
): PinPressOutcome = try {
    withTimeout<PinPressOutcome>(timeoutMs) {
        var pending = awaitPointerEvent()
        while (true) {
            val change = pending.changes.firstOrNull { it.id == pointerId }
            val fingerLifted = change == null || !change.pressed
            val movedAway = change != null && (change.position - anchor).getDistance() > touchSlop
            val secondFinger = pending.changes.count { it.pressed } >= 2
            if (fingerLifted || movedAway || secondFinger) break
            pending = awaitPointerEvent()
        }
        PinPressOutcome.Continue(pending)
    }
} catch (_: PointerEventTimeoutCancellationException) {
    PinPressOutcome.LongPress
} catch (_: TimeoutCancellationException) {
    PinPressOutcome.LongPress
}

private suspend fun PointerInputScope.detectPinDragAndTap(
    onDragStart: () -> Unit,
    onDrag: (dx: Float, dy: Float, localX: Float, localY: Float) -> Unit,
    onDragEnd: (localX: Float, localY: Float) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    onZoom: ((zoomFactor: Float) -> Unit)? = null,
    onLongPressDrag: (() -> Unit)? = null,
    horizontalDragOnly: Boolean = false
) {
    var lastTapTime = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        val touchSlop = viewConfiguration.touchSlop
        var dragged = false
        var isPinchZooming = false
        var prevPinchDistance = 0f
        var pendingEvent: PointerEvent? = null

        if (onLongPressDrag != null) {
            when (val outcome = awaitLongPressHold(
                anchor = down.position,
                pointerId = pointerId,
                touchSlop = touchSlop,
                timeoutMs = viewConfiguration.longPressTimeoutMillis
            )) {
                is PinPressOutcome.LongPress -> {
                    // 这次触控已交给系统拖拽，既不当作点击，也不再挪窗口。
                    lastTapTime = 0L
                    onLongPressDrag()
                    return@awaitEachGesture
                }
                is PinPressOutcome.Continue -> pendingEvent = outcome.event
            }
        }

        while (true) {
            val event = pendingEvent?.also { pendingEvent = null } ?: awaitPointerEvent()
            val pressedPointers = event.changes.filter { it.pressed }

            if (pressedPointers.size >= 2) {
                if (!isPinchZooming) {
                    if (dragged) {
                        onDragEnd(down.position.x, down.position.y)
                        dragged = false
                    }
                    isPinchZooming = true
                    val p0 = pressedPointers[0].position
                    val p1 = pressedPointers[1].position
                    prevPinchDistance = (p0 - p1).getDistance()
                } else {
                    val p0 = pressedPointers[0].position
                    val p1 = pressedPointers[1].position
                    val currentDistance = (p0 - p1).getDistance()
                    if (prevPinchDistance > 0f && currentDistance > 0f) {
                        val zoomFactor = currentDistance / prevPinchDistance
                        if (abs(zoomFactor - 1f) > 0.001f) {
                            onZoom?.invoke(zoomFactor)
                            event.changes.forEach { it.consume() }
                        }
                    }
                    prevPinchDistance = currentDistance
                }
                continue
            }

            if (isPinchZooming) {
                if (pressedPointers.isEmpty()) {
                    break
                }
                prevPinchDistance = 0f
                continue
            }

            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                if (dragged) {
                    onDragEnd(change.position.x, change.position.y)
                } else {
                    val now = System.currentTimeMillis()
                    if (onDoubleTap != null && (now - lastTapTime) in 1..300) {
                        onDoubleTap()
                        lastTapTime = 0L
                    } else {
                        lastTapTime = now
                        onTap()
                    }
                }
                break
            }
            val offset = change.position - down.position
            if (!dragged && offset.getDistance() > touchSlop) {
                if (horizontalDragOnly && abs(offset.y) >= abs(offset.x)) {
                    break
                }
                dragged = true
                onDragStart()
            }
            if (dragged) {
                val delta = change.positionChange()
                if (delta != Offset.Zero) {
                    change.consume()
                    onDrag(delta.x, delta.y, change.position.x, change.position.y)
                }
            }
        }
    }
}

/** 贴边标签圆哪两角：顶上垂下来的圆下面、底下的圆上面、右侧的圆左边。 */
private enum class DropTargetCorners { TOP, BOTTOM, START }

@Composable
private fun ScreenPinDropTargets(
    highlighted: ScreenPinDropZone,
    armed: ScreenPinDropZone,
    visible: Boolean,
    topInsetPx: Int,
    bottomInsetPx: Int,
    rightInsetPx: Int,
    onDeleteBounds: (Rect) -> Unit,
    onNotifyBounds: (Rect) -> Unit,
    onStashBounds: (Rect) -> Unit
) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        // 「通知」放顶上：**半隐**贴着状态栏/刘海的下沿，只露下半截，像从顶边垂下来的一片标签。
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_notification),
            widthDp = DROP_TARGET_NOTIFY_WIDTH_DP,
            heightDp = DROP_TARGET_NOTIFY_HEIGHT_DP,
            corners = DropTargetCorners.BOTTOM,
            danger = false,
            hovered = highlighted == ScreenPinDropZone.NOTIFICATION,
            armed = armed == ScreenPinDropZone.NOTIFICATION,
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(
                    y = with(density) {
                        topInsetPx.toDp() - (DROP_TARGET_NOTIFY_HEIGHT_DP / 2).dp
                    }
                ),
            onBoundsInWindow = onNotifyBounds
        )
        // 「删除」常驻危险色，**半隐**贴着导航栏上沿，只露上半截。
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_delete),
            widthDp = DROP_TARGET_DELETE_WIDTH_DP,
            heightDp = DROP_TARGET_DELETE_HEIGHT_DP,
            corners = DropTargetCorners.TOP,
            danger = true,
            hovered = highlighted == ScreenPinDropZone.DELETE,
            armed = armed == ScreenPinDropZone.DELETE,
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(
                    y = with(density) {
                        (DROP_TARGET_DELETE_HEIGHT_DP / 2).dp - bottomInsetPx.toDp()
                    }
                ),
            onBoundsInWindow = onDeleteBounds
        )
        // 「暂存」贴右边缘的竖长条，**半隐**在屏幕右侧之外，只露左半截。
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_stash),
            widthDp = DROP_TARGET_STASH_WIDTH_DP,
            heightDp = DROP_TARGET_STASH_HEIGHT_DP,
            corners = DropTargetCorners.START,
            danger = false,
            hovered = highlighted == ScreenPinDropZone.STASH,
            armed = armed == ScreenPinDropZone.STASH,
            visible = visible,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(
                    x = with(density) {
                        (DROP_TARGET_STASH_WIDTH_DP / 2).dp - rightInsetPx.toDp()
                    }
                ),
            onBoundsInWindow = onStashBounds
        )
    }
}

@Composable
private fun DropTargetChip(
    text: String,
    widthDp: Float,
    heightDp: Float,
    corners: DropTargetCorners,
    danger: Boolean,
    hovered: Boolean,
    armed: Boolean,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBoundsInWindow: (Rect) -> Unit
) {
    val radius = 20.dp
    val shape = when (corners) {
        DropTargetCorners.TOP -> RoundedCornerShape(topStart = radius, topEnd = radius)
        DropTargetCorners.BOTTOM -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        DropTargetCorners.START -> RoundedCornerShape(topStart = radius, bottomStart = radius)
    }
    val container = when {
        danger -> MaterialTheme.colorScheme.error
        armed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        danger -> MaterialTheme.colorScheme.onError
        armed -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stroke = if (danger) content.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
    // 半隐之后有一半在屏幕外/状态栏后面，文字只能待在**可见的那一半**里。
    val labelAlign = when (corners) {
        DropTargetCorners.BOTTOM -> Alignment.BottomCenter
        DropTargetCorners.TOP -> Alignment.TopCenter
        DropTargetCorners.START -> Alignment.CenterStart
    }
    val labelPadding = when (corners) {
        DropTargetCorners.BOTTOM -> PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp)
        DropTargetCorners.TOP -> PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp)
        DropTargetCorners.START -> PaddingValues(start = 10.dp, end = 6.dp)
    }

    // 一次拖动 = 一次 0→1（滑入）与 1→0（滑出）：窗口常驻后不能用"首次组合"来触发动画。
    // 注意变量名不能叫 scale/alpha —— 那会撞上 GraphicsLayerScope 的同名成员。
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(DROP_TARGET_ENTER_MS)
    )
    val chipScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (armed) DROP_TARGET_ARMED_SCALE else 1f,
        animationSpec = androidx.compose.animation.core.tween(DROP_TARGET_ENTER_MS)
    )
    val chipAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (hovered || armed) 1f else DROP_TARGET_IDLE_ALPHA,
        animationSpec = androidx.compose.animation.core.tween(DROP_TARGET_ENTER_MS)
    )

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            val bounds = coords.boundsInParent()
            onBoundsInWindow(
                Rect(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt()
                )
            )
        }
    ) {
        Box(
            modifier = Modifier
                .size(widthDp.dp, heightDp.dp)
                .graphicsLayer {
                    // 缩放/位移都走 graphicsLayer：不动 layout，上面上报的命中矩形保持稳定。
                    val slide = (1f - progress) * DROP_TARGET_ENTER_SLIDE_DP.dp.toPx()
                    translationX = if (corners == DropTargetCorners.START) slide else 0f
                    translationY = when (corners) {
                        DropTargetCorners.TOP -> -slide
                        DropTargetCorners.BOTTOM -> slide
                        DropTargetCorners.START -> 0f
                    }
                    scaleX = chipScale
                    scaleY = chipScale
                    this.alpha = chipAlpha * progress
                }
                .clip(shape)
                .background(container)
                .border(2.dp, stroke, shape),
            contentAlignment = labelAlign
        ) {
            Text(
                text = text,
                color = content,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.padding(labelPadding)
            )
        }
    }
}
