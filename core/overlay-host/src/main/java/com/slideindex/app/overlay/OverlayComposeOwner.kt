package com.slideindex.app.overlay

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.ConcurrentHashMap

class OverlayComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

object OverlayCompose {
    private const val TAG = "OverlayCompose"
    private val windowContextCache = ConcurrentHashMap<String, Context>()

    fun clearWindowContextCache() {
        windowContextCache.clear()
    }

    /**
     * 为浮层 Compose 提供带主题的 UI Context。
     * [ContextThemeWrapper] 直接包 [android.accessibilityservice.AccessibilityService] 时，
     * androidx.window 会在 unwrap 后判定为非 UI Context 并崩溃；需先通过 [Context.createWindowContext] 转换。
     */
    fun themedContext(context: Context): Context {
        val uiContext = resolveUiContext(unwrapThemeContext(context))
        val localizedContext = OverlayHostRuntime.wrapOverlayLocale?.invoke(uiContext) ?: uiContext
        val themeRes = OverlayHostRuntime.overlayThemeResId
            .takeIf { it != 0 }
            ?: android.R.style.Theme_Translucent_NoTitleBar
        return ContextThemeWrapper(localizedContext, themeRes)
    }

    private fun unwrapThemeContext(context: Context): Context {
        var current = context
        while (current is ContextThemeWrapper) {
            current = current.baseContext
        }
        return current
    }

    private fun resolveUiContext(context: Context): Context {
        if (context is Activity || context is InputMethodService || isWindowContext(context)) {
            return context
        }
        val display = resolveDisplay(context) ?: run {
            Log.w(TAG, "resolveUiContext: no display for ${context.javaClass.name}")
            return context.applicationContext
        }
        val windowTypes = listOf(
            OverlayWindowTypes.overlayWindowType(unwrapThemeContext(context)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        ).distinct()
        for (windowType in windowTypes) {
            cachedWindowContext(context, display, windowType)?.let { return it }
            val appContext = context.applicationContext
            if (appContext !== context) {
                cachedWindowContext(appContext, display, windowType)?.let { return it }
            }
        }
        val appContext = context.applicationContext
        cachedWindowContext(
            appContext,
            display,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        )?.let { return it }
        Log.e(TAG, "resolveUiContext: all createWindowContext attempts failed for ${context.javaClass.name}")
        return context.applicationContext
    }

    private fun cachedWindowContext(
        context: Context,
        display: Display,
        windowType: Int,
    ): Context? {
        val key = "${display.displayId}:$windowType"
        windowContextCache[key]?.let { return it }
        val created = createWindowContextOrNull(context, display, windowType) ?: return null
        windowContextCache[key] = created
        return created
    }

    private fun createWindowContextOrNull(
        context: Context,
        display: Display,
        windowType: Int
    ): Context? = runCatching {
        context.createWindowContext(display, windowType, null)
    }.onFailure { error ->
        Log.w(TAG, "createWindowContext failed for ${context.javaClass.name} type=$windowType", error)
    }.getOrNull()

    /**
     * Android 16+ 对非视觉 Context 调用 [Context.getDisplay] 会直接抛异常；
     * 统一通过 [DisplayManager] 回退，避免 Flyme/OPPO 等设备启动即崩。
     */
    private fun resolveDisplay(context: Context): Display? {
        if (context is Activity || context is InputMethodService || isWindowContext(context)) {
            runCatching { return context.display }.onFailure {
                Log.w(TAG, "getDisplay failed for ${context.javaClass.name}", it)
            }
        }
        val displayManager = context.applicationContext
            .getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    /** [android.window.WindowContext] 为 @hide，用类名判断避免重复创建。 */
    private fun isWindowContext(context: Context): Boolean =
        context.javaClass.name == "android.window.WindowContext"

    fun bindOwners(view: View, owner: OverlayComposeOwner) {
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
    }

    fun clearViewTreeOwners(view: View) {
        view.setViewTreeLifecycleOwner(null)
        view.setViewTreeSavedStateRegistryOwner(null)
        view.setViewTreeViewModelStoreOwner(null)
    }

    fun createComposeView(context: Context, owner: OverlayComposeOwner): ComposeView {
        return ComposeView(themedContext(context)).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            bindOwners(this, owner)
            // Overlays manage their own owner lifecycle; dispose on window detach instead of
            // ON_DESTROY to avoid races when removeView and destroy() happen close together.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
    }

    fun disposeComposeView(view: ComposeView?) {
        if (view == null) return
        runCatching { view.disposeComposition() }
    }

    /**
     * 浮层拆除时释放 [OverlayComposeOwner]。
     * 等 [ComposeView] 从窗口 detach 后再 destroy owner，避免 layout 阶段
     * `ViewTreeLifecycleOwner not found`；composition 由 [DisposeOnDetachedFromWindow] 释放。
     */
    fun teardownOverlayCompose(composeView: ComposeView?, owner: OverlayComposeOwner?) {
        val view = composeView
        val dialogOwner = owner
        if (view == null) {
            dialogOwner?.destroy()
            return
        }
        if (!view.isAttachedToWindow) {
            dialogOwner?.destroy()
            return
        }
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    view.removeOnAttachStateChangeListener(this)
                    dialogOwner?.destroy()
                }
            }
        )
    }
}
