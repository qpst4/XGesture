package com.slideindex.app.overlay

import android.graphics.Rect
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Reads IME bottom inset from the overlay [View] tree. Compose [WindowInsets.ime] is often zero
 * on TYPE_APPLICATION_OVERLAY windows, so panel layouts should use this helper instead.
 */
@Composable
fun rememberOverlayImeBottomHeight(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var imeBottomPx by remember { mutableIntStateOf(0) }

    DisposableEffect(view) {
        fun readImeBottom(): Int {
            val insets = ViewCompat.getRootWindowInsets(view)
            val insetBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            if (insetBottom > 0) return insetBottom

            val visibleFrame = Rect()
            view.getWindowVisibleDisplayFrame(visibleFrame)
            val rootHeight = view.rootView.height
            if (rootHeight <= 0) return 0
            return (rootHeight - visibleFrame.bottom).coerceAtLeast(0)
        }

        fun updateImeBottom() {
            val updated = readImeBottom()
            if (updated != imeBottomPx) {
                imeBottomPx = updated
            }
        }

        updateImeBottom()

        val insetsListener = OnApplyWindowInsetsListener { _, insets ->
            updateImeBottom()
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, insetsListener)
        ViewCompat.requestApplyInsets(view)

        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateImeBottom()
            }
        view.addOnLayoutChangeListener(layoutListener)

        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            view.removeOnLayoutChangeListener(layoutListener)
        }
    }

    return with(density) { imeBottomPx.toDp() }
}
