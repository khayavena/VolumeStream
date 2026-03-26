package com.vditital.data.security

import android.content.Context
import android.content.SharedPreferences

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vs_settings", Context.MODE_PRIVATE)

    actual fun isWifiOnlyDownloads(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, true)
    actual fun setWifiOnlyDownloads(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    actual fun isAutoPlay(): Boolean = prefs.getBoolean(KEY_AUTO_PLAY, true)
    actual fun setAutoPlay(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY, value).apply()
    }

    actual fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, false)
    actual fun setNotificationsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    }

    actual fun areSubtitlesEnabled(): Boolean = prefs.getBoolean(KEY_SUBTITLES, false)
    actual fun setSubtitlesEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLES, value).apply()
    }

    actual fun isAdaptiveQuality(): Boolean = prefs.getBoolean(KEY_ADAPTIVE_QUALITY, true)
    actual fun setAdaptiveQuality(value: Boolean) {
        prefs.edit().putBoolean(KEY_ADAPTIVE_QUALITY, value).apply()
    }

    private companion object {
        const val KEY_WIFI_ONLY        = "wifi_only_downloads"
        const val KEY_AUTO_PLAY        = "auto_play"
        const val KEY_NOTIFICATIONS    = "notifications_enabled"
        const val KEY_SUBTITLES        = "subtitles_enabled"
        const val KEY_ADAPTIVE_QUALITY = "adaptive_quality"
    }
}

