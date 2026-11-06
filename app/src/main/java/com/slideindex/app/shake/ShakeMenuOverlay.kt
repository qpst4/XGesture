package com.slideindex.app.shake

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.di.AppGraphEntryPoint
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.gestureActionSettingSubtitle
import com.slideindex.app.ui.shakeGestureIcon
import com.slideindex.app.ui.shakeGestureLabel
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import dagger.hilt.android.EntryPointAccessors

/**
 * 「晃动菜单」浮层。
 *
 * 只做展示、不接收触摸（`isClickable = false`），所以不会挡住下面的应用；
 * 用户看到方向表后再晃一下手机，由 [ShakeGestureHost] 决定执行哪个动作。
 */
object ShakeMenuOverlay {
    private const val TAG = "ShakeMenuOverlay"
    private const val ATTACH_RETRY_MS = 30_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var composeOwner: OverlayComposeOwner? = null
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var attachFailedAt = 0L

    fun show(context: Context, menu: ShakeMenu) {
        mainHandler.post {
            if (!ensureAttached(context)) return@post
            Log.i(
                TAG,
                "show menu=${menu.id} name=${menu.name} directions=${menu.configuredDirections}",
            )
            composeView?.setContent {
                // OverlayAwareModuleTheme 会读 LocalAppDependencies（主题色等），悬浮窗的
                // Compose 环境里没有 MainActivity 那层 Provider，必须自己补上，
                // 否则组合期直接抛 "AppDependencies not provided"。
                val appDeps = remember {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        AppGraphEntryPoint::class.java,
                    ).dependencies()
                }
                CompositionLocalProvider(LocalAppDependencies provides appDeps) {
                    OverlayAwareModuleTheme {
                        ShakeMenuPanel(menu)
                    }
                }
            }
            bringToFront()
        }
    }

    fun detach() {
        mainHandler.post {
            val wm = windowManager
            val view = composeView
            if (wm != null && view != null) {
                Log.d(TAG, "detach")
                runCatching { wm.removeView(view) }
            }
            OverlayCompose.teardownOverlayCompose(view, composeOwner)
            composeOwner = null
            composeView = null
            windowManager = null
            attachFailedAt = 0L
        }
    }

    private fun bringToFront() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        runCatching {
            wm.removeView(view)
            wm.addView(view, params)
        }.onFailure { error ->
            Log.w(TAG, "bringToFront failed", error)
        }
    }

    private fun ensureAttached(context: Context): Boolean {
        if (composeView != null) return true

        val now = System.currentTimeMillis()
        if (attachFailedAt > 0L && now - attachFailedAt < ATTACH_RETRY_MS) {
            return false
        }
        attachFailedAt = 0L

        val hostContext = SlideIndexAccessibilityService.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                attachFailedAt = now
                return false
            }
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return false
        val params = OverlayWindowTypes.createPresentationParams(hostContext).apply {
            OverlayWindowTypes.applyFullScreen(this)
        }
        val owner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, owner).apply {
            isClickable = false
            isFocusable = false
        }

        val attached = runCatching { wm.addView(view, params) }
            .onFailure { error ->
                attachFailedAt = System.currentTimeMillis()
                Log.e(TAG, "addView failed", error)
            }
            .isSuccess
        if (!attached) {
            owner.destroy()
            return false
        }

        composeOwner = owner
        composeView = view
        windowManager = wm
        return true
    }

    @Composable
    private fun ShakeMenuPanel(menu: ShakeMenu) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 88.dp, start = 24.dp, end = 24.dp)
                    .widthIn(min = 280.dp, max = 360.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF2323232),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = menu.name.ifBlank {
                            stringResource(R.string.shake_menu_untitled)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.shake_menu_overlay_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    ShakeGestureType.entries.forEach { type ->
                        MenuDirectionRow(
                            type = type,
                            action = menu.actionFor(type),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuDirectionRow(type: ShakeGestureType, action: GestureAction) {
        val bound = action != GestureAction.None
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        color = if (bound) Color(0xFF2E6BE6) else Color.White.copy(alpha = 0.12f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = shakeGestureIcon(type),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (bound) 1f else 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = shakeGestureLabel(type),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = if (bound) 0.95f else 0.55f),
                maxLines = 1,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = gestureActionSettingSubtitle(action),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = if (bound) 0.92f else 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (bound) {
                Icon(
                    imageVector = gestureActionIcon(action),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
