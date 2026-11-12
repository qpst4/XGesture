package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddFlowScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

/**
 * 「向快速启动器面板添加条目」的透明 Activity 壳。
 *
 * 页面本身与设置入口共用 `QuickLauncherAddFlowScreen`（Miuix 设置页 + 同一套子页），
 * 这里只负责：读 panelId、提供主题、关闭时通知浮层恢复。
 */
@dagger.hilt.android.AndroidEntryPoint
class QuickLauncherAddTrampolineActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var deps: AppDependencies

    private val viewModel: ExtensionSettingsViewModel by viewModels()

    private var dismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(STATE_DISMISSED, false) == true) {
            finish()
            return
        }
        val panelId = intent.getStringExtra(EXTRA_PANEL_ID).orEmpty()
        if (panelId.isBlank()) {
            finishPicker()
            return
        }

        setContent {
            var appSettings by remember { mutableStateOf(deps.settingsRepository.readSnapshot()) }
            LaunchedEffect(Unit) {
                deps.settingsRepository.settings.collect { appSettings = it }
            }
            CompositionLocalProvider(LocalAppDependencies provides deps) {
                ModuleTheme(settings = appSettings) {
                    QuickLauncherAddFlowScreen(
                        panelId = panelId,
                        viewModel = viewModel,
                        onExit = { finishPicker() },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISMISSED, dismissed)
    }

    override fun onDestroy() {
        if (!dismissed) {
            QuickLauncherAddTrampoline.deliverDismiss()
        }
        super.onDestroy()
    }

    private fun finishPicker() {
        if (dismissed) return
        dismissed = true
        QuickLauncherAddTrampoline.deliverDismiss()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_PANEL_ID = "panel_id"
        private const val EXTRA_PANEL_SIDE = "panel_side"
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(
            context: Context,
            panelId: String,
            panelSide: PanelSide,
        ): Intent =
            Intent(context, QuickLauncherAddTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PANEL_ID, panelId)
                putExtra(EXTRA_PANEL_SIDE, panelSide.name)
            }
    }
}
