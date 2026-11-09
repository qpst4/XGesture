package com.slideindex.app.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.slideindex.app.service.LaunchTrampolineActivity

/**
 * Launch the user's default digital assistant (Gemini, Bixby, 小爱, 小艺, Aicy, etc.).
 *
 * Order: Shizuku (if granted) → system assist APIs → explicit component → trampoline →
 * implicit ASSIST / VOICE_ASSIST fallbacks (may show OEM disambiguation).
 */
object AssistantLauncher {

    private const val TAG = "AssistantLauncher"
    private const val VOICE_INTERACTION_SERVICE = "voiceinteraction"
    private const val SECURE_ASSISTANT = "assistant"
    private const val SECURE_VOICE_INTERACTION_SERVICE = "voice_interaction_service"
    private const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"
    private const val TRAMPOLINE_REQUEST_CODE = 0x41534953 // "ASIS"
    private const val PER_USER_RANGE = 100_000
    // VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE — AssistUtils only.
    private const val SHOW_SOURCE_ASSIST_GESTURE = 4
    private const val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    private val VIA_SHOW_METHODS = setOf("showSession", "show")
    // ActivityOptions background-start modes — use ints so lint does not tie them to minSdk.
    private const val BAL_START_ALLOWED = 1 // API 34: MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    private const val BAL_START_ALLOW_ALWAYS = 2 // API 36: MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS

    private val mainHandler = Handler(Looper.getMainLooper())

    fun launchDefault(context: Context) {
        val appContext = context.applicationContext
        Thread {
            if (tryLaunchViaShizuku(appContext)) return@Thread
            mainHandler.post {
                if (!tryLaunchInApp(appContext)) {
                    Log.w(TAG, "all assistant launch paths failed")
                }
            }
        }.start()
    }

    /** Called from [LaunchTrampolineActivity] while in the foreground. */
    internal fun launchFromActivity(activity: Activity): Boolean {
        if (tryLaunchCore(activity)) return true
        if (tryLaunchViaShizuku(activity)) return true
        if (tryLaunchFallbacks(activity)) return true
        Log.w(TAG, "assistant launch failed from trampoline")
        return false
    }

    private fun tryLaunchInApp(context: Context): Boolean =
        tryLaunchCore(context) ||
            startTrampoline(context) ||
            tryLaunchFallbacks(context)

    private fun tryLaunchCore(context: Context): Boolean =
        HiddenAssistApi.launchAssist(context) ||
            launchViaVoiceInteractionManager(context) ||
            HiddenAssistApi.showSessionForActiveService(context) ||
            launchResolvedAssist(context)

    private fun tryLaunchFallbacks(context: Context): Boolean =
        launchViaResolvableIntent(context, Intent.ACTION_ASSIST, preferDefault = true) ||
            launchViaResolvableIntent(context, ACTION_VOICE_ASSIST, preferDefault = false)

    @SuppressLint("DiscouragedPrivateApi")
    private fun launchViaVoiceInteractionManager(context: Context): Boolean {
        val service = context.applicationContext.getSystemService(VOICE_INTERACTION_SERVICE) ?: return false
        for (method in service.javaClass.methods) {
            if (method.name !in VIA_SHOW_METHODS) continue
            val ok = runCatching {
                when (method.parameterCount) {
                    1 -> method.invoke(service, Bundle())
                    2 -> method.invoke(service, Bundle(), 0)
                    else -> null
                }
            }.getOrNull()
            if (ok != false) {
                logSuccess("VoiceInteractionManager.${method.name}")
                return true
            }
        }
        return false
    }

    private fun launchResolvedAssist(context: Context): Boolean {
        HiddenAssistApi.getAssistIntent(context, requireAssist = true)?.let { intent ->
            if (startSafely(context, intent, "getAssistIntent(true)")) return true
        }
        HiddenAssistApi.getAssistIntent(context, requireAssist = false)?.let { intent ->
            if (startSafely(context, intent, "getAssistIntent(false)")) return true
        }

        val component = resolveDefaultAssistantComponent(context) ?: run {
            Log.w(TAG, "no default assistant configured in Settings")
            return false
        }

        return startSafely(
            context,
            assistIntent(Intent.ACTION_ASSIST, component),
            "ACTION_ASSIST explicit $component",
        )
    }

    /** Resolves [action] to a single handler when possible; otherwise defers to system/OEM UI. */
    private fun launchViaResolvableIntent(
        context: Context,
        action: String,
        preferDefault: Boolean,
    ): Boolean {
        val intent = assistIntent(action)
        val handlers = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        if (handlers.isEmpty()) return false

        if (handlers.size == 1) {
            val info = handlers[0].activityInfo
            intent.component = ComponentName(info.packageName, info.name)
            return startSafely(context, intent, "$action sole handler ${intent.component}")
        }

        if (preferDefault) {
            resolveDefaultAssistantComponent(context)?.let { preferred ->
                if (handlers.any { it.activityInfo.packageName == preferred.packageName }) {
                    intent.component = preferred
                    if (startSafely(context, intent, "$action preferred $preferred")) return true
                }
            }
            return startSafely(context, intent, "$action disambiguation")
        }

        return startSafely(context, intent, action)
    }

    private fun tryLaunchViaShizuku(context: Context): Boolean {
        if (!TaskManagerUtil.hasPermission()) return false
        if (TaskManagerUtil.showVoiceAssistant()) {
            logSuccess("shizuku cmd voiceinteraction show")
            return true
        }

        val component = resolveDefaultAssistantComponent(context) ?: return false
        if (isViaComponent(component)) return false

        val command = arrayOf(
            "am", "start",
            "-a", Intent.ACTION_ASSIST,
            "-n", "${component.packageName}/${component.className}",
        )
        if (TaskManagerUtil.runShellCommand(*command)) {
            logSuccess("shizuku am start $component")
            return true
        }
        return false
    }

    private fun isViaComponent(component: ComponentName): Boolean =
        component.className.contains("VoiceInteraction", ignoreCase = true)

    private fun startTrampoline(context: Context): Boolean =
        startTrampolineActivity(context) || startPendingIntentTrampoline(context)

    private fun startTrampolineActivity(context: Context): Boolean =
        runCatching {
            context.startActivity(
                LaunchTrampolineActivity.createAssistIntent(context).apply {
                    flags = flags or LAUNCH_FLAGS
                },
            )
            Log.i(TAG, "trampoline via startActivity")
            true
        }.getOrElse { error ->
            Log.w(TAG, "trampoline via startActivity failed", error)
            false
        }

    private fun startPendingIntentTrampoline(context: Context): Boolean =
        runCatching {
            val intent = LaunchTrampolineActivity.createAssistIntent(context)
            val pendingIntent = createTrampolinePendingIntent(context, intent)
            val sendOptions = createPendingIntentSendOptions()
            if (sendOptions != null) {
                pendingIntent.send(context, 0, null, null, null, null, sendOptions)
            } else {
                pendingIntent.send()
            }
            Log.i(TAG, "trampoline via PendingIntent")
            true
        }.getOrElse { error ->
            Log.w(TAG, "PendingIntent trampoline failed", error)
            false
        }

    private fun createTrampolinePendingIntent(context: Context, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val creatorOptions = ActivityOptions.makeBasic()
            applyPendingIntentCreatorBackgroundStartMode(creatorOptions)
            return PendingIntent.getActivity(
                context,
                TRAMPOLINE_REQUEST_CODE,
                intent,
                flags,
                creatorOptions.toBundle(),
            )
        }
        return PendingIntent.getActivity(context, TRAMPOLINE_REQUEST_CODE, intent, flags)
    }

    private fun createPendingIntentSendOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        applyPendingIntentBackgroundStartMode(options)
        return options.toBundle()
    }

    /** @return mode int, or null when the platform API is unavailable. */
    private fun pendingIntentBackgroundStartMode(): Int? = when {
        Build.VERSION.SDK_INT >= 36 -> BAL_START_ALLOW_ALWAYS
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> BAL_START_ALLOWED
        else -> null
    }

    @SuppressLint("NewApi")
    private fun applyPendingIntentBackgroundStartMode(options: ActivityOptions) {
        val mode = pendingIntentBackgroundStartMode() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.pendingIntentBackgroundActivityStartMode = mode
        }
    }

    @SuppressLint("NewApi")
    private fun applyPendingIntentCreatorBackgroundStartMode(options: ActivityOptions) {
        val mode = pendingIntentBackgroundStartMode() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            options.pendingIntentCreatorBackgroundActivityStartMode = mode
        }
    }

    private fun resolveDefaultAssistantComponent(context: Context): ComponentName? =
        HiddenAssistApi.resolveAssistComponent(context, currentUserId())
            ?: readSecureComponent(context, SECURE_ASSISTANT)
            ?: readSecureComponent(context, SECURE_VOICE_INTERACTION_SERVICE)

    private fun readSecureComponent(context: Context, key: String): ComponentName? {
        val raw = Settings.Secure.getString(context.contentResolver, key)?.trim().orEmpty()
        if (raw.isBlank()) return null
        return ComponentName.unflattenFromString(raw)
    }

    private fun assistIntent(action: String, target: ComponentName? = null): Intent =
        Intent(action).apply {
            component = target
            flags = flags or LAUNCH_FLAGS
        }

    private fun currentUserId(): Int = Process.myUid() / PER_USER_RANGE

    private fun startSafely(context: Context, intent: Intent, label: String): Boolean =
        runCatching {
            context.startActivity(intent)
            logSuccess(label)
            true
        }.getOrElse { error ->
            if (error !is ActivityNotFoundException) {
                Log.w(TAG, "$label startActivity failed", error)
            } else {
                Log.w(TAG, "$label: no activity for action=${intent.action}")
            }
            false
        }

    private fun logSuccess(path: String) {
        Log.i(TAG, "assistant via $path")
    }

    /**
     * Hidden SearchManager / AssistUtils entry points — no public SDK equivalent.
     * Reflection is isolated here; suppression is limited to this object.
     */
    @SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi")
    private object HiddenAssistApi {

        fun launchAssist(context: Context): Boolean {
            val searchManager = context.getSystemService(SearchManager::class.java) ?: return false
            return runCatching {
                val method = SearchManager::class.java.getMethod("launchAssist", Bundle::class.java)
                if (method.invoke(searchManager, Bundle()) == false) return false
                logSuccess("SearchManager.launchAssist")
                true
            }.getOrElse { error ->
                Log.w(TAG, "SearchManager.launchAssist failed", error)
                false
            }
        }

        fun getAssistIntent(context: Context, requireAssist: Boolean): Intent? {
            val searchManager = context.getSystemService(SearchManager::class.java) ?: return null
            return runCatching {
                val method = SearchManager::class.java.getMethod(
                    "getAssistIntent",
                    Boolean::class.javaPrimitiveType,
                )
                val intent = method.invoke(searchManager, requireAssist) as? Intent ?: return null
                if (intent.action.isNullOrBlank()) return null
                if (intent.component == null && intent.`package` == null) return null
                intent.apply { flags = flags or LAUNCH_FLAGS }
            }.getOrElse { error ->
                Log.w(TAG, "getAssistIntent($requireAssist) failed", error)
                null
            }
        }

        fun showSessionForActiveService(context: Context): Boolean {
            val success = withAssistUtils(context) { assistUtils, clazz ->
                val callbackClass =
                    Class.forName("com.android.internal.app.IVoiceInteractionSessionShowCallback")
                val method = clazz.getMethod(
                    "showSessionForActiveService",
                    Bundle::class.java,
                    Int::class.javaPrimitiveType,
                    callbackClass,
                    IBinder::class.java,
                )
                method.invoke(assistUtils, Bundle(), SHOW_SOURCE_ASSIST_GESTURE, null, null) == true
            } ?: false
            if (success) logSuccess("AssistUtils.showSessionForActiveService")
            return success
        }

        fun resolveAssistComponent(context: Context, userId: Int): ComponentName? =
            withAssistUtils(context) { assistUtils, clazz ->
                clazz.getMethod("getAssistComponentForUser", Int::class.javaPrimitiveType)
                    .invoke(assistUtils, userId) as? ComponentName
            }

        private inline fun <T> withAssistUtils(context: Context, block: (Any, Class<*>) -> T): T? =
            runCatching {
                val clazz = Class.forName("com.android.internal.app.AssistUtils")
                val instance = clazz.getConstructor(Context::class.java).newInstance(context)
                block(instance, clazz)
            }.getOrNull()
    }
}
