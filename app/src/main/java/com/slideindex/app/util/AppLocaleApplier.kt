package com.slideindex.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.slideindex.app.settings.AppUiLanguage
import java.util.Locale

object AppLocaleApplier {
    private const val PREFS_NAME = "app_locale_cache"
    private const val KEY_STORAGE_TAG = "app_ui_language_tag"

    @Volatile
    private var lastApplied: AppUiLanguage = AppUiLanguage.SYSTEM

    fun currentLanguage(): AppUiLanguage = lastApplied

    /**
     * `attachBaseContext` 期间专用：Application 还没 attach 完，`applicationContext` 为 null，
     * 所以必须用传进来的 base 直接读 SharedPreferences（只读一个键，微秒级）。
     */
    fun primeFromContext(context: Context) {
        val tag = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STORAGE_TAG, null)
        }.getOrNull()
        lastApplied = AppUiLanguage.fromStorageTag(tag)
    }

    /**
     * 非主进程启动时用：只读一个 SharedPreferences。
     *
     * `:overlay` 进程启动路径上不能同步读 DataStore、也不该再写一次系统 LocaleManager，
     * 否则冷启时前台服务可能来不及 startForeground（实测会抛
     * ForegroundServiceDidNotStartInTimeException）。主进程在应用语言时会缓存这个 tag。
     */
    fun primeFromStorage(context: Context) {
        primeFromContext(context.applicationContext ?: context)
    }

    fun apply(context: Context, language: AppUiLanguage, refreshOverlays: Boolean = false) {
        val changed = language != lastApplied
        lastApplied = language
        persistStorageTag(context, language)
        val tags = language.toLanguageTags()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.applicationContext.getSystemService(
                android.app.LocaleManager::class.java,
            )
            localeManager.applicationLocales = if (tags == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(tags)
            }
        } else {
            val locales = if (tags == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tags)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
        if (refreshOverlays && changed) {
            com.slideindex.app.overlay.OverlayLocaleCoordinator.onApplicationLocaleChanged(context)
        }
    }

    fun wrapContextIfNeeded(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        return wrapOverlayContext(base)
    }

    /**
     * 无障碍浮窗经 [android.content.Context.createWindowContext] 后常仍按系统语言读资源；
     * 对非 SYSTEM 的应用内语言显式包一层 Configuration。
     */
    fun wrapOverlayContext(base: Context): Context {
        val locale = resolveAppLocale() ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    private fun resolveAppLocale(): Locale? {
        val tags = lastApplied.toLanguageTags() ?: return null
        return Locale.forLanguageTag(tags)
    }

    private fun persistStorageTag(context: Context, language: AppUiLanguage) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STORAGE_TAG, language.toStorageTag())
                .apply()
        }
    }
}
