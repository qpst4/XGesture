package com.slideindex.app.search.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import android.util.AttributeSet
import android.util.Log
import org.xmlpull.v1.XmlPullParser

/**
 * SEVO-style fallback: parse Settings APK AndroidManifest for activities whose
 * intent-filter actions start with [ACTION_PREFIX]. Uses framework AssetManager
 * AXML parsing (not a third-party binary XML reimplementation).
 */
object SystemSettingsManifestIndex {
    private const val TAG = "SystemSettingsManifest"
    private const val ACTION_PREFIX = "android.settings."

    private val settingsPackages = listOf(
        "com.android.settings",
        "com.meizu.settings",
        "com.meizu.flyme.settings",
        "com.samsung.android.settings",
        "com.android.settings.intelligence",
    )

    fun loadEntries(context: Context): List<SystemSettingsSearchEntry> {
        val pm = context.packageManager
        val loaded = linkedSetOf<SystemSettingsSearchEntry>()
        for (pkg in settingsPackages) {
            val appInfo = runCatching {
                pm.getApplicationInfo(pkg, 0)
            }.getOrNull() ?: continue
            val fromApk = runCatching { loadFromApk(pm, appInfo) }
                .onFailure { Log.d(TAG, "manifest parse failed for $pkg: ${it.message}") }
                .getOrDefault(emptyList())
            if (fromApk.isNotEmpty()) {
                loaded += fromApk
                Log.i(TAG, "loaded ${fromApk.size} settings activities from $pkg manifest")
            }
        }
        return loaded.toList()
    }

    private fun loadFromApk(
        pm: PackageManager,
        appInfo: ApplicationInfo,
    ): List<SystemSettingsSearchEntry> {
        val apkPath = appInfo.sourceDir ?: return emptyList()
        openManifestParser(apkPath)?.use { parser ->
            return parseSettingsActivities(parser, pm, appInfo.packageName)
        }
        return emptyList()
    }

    private fun openManifestParser(apkPath: String): XmlResourceParser? {
        return runCatching {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            val cookie = addAssetPath.invoke(assetManager, apkPath) as Int
            if (cookie == 0) return@runCatching null
            assetManager.openXmlResourceParser(cookie, "AndroidManifest.xml")
        }.getOrNull()
    }

    private fun parseSettingsActivities(
        parser: XmlResourceParser,
        pm: PackageManager,
        packageName: String,
    ): List<SystemSettingsSearchEntry> {
        val results = linkedSetOf<SystemSettingsSearchEntry>()
        var event = parser.eventType
        var inManifest = false
        var inApplication = false
        var inActivity = false
        var inIntentFilter = false
        var activityName: String? = null
        var activityLabelAttr: String? = null
        var activityLabelRes = 0
        var matchedAction: String? = null
        var hasData = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "manifest" -> inManifest = true
                        "application" -> if (inManifest) inApplication = true
                        "activity", "activity-alias" -> if (inApplication) {
                            inActivity = true
                            inIntentFilter = false
                            activityName = parser.attr("name")
                            activityLabelAttr = parser.attr("label")
                            activityLabelRes = parser.attrResId("label")
                            matchedAction = null
                            hasData = false
                        }
                        "intent-filter" -> if (inActivity) {
                            inIntentFilter = true
                        }
                        "action" -> if (inIntentFilter) {
                            val action = parser.attr("name")
                            if (!action.isNullOrBlank() &&
                                action.startsWith(ACTION_PREFIX) &&
                                matchedAction == null
                            ) {
                                matchedAction = action
                            }
                        }
                        "data" -> if (inIntentFilter) {
                            hasData = true
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "intent-filter" -> inIntentFilter = false
                        "activity", "activity-alias" -> {
                            val action = matchedAction
                            val className = resolveClassName(packageName, activityName)
                            if (inActivity &&
                                !hasData &&
                                !action.isNullOrBlank() &&
                                !className.isNullOrBlank()
                            ) {
                                val title = resolveLabel(
                                    pm = pm,
                                    packageName = packageName,
                                    labelAttr = activityLabelAttr,
                                    labelRes = activityLabelRes,
                                    fallbackClass = className,
                                )
                                if (title.isNotBlank()) {
                                    results += SystemSettingsSearchEntry(
                                        title = title,
                                        screenTitle = null,
                                        keywords = action.removePrefix(ACTION_PREFIX)
                                            .lowercase()
                                            .replace('_', ' '),
                                        packageName = packageName,
                                        className = className,
                                        action = action,
                                        key = null,
                                    )
                                }
                            }
                            inActivity = false
                            activityName = null
                            activityLabelAttr = null
                            activityLabelRes = 0
                            matchedAction = null
                            hasData = false
                        }
                        "application" -> inApplication = false
                        "manifest" -> inManifest = false
                    }
                }
            }
            event = parser.next()
        }
        return results.toList()
    }

    private fun resolveClassName(packageName: String, rawName: String?): String? {
        val name = rawName?.trim().orEmpty()
        if (name.isEmpty()) return null
        return when {
            name.startsWith(".") -> packageName + name
            name.contains('.') -> name
            else -> "$packageName.$name"
        }
    }

    private fun resolveLabel(
        pm: PackageManager,
        packageName: String,
        labelAttr: String?,
        labelRes: Int,
        fallbackClass: String,
    ): String {
        if (labelRes != 0) {
            runCatching {
                val res = pm.getResourcesForApplication(packageName)
                val text = res.getString(labelRes).trim()
                if (text.isNotEmpty()) return text
            }
        }
        val attr = labelAttr?.trim().orEmpty()
        if (attr.isNotEmpty() && !attr.startsWith("@")) {
            return attr
        }
        return fallbackClass.substringAfterLast('.')
    }

    private fun XmlPullParser.attr(name: String): String? {
        val ns = "http://schemas.android.com/apk/res/android"
        getAttributeValue(ns, name)?.let { return it }
        for (i in 0 until attributeCount) {
            val attrName = getAttributeName(i) ?: continue
            if (attrName == name || attrName.endsWith(":$name")) {
                return getAttributeValue(i)
            }
        }
        return null
    }

    private fun AttributeSet.attrResId(name: String): Int {
        val ns = "http://schemas.android.com/apk/res/android"
        return runCatching { getAttributeResourceValue(ns, name, 0) }.getOrDefault(0)
    }
}
