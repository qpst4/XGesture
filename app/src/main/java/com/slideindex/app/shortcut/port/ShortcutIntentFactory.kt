package com.slideindex.app.shortcut.port

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

/** Build a snapshot Intent from the current ShortCutItem for display/widgets. */
fun ShortCutItem.toCurrentIntent(): Intent =
    Intent().apply {
        action = intent["act"]
        intent["cmp"]?.let { flat ->
            ComponentName.unflattenFromString(flat)?.let(::setComponent)
        }
        intent["dat"]?.let { data = Uri.parse(it) }
        extra.forEach { (key, value) -> putExtra(key, value) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

fun ShortCutItem.toIntentUri(): String? =
    runCatching { toCurrentIntent().toUri(Intent.URI_INTENT_SCHEME) }.getOrNull()
