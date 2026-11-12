package com.slideindex.app.overlay.screenshot

import com.slideindex.app.overlay.overlayHostContext

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.overlay.buildScreenshotLayoutMeta
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt

@SuppressLint("StaticFieldLeak")
object SmartScreenshotOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dialogHost: OverlayComposeDialogHost? = null
    private val state = SmartScreenshotState()

    val isShowing: Boolean get() = dialogHost != null && state.visible

    fun show(context: Context, screenshot: Bitmap) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, screenshot) }
            return
        }

        if (!PermissionHelper.canDrawOverlays(context)) {
            context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            return
        }

        val hostContext = overlayHostContext() ?: context.applicationContext
        val minSelectionPx = (48 * hostContext.resources.displayMetrics.density).toInt()
        state.show(screenshot, minSelectionPx)

        val host = dialogHost ?: OverlayComposeDialogHost(
            context = hostContext,
            fullScreen = true
        ).also { dialogHost = it }

        host.show(
            onBackPressed = {
                dismiss()
                true
            }
        ) {
            OverlayAwareModuleTheme {
                SmartScreenshotEditor(
                    bitmap = screenshot,
                    state = state,
                    onCancel = { dismiss() },
                    onSave = {
                        val cropped = ScreenshotCropper.crop(screenshot, state.selectionRect, state.shape, state.freeformPath)
                        val uri = ScreenshotStorage.saveToGallery(hostContext, cropped)
                        if (uri != null) {
                            Toast.makeText(hostContext, R.string.smart_screenshot_saved, Toast.LENGTH_SHORT).show()
                        }
                        dismiss()
                    },
                    onCopy = {
                        val cropped = ScreenshotCropper.crop(screenshot, state.selectionRect, state.shape, state.freeformPath)
                        val uri = ScreenshotStorage.createClipboardUri(hostContext, cropped)
                        if (uri != null) {
                            ScreenshotStorage.copyToClipboard(hostContext, uri)
                            Toast.makeText(hostContext, R.string.smart_screenshot_copied, Toast.LENGTH_SHORT).show()
                        }
                        dismiss()
                    },
                    onShare = {
                        val cropped = ScreenshotCropper.crop(screenshot, state.selectionRect, state.shape, state.freeformPath)
                        val uri = ScreenshotStorage.createShareUri(hostContext, cropped)
                        if (uri != null) {
                            ScreenshotStorage.share(hostContext, uri)
                        }
                        dismiss()
                    },
                    onPin = {
                        val cropped = ScreenshotCropper.crop(screenshot, state.selectionRect, state.shape, state.freeformPath)
                        val rect = android.graphics.Rect(
                            state.selectionRect.left.roundToInt(),
                            state.selectionRect.top.roundToInt(),
                            state.selectionRect.right.roundToInt(),
                            state.selectionRect.bottom.roundToInt()
                        )
                        val layoutMeta = buildScreenshotLayoutMeta(
                            bitmap = screenshot,
                            screenWidthPx = hostContext.resources.displayMetrics.widthPixels,
                            screenHeightPx = hostContext.resources.displayMetrics.heightPixels
                        )
                        com.slideindex.app.overlay.panels.PanelStashWriteHooks.pinImage
            ?.invoke(hostContext, cropped, rect, layoutMeta)
                        dismiss()
                    },
                    onWordSegmentation = {
                        val cropped = ScreenshotCropper.crop(screenshot, state.selectionRect, state.shape, state.freeformPath)
                        val rect = android.graphics.Rect(
                            state.selectionRect.left.roundToInt(),
                            state.selectionRect.top.roundToInt(),
                            state.selectionRect.right.roundToInt(),
                            state.selectionRect.bottom.roundToInt()
                        )
                        val layoutMeta = buildScreenshotLayoutMeta(
                            bitmap = screenshot,
                            screenWidthPx = hostContext.resources.displayMetrics.widthPixels,
                            screenHeightPx = hostContext.resources.displayMetrics.heightPixels
                        )
                        SmartScreenshotOcr.pickTextFromBitmap(hostContext, cropped, screenRect = rect, layoutMeta = layoutMeta)
                        dismiss()
                    },
                    onEdit = {
                        val cropped = ScreenshotCropper.crop(screenshot, state.selectionRect, state.shape, state.freeformPath)
                        val rect = android.graphics.Rect(
                            state.selectionRect.left.roundToInt(),
                            state.selectionRect.top.roundToInt(),
                            state.selectionRect.right.roundToInt(),
                            state.selectionRect.bottom.roundToInt()
                        )
                        val layoutMeta = buildScreenshotLayoutMeta(
                            bitmap = screenshot,
                            screenWidthPx = hostContext.resources.displayMetrics.widthPixels,
                            screenHeightPx = hostContext.resources.displayMetrics.heightPixels
                        )
                        val settings = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsSnapshot
                            ?.invoke(hostContext)
                        val targetPackage = settings?.defaultImageViewerPackage
                        val opened = com.slideindex.app.overlay.panels.PanelRuntimeHooks.viewScreenshot
                            ?.invoke(hostContext, cropped, targetPackage, rect) ?: false
                        if (opened) {
                            dismiss()
                        }
                    }
                )
            }
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        state.dismiss()
        dialogHost?.dismiss()
        dialogHost = null
    }
}
