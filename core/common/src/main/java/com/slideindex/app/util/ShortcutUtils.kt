package com.slideindex.app.util

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import java.util.UUID

/**
 * Discovers launch shortcuts by scanning launcher activities and parsing
 * [android.app.shortcuts] manifest metadata — same approach as SideGesture.
 */
object ShortcutUtils {
    private const val XMLNS_ANDROID = "http://schemas.android.com/apk/res/android"

    fun getAllAppsWithShortcut(context: Context): List<LauncherShortcutInfo> {
        val result = arrayListOf<LauncherShortcutInfo>()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        for (resolveInfo in context.packageManager.queryIntentActivitiesCompat(
            intent,
            PackageManager.GET_META_DATA,
        )) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val shortcutsMetadata = runCatching {
                activityInfo.loadXmlMetaData(
                    context.packageManager,
                    "android.app.shortcuts",
                )
            }.getOrNull() ?: continue

            runCatching {
                shortcutsMetadata.use { parser ->
                    val shortcuts = parseShortcuts(context, activityInfo, parser)
                    if (shortcuts.isNotEmpty()) {
                        result += LauncherShortcutInfo(
                            packageName = activityInfo.packageName.orEmpty(),
                            className = activityInfo.name.orEmpty(),
                            label = runCatching { activityInfo.loadLabel(context.packageManager).toString() }
                                .getOrDefault(activityInfo.packageName.orEmpty()),
                            shortcuts = shortcuts,
                        )
                    }
                }
            }
        }
        return result
    }

    fun queryCreateShortcutActivities(
        context: Context,
        allowRepeatPackage: Boolean = true,
    ): List<LauncherShortcutInfo> {
        val list = mutableListOf<LauncherShortcutInfo>()
        val pkgList = mutableListOf<String>()
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val activities = packageManager.queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
        for (resolveInfo in activities) {
            val activityInfo = resolveInfo.activityInfo
            val packageName = activityInfo?.packageName
            if (!activityInfo.exported) continue
            if (packageName.isNullOrEmpty()) continue
            if (!allowRepeatPackage && packageName in pkgList) continue
            list += LauncherShortcutInfo(
                packageName = packageName,
                className = activityInfo.name,
                label = runCatching { activityInfo.loadLabel(packageManager).toString() }.getOrDefault(packageName),
            )
            pkgList += packageName
        }
        return list
    }

    fun shortcutsForPackage(context: Context, packageName: String): List<LauncherShortcutInfo.Entry> {
        return getAllAppsWithShortcut(context)
            .filter { it.packageName == packageName }
            .flatMap { it.shortcuts }
    }

    private fun parseShortcuts(
        context: Context,
        actInfo: ActivityInfo,
        parser: XmlResourceParser,
    ): List<LauncherShortcutInfo.Entry> = runCatching {
        val result = arrayListOf<LauncherShortcutInfo.Entry>()

        var eventType = parser.eventType
        var currentId: String? = null
        var currentLabel: String? = null
        var currentIconRes = 0
        var currentIntent: Intent? = null
        var currentIntents = arrayListOf<String>()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when {
                eventType == XmlPullParser.END_TAG && parser.name == "shortcut" -> {
                    if (currentId != null && currentLabel != null && currentIntents.isNotEmpty()) {
                        result += LauncherShortcutInfo.Entry(
                            packageName = actInfo.packageName,
                            className = actInfo.name,
                            label = currentLabel,
                            iconRes = currentIconRes,
                            intents = currentIntents,
                        )
                    }
                    currentId = null
                    currentLabel = null
                    currentIconRes = 0
                    currentIntents = arrayListOf()
                }
                eventType == XmlPullParser.END_TAG && parser.name == "intent" && currentIntent != null -> {
                    currentIntents.add(currentIntent.toUri(Intent.URI_INTENT_SCHEME))
                }
                eventType != XmlPullParser.START_TAG -> Unit
                parser.name == "shortcut" &&
                    parser.getAttributeBooleanValue(XMLNS_ANDROID, "enabled", true) -> {
                    currentId = parser.getAttributeValue("shortcutId") ?: UUID.randomUUID().toString()
                    var labelRes = parser.getAttributeResourceValue("shortcutShortLabel")
                    if (labelRes == 0) {
                        labelRes = parser.getAttributeResourceValue("shortcutLongLabel")
                    }
                    currentLabel = if (labelRes == 0) {
                        null
                    } else {
                        runCatching {
                            val resources = context.packageManager.getResourcesForApplication(actInfo.packageName)
                            resources.getString(labelRes)
                        }.getOrNull()
                    }
                    currentIconRes = parser.getAttributeResourceValue("icon")
                }
                parser.name == "intent" && currentId != null -> {
                    try {
                        val shortcutIntent = Intent().apply {
                            action = parser.getAttributeValue("action")
                            val pkg = parser.getAttributeValue("targetPackage")
                            val cls = parser.getAttributeValue("targetClass")
                                ?: parser.getAttributeValue("targetActivity")?.replace('$', '_')
                            if (pkg != null && cls != null) {
                                setClassName(pkg, cls)
                            }
                            currentLabel = currentLabel ?: cls?.substringAfterLast('.')
                            data = parser.getAttributeValue("data")?.let(Uri::parse)
                        }
                        currentIntent = shortcutIntent
                    } catch (_: Exception) {
                    }
                }
                parser.name == "extra" && currentIntent != null -> {
                    val name = parser.getAttributeValue("name")
                    val value = parser.getAttributeValue("value")
                    if (name != null && value != null) {
                        currentIntent.putExtra(name, value)
                    }
                }
                parser.name == "categories" && currentIntent != null -> {
                    val name = parser.getAttributeValue("name")
                    if (name != null) {
                        currentIntent.addCategory(name)
                    }
                }
            }
            eventType = parser.next()
        }

        result
    }.getOrDefault(emptyList())

    private fun XmlResourceParser.getAttributeValue(attribute: String): String? {
        return getAttributeValue(XMLNS_ANDROID, attribute) ?: getAttributeValue(null, attribute)
    }

    private fun XmlResourceParser.getAttributeResourceValue(attribute: String): Int {
        val res = getAttributeResourceValue(XMLNS_ANDROID, attribute, 0)
        if (res != 0) return res
        return getAttributeResourceValue(null, attribute, 0)
    }
}
