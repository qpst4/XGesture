package com.slideindex.app.overlay.searchpanel

import android.graphics.Bitmap
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/** Persists the last text query across search panel dismiss / recreate cycles. */
object SearchPanelSessionState {
    var lastTextQuery: String = ""

    /**
     * 外部（拖拽动作 / 系统分享）塞进来、等面板消费的图片。
     *
     * 面板窗口是预热保活的，组合不会重建，所以这里必须是可观察状态：
     * 面板里用 `LaunchedEffect` 收，不能靠 `remember` 初值——那只在第一次组合时执行。
     */
    private val pendingImageState = mutableStateOf<Bitmap?>(null)

    val pendingImage: State<Bitmap?> get() = pendingImageState

    fun pushImage(bitmap: Bitmap?) {
        pendingImageState.value = bitmap
    }

    fun consumePendingImage(): Bitmap? = pendingImageState.value.also { pendingImageState.value = null }

    var persistBeforeDismiss: (() -> Unit)? = null
    /** Return true if back was consumed (e.g. dismiss file preview only). */
    var onBackPressed: (() -> Boolean)? = null
    /**
     * 窗口获得焦点时由浮层窗口调用：把焦点交给搜索框（返回是否成功，输入框可能还没进入组合）。
     *
     * 走这条回调是为了绕开 Compose 重组延迟，尽早把焦点就绪，好让输入法更早被拉起。
     */
    var focusSearchTextField: (() -> Boolean)? = null
}
