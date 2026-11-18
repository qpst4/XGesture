package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import com.slideindex.app.ui.miuix.MiuixTopLayerRoot
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.corner.CornerGestureHost
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import com.slideindex.app.ui.CornerGestureSlotEditorHost
import com.slideindex.app.ui.compose.LocalAppDependencies
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class CornerGestureSlotPickTrampolineActivity : ComponentActivity() {

    @Inject lateinit var deps: AppDependencies

    private var dismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(STATE_DISMISSED, false) == true) {
            finish()
            return
        }

        val corner = intent.getStringExtra(EXTRA_CORNER).orEmpty()
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
        if (slotIndex !in 0 until CornerRadialMenuCodec.SLOT_COUNT) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val cornerTitleRes = if (corner != CORNER_RIGHT) {
            com.slideindex.app.R.string.corner_gesture_slot_corner_left
        } else {
            com.slideindex.app.R.string.corner_gesture_slot_corner_right
        }

        setContent {
            MiuixTopLayerRoot {
                var appSettings by remember { mutableStateOf(AppSettings()) }
                LaunchedEffect(corner, slotIndex) {
                    appSettings = deps.settingsRepository.settings.first()
                }

                CompositionLocalProvider(LocalAppDependencies provides deps) {
                    com.slideindex.app.ui.miuix.theme.ModuleTheme(settings = appSettings) {
                        CornerGestureSlotEditorHost(
                            corner = corner,
                            slotIndex = slotIndex,
                            cornerTitle = getString(cornerTitleRes),
                            onExit = { finishPicker() },
                            settingsRepository = deps.settingsRepository
                        )
                    }
                }
            }
        }
    }

    private fun finishPicker() {
        if (dismissed) return
        dismissed = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        CornerGestureHost.resumeAfterSlotPicker()
        // 本 Activity 跑在主进程，而轮盘宿主在 :overlay —— 上面那次调用在本进程其实是空操作，
        // 必须再跨进程通知 overlay 进程恢复可视层，否则轮盘会"看不见但震动还在"。
        com.slideindex.app.overlay.OverlayStatePort.sendCommand(
            applicationContext,
            com.slideindex.app.overlay.OverlayStatePort.COMMAND_RESUME_CORNER_OVERLAY,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISMISSED, dismissed)
    }

    companion object {
        const val CORNER_LEFT = "left"
        const val CORNER_RIGHT = "right"

        private const val EXTRA_CORNER = "corner"
        private const val EXTRA_SLOT_INDEX = "slot_index"
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(context: Context, corner: String, slotIndex: Int): Intent =
            Intent(context, CornerGestureSlotPickTrampolineActivity::class.java).apply {
                putExtra(EXTRA_CORNER, corner)
                putExtra(EXTRA_SLOT_INDEX, slotIndex)
            }
    }
}
