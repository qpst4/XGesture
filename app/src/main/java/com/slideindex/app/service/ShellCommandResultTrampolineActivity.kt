package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.ShellCommandResultOverlaySheet
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import kotlinx.coroutines.flow.first

@dagger.hilt.android.AndroidEntryPoint
class ShellCommandResultTrampolineActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var deps: AppDependencies

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

        val result = ShellCommandResultTrampoline.consumePayload() ?: run {
            finish()
            return
        }

        ShellCommandResultTrampoline.registerActivityFinisher { finishPicker() }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        setContent {
            var appSettings by remember { mutableStateOf(AppSettings()) }
            var dismissRequest by remember { mutableStateOf<(() -> Unit)?>(null) }

            LaunchedEffect(Unit) {
                appSettings = deps.settingsRepository.settings.first()
            }

            BackHandler(enabled = dismissRequest != null) {
                dismissRequest?.invoke()
            }

            ModuleTheme(settings = appSettings) {
                ShellCommandResultOverlaySheet(
                    label = result.label,
                    command = result.command,
                    exitCode = result.exitCode,
                    output = result.output,
                    onDismissComplete = { finishPicker() },
                    onCopy = { copyOutput(result.output) },
                    onWindowReady = { ShellCommandResultTrampoline.runPrepareIfNeeded() },
                    registerBackHandler = { dismissRequest = it }
                )
            }
        }
    }

    /**
     * 临时浮窗语义：离开前台即关闭。
     *
     * [ShellCommandResultTrampoline.active] 会挡住边缘浮层恢复（`OverlayTrampolineGuard`），
     * 而它只在 `onDestroy` 复位。以前只有面板 Activity 里打开结果页，面板转 stopped 时会被
     * `ShellCommandPanelTrampolineActivity.onStop` 收掉；现在手势 / 快速启动器 / 应用内直达也会
     * 拉起结果页，从别的 App 触发后按 Home 时本 Activity 只会 stopped、不会 destroy，
     * 边缘触钮就永远恢复不了。这里补同一条兜底。
     */
    override fun onStop() {
        super.onStop()
        // 配置变更（如旋转）会重建 Activity，不能在这里收掉。
        if (!isChangingConfigurations) finishPicker()
    }

    override fun onDestroy() {
        ShellCommandResultTrampoline.unregisterActivityFinisher()
        ShellCommandResultTrampoline.deliverDismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISMISSED, dismissed)
    }

    private fun copyOutput(output: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", output))
        Toast.makeText(this, R.string.shell_panel_copied, Toast.LENGTH_SHORT).show()
    }

    private fun finishPicker() {
        if (dismissed) return
        dismissed = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(context: Context): Intent =
            Intent(context, ShellCommandResultTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
