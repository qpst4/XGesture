package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.ShellCommandPanelOverlaySheet
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@dagger.hilt.android.AndroidEntryPoint
class ShellCommandPanelTrampolineActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var deps: AppDependencies

    private var dismissed = false
    private val shizukuGrantedState = mutableStateOf(false)

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        shizukuGrantedState.value = grantResult == PackageManager.PERMISSION_GRANTED
        if (shizukuGrantedState.value) {
            TaskManagerUtil.warmUpPrivilegedBackend()
        }
    }

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

        ShellCommandPanelTrampoline.registerActivityFinisher { finishPicker() }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        shizukuGrantedState.value = TaskManagerUtil.hasPermission()

        setContent {
            var commands by remember { mutableStateOf<List<ShellCommand>>(emptyList()) }
            val shizukuGranted by shizukuGrantedState
            var appSettings by remember { mutableStateOf(AppSettings()) }
            var dismissRequest by remember { mutableStateOf<(() -> Unit)?>(null) }

            LaunchedEffect(Unit) {
                val settings = deps.settingsRepository.settings.first()
                commands = settings.shellCommands
                appSettings = settings
            }

            BackHandler(enabled = dismissRequest != null) {
                dismissRequest?.invoke()
            }

            ModuleTheme(settings = appSettings) {
                CompositionLocalProvider(LocalAppDependencies provides deps) {
                    ShellCommandPanelOverlaySheet(
                        initialCommands = commands,
                        shizukuGranted = shizukuGranted,
                        onRequestShizuku = { TaskManagerUtil.requestPermission(this@ShellCommandPanelTrampolineActivity) },
                        onDismissComplete = { finishPicker() },
                        onPersistCommands = { updated ->
                            commands = updated
                            ShellCommandPanelTrampoline.onCommandsPersist(updated)
                            lifecycleScope.launch {
                                deps.settingsRepository.setShellCommands(updated)
                            }
                        },
                        onWindowReady = { ShellCommandPanelTrampoline.runPrepareIfNeeded() },
                        registerBackHandler = { dismissRequest = it },
                        registerContinuousDismissHandler = { handler ->
                            ShellCommandPanelTrampoline.registerContinuousDismissRequest(handler)
                        },
                        onCopyOutput = { output -> copyOutput(output) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shizukuGrantedState.value = TaskManagerUtil.hasPermission()
    }

    /**
     * 临时浮窗语义：离开前台即关闭。
     *
     * 面板跳去别的 app（例如测试执行 `am start`）后本 Activity 只会 stopped、不会 destroy，
     * 而 ShellCommandPanelTrampoline.active 只在 onDestroy 复位；于是后续所有手势入口都会在
     * `if (isActive()) return` 处静默丢弃，表现为"面板再也唤不出来，只能靠桌面长按快捷方式"。
     * 这里在不可见时直接收掉，让 active 随 onDestroy 一起复位。
     */
    override fun onStop() {
        super.onStop()
        // 配置变更（如旋转）会重建 Activity，不能在这里收掉。
        if (!isChangingConfigurations) finishPicker()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        ShellCommandPanelTrampoline.unregisterActivityFinisher()
        ShellCommandPanelTrampoline.unregisterContinuousDismissRequest()
        ShellCommandPanelTrampoline.deliverDismiss()
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
            Intent(context, ShellCommandPanelTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
