package com.vditital.data.security

import platform.Foundation.NSUserDefaults

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    /** Returns [default] when the key has never been written (NSUserDefaults returns false
     *  for unset booleans, so we must check key existence for keys whose default is true). */
    private fun getBool(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else default

    actual fun isWifiOnlyDownloads(): Boolean = getBool(KEY_WIFI_ONLY, true)
    actual fun setWifiOnlyDownloads(value: Boolean) {
        defaults.setBool(value, forKey = KEY_WIFI_ONLY); defaults.synchronize()
    }

    actual fun isAutoPlay(): Boolean = getBool(KEY_AUTO_PLAY, true)
    actual fun setAutoPlay(value: Boolean) {
        defaults.setBool(value, forKey = KEY_AUTO_PLAY); defaults.synchronize()
    }

    actual fun isNotificationsEnabled(): Boolean = getBool(KEY_NOTIFICATIONS, false)
    actual fun setNotificationsEnabled(value: Boolean) {
        defaults.setBool(value, forKey = KEY_NOTIFICATIONS); defaults.synchronize()
    }

    actual fun areSubtitlesEnabled(): Boolean = getBool(KEY_SUBTITLES, false)
    actual fun setSubtitlesEnabled(value: Boolean) {
        defaults.setBool(value, forKey = KEY_SUBTITLES); defaults.synchronize()
    }

    actual fun isAdaptiveQuality(): Boolean = getBool(KEY_ADAPTIVE_QUALITY, true)
    actual fun setAdaptiveQuality(value: Boolean) {
        defaults.setBool(value, forKey = KEY_ADAPTIVE_QUALITY); defaults.synchronize()
    }

    private companion object {
        const val KEY_WIFI_ONLY        = "vs_wifi_only_downloads"
        const val KEY_AUTO_PLAY        = "vs_auto_play"
        const val KEY_NOTIFICATIONS    = "vs_notifications_enabled"
        const val KEY_SUBTITLES        = "vs_subtitles_enabled"
        const val KEY_ADAPTIVE_QUALITY = "vs_adaptive_quality"
    }
}

