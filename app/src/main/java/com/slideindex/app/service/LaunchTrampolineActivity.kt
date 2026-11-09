package com.slideindex.app.service

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationHistoryIntentCapture
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.TaskManagerUtil

/**
 * Invisible one-shot activity so [Intent]s and published shortcuts can be started from overlay / FGS context.
 * Android 10+ blocks background activity starts; relaying through a foreground activity works.
 */
class LaunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pendingIntentEncoded = intent.getStringExtra(EXTRA_PENDING_INTENT_B64)
        val fallbackIntent = readFallbackIntent()
        val launchIntent = readLaunchIntent()
        val shortcutPackage = intent.getStringExtra(EXTRA_SHORTCUT_PACKAGE)
        val wantsAssist = intent.getBooleanExtra(EXTRA_ASSIST, false)
        val wantsRestorePin = intent.getBooleanExtra(EXTRA_RESTORE_PIN, false)
        val wantsWallpaperPermission = intent.getBooleanExtra(EXTRA_WALLPAPER_PERMISSION, false)
        val shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID)
        window.decorView.post {
            when {
                wantsAssist -> {
                    com.slideindex.app.util.AssistantLauncher.launchFromActivity(this)
                }
                wantsRestorePin -> {
                    com.slideindex.app.stash.StashPinNotificationHelper
                        .restoreToScreenPin(applicationContext)
                }
                wantsWallpaperPermission -> {
                    com.slideindex.app.overlay.SystemWallpaperBlurHelper
                        .requestWallpaperPermission(this)
                }
                !pendingIntentEncoded.isNullOrBlank() -> {
                    sendStoredPendingIntent(pendingIntentEncoded, fallbackIntent)
                }
                launchIntent != null -> {
                    val started = runCatching {
                        // vivo / OriginOS：小窗必须由"分享目标"这条链路拉开，ActivityOptions 在这里无效。
                        // 这里已经有前台上下文，正好是从壳里发起分享的位置（见 FreeWindowLauncher）。
                        if (intent.getBooleanExtra(EXTRA_SHARE_PROXY, false)) {
                            startActivity(FreeWindowLauncher.buildOriginOsShareChooser(this, launchIntent))
                        } else {
                            val launchOptions = readLaunchOptions()
                            if (launchOptions != null) {
                                startActivity(launchIntent, launchOptions)
                            } else {
                                startActivity(launchIntent)
                            }
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "startActivity from trampoline failed", error)
                    }.isSuccess
                    if (!started && !tryShellLaunch(launchIntent)) {
                        Toast.makeText(
                            this,
                            R.string.float_ball_action_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                !shortcutPackage.isNullOrBlank() && !shortcutId.isNullOrBlank() -> {
                    launchPublishedShortcut(shortcutPackage, shortcutId)
                }
            }
            window.decorView.postDelayed({
                if (!isFinishing) {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }, FINISH_DELAY_MS)
        }
    }

    private fun sendStoredPendingIntent(encoded: String, fallbackIntent: Intent?) {
        val pendingIntent = NotificationHistoryIntentCapture.deserializePendingIntent(encoded)
        if (pendingIntent != null) {
            val options = createPendingIntentSendOptions()
            val sentWithOptions = runCatching {
                pendingIntent.send(this, 0, null, null, null, null, options)
            }.onFailure { error ->
                Log.e(TAG, "PendingIntent.send from trampoline failed", error)
            }.isSuccess
            if (sentWithOptions) return
            val sent = runCatching {
                pendingIntent.send()
            }.onFailure { error ->
                Log.e(TAG, "PendingIntent.send() from trampoline failed", error)
            }.isSuccess
            if (sent) return
        }
        if (fallbackIntent != null) {
            runCatching {
                startActivity(fallbackIntent)
            }.onFailure { error ->
                Log.e(TAG, "fallback startActivity from trampoline failed", error)
            }
        }
    }

    private fun readLaunchOptions(): Bundle? {
        intent.getStringExtra(EXTRA_LAUNCH_OPTIONS_B64)?.let { encoded ->
            NotificationHistoryIntentCapture.deserializeBundle(encoded)?.let { return it }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_LAUNCH_OPTIONS, Bundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_LAUNCH_OPTIONS)
        }
    }

    private fun readFallbackIntent(): Intent? {
        intent.getStringExtra(EXTRA_FALLBACK_INTENT_B64)?.let { encoded ->
            NotificationHistoryIntentCapture.deserializeIntentParcel(encoded)
                ?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_FALLBACK_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_FALLBACK_INTENT)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun createPendingIntentSendOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        val mode = if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        options.pendingIntentBackgroundActivityStartMode = mode
        return options.toBundle()
    }

    private fun tryShellLaunch(launchIntent: Intent): Boolean {
        val component = launchIntent.component ?: return false
        if (!TaskManagerUtil.hasPermission()) return false
        val intentUri = runCatching {
            launchIntent.toUri(Intent.URI_INTENT_SCHEME)
        }.getOrNull()
        val started = if (intentUri != null) {
            // 用 intent URI 而不是 -n，避免丢掉 extras。
            TaskManagerUtil.runShellCommand("am", "start", intentUri)
        } else {
            TaskManagerUtil.runShellCommand(
                "am",
                "start",
                "-n",
                "${component.packageName}/${component.className}",
            )
        }
        if (started) {
            Log.i(TAG, "launched via shell fallback: ${component.flattenToShortString()}")
        } else {
            Log.w(TAG, "shell fallback failed: ${component.flattenToShortString()}")
        }
        return started
    }

    private fun launchPublishedShortcut(packageName: String, shortcutId: String) {
        val launcherApps = getSystemService(LauncherApps::class.java)
        val launchOptions = readLaunchOptions()
        if (launcherApps != null) {
            val started = runCatching {
                launcherApps.startShortcut(
                    packageName,
                    shortcutId,
                    null,
                    launchOptions,
                    Process.myUserHandle()
                )
            }.onFailure { error ->
                Log.e(TAG, "startShortcut($packageName, $shortcutId) failed", error)
            }.isSuccess
            if (started) return
        }
        if (TaskManagerUtil.hasPermission()) {
            Thread {
                TaskManagerUtil.startPublishedShortcut(packageName, shortcutId)
            }.start()
        }
    }

    private fun readLaunchIntent(): Intent? {
        intent.getStringExtra(EXTRA_LAUNCH_INTENT_B64)?.let { encoded ->
            NotificationHistoryIntentCapture.deserializeIntentParcel(encoded)
                ?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_LAUNCH_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_LAUNCH_INTENT)
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private const val TAG = "LaunchTrampoline"
        private const val FINISH_DELAY_MS = 400L
        private const val EXTRA_LAUNCH_INTENT = "launch_intent"
        private const val EXTRA_LAUNCH_INTENT_B64 = "launch_intent_b64"
        private const val EXTRA_LAUNCH_OPTIONS = "launch_options"
        private const val EXTRA_LAUNCH_OPTIONS_B64 = "launch_options_b64"
        private const val EXTRA_PENDING_INTENT_B64 = "pending_intent_b64"
        private const val EXTRA_FALLBACK_INTENT = "fallback_intent"
        private const val EXTRA_FALLBACK_INTENT_B64 = "fallback_intent_b64"
        private const val EXTRA_SHARE_PROXY = "share_proxy"
        private const val EXTRA_SHORTCUT_PACKAGE = "shortcut_package"
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        private const val EXTRA_ASSIST = "assist_launch"
        private const val EXTRA_RESTORE_PIN = "restore_pin"
        private const val EXTRA_WALLPAPER_PERMISSION = "wallpaper_permission"

        /** 拉起系统助手：「助手」类 Intent 必须在 Activity 里发起。 */
        fun createAssistIntent(context: Context): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_ASSIST, true)
            }

        /** 通知里「恢复钉图」的一次性入口（内容 Intent 不能用 BroadcastReceiver）。 */
        fun createRestorePinIntent(context: Context): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_RESTORE_PIN, true)
            }

        /** 跳转「所有文件访问权限」页：壁纸模糊需要。 */
        fun createWallpaperPermissionIntent(context: Context): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_WALLPAPER_PERMISSION, true)
            }

        fun createPendingIntentIntent(
            context: Context,
            pendingIntentBase64: String,
            fallbackIntent: Intent? = null
        ): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PENDING_INTENT_B64, pendingIntentBase64)
                fallbackIntent?.let { intent ->
                    NotificationHistoryIntentCapture.serializeIntentParcel(intent)?.let { encoded ->
                        putExtra(EXTRA_FALLBACK_INTENT_B64, encoded)
                    } ?: putExtra(EXTRA_FALLBACK_INTENT, intent)
                }
            }

        fun createIntent(
            context: Context,
            launchIntent: Intent,
            launchOptions: Bundle? = null,
            shareProxy: Boolean = false,
        ): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val prepared = launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                NotificationHistoryIntentCapture.serializeIntentParcel(prepared)?.let { encoded ->
                    putExtra(EXTRA_LAUNCH_INTENT_B64, encoded)
                } ?: putExtra(EXTRA_LAUNCH_INTENT, prepared)
                launchOptions?.let { options ->
                    NotificationHistoryIntentCapture.serializeBundle(options, "free_window_launch")?.let { encoded ->
                        putExtra(EXTRA_LAUNCH_OPTIONS_B64, encoded)
                    } ?: putExtra(EXTRA_LAUNCH_OPTIONS, options)
                }
                if (shareProxy) putExtra(EXTRA_SHARE_PROXY, true)
            }

        fun createShortcutIntent(
            context: Context,
            packageName: String,
            shortcutId: String,
            launchOptions: Bundle? = null
        ): Intent =
            Intent(context, LaunchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_SHORTCUT_PACKAGE, packageName)
                putExtra(EXTRA_SHORTCUT_ID, shortcutId)
                launchOptions?.let { options ->
                    NotificationHistoryIntentCapture.serializeBundle(options, "free_window_launch")?.let { encoded ->
                        putExtra(EXTRA_LAUNCH_OPTIONS_B64, encoded)
                    } ?: putExtra(EXTRA_LAUNCH_OPTIONS, options)
                }
            }
    }
}
