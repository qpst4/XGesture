package com.slideindex.app.launcher

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandLabelResolver
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.util.AppLocaleApplier

object QuickLauncherLabels {
    fun defaultTypeLabel(context: Context, type: QuickLauncherItemType): String =
        when (type) {
            QuickLauncherItemType.APP -> ""
            QuickLauncherItemType.SHORTCUT ->
                context.getString(R.string.quick_launcher_item_shortcut)
            QuickLauncherItemType.ACTION ->
                context.getString(R.string.quick_launcher_item_action)
            QuickLauncherItemType.WIDGET ->
                context.getString(R.string.quick_launcher_item_widget)
            QuickLauncherItemType.FOLDER ->
                context.getString(R.string.quick_launcher_item_folder)
        }

    fun resolveLabel(
        context: Context,
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        /**
         * Shell 命令表：动作里只存命令文本，渲染时按文本回查用户自定义名称。
         * 不传时退回通用文案（宏、打开链接等仍按动作本身解析）。
         */
        shellCommands: List<ShellCommand> = emptyList(),
    ): String {
        val localized = AppLocaleApplier.wrapOverlayContext(context)
        return when (item.type) {
            QuickLauncherItemType.APP -> {
                val target = item.appTarget
                val base = appsByPackage[target?.packageName ?: item.payload]?.label
                    ?: item.label.ifBlank { item.payload }
                if (target?.isClone == true) {
                    com.slideindex.app.overlay.CloneAppsIndex.labelForUser(
                        localized,
                        target.cloneUserId,
                        base,
                    )
                } else {
                    base
                }
            }
            QuickLauncherItemType.SHORTCUT ->
                item.label.ifBlank { defaultTypeLabel(localized, item.type) }
            QuickLauncherItemType.ACTION -> {
                val action = QuickLauncherItemCodec.parseActionPayload(item.payload)
                if (action != null) {
                    val customShellLabel = (action as? GestureAction.ExecuteShellCommand)
                        ?.let { ShellCommandLabelResolver.customLabelFor(it.command, shellCommands) }
                    customShellLabel ?: gestureActionLabelText(localized, action)
                } else {
                    item.label.ifBlank { defaultTypeLabel(localized, item.type) }
                }
            }
            QuickLauncherItemType.WIDGET ->
                item.label.ifBlank { defaultTypeLabel(localized, item.type) }
            QuickLauncherItemType.FOLDER ->
                item.label.ifBlank { defaultTypeLabel(localized, item.type) }
        }
    }
}
