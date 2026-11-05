package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandCodec
import com.slideindex.app.ui.ShellCommandEditorOverlaySheet
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.util.ShellCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent activity that hosts [ShellCommandEditorOverlaySheet] so text input uses the normal
 * window pipeline while keeping the centered-sheet look of the shell command panel.
 */
@dagger.hilt.android.AndroidEntryPoint
class ShellCommandEditorTrampolineActivity : ComponentActivity() {

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

        val existing = intent.getStringExtra(EXTRA_COMMAND_PAYLOAD)
            ?.let(ShellCommandCodec::decode)
        val shizukuGranted = intent.getBooleanExtra(EXTRA_SHIZUKU_GRANTED, false)
        val canDelete = intent.getBooleanExtra(EXTRA_CAN_DELETE, false)

        ShellCommandEditorTrampoline.registerActivityFinisher { finishPicker() }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        setContent {
            val scope = rememberCoroutineScope()
            var appSettings by remember { mutableStateOf(AppSettings()) }
            var dismissRequest by remember { mutableStateOf<(() -> Unit)?>(null) }

            LaunchedEffect(Unit) {
                appSettings = deps.settingsRepository.settings.first()
            }

            BackHandler(enabled = dismissRequest != null) {
                dismissRequest?.invoke()
            }

            ModuleTheme(settings = appSettings) {
                CompositionLocalProvider(LocalAppDependencies provides deps) {
                    ShellCommandEditorOverlaySheet(
                        onDismissComplete = { finishPicker() },
                        onWindowReady = { ShellCommandEditorTrampoline.runPrepareIfNeeded() },
                        initial = existing,
                        shizukuGranted = shizukuGranted,
                        onSave = { saved ->
                            ShellCommandEditorTrampoline.onItemSave(saved)
                            finishPicker()
                        },
                        onDelete = if (canDelete) {
                            {
                                ShellCommandEditorTrampoline.onItemDelete()
                                finishPicker()
                            }
                        } else {
                            null
                        },
                        onTest = { command, callback ->
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    ShellCommandRunner.execute(this@ShellCommandEditorTrampolineActivity, command)
                                }
                                callback(outcome.exitCode, outcome.output)
                            }
                        },
                        registerBackHandler = { dismissRequest = it }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ShellCommandEditorTrampoline.unregisterActivityFinisher()
        ShellCommandEditorTrampoline.deliverDismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISMISSED, dismissed)
    }

    private fun finishPicker() {
        if (dismissed) return
        dismissed = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_COMMAND_PAYLOAD = "command_payload"
        private const val EXTRA_SHIZUKU_GRANTED = "shizuku_granted"
        private const val EXTRA_CAN_DELETE = "can_delete"
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(
            context: Context,
            existing: ShellCommand?,
            shizukuGranted: Boolean
        ): Intent =
            Intent(context, ShellCommandEditorTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_SHIZUKU_GRANTED, shizukuGranted)
                putExtra(EXTRA_CAN_DELETE, existing != null)
                existing?.let { putExtra(EXTRA_COMMAND_PAYLOAD, ShellCommandCodec.encode(it)) }
            }
    }
}
