package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.util.AppShortcutLoader

/**
 * Relays "create shortcut" flows from overlay context: hides overlay windows, starts the host
 * activity for result, then delivers [AppShortcutLoader.CreatedShortcut] back to the caller.
 *
 * Must stay alive until the host activity returns a result — do not use [android.R.attr.noHistory].
 */
class CreateShortcutTrampolineActivity : ComponentActivity() {

    private var pendingHostPackage: String? = null
    private var resultDelivered = false

    private val createLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val hostPackage = pendingHostPackage
        pendingHostPackage = null
        val created = if (result.resultCode == RESULT_OK && hostPackage != null) {
            AppShortcutLoader.parseCreateShortcutResult(hostPackage, result.data, this)
        } else {
            null
        }
        finishWithResult(created)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            resultDelivered = savedInstanceState.getBoolean(STATE_RESULT_DELIVERED, false)
            if (resultDelivered) {
                finish()
                return
            }
        }
        val hostPackage = intent.getStringExtra(EXTRA_HOST_PACKAGE)
        val hostClass = intent.getStringExtra(EXTRA_HOST_CLASS)
        if (hostPackage.isNullOrBlank() || hostClass.isNullOrBlank()) {
            finishWithResult(null)
            return
        }
        pendingHostPackage = hostPackage
        runCatching {
            createLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setClassName(hostPackage, hostClass))
        }.onFailure {
            pendingHostPackage = null
            finishWithResult(null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_RESULT_DELIVERED, resultDelivered)
    }

    override fun onDestroy() {
        if (!resultDelivered && pendingHostPackage != null) {
            pendingHostPackage = null
            finishWithResult(null)
        }
        super.onDestroy()
    }

    private fun finishWithResult(created: AppShortcutLoader.CreatedShortcut?) {
        if (resultDelivered) return
        resultDelivered = true
        pendingHostPackage = null
        // 结果经跨进程通道回给发起方（发起方可能在 :overlay，不能依赖进程内静态回调）。
        val token = intent.getStringExtra(com.slideindex.app.util.TrampolineResultPort.EXTRA_TOKEN)
        if (!token.isNullOrBlank()) {
            val payload = Bundle().apply {
                putBoolean(com.slideindex.app.util.TrampolineResultPort.EXTRA_CANCELLED, created == null)
                if (created != null) {
                    putString(EXTRA_RESULT_HOST_PACKAGE, created.hostPackageName)
                    putString(EXTRA_RESULT_LABEL, created.label)
                    putString(EXTRA_RESULT_COMPONENT_FLAT, created.componentFlat)
                    putString(EXTRA_RESULT_INTENT_URI, created.intentUri)
                    putParcelable(EXTRA_RESULT_SHORTCUT_INTENT, created.shortcutIntent)
                }
            }
            com.slideindex.app.util.TrampolineResultPort.deliver(this, token, payload)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_HOST_PACKAGE = "host_package"
        private const val EXTRA_HOST_CLASS = "host_class"
        private const val STATE_RESULT_DELIVERED = "result_delivered"

        internal const val EXTRA_RESULT_HOST_PACKAGE = "result_host_package"
        internal const val EXTRA_RESULT_LABEL = "result_label"
        internal const val EXTRA_RESULT_COMPONENT_FLAT = "result_component_flat"
        internal const val EXTRA_RESULT_INTENT_URI = "result_intent_uri"
        internal const val EXTRA_RESULT_SHORTCUT_INTENT = "result_shortcut_intent"

        fun createIntent(
            context: Context,
            host: AppShortcutLoader.CreateShortcutHost,
            token: String? = null,
        ): Intent =
            Intent(context, CreateShortcutTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_HOST_PACKAGE, host.packageName)
                putExtra(EXTRA_HOST_CLASS, host.className)
                if (!token.isNullOrBlank()) {
                    putExtra(com.slideindex.app.util.TrampolineResultPort.EXTRA_TOKEN, token)
                }
            }

        internal fun decodeResult(payload: Bundle): AppShortcutLoader.CreatedShortcut? {
            if (payload.getBoolean(com.slideindex.app.util.TrampolineResultPort.EXTRA_CANCELLED, false)) return null
            val shortcutIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                payload.getParcelable(EXTRA_RESULT_SHORTCUT_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                payload.getParcelable(EXTRA_RESULT_SHORTCUT_INTENT)
            }
            return AppShortcutLoader.CreatedShortcut(
                hostPackageName = payload.getString(EXTRA_RESULT_HOST_PACKAGE).orEmpty(),
                label = payload.getString(EXTRA_RESULT_LABEL).orEmpty(),
                componentFlat = payload.getString(EXTRA_RESULT_COMPONENT_FLAT),
                intentUri = payload.getString(EXTRA_RESULT_INTENT_URI),
                shortcutIntent = shortcutIntent,
            )
        }
    }
}

object CreateShortcutTrampoline {
    fun launch(
        context: Context,
        host: AppShortcutLoader.CreateShortcutHost,
        onPrepare: () -> Unit,
        onResult: (AppShortcutLoader.CreatedShortcut?) -> Unit
    ) {
        val appContext = context.applicationContext
        onPrepare()
        val token = java.util.UUID.randomUUID().toString()
        com.slideindex.app.util.TrampolineResultPort.register(appContext, token) { payload ->
            onResult(CreateShortcutTrampolineActivity.decodeResult(payload))
        }
        runCatching {
            context.startActivity(CreateShortcutTrampolineActivity.createIntent(context, host, token))
        }.onFailure {
            com.slideindex.app.util.TrampolineResultPort.clearToken(token)
            onResult(null)
        }
    }

    /** 兼容旧调用点；回调已改为按 token 回传，无需外部取消。 */
    fun cancelPending() = Unit
}
