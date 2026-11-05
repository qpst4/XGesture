package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.shortcut.port.RootShell
import com.slideindex.app.shortcut.port.ShizukuShell
import com.slideindex.app.shortcut.port.ShortCutManager
import com.slideindex.app.shortcut.port.ShortcutAbxParser
import com.slideindex.app.shortcut.port.ShortcutRootBatchReader
import com.slideindex.app.shortcut.port.toIntentUri
import kotlinx.coroutines.runBlocking

/**
 * Resolve a ShortcutService shortcut right before launch.
 *
 * The action payload stores only packageName + real shortcutId. This resolver
 * fetches the current ShortcutInfo so dynamic extras (traceId etc.) are fresh.
 */
object ShortcutServiceResolver {

    fun resolve(
        context: Context,
        packageName: String,
        shortcutId: String,
    ): TaskSwitcherMenuItem? {
        if (packageName.isBlank() || shortcutId.isBlank()) return null

        if (PrivilegeGateway.isRootMode()) {
            resolveRoot(packageName, shortcutId)?.let { return it }
        }
        resolveShizuku(context, packageName, shortcutId)?.let { return it }
        if (!PrivilegeGateway.isRootMode()) {
            resolveRoot(packageName, shortcutId)?.let { return it }
        }
        return null
    }

    private fun resolveRoot(packageName: String, shortcutId: String): TaskSwitcherMenuItem? {
        val rootShell = RootShell()
        return try {
            if (!rootShell.openShell()) return null
            val bytes = ShortcutRootBatchReader.readPackage(rootShell, packageName) ?: return null
            val parsed = ShortcutAbxParser.parse(bytes, packageName)
                .firstOrNull { it.item.shortcut == shortcutId } ?: return null
            parsed.toMenuItem()
        } catch (_: Throwable) {
            null
        } finally {
            rootShell.close()
        }
    }

    private fun resolveShizuku(
        context: Context,
        packageName: String,
        shortcutId: String,
    ): TaskSwitcherMenuItem? = runBlocking {
        try {
            val shell = ShizukuShell()
            when {
                !shell.isSupported() || shell.isPermissionDenied() -> null
                else -> {
                    val dump = shell.runAndGetOutput("dumpsys shortcut")
                    if (dump.isBlank()) {
                        null
                    } else {
                        val manager = ShortCutManager(context)
                        manager.fromShellData(dump) { }
                        manager.fullData()
                            .firstOrNull { it.pkg == packageName && it.shortcut == shortcutId }
                            ?.let { item ->
                                val kinds = manager.kindsOf(item)
                                TaskSwitcherMenuItem(
                                    label = item.name.ifBlank { item.title.ifBlank { item.pkg } },
                                    type = TaskSwitcherMenuItemType.SHORTCUT,
                                    shortcutId = item.shortcut,
                                    shortcutIntent = manager.intentOf(item),
                                    intentUris = listOfNotNull(item.toIntentUri()),
                                    isServiceShortcut = true,
                                    kind = kinds.toPickerKind(),
                                )
                            }
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ShortcutAbxParser.Parsed.toMenuItem(): TaskSwitcherMenuItem =
        TaskSwitcherMenuItem(
            label = item.name.ifBlank { item.title.ifBlank { item.pkg } },
            type = TaskSwitcherMenuItemType.SHORTCUT,
            shortcutId = item.shortcut,
            shortcutIntent = intent,
            intentUris = listOfNotNull(item.toIntentUri()),
            isServiceShortcut = true,
            kind = kinds.toPickerKind(),
        )

    private fun Set<ShortcutKind>.toPickerKind(): ShortcutKind = when {
        ShortcutKind.PINNED in this -> ShortcutKind.PINNED
        ShortcutKind.DYNAMIC in this -> ShortcutKind.DYNAMIC
        else -> ShortcutKind.STATIC
    }
}
