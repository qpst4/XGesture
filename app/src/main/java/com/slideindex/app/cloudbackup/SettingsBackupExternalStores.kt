package com.slideindex.app.cloudbackup

import android.content.Context
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.fvgesture.FvGesturePrefs
import com.slideindex.app.macro.MacroStore
import com.slideindex.app.overlay.filebrowser.FileBrowserPreferences
import com.slideindex.app.overlay.pickresult.PickResultImageSharePrefs
import com.slideindex.app.overlay.quickmenu.QuickMenuPreferences
import com.slideindex.app.overlay.share.ShareSheetDefaults
import com.slideindex.app.overlay.share.ShareSheetPins
import com.slideindex.app.pagescroll.PageScrollPrefs
import com.slideindex.app.screencontrol.ScreenControlPrefs
import com.slideindex.app.settings.SettingsBackupExternalStore
import com.slideindex.app.stash.StashMetaRepository
import com.slideindex.app.stash.StashReminderMirror
import com.slideindex.app.stash.StashReminderScheduler
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.ui.ActionDisplayNameStore
import com.slideindex.app.ui.GlobalIconStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主 DataStore 之外的独立存储：导出、恢复与恢复后的内存刷新。
 *
 * 每一项的 id/模块/敏感标记登记在 [SettingsBackupExternalStore]（`feature/settings`），
 * 这里只负责“怎么读写”。文件目录不在这里（由 `SettingsBackupPaths` 按目录打包）。
 */
@Singleton
class SettingsBackupExternalStores @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clipboardHistoryRepository: ClipboardHistoryRepository,
    private val stashMetaRepository: StashMetaRepository,
    private val stashRepository: StashRepository,
) {

    suspend fun export(includeSensitive: Boolean): Map<String, String> {
        val stores = linkedMapOf<String, String>()
        fun prefs(store: SettingsBackupExternalStore, name: String) {
            stores[store.id] = SharedPreferencesBackupCodec.export(context, name)
        }

        prefs(SettingsBackupExternalStore.MACRO, MacroStore.PREFS_NAME)
        prefs(SettingsBackupExternalStore.ACTION_DISPLAY_NAMES, ActionDisplayNameStore.PREFS_NAME)
        prefs(SettingsBackupExternalStore.GLOBAL_ICON_OVERRIDES, GlobalIconStore.PREFS)
        prefs(SettingsBackupExternalStore.PAGE_SCROLL, PageScrollPrefs.PREFS_NAME)
        prefs(SettingsBackupExternalStore.SCREEN_CONTROL, ScreenControlPrefs.PREFS_NAME)
        prefs(SettingsBackupExternalStore.PICK_RESULT, PickResultImageSharePrefs.PREFS_NAME)
        prefs(SettingsBackupExternalStore.FV_GESTURE, FvGesturePrefs.PREFS_NAME)

        if (includeSensitive) {
            clipboardHistoryRepository.exportRawJson()
                .takeIf { it.isNotBlank() }
                ?.let { stores[SettingsBackupExternalStore.CLIPBOARD_HISTORY.id] = it }
        }

        stashMetaRepository.exportRawJson()
            .takeIf { it.isNotBlank() }
            ?.let { stores[SettingsBackupExternalStore.STASH_META.id] = it }

        FileBrowserPreferences.exportRawJson(context)
            .takeIf { it.isNotBlank() }
            ?.let { stores[SettingsBackupExternalStore.FILE_BROWSER.id] = it }

        ShareSheetPins.exportRawJson(context)
            .takeIf { it.isNotBlank() }
            ?.let { stores[SettingsBackupExternalStore.SHARE_SHEET.id] = it }

        ShareSheetDefaults.exportRawJson(context)
            .takeIf { it.isNotBlank() }
            ?.let { stores[SettingsBackupExternalStore.SHARE_DEFAULTS.id] = it }

        QuickMenuPreferences.exportRawJson(context)
            .takeIf { it.isNotBlank() }
            ?.let { stores[SettingsBackupExternalStore.QUICK_MENU.id] = it }

        return stores
    }

    suspend fun import(stores: Map<String, String>) {
        if (stores.isEmpty()) return

        stores[SettingsBackupExternalStore.MACRO.id]?.let {
            SharedPreferencesBackupCodec.import(context, MacroStore.PREFS_NAME, it)
        }
        stores[SettingsBackupExternalStore.ACTION_DISPLAY_NAMES.id]?.let {
            SharedPreferencesBackupCodec.import(context, ActionDisplayNameStore.PREFS_NAME, it)
        }
        stores[SettingsBackupExternalStore.GLOBAL_ICON_OVERRIDES.id]?.let {
            SharedPreferencesBackupCodec.import(context, GlobalIconStore.PREFS, it)
        }
        stores[SettingsBackupExternalStore.PAGE_SCROLL.id]?.let {
            SharedPreferencesBackupCodec.import(context, PageScrollPrefs.PREFS_NAME, it)
        }
        stores[SettingsBackupExternalStore.SCREEN_CONTROL.id]?.let {
            SharedPreferencesBackupCodec.import(context, ScreenControlPrefs.PREFS_NAME, it)
        }
        stores[SettingsBackupExternalStore.PICK_RESULT.id]?.let {
            SharedPreferencesBackupCodec.import(context, PickResultImageSharePrefs.PREFS_NAME, it)
        }
        stores[SettingsBackupExternalStore.FV_GESTURE.id]?.let {
            SharedPreferencesBackupCodec.import(context, FvGesturePrefs.PREFS_NAME, it)
        }

        stores[SettingsBackupExternalStore.FILE_BROWSER.id]?.let {
            FileBrowserPreferences.importRawJson(context, it)
        }
        stores[SettingsBackupExternalStore.SHARE_SHEET.id]?.let {
            ShareSheetPins.importRawJson(context, it)
        }
        stores[SettingsBackupExternalStore.SHARE_DEFAULTS.id]?.let {
            ShareSheetDefaults.importRawJson(context, it)
        }
        stores[SettingsBackupExternalStore.QUICK_MENU.id]?.let {
            QuickMenuPreferences.importRawJson(context, it)
        }

        stores[SettingsBackupExternalStore.CLIPBOARD_HISTORY.id]?.let {
            clipboardHistoryRepository.importRawJson(it)
        }

        stores[SettingsBackupExternalStore.STASH_META.id]?.let { raw ->
            if (stashMetaRepository.importRawJson(raw)) {
                rescheduleStashReminders()
            }
        }
    }

    private fun rescheduleStashReminders() {
        // 先清掉备份里已经不存在的旧闹钟/镜像，否则恢复后旧提醒还会响一次。
        StashReminderMirror.all(context).forEach { (entryId, _, _) ->
            StashReminderScheduler.cancel(context, entryId)
        }
        val reminders = stashMetaRepository.store.value.reminders
        if (reminders.isEmpty()) return
        StashReminderScheduler.rescheduleAll(context, reminders) { entryId ->
            stashRepository.entries.value.firstOrNull { it.id == entryId }?.text.orEmpty()
        }
    }
}
