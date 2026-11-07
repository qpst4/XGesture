package com.slideindex.app.overlay.corner

import android.content.Intent
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.util.KnownAppShortcuts

fun GestureAction.resolveHostPackageName(): String? = when (this) {
    is GestureAction.LaunchApp -> packageName.takeIf { it.isNotBlank() }
    is GestureAction.LaunchShortcut -> resolveLaunchShortcutHostPackage(payloadKey)
    else -> null
}

private fun resolveLaunchShortcutHostPackage(payloadKey: String): String? {
    val decoded = GestureShortcutPayload.decode(payloadKey)
    when (decoded) {
        is GestureShortcutPayload.Decoded.Dynamic ->
            return decoded.packageName.takeIf { it.isNotBlank() }
        is GestureShortcutPayload.Decoded.Component -> {
            val pkg = decoded.componentFlat.substringBefore('/').takeIf { it.isNotBlank() }
            if (pkg != null) return pkg
        }
        is GestureShortcutPayload.Decoded.IntentShortcut ->
            return intentUriToPackage(decoded.intentUri)
        is GestureShortcutPayload.Decoded.IntentsShortcut ->
            return decoded.intentUris.firstOrNull()?.let { intentUriToPackage(it) }
        null -> Unit
    }
    return KnownAppShortcuts.packageForIntentUri(payloadKey)
}

private fun intentUriToPackage(uri: String): String? {
    val intent = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
    if (intent != null) {
        intent.`package`?.takeIf { it.isNotBlank() }?.let { return it }
        intent.component?.packageName?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return KnownAppShortcuts.packageForIntentUri(uri)
}
