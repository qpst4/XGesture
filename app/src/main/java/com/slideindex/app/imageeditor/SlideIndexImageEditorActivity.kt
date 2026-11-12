/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：inspire/imageeditor/InspireImageEditorActivity.kt
 * 已按本项目的数据模型、主题与集成层改写（部分模块拆分为多文件、部分能力为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.imageeditor

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.slideindex.app.R
import com.slideindex.app.databinding.ActivityInspireImageEditorBinding
import com.slideindex.app.databinding.PopupInspireImageEditorSaveOptionsBinding
import com.slideindex.app.imageeditor.model.EditAction
import com.slideindex.app.imageeditor.model.EditorMode
import com.slideindex.app.imageeditor.model.EditorPoint
import com.slideindex.app.imageeditor.model.ShapeType
import com.slideindex.app.imageeditor.state.EditorSession
import com.slideindex.app.imageeditor.state.EditorUiState
import com.slideindex.app.imageeditor.ui.ImageEditorView
import com.slideindex.app.inspire.ManagedBitmap
import com.slideindex.app.util.ImageDecodeGuard
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.overlay.buildScreenshotLayoutMeta
import com.slideindex.app.overlay.resolvePinImageDisplaySizePx
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashMetaRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SlideIndexImageEditorActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var binding: ActivityInspireImageEditorBinding
    private lateinit var quickTools: ImageEditorQuickToolsCoordinator
    private val editorSession = EditorSession()

    private var sourceBitmapHandle: ManagedBitmap? = null
    private var saveOptionsPopup: PopupWindow? = null
    private var loadBitmapJob: Job? = null
    private var exportJob: Job? = null
    private var enterAnimationPlayed = false

    private var pinScreenRect: Rect? = null
    private var pinLayoutMeta: ScreenshotLayoutMeta? = null
    private var pickReturnContext: ImageEditorPickReturnContext? = null
    /**
     * 「保存的结果写到这个文件」（§0.16.22）：非 null = 本实例是被 [resultIntent] 拉起来的，
     * 「保存」要交出结果而不是写相册。见 [saveEditedBitmapToOutput]。
     */
    private var resultOutputPath: String? = null

    private var shareEngineDragHelper: ImageEditorShareEngineDragHelper? = null

    private lateinit var modeButtons: List<Pair<MaterialButton, EditorMode>>
    private val adaptiveStrokeWidths = linkedMapOf<EditorMode, Float>()

    private var lastPersistedMode: EditorMode? = null
    private var lastPersistedColor: Int? = null
    private var lastPersistedShapeType: ShapeType? = null
    private var lastObservedChromeState: EditorUiState? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImageEditorThemeApplicator.applyBeforeContent(this, settingsRepository.readSnapshot())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityInspireImageEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resultOutputPath = intent.getStringExtra(EXTRA_RESULT_OUTPUT_PATH)?.takeIf { it.isNotBlank() }
        // §0.16.23：判读日志（tag 与中转 Activity 相同，`adb logcat -s StashImageEdit` 一次看全）。
        // 这一行是"extra 到底有没有传进来"的**编辑器侧证据**：null 就说明调用方没带 extra，
        // 本次「保存」会走老的"写相册 + 保存选项"，调用方永远等不到 RESULT_OK。
        android.util.Log.i(
            com.slideindex.app.service.StashEditImageTrampolineActivity.LOG_TAG,
            "editor onCreate resultOutputPath=$resultOutputPath " +
                "inputCachePath=${intent.getStringExtra(EXTRA_IMAGE_CACHE_PATH)}",
        )

        quickTools = ImageEditorQuickToolsCoordinator(this, binding, editorSession)

        binding.editorView.bindSession(editorSession)
        binding.editorView.setCallback(object : ImageEditorView.Callback {
            override fun onRequestAddText(anchor: EditorPoint, suggestedSize: Float) {
                showTextEditor(anchor, suggestedSize, null)
            }

            override fun onRequestEditText(action: EditAction.Text) {
                showTextEditor(action.anchor, action.textSize, action)
            }
        })

        setupToolbar()
        setupControls()
        setupShareEngineDrag()
        applyWindowInsets()
        observeSession()

        lifecycleScope.launch {
            restoreLastUsedEditorPreferences()
            loadSourceBitmap()
        }
    }

    override fun onDestroy() {
        loadBitmapJob?.cancel()
        exportJob?.cancel()
        saveOptionsPopup?.dismiss()
        saveOptionsPopup = null
        shareEngineDragHelper?.detach()
        shareEngineDragHelper = null
        pickReturnContext?.recycleImageCopies()
        pickReturnContext = null
        quickTools.onDestroy()
        binding.root.animate().cancel()
        binding.bottomPanel.animate().cancel()
        binding.editorView.clearImage()
        sourceBitmapHandle?.close()
        sourceBitmapHandle = null
        ImageEditorLaunchCache.clear()
        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.btnClose.setOnClickListener { finish() }
        binding.btnClose.setOnLongClickListener {
            if (pickReturnContext != null) {
                returnEditedImageToPickPanel()
            }
            pickReturnContext != null
        }
    }

    private fun setupShareEngineDrag() {
        val helper = ImageEditorShareEngineDragHelper(
            anchor = binding.btnShare,
            settingsRepository = settingsRepository,
            onSystemShare = { shareEditedBitmapSystem() },
            onShareEngine = { engine -> shareEditedBitmapToEngine(engine) },
        )
        helper.attach()
        shareEngineDragHelper = helper
    }

    private fun applyWindowInsets() {
        val baseBottom = (binding.bottomPanel.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
            ?.bottomMargin ?: 0
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            (binding.bottomPanel.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.bottomMargin =
                baseBottom + bars.bottom
            binding.bottomPanel.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupControls() {
        modeButtons = listOf(
            binding.modeNavigate to EditorMode.NAVIGATE,
            binding.modeCrop to EditorMode.CROP,
            binding.modeDoodle to EditorMode.DOODLE,
            binding.modeEraser to EditorMode.ERASER,
            binding.modeShape to EditorMode.SHAPE,
            binding.modeText to EditorMode.TEXT,
            binding.modeMosaic to EditorMode.MOSAIC,
            binding.modeNumber to EditorMode.NUMBER,
        )
        modeButtons.forEach { (button, mode) ->
            button.setOnClickListener {
                binding.editorView.cancelOngoingInteraction()
                binding.editorView.suppressMultiTouchTemporarily(0L)
                val current = editorSession.state.currentMode
                if (current != mode) {
                    editorSession.switchMode(mode)
                    quickTools.isModeQuickToolsExpanded = quickTools.supportsModeQuickTools(mode)
                    quickTools.dismissScrubPopup()
                } else if (quickTools.supportsModeQuickTools(mode)) {
                    quickTools.isModeQuickToolsExpanded = true
                }
                renderModeSelection(editorSession.state.currentMode)
                quickTools.renderModeQuickTools(editorSession.state)
                scrollSelectedModeIntoView(editorSession.state.currentMode)
            }
        }
        binding.modeNavigate.setOnLongClickListener {
            binding.editorView.resetViewport()
            true
        }

        quickTools.renderColorSelection(editorSession.state.currentColor)
        quickTools.setupCompactColorControls()
        quickTools.setupCompactBrushControls()
        quickTools.setupCompactShapeControls()
        quickTools.setupCompactNumberStyleControls()

        binding.editorView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                quickTools.dismissQuickToolPopup()
            }
            false
        }

        binding.btnUndo.setOnClickListener { editorSession.undo() }
        binding.btnRedo.setOnClickListener { editorSession.redo() }
        binding.btnTopClearCrop.setOnClickListener { binding.editorView.clearCropSelection() }
        binding.btnTopApplyCrop.setOnClickListener {
            binding.editorView.cancelOngoingInteraction()
            val bitmapSize = binding.editorView.currentBitmapSize()
            val cropRect = binding.editorView.peekCropBitmapRect()
            if (binding.editorView.applyCropSelection()) {
                if (bitmapSize != null && cropRect != null) {
                    val (newRect, newMeta) = ImageEditorPinMeta.applyCropToPinMeta(
                        screenRect = pinScreenRect,
                        layoutMeta = pinLayoutMeta,
                        cropRectInBitmap = cropRect,
                        bitmapWidth = bitmapSize.first,
                        bitmapHeight = bitmapSize.second,
                    )
                    pinScreenRect = newRect
                    pinLayoutMeta = newMeta
                }
                applyAdaptiveBrushSizeIfNeeded()
                quickTools.dismissQuickToolPopup()
                editorSession.switchMode(EditorMode.NAVIGATE)
                binding.editorView.suppressMultiTouchTemporarily(0L)
            } else {
                toast(R.string.inspire_image_edit_crop_empty)
            }
        }
        binding.btnPin.setOnClickListener { pinEditedBitmapToScreen() }
        binding.btnPin.setOnLongClickListener {
            addEditedBitmapToStash()
            true
        }
        binding.btnCopy.setOnClickListener { copyEditedBitmap() }
        binding.btnSave.setOnClickListener {
            // §0.16.22：调用方（闪念编辑浮窗的 ✎）要求"结果回传"时，「保存」不再是
            // "写进相册 + 弹保存选项"，而是"写到它指定的文件 + RESULT_OK 返回"。
            val output = resultOutputPath
            if (output != null) {
                saveEditedBitmapToOutput(output)
            } else {
                showSaveOptions(it)
            }
        }
        binding.compactAddTextButton.setOnClickListener {
            val center = binding.editorView.visibleImageCenterPoint() ?: return@setOnClickListener
            showTextEditor(center, binding.editorView.suggestedTextSizeForInsert(), null)
        }

        renderModeSelection(EditorMode.NAVIGATE)
    }

    private fun observeSession() {
        editorSession.addListener(object : EditorSession.Listener {
            override fun onStateChanged(state: EditorUiState) {
                binding.btnUndo.isEnabled = state.canUndo
                binding.btnRedo.isEnabled = state.canRedo
                val prev = lastObservedChromeState
                val chromeChanged = prev == null ||
                    prev.currentMode != state.currentMode ||
                    prev.currentColor != state.currentColor ||
                    prev.currentShapeType != state.currentShapeType ||
                    prev.currentNumberStyle != state.currentNumberStyle ||
                    prev.doodleStrokeWidth != state.doodleStrokeWidth ||
                    prev.eraserStrokeWidth != state.eraserStrokeWidth ||
                    prev.shapeStrokeWidth != state.shapeStrokeWidth ||
                    prev.mosaicStrokeWidth != state.mosaicStrokeWidth
                if (chromeChanged) {
                    renderModeSelection(state.currentMode)
                    quickTools.renderColorSelection(state.currentColor)
                    quickTools.renderShapeSelection(state.currentShapeType)
                    quickTools.renderNumberStyleSelection(state)
                    quickTools.renderBrushControls(state)
                    quickTools.renderModeQuickTools(state)
                    persistLastUsedEditorPreferencesIfChanged(state)
                }
                lastObservedChromeState = state
            }
        })
    }

    private fun renderModeSelection(currentMode: EditorMode) {
        val selectedBg = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer)
        val selectedFg = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSecondaryContainer)
        val unselectedFg = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        modeButtons.forEach { (button, mode) ->
            val selected = mode == currentMode
            button.backgroundTintList = ColorStateList.valueOf(if (selected) selectedBg else 0)
            button.setTextColor(if (selected) selectedFg else unselectedFg)
        }
    }

    private fun scrollSelectedModeIntoView(mode: EditorMode) {
        val button = modeButtons.firstOrNull { it.second == mode }?.first ?: return
        binding.modeActionsScroll.post {
            val scrollWidth = binding.modeActionsScroll.width
            if (scrollWidth <= 0) return@post
            val left = binding.modeActionsRow.left + button.left
            val right = left + button.width
            var targetScroll = binding.modeActionsScroll.scrollX
            if (left < targetScroll) {
                targetScroll = left
            } else if (right > targetScroll + scrollWidth) {
                targetScroll = right - scrollWidth
            }
            targetScroll = max(0, targetScroll)
            if (targetScroll != binding.modeActionsScroll.scrollX) {
                binding.modeActionsScroll.smoothScrollTo(targetScroll, 0)
            }
        }
    }

    private suspend fun loadSourceBitmap() {
        loadBitmapJob?.cancel()
        loadBitmapJob = lifecycleScope.launch {
            val payload = withContext(Dispatchers.Default) {
                ImageEditorLaunchCache.take()
            }
            if (payload == null) {
                val crossPath = intent.getStringExtra(EXTRA_IMAGE_CACHE_PATH)
                val decoded = crossPath?.takeIf { it.isNotBlank() }?.let { path ->
                    withContext(Dispatchers.Default) {
                        ImageDecodeGuard.decodeFile(
                            path = path,
                            maxSidePx = ImageDecodeGuard.MAX_EDITOR_SIDE_PX,
                            mutable = true,
                        )
                    }?.also { runCatching { java.io.File(path).delete() } }
                }
                if (decoded == null) {
                    toast(R.string.inspire_image_edit_load_failed)
                    finish()
                    return@launch
                }
                pinScreenRect = null
                pinLayoutMeta = null
                pickReturnContext = null
                val crossHandle = ManagedBitmap.from(decoded)
                sourceBitmapHandle = crossHandle.acquire()
                binding.editorView.setImageBitmap(crossHandle)
                applyAdaptiveBrushSizeIfNeeded()
                runEnterAnimationIfNeeded()
                return@launch
            }
            pinScreenRect = payload.screenRect
            pinLayoutMeta = payload.layoutMeta
            pickReturnContext = payload.pickReturnContext
            val handle = ManagedBitmap.from(payload.bitmap)
            sourceBitmapHandle = handle.acquire()
            binding.editorView.setImageBitmap(handle)
            applyAdaptiveBrushSizeIfNeeded()
            runEnterAnimationIfNeeded()
        }
    }

    private fun overlayContext(): Context =
        OverlayDependencyAccess.overlayHostContext() ?: applicationContext

    private fun resolvePinLayoutMeta(bitmap: Bitmap): ScreenshotLayoutMeta {
        val existing = pinLayoutMeta
        if (existing != null) return existing
        val metrics = overlayContext().resources.displayMetrics
        return buildScreenshotLayoutMeta(
            bitmap = bitmap,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
        ).also { pinLayoutMeta = it }
    }

    /** 取词 [pinScreenRect] 已是屏幕像素钉图框时不再传 layoutMeta，避免二次 map 导致位置/尺寸错误。 */
    private fun layoutMetaForPin(bitmap: Bitmap): ScreenshotLayoutMeta? {
        val rect = pinScreenRect
        if (rect != null && !rect.isEmpty) return null
        return resolvePinLayoutMeta(bitmap)
    }

    private fun runEnterAnimationIfNeeded() {
        if (enterAnimationPlayed) return
        enterAnimationPlayed = true
        binding.editorView.post {
            binding.editorView.alpha = 0f
            binding.editorView.scaleX = 0.985f
            binding.editorView.scaleY = 0.985f
            binding.editorView.translationY = 12f
            binding.editorView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(IMAGE_ENTER_DURATION_MS)
                .setInterpolator(IMAGE_ENTER_INTERPOLATOR)
                .start()
        }
        prepareBarsForEnter()
        AnimatorSet().apply {
            startDelay = BAR_ENTER_DELAY_MS
            interpolator = BAR_ENTER_INTERPOLATOR
            duration = BAR_ENTER_DURATION_MS
            playTogether(
                ObjectAnimator.ofFloat(binding.bottomPanel, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(binding.bottomPanel, View.TRANSLATION_Y, binding.bottomPanel.translationY, 0f),
            )
            start()
        }
    }

    private fun prepareBarsForEnter() {
        binding.bottomPanel.alpha = 0f
        val height = binding.bottomPanel.height.takeIf { it > 0 }
            ?: binding.bottomPanel.measuredHeight
        binding.bottomPanel.translationY = max(height, 1) * 0.8f
    }

    private fun applyAdaptiveBrushSizeIfNeeded() {
        val size = binding.editorView.currentBitmapSize() ?: return
        val shortEdge = min(size.first, size.second)
        brushModes().forEach { mode ->
            val adaptive = calculateAdaptiveBrushSize(shortEdge, mode)
            val current = editorSession.state.brushWidthFor(mode)
            val lastAdaptive = adaptiveStrokeWidths[mode]
            if (lastAdaptive == null || abs(current - lastAdaptive) < 0.5f) {
                adaptiveStrokeWidths[mode] = adaptive
                editorSession.updateStrokeWidthForMode(mode, adaptive)
            }
        }
    }

    private fun calculateAdaptiveBrushSize(shortEdge: Int, mode: EditorMode): Float {
        val f = shortEdge.toFloat()
        val raw = when (mode) {
            EditorMode.MOSAIC -> f * 0.04f + BRUSH_SIZE_MIN
            EditorMode.DOODLE -> f * 0.012f + 2f
            EditorMode.ERASER -> f * 0.026f + 4f
            EditorMode.SHAPE -> f * 0.015f + 2f
            EditorMode.NAVIGATE, EditorMode.CROP, EditorMode.TEXT, EditorMode.NUMBER ->
                editorSession.state.brushWidthFor(EditorMode.DOODLE)
        }
        return raw.coerceIn(BRUSH_SIZE_MIN, BRUSH_SIZE_MAX)
    }

    private fun brushModes(): List<EditorMode> =
        listOf(EditorMode.DOODLE, EditorMode.ERASER, EditorMode.SHAPE, EditorMode.MOSAIC)

    private fun exportEditedBitmap(): Bitmap? = binding.editorView.exportBitmap(1f)

    private fun copyEditedBitmap() {
        exportJob?.cancel()
        val bitmap = exportEditedBitmap() ?: run {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch {
            FloatBallTextPick.copyImage(this@SlideIndexImageEditorActivity, bitmap)
            finish()
        }
    }

    private fun shareEditedBitmapSystem() {
        exportJob?.cancel()
        val bitmap = exportEditedBitmap() ?: run {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch {
            FloatBallTextPick.shareScreenshot(this@SlideIndexImageEditorActivity, bitmap)
        }
    }

    private fun shareEditedBitmapToEngine(engine: SearchEngineConfig) {
        exportJob?.cancel()
        val bitmap = exportEditedBitmap() ?: run {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch {
            SearchEngineLauncher.launchImageShare(this@SlideIndexImageEditorActivity, engine, bitmap)
        }
    }

    private fun returnEditedImageToPickPanel() {
        val returnContext = pickReturnContext ?: return
        exportJob?.cancel()
        val editedBitmap = exportEditedBitmap() ?: run {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch {
            val index = returnContext.editedImageIndex.coerceIn(0, returnContext.imageCopies.lastIndex)
            val images = returnContext.imageCopies.toMutableList()
            val previous = images[index]
            if (previous !== editedBitmap && !previous.isRecycled) {
                previous.recycle()
            }
            images[index] = editedBitmap
            val result = FloatBallPickResult(
                a11yText = returnContext.a11yText,
                ocrText = returnContext.ocrText,
                screenshot = images.getOrNull(index),
                screenRect = pinScreenRect?.let { Rect(it) },
                layoutMeta = pinLayoutMeta,
                activeSource = returnContext.activeSource,
                ocrAvailable = returnContext.ocrAvailable,
                ocrPending = false,
                ocrPreferSwitchOnComplete = returnContext.ocrPreferSwitchOnComplete,
                a11ySourceEnabled = returnContext.a11ySourceEnabled,
                isShareImageOcr = returnContext.isShareImageOcr,
                barcodeResults = returnContext.barcodeResults,
                contentOrigin = returnContext.contentOrigin,
                contentKind = returnContext.contentKind,
                images = images,
                initialImageIndex = index,
                ownsImages = true,
            )
            pickReturnContext = null
            FloatBallPickResultPanel.showResult(
                overlayContext(),
                result = result,
                initialTextMode = returnContext.textMode,
                ocrTextsByImageIndex = returnContext.ocrTextsByImageIndex,
            )
            finish()
        }
    }

    private fun pinEditedBitmapToScreen() {
        exportJob?.cancel()
        val bitmap = exportEditedBitmap() ?: run {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch {
            val ctx = overlayContext()
            StashCoordinator.pinImageToScreen(ctx, bitmap, pinScreenRect, layoutMetaForPin(bitmap))
            finishAndRemoveTask()
        }
    }

    private fun addEditedBitmapToStash() {
        exportJob?.cancel()
        val bitmap = exportEditedBitmap() ?: run {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch {
            val ctx = overlayContext()
            val metrics = ctx.resources.displayMetrics
            val meta = layoutMetaForPin(bitmap)
            val (displayW, displayH) = resolvePinImageDisplaySizePx(
                bitmap = bitmap,
                screenRect = pinScreenRect,
                layoutMeta = meta,
                screenWidthPx = metrics.widthPixels,
                screenHeightPx = metrics.heightPixels,
            )
            StashCoordinator.addImage(
                bitmap = bitmap,
                pinDisplayWidthPx = displayW,
                pinDisplayHeightPx = displayH,
                source = StashMetaRepository.SOURCE_IMAGE,
            ) { success ->
                Toast.makeText(
                    ctx,
                    if (success) R.string.stash_saved else R.string.stash_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                finishAndRemoveTask()
            }
        }
    }

    private fun showSaveOptions(anchor: View) {
        saveOptionsPopup?.dismiss()
        val popupBinding = PopupInspireImageEditorSaveOptionsBinding.inflate(LayoutInflater.from(this))
        val popup = PopupWindow(
            popupBinding.root,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            elevation = dp(10f)
        }
        popupBinding.actionSavePersist.setOnClickListener {
            popup.dismiss()
            exportAndSave(deleteAfterMinutes = 0)
        }
        popupBinding.actionSaveAutoDelete.setOnClickListener {
            popup.dismiss()
            exportAndSave(deleteAfterMinutes = 5)
        }
        popupBinding.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val xOff = anchor.width - popupBinding.root.measuredWidth
        val yOff = -(anchor.height + popupBinding.root.measuredHeight + dpToPx(8))
        popup.showAsDropDown(anchor, xOff, yOff)
        saveOptionsPopup = popup
    }

    /**
     * 「保存」的结果版本（§0.16.22）：把编辑后的位图写进调用方指定的文件，然后 `RESULT_OK` 返回。
     *
     * 为什么走文件而不是把 Bitmap 塞进 `Intent`：Bitmap 走 Binder 有 1MB 级别的事务上限，
     * 一张 2048 长的图必定 `TransactionTooLargeException`。与
     * `StashComposerImageTrampolineActivity` 回传"选图路径"是同一套做法。
     *
     * 失败（导出不出来 / 写不进去）时**写 `RESULT_CANCELED`** 并留在编辑器里：
     * 调用方按"取消 = 什么都不动"处理，用户不会以为改丢了。写到一半的残文件也在这里删掉。
     */
    private fun saveEditedBitmapToOutput(outputPath: String) {
        exportJob?.cancel()
        val bitmap = exportEditedBitmap()
        if (bitmap == null) {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch(Dispatchers.IO) {
            val file = java.io.File(outputPath)
            val written = runCatching {
                file.parentFile?.mkdirs()
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                file.length() > 0L
            }.onFailure {
                android.util.Log.w("SlideIndexImageEditor", "write edited image failed: $outputPath", it)
            }.getOrDefault(false)
            android.util.Log.i(
                com.slideindex.app.service.StashEditImageTrampolineActivity.LOG_TAG,
                "editor save→output outputPath=$outputPath 写入成功=$written 字节=${file.length()}",
            )
            withContext(Dispatchers.Main) {
                if (!written) {
                    runCatching { file.delete() }
                    toast(R.string.inspire_image_edit_export_failed)
                    return@withContext
                }
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun exportAndSave(deleteAfterMinutes: Int) {
        exportJob?.cancel()
        val bitmap = exportEditedBitmap()
        if (bitmap == null) {
            toast(R.string.inspire_image_edit_export_failed)
            return
        }
        exportJob = lifecycleScope.launch(Dispatchers.IO) {
            val uri = FloatBallTextPick.saveScreenshotReturningUri(
                this@SlideIndexImageEditorActivity,
                bitmap,
                ingestToHistory = (deleteAfterMinutes == 0),
            )
            withContext(Dispatchers.Main) {
                if (uri == null) {
                    toast(R.string.inspire_image_edit_export_failed)
                    return@withContext
                }
                if (deleteAfterMinutes > 0) {
                    ImageEditorSavedImageDeleteScheduler.scheduleDeleteAfterMinutes(
                        applicationContext,
                        uri,
                        deleteAfterMinutes.toLong(),
                    )
                    Toast.makeText(
                        applicationContext,
                        R.string.inspire_image_edit_save_auto_delete,
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        applicationContext,
                        R.string.inspire_image_edit_save_persist,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                finish()
            }
        }
    }

    private fun showTextEditor(anchor: EditorPoint, suggestedSize: Float, existing: EditAction.Text?) {
        val sheet = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(20)
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            setText(R.string.inspire_image_edit_text_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        val scope = TextView(this).apply {
            setText(R.string.inspire_image_edit_text_scope)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant),
            )
        }
        val input = EditText(this).apply {
            setText(existing?.text.orEmpty())
            hint = getString(R.string.inspire_image_edit_text_hint)
            setSelection(text?.length ?: 0)
        }
        val confirm = MaterialButton(this).apply {
            setText(android.R.string.ok)
        }
        container.addView(title)
        container.addView(scope)
        container.addView(input)
        container.addView(confirm)
        sheet.setContentView(container)
        sheet.setOnShowListener { input.requestFocus() }
        confirm.setOnClickListener {
            val value = input.text?.toString()?.trim().orEmpty()
            if (value.isEmpty()) {
                existing?.id?.let { editorSession.removeAction(it) }
            } else if (existing != null) {
                editorSession.updateTextAction(existing.id) { it.copy(text = value) }
            } else {
                editorSession.addAction(
                    EditAction.Text(
                        id = System.nanoTime().toString(),
                        text = value,
                        anchor = anchor,
                        color = editorSession.state.currentColor,
                        textSize = suggestedSize,
                    ),
                )
            }
            sheet.dismiss()
        }
        sheet.show()
    }

    private suspend fun restoreLastUsedEditorPreferences() {
        val mode = prefs.getString(KEY_LAST_MODE, null)?.let { runCatching { EditorMode.valueOf(it) }.getOrNull() }
        val color = prefs.getInt(KEY_LAST_COLOR, Color.RED)
        val shape = prefs.getString(KEY_LAST_SHAPE, null)?.let { runCatching { ShapeType.valueOf(it) }.getOrNull() }
        val doodle = prefs.getFloat(KEY_DOODLE_STROKE, EditorUiState.defaults().doodleStrokeWidth)
        val eraser = prefs.getFloat(KEY_ERASER_STROKE, EditorUiState.defaults().eraserStrokeWidth)
        val shapeStroke = prefs.getFloat(KEY_SHAPE_STROKE, EditorUiState.defaults().shapeStrokeWidth)
        val mosaic = prefs.getFloat(KEY_MOSAIC_STROKE, EditorUiState.defaults().mosaicStrokeWidth)

        editorSession.updateStrokeWidthForMode(EditorMode.DOODLE, doodle)
        editorSession.updateStrokeWidthForMode(EditorMode.ERASER, eraser)
        editorSession.updateStrokeWidthForMode(EditorMode.SHAPE, shapeStroke)
        editorSession.updateStrokeWidthForMode(EditorMode.MOSAIC, mosaic)
        editorSession.updateColor(color)
        shape?.let { editorSession.updateShapeType(it) }
        mode?.let { editorSession.switchMode(it) }

        lastPersistedMode = editorSession.state.currentMode
        lastPersistedColor = editorSession.state.currentColor
        lastPersistedShapeType = editorSession.state.currentShapeType
    }

    private fun persistLastUsedEditorPreferencesIfChanged(state: EditorUiState) {
        val modeChanged = lastPersistedMode != state.currentMode
        val colorChanged = lastPersistedColor != state.currentColor
        val shapeChanged = lastPersistedShapeType != state.currentShapeType
        if (!modeChanged && !colorChanged && !shapeChanged) return

        lastPersistedMode = state.currentMode
        lastPersistedColor = state.currentColor
        lastPersistedShapeType = state.currentShapeType

        prefs.edit()
            .putString(KEY_LAST_MODE, state.currentMode.name)
            .putInt(KEY_LAST_COLOR, state.currentColor)
            .putString(KEY_LAST_SHAPE, state.currentShapeType.name)
            .putFloat(KEY_DOODLE_STROKE, state.doodleStrokeWidth)
            .putFloat(KEY_ERASER_STROKE, state.eraserStrokeWidth)
            .putFloat(KEY_SHAPE_STROKE, state.shapeStrokeWidth)
            .putFloat(KEY_MOSAIC_STROKE, state.mosaicStrokeWidth)
            .apply()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun dp(value: Float): Float = EditorViewMetrics.dp(this, value)

    private fun dpToPx(value: Int): Int = dp(value.toFloat()).roundToInt()

    companion object {
        const val EXTRA_IMAGE_CACHE_PATH = "extra_image_cache_path"

        /**
         * 「保存的结果写到这个文件，然后 `RESULT_OK` 返回」（§0.16.22）。
         *
         * 只有 [resultIntent] 会传它；不传 = 老行为（保存进相册 / 自动删除）。
         */
        const val EXTRA_RESULT_OUTPUT_PATH = "extra_image_editor_result_output_path"

        private const val PREFS_NAME = "slide_index_image_editor"
        private const val KEY_LAST_MODE = "inspire_image_editor_last_mode"
        private const val KEY_LAST_COLOR = "inspire_image_editor_last_color"
        private const val KEY_LAST_SHAPE = "inspire_image_editor_last_shape_type"
        private const val KEY_DOODLE_STROKE = "inspire_image_editor_doodle_stroke"
        private const val KEY_ERASER_STROKE = "inspire_image_editor_eraser_stroke"
        private const val KEY_SHAPE_STROKE = "inspire_image_editor_shape_stroke"
        private const val KEY_MOSAIC_STROKE = "inspire_image_editor_mosaic_stroke"

        private const val BRUSH_SIZE_MIN = 6f
        private const val BRUSH_SIZE_MAX = 120f
        private const val BAR_ENTER_DELAY_MS = 0L
        private const val BAR_ENTER_DURATION_MS = 240L
        private const val IMAGE_ENTER_DURATION_MS = 340L

        private val IMAGE_ENTER_INTERPOLATOR = PathInterpolator(0.16f, 0f, 0f, 1f)
        private val BAR_ENTER_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)

        /** [imageCachePath] 为调用方落盘的图片路径（跨进程必传：调用方在 :overlay，静态缓存读不到）。 */
        fun launch(context: Context, imageCachePath: String? = null) {
            context.startActivity(Intent(context, SlideIndexImageEditorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                imageCachePath?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_IMAGE_CACHE_PATH, it) }
            })
        }

        /**
         * 给**中转 Activity** 用的启动 Intent（§0.16.22）：多传一个 [resultOutputPath]。
         *
         * 多出来的这一个 extra 会把「保存」改成 **"保存到指定文件并 `RESULT_OK` 返回"**
         * （见 [saveEditedBitmapToOutput]）—— 闪念编辑浮窗里的图片块要的就是这个：
         * 点 ✎ 进编辑器、保存后**换掉那一块的那张图**，而不是像普通人那样"存进相册再 finish"。
         *
         * ⚠️ §0.16.23 回归修复：这里**只造 Intent，不自己 `startActivity`**。
         * 调用方（`StashEditImageTrampolineActivity`）必须用它的 `registerForActivityResult` launcher
         * 去 launch —— 原来这个方法内部直接 `context.startActivity(...)`，`requestCode` 是 -1，
         * 编辑器的 `RESULT_OK` 永远回不到中转 Activity，结果就是"涂鸦保存了但图片没换 +
         * 面板挂起不恢复"。也**不能**加 `FLAG_ACTIVITY_NEW_TASK`：那会把编辑器放进另一个任务，
         * 结果同样回不来。
         *
         * 为什么不让调用方直接读 [ImageEditorLaunchCache] 里的 bitmap：那个静态缓存是给
         * "取词面板长按关闭"那条同进程短路径用的，普通关闭 / 保存都不会把结果放回去。
         */
        fun resultIntent(context: Context, imageCachePath: String?, resultOutputPath: String): Intent =
            Intent(context, SlideIndexImageEditorActivity::class.java).apply {
                imageCachePath?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_IMAGE_CACHE_PATH, it) }
                putExtra(EXTRA_RESULT_OUTPUT_PATH, resultOutputPath)
            }
    }
}
