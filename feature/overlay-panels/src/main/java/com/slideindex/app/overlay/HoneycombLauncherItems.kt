package com.slideindex.app.overlay

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType

/** Items shown in the honeycomb overlay and layout preview (matches runtime filter). */
fun List<QuickLauncherItem>.honeycombRuntimeItems(): List<QuickLauncherItem> =
    filter {
        it.type == QuickLauncherItemType.APP ||
            it.type == QuickLauncherItemType.SHORTCUT ||
            it.type == QuickLauncherItemType.ACTION
    }
