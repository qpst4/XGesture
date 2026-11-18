package com.slideindex.app.service



import android.content.Context

import android.content.Intent

import android.graphics.Color as AndroidColor

import android.os.Bundle

import androidx.activity.ComponentActivity

import androidx.activity.SystemBarStyle

import androidx.activity.compose.BackHandler

import androidx.activity.compose.setContent
import com.slideindex.app.ui.miuix.MiuixTopLayerRoot

import androidx.activity.enableEdgeToEdge

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import com.slideindex.app.di.AppDependencies

import com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.settings.FvRingLauncherAxis

import com.slideindex.app.settings.FvRingLauncherSlotIconOverride

import com.slideindex.app.settings.fvRingLauncherFor

import com.slideindex.app.ui.ringlauncher.RingLauncherSlotIconEditorHost

import com.slideindex.app.ui.compose.LocalAppDependencies

import com.slideindex.app.ui.miuix.theme.ModuleTheme

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.launch



@AndroidEntryPoint

class RingLauncherSlotIconEditorTrampolineActivity : ComponentActivity() {



    @Inject lateinit var deps: AppDependencies



    private var dismissed = false

    private var slotIndex = -1



    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge(

            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),

            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),

        )

        super.onCreate(savedInstanceState)

        if (savedInstanceState?.getBoolean(STATE_DISMISSED, false) == true) {

            finish()

            return

        }



        val axis = intent.getStringExtra(EXTRA_AXIS)?.let(::axisFromName)

        slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)

        if (axis == null || slotIndex < 0) {

            finish()

            return

        }



        @Suppress("DEPRECATION")

        overridePendingTransition(0, 0)



        setContent {
            MiuixTopLayerRoot {

                val scope = rememberCoroutineScope()

                var appSettings by remember { mutableStateOf(AppSettings()) }

                var slotLabel by remember { mutableStateOf("") }

                var slotIconOverride by remember { mutableStateOf<FvRingLauncherSlotIconOverride?>(null) }



                LaunchedEffect(axis, slotIndex) {

                    appSettings = deps.settingsRepository.settings.first()

                    val fvSettings = appSettings.fvRingLauncherFor(axis)

                    val item = fvSettings.itemAt(slotIndex)

                    slotIconOverride = fvSettings.iconOverrideAt(slotIndex)

                    slotLabel = item?.label?.ifBlank { item.payload }.orEmpty()

                }



                BackHandler { finishPicker() }



                CompositionLocalProvider(LocalAppDependencies provides deps) {

                    ModuleTheme(settings = appSettings) {

                        RingLauncherSlotIconEditorHost(

                            slotLabel = slotLabel,

                            initialOverride = slotIconOverride,

                            onBack = { finishPicker() },

                            onSave = { override ->

                                scope.launch {

                                    deps.settingsRepository.setFvRingLauncherSlotIconOverride(

                                        axis = axis,

                                        index = slotIndex,

                                        override = override,

                                    )

                                    RingLauncherOverlayWindow.refreshFromSettings()

                                    finishPicker()

                                }

                            },

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

        RingLauncherOverlayWindow.resumeAfterSlotIconEditor()
        // 本 Activity 跑在主进程，而圆环/悬浮球宿主在 :overlay —— 上面那次调用在主进程其实是空操作，
        // 必须跨进程通知 overlay 进程恢复（否则返回后圆环与悬浮球都不见了）。
        com.slideindex.app.overlay.OverlayStatePort.sendCommand(
            applicationContext,
            com.slideindex.app.overlay.OverlayStatePort.COMMAND_RESUME_RING_LAUNCHER_OVERLAY,
        )

    }



    override fun onSaveInstanceState(outState: Bundle) {

        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_DISMISSED, dismissed)

    }



    companion object {

        private const val EXTRA_AXIS = "axis"

        private const val EXTRA_SLOT_INDEX = "slot_index"

        private const val STATE_DISMISSED = "dismissed"



        private const val AXIS_VERTICAL = "vertical"

        private const val AXIS_HORIZONTAL = "horizontal"



        fun createIntent(

            context: Context,

            axis: FvRingLauncherAxis,

            slotIndex: Int,

        ): Intent =

            Intent(context, RingLauncherSlotIconEditorTrampolineActivity::class.java).apply {

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                putExtra(EXTRA_AXIS, axisToName(axis))

                putExtra(EXTRA_SLOT_INDEX, slotIndex)

            }



        private fun axisToName(axis: FvRingLauncherAxis): String = when (axis) {

            FvRingLauncherAxis.VERTICAL -> AXIS_VERTICAL

            FvRingLauncherAxis.HORIZONTAL -> AXIS_HORIZONTAL

        }



        private fun axisFromName(name: String): FvRingLauncherAxis? = when (name) {

            AXIS_VERTICAL -> FvRingLauncherAxis.VERTICAL

            AXIS_HORIZONTAL -> FvRingLauncherAxis.HORIZONTAL

            else -> null

        }

    }

}


