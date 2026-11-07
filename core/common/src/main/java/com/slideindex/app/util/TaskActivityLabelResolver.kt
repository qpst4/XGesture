package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Resolve the user-visible label for a recents task from its activity component. */
object TaskActivityLabelResolver {

    fun componentFromIdentifier(identifier: String): ComponentName? {
        val trimmed = identifier.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains('/')) {
            val pkg = trimmed.substringBefore('/').trim()
            var cls = trimmed.substringAfter('/').trim()
            if (cls.startsWith('.')) cls = pkg + cls
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return ComponentName(pkg, cls)
        }
        val segments = trimmed.split('.')
        if (segments.size < 3) return null
        val last = segments.last()
        if (last.isEmpty() || !last[0].isUpperCase() || !looksLikeActivityClass(last)) return null
        val pkg = segments.dropLast(1).joinToString(".")
        if (pkg.isEmpty()) return null
        return ComponentName(pkg, trimmed)
    }

    fun resolveDisplayTitle(context: Context, identifier: String, parsedTitle: String?): String? {
        parsedTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        resolveLabel(context, identifier)?.let { return it }
        return null
    }

    fun resolveLabel(context: Context, identifier: String): String? {
        val pm = context.packageManager
        componentFromIdentifier(identifier)?.let { component ->
            loadActivityLabel(pm, component)?.let { return it }
        }
        if (!identifier.contains('/')) {
            componentCandidates(identifier).forEach { component ->
                loadActivityLabel(pm, component)?.let { return it }
            }
        }
        return null
    }

    private fun loadActivityLabel(pm: PackageManager, component: ComponentName): String? {
        return try {
            pm.getActivityInfo(component, 0).loadLabel(pm).toString().trim().takeIf { it.isNotEmpty() }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun componentCandidates(identifier: String): List<ComponentName> {
        val trimmed = identifier.trim()
        val segments = trimmed.split('.')
        if (segments.size < 3) return emptyList()
        return (segments.size - 1 downTo 2).map { packageSegmentCount ->
            ComponentName(
                segments.take(packageSegmentCount).joinToString("."),
                trimmed,
            )
        }
    }

    private fun looksLikeActivityClass(name: String): Boolean =
        name.endsWith("Activity") ||
            name.endsWith("Service") ||
            name.endsWith("Fragment") ||
            name.endsWith("Receiver") ||
            name.endsWith("Provider") ||
            name.endsWith("Application") ||
            name.endsWith("Home") ||
            name.endsWith("Launcher") ||
            name.endsWith("Dialog") ||
            name.endsWith("Sheet") ||
            name.endsWith("Details") ||
            name.endsWith("Page") ||
            name.endsWith("UI") ||
            (name.endsWith("Settings") && name.length > "Settings".length)
}
