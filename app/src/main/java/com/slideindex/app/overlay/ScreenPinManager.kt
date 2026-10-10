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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.slideindex.app.di.OverlayDependencyAccess
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
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
private const val DROP_TARGET_HIT_SLOP_DP = 10f
private const val DROP_TARGET_CHIP_WIDTH_DP = 100f
private const val DROP_TARGET_CHIP_HEIGHT_DP = 52f
private const val DROP_TARGET_EDGE_PADDING_DP = 8f

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

private class DropOverlayState {
    var deleteBounds: Rect = Rect()
    var notifyBounds: Rect = Rect()
    var stashBounds: Rect = Rect()
    val highlighted = mutableStateOf(ScreenPinDropZone.NONE)
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
        hideDropOverlay()
    }

    private fun addPin(
        context: Context,
        content: PinContent,
        placement: PinInitialPlacement? = null
    ): Boolean {
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureScreenOffReceiver(hostContext)
        val wm = windowManager ?: (
            hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        )?.also { windowManager = it } ?: return false

        val id = UUID.randomUUID().toString()
        val owner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(hostContext, owner)
        val metrics = hostContext.resources.displayMetrics
        val contentDisplaySize = when (content) {
            is PinContent.Image -> {
                content.bitmap.density = metrics.densityDpi
                if (placement != null &&
                    placement.expandedWidthPx > 0 &&
                    placement.expandedHeightPx > 0
                ) {
                    placement.expandedWidthPx to placement.expandedHeightPx
                } else {
                    resolvePinImageDisplaySizePx(
                        bitmap = content.bitmap,
                        screenRect = content.screenRect,
                        layoutMeta = content.layoutMeta,
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
                        text = content.body,
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
                val imageContent = content as? PinContent.Image
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
            content = content,
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
        if (content is PinContent.Text || content is PinContent.Rich) {
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
        val maxW = (metrics.widthPixels * 0.95f).roundToInt().coerceAtLeast(minW)

        val newW = (currentW * zoomFactor).roundToInt().coerceIn(minW, maxW)
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
        val context = appContext ?: return
        val metrics = context.resources.displayMetrics

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
            val maxW = (metrics.widthPixels * 0.95f).roundToInt()
            val aspectRatio = baseW.toFloat() / baseH.toFloat()
            targetW = (baseW * 1.8f).roundToInt().coerceAtMost(maxW)
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
        val zone = resolveDropZone(fingerX, fingerY)
        when (zone) {
            ScreenPinDropZone.STASH -> stashPin(pinId)
            ScreenPinDropZone.DELETE -> removePin(pinId)
            ScreenPinDropZone.NOTIFICATION -> postPinToNotification(pinId)
            ScreenPinDropZone.NONE -> checkEdgeSnap(instance)
        }
    }

    private fun resolveDropZone(fingerX: Float, fingerY: Float): ScreenPinDropZone {
        val overlay = dropOverlay ?: return ScreenPinDropZone.NONE
        val metrics = appContext?.resources?.displayMetrics
        val fallback = metrics?.let { computeFallbackDropBounds(it) }
        val overlayView = dropComposeView
        val x = fingerX.roundToInt()
        val y = fingerY.roundToInt()
        val delete = screenDropBounds(overlayView, overlay.deleteBounds, fallback?.deleteBounds)
        val notify = screenDropBounds(overlayView, overlay.notifyBounds, fallback?.notifyBounds)
        val stash = screenDropBounds(overlayView, overlay.stashBounds, fallback?.stashBounds)
        if (delete != null && hitDropTarget(delete, x, y, metrics)) return ScreenPinDropZone.DELETE
        if (notify != null && hitDropTarget(notify, x, y, metrics)) return ScreenPinDropZone.NOTIFICATION
        if (stash != null && hitDropTarget(stash, x, y, metrics)) return ScreenPinDropZone.STASH
        return ScreenPinDropZone.NONE
    }

    private fun screenDropBounds(
        overlayView: ComposeView?,
        boundsInOverlay: Rect,
        fallback: Rect?
    ): Rect? {
        if (overlayView != null && !boundsInOverlay.isEmpty) {
            return overlayBoundsToScreen(overlayView, boundsInOverlay)
        }
        return fallback?.takeUnless { it.isEmpty }
    }

    private fun hitDropTarget(bounds: Rect, x: Int, y: Int, metrics: DisplayMetrics?): Boolean {
        if (bounds.isEmpty) return false
        val slop = metrics?.let { (DROP_TARGET_HIT_SLOP_DP * it.density).roundToInt() } ?: 0
        return x >= bounds.left - slop &&
            x <= bounds.right + slop &&
            y >= bounds.top - slop &&
            y <= bounds.bottom + slop
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
        val snapshot = buildNotificationSnapshot(instance)
        val (title, displayBitmap) = when (val content = instance.content) {
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
        removePin(pinId)
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

    private fun computeFallbackDropBounds(metrics: DisplayMetrics): DropOverlayState {
        val chipW = (DROP_TARGET_CHIP_WIDTH_DP * metrics.density).roundToInt()
        val chipH = (DROP_TARGET_CHIP_HEIGHT_DP * metrics.density).roundToInt()
        val edge = (DROP_TARGET_EDGE_PADDING_DP * metrics.density).roundToInt()
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        return DropOverlayState().apply {
            notifyBounds = Rect(
                edge,
                screenH / 2 - chipH / 2,
                edge + chipW,
                screenH / 2 + chipH / 2
            )
            deleteBounds = Rect(
                screenW / 2 - chipW / 2,
                screenH - edge - chipH,
                screenW / 2 + chipW / 2,
                screenH - edge
            )
            stashBounds = Rect(
                screenW - edge - chipW,
                screenH / 2 - chipH / 2,
                screenW - edge,
                screenH / 2 + chipH / 2
            )
        }
    }

    private fun showDropOverlay() {
        if (dropComposeView != null) return
        val context = appContext ?: return
        val wm = windowManager ?: return
        val metrics = context.resources.displayMetrics
        val state = computeFallbackDropBounds(metrics)
        val owner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(context, owner)
        composeView.setContent {
            OverlayAwareModuleTheme {
                val highlighted by state.highlighted
                ScreenPinDropTargets(
                    highlighted = highlighted,
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
            return
        }
        dropOverlay = state
        dropComposeView = composeView
        dropOwner = owner
    }

    private fun updateDropHighlight(fingerX: Float, fingerY: Float) {
        dropOverlay?.highlighted?.value = resolveDropZone(fingerX, fingerY)
    }

    private fun hideDropOverlay() {
        dropOverlay?.highlighted?.value = ScreenPinDropZone.NONE
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

@Composable
private fun ScreenPinDropTargets(
    highlighted: ScreenPinDropZone,
    onDeleteBounds: (Rect) -> Unit,
    onNotifyBounds: (Rect) -> Unit,
    onStashBounds: (Rect) -> Unit
) {
    val stashActive = highlighted == ScreenPinDropZone.STASH
    val deleteActive = highlighted == ScreenPinDropZone.DELETE
    val notifyActive = highlighted == ScreenPinDropZone.NOTIFICATION
    Box(modifier = Modifier.fillMaxSize()) {
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_notification),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
            active = notifyActive,
            onBoundsInWindow = onNotifyBounds
        )
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_delete),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            active = deleteActive,
            onBoundsInWindow = onDeleteBounds
        )
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_stash),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            active = stashActive,
            onBoundsInWindow = onStashBounds
        )
    }
}

@Composable
private fun DropTargetChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean,
    onBoundsInWindow: (Rect) -> Unit
) {
    val bg = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }
    val fg = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
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
            Modifier
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(text = text, color = fg, fontSize = 14.sp)
        }
    }
}
