package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

data class AppCarouselSwitcherSettings(
    val cancelDistanceUpDp: Int = DEFAULT_CANCEL_DISTANCE_DP,
    val cancelDistanceDownDp: Int = DEFAULT_CANCEL_DISTANCE_DP,
    /** 水平滑动方向反转：开启后朝手掌方向滑是回退，反方向滑才浏览历史应用。 */
    val slideReversed: Boolean = false,
    /** 左右切换灵敏度（1~10）：越大，需要的滑动行程越短、切换越快。 */
    val slideSensitivity: Int = DEFAULT_SLIDE_SENSITIVITY,
) {
    /** 左右切换一整个列表所需的手指行程（px）。 */
    fun slideTravelPx(density: Float): Float {
        val t = (slideSensitivity.coerceIn(MIN_SLIDE_SENSITIVITY, MAX_SLIDE_SENSITIVITY) - 1) /
            (MAX_SLIDE_SENSITIVITY - 1).toFloat()
        return (MAX_SLIDE_TRAVEL_DP + (MIN_SLIDE_TRAVEL_DP - MAX_SLIDE_TRAVEL_DP) * t) * density
    }
    fun cancelDistanceUpPx(density: Float): Float {
        return cancelDistanceUpDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP) * density
    }

    fun cancelDistanceDownPx(density: Float): Float {
        return cancelDistanceDownDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP) * density
    }

    companion object {
        const val MIN_CANCEL_DISTANCE_DP = 60
        const val MAX_CANCEL_DISTANCE_DP = 400
        const val DEFAULT_CANCEL_DISTANCE_DP = 120
        const val MIN_SLIDE_SENSITIVITY = 1
        const val MAX_SLIDE_SENSITIVITY = 10
        const val DEFAULT_SLIDE_SENSITIVITY = 5
        /** 灵敏度最低（1 档）时切换整列需要的行程。 */
        const val MAX_SLIDE_TRAVEL_DP = 280
        /** 灵敏度最高（10 档）时切换整列需要的行程。 */
        const val MIN_SLIDE_TRAVEL_DP = 60

        fun fromPreferences(prefs: Preferences): AppCarouselSwitcherSettings {
            val legacyUnified = prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DP]
                ?: DEFAULT_CANCEL_DISTANCE_DP
            return AppCarouselSwitcherSettings(
                cancelDistanceUpDp = prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_UP_DP]
                    ?: legacyUnified,
                cancelDistanceDownDp = prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DOWN_DP]
                    ?: legacyUnified,
                slideReversed = prefs[SettingsPreferenceKeys.APP_CAROUSEL_SLIDE_REVERSED] ?: false,
                slideSensitivity = prefs[SettingsPreferenceKeys.APP_CAROUSEL_SLIDE_SENSITIVITY]
                    ?: DEFAULT_SLIDE_SENSITIVITY,
            )
        }

        fun writeToPreferences(settings: AppCarouselSwitcherSettings, prefs: MutablePreferences) {
            val up = settings.cancelDistanceUpDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP)
            val down = settings.cancelDistanceDownDp.coerceIn(MIN_CANCEL_DISTANCE_DP, MAX_CANCEL_DISTANCE_DP)
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_UP_DP] = up
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DOWN_DP] = down
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DP] = down
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_SEPARATE_CANCEL_DISTANCES] = true
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_BOTH_DIRECTIONS] = true
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_SLIDE_REVERSED] = settings.slideReversed
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_SLIDE_SENSITIVITY] =
                settings.slideSensitivity.coerceIn(MIN_SLIDE_SENSITIVITY, MAX_SLIDE_SENSITIVITY)
        }
    }
}
