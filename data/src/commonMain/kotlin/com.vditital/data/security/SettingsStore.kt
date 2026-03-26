package com.vditital.data.security

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class SettingsStore {
    fun isWifiOnlyDownloads(): Boolean
    fun setWifiOnlyDownloads(value: Boolean)
    fun isAutoPlay(): Boolean
    fun setAutoPlay(value: Boolean)
    fun isNotificationsEnabled(): Boolean
    fun setNotificationsEnabled(value: Boolean)
    fun areSubtitlesEnabled(): Boolean
    fun setSubtitlesEnabled(value: Boolean)
    fun isAdaptiveQuality(): Boolean
    fun setAdaptiveQuality(value: Boolean)
}
