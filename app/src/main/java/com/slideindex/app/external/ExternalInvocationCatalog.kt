package com.slideindex.app.external

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.service.ToggleFloatBallTrampolineActivity

object ExternalInvocationCatalog {
    const val PACKAGE_NAME = "com.slideindex.app"
    const val SCHEME = AppLinks.SCHEME
    const val HOST = AppLinks.HOST
    const val QUERY_PARAM = AppLinks.QUERY_PARAM

    data class DeeplinkEntry(
        val titleRes: Int,
        val descriptionRes: Int,
        val path: String,
        val supportsQuery: Boolean
    )

    data class ActionEntry(
        val titleRes: Int,
        val descriptionRes: Int,
        val action: String,
        val componentClass: String,
        val supportsQuery: Boolean
    )

    val deeplinks: List<DeeplinkEntry> = listOf(
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_notification_history,
            descriptionRes = R.string.external_invocation_entry_notification_history_desc,
            path = "notification-history",
            supportsQuery = true
        ),
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_stash,
            descriptionRes = R.string.external_invocation_entry_stash_desc,
            path = "stash",
            supportsQuery = true
        ),
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_clipboard,
            descriptionRes = R.string.external_invocation_entry_clipboard_desc,
            path = "clipboard",
            supportsQuery = true
        ),
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_search_panel,
            descriptionRes = R.string.external_invocation_entry_search_panel_desc,
            path = "search-panel",
            supportsQuery = true
        )
    )

    val actions: List<ActionEntry> = listOf(
        ActionEntry(
            titleRes = R.string.external_invocation_entry_notification_history,
            descriptionRes = R.string.external_invocation_action_notification_history_desc,
            action = "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY",
            componentClass = "com.slideindex.app.MainActivity",
            supportsQuery = false
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_stash,
            descriptionRes = R.string.external_invocation_action_stash_desc,
            action = "com.slideindex.app.action.OPEN_STASH_PANEL",
            componentClass = "com.slideindex.app.service.StashClipboardTrampolineActivity",
            supportsQuery = true
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_clipboard,
            descriptionRes = R.string.external_invocation_action_clipboard_desc,
            action = "com.slideindex.app.action.OPEN_CLIPBOARD_PANEL",
            componentClass = "com.slideindex.app.service.StashClipboardTrampolineActivity",
            supportsQuery = true
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_search_panel,
            descriptionRes = R.string.external_invocation_action_search_panel_desc,
            action = "com.slideindex.app.action.OPEN_SEARCH_PANEL",
            componentClass = "com.slideindex.app.service.SearchPanelTrampolineActivity",
            supportsQuery = true
        ),
        ActionEntry(
            titleRes = R.string.file_browser_title,
            descriptionRes = R.string.file_browser_entry_desc,
            action = "com.slideindex.app.action.OPEN_FILE_BROWSER",
            componentClass = "com.slideindex.app.service.FileBrowserTrampolineActivity",
            supportsQuery = false
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_toggle_gesture,
            descriptionRes = R.string.external_invocation_action_toggle_gesture_desc,
            action = "com.slideindex.app.action.TOGGLE_GESTURE",
            componentClass = "com.slideindex.app.service.ToggleGestureTrampolineActivity",
            supportsQuery = false
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_toggle_float_ball,
            descriptionRes = R.string.external_invocation_action_toggle_float_ball_desc,
            action = ToggleFloatBallTrampolineActivity.ACTION_TOGGLE_FLOAT_BALL,
            componentClass = "com.slideindex.app.service.ToggleFloatBallTrampolineActivity",
            supportsQuery = false
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_shell_panel,
            descriptionRes = R.string.external_invocation_action_shell_panel_desc,
            action = "com.slideindex.app.action.OPEN_SHELL_PANEL",
            componentClass = "com.slideindex.app.service.ShellCommandPanelTrampolineActivity",
            supportsQuery = false
        )
    )

    fun deeplinkUri(path: String): String =
        AppLinks.uriString(path)

    fun adbViewCommand(uri: String): String =
        "adb shell am start -a android.intent.action.VIEW -d \"$uri\""

    fun adbActionCommand(context: Context, entry: ActionEntry): String {
        val component = componentRelativeName(entry.componentClass)
        val builder = StringBuilder()
            .append("adb shell am start -a ")
            .append(entry.action)
            .append(" -n ")
            .append(PACKAGE_NAME)
            .append('/')
            .append(component)
        if (entry.supportsQuery) {
            builder.append(" --es ")
                .append(QUERY_PARAM)
                .append(context.getString(R.string.external_invocation_keyword_example))
        }
        return builder.toString()
    }

    /**
     * 拼 `am -n <包>/<类>` 里的类名。
     *
     * 这里必须是**带点的相对名**（`.service.XxxActivity`）或全限定名。只去掉包名前缀、
     * 留下 `service.XxxActivity` 会被 `ComponentName` 当成全限定类名去解析，直接报
     * `Activity class ... does not exist`（外部调用页复制的命令曾因此跑不通）。
     */
    private fun componentRelativeName(componentClass: String): String =
        if (componentClass.startsWith("$PACKAGE_NAME.")) {
            "." + componentClass.removePrefix("$PACKAGE_NAME.")
        } else {
            componentClass
        }
}
