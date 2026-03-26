package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vditital.data.security.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs [SettingsScreen] with persisted state from [SettingsStore].
 *
 * Each setting is exposed as a [StateFlow] so Compose automatically recomposes
 * when a value changes, and persisted immediately via [SettingsStore] on mutation.
 */
class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    private val _autoPlay        = MutableStateFlow(settingsStore.isAutoPlay())
    private val _wifiOnly        = MutableStateFlow(settingsStore.isWifiOnlyDownloads())
    private val _notifications   = MutableStateFlow(settingsStore.isNotificationsEnabled())
    private val _subtitles       = MutableStateFlow(settingsStore.areSubtitlesEnabled())
    private val _adaptiveQuality = MutableStateFlow(settingsStore.isAdaptiveQuality())

    val autoPlay        = _autoPlay.asStateFlow()
    val wifiOnly        = _wifiOnly.asStateFlow()
    val notifications   = _notifications.asStateFlow()
    val subtitles       = _subtitles.asStateFlow()
    val adaptiveQuality = _adaptiveQuality.asStateFlow()

    fun setAutoPlay(value: Boolean) {
        settingsStore.setAutoPlay(value)
        _autoPlay.value = value
    }

    fun setWifiOnly(value: Boolean) {
        settingsStore.setWifiOnlyDownloads(value)
        _wifiOnly.value = value
    }

    fun setNotifications(value: Boolean) {
        settingsStore.setNotificationsEnabled(value)
        _notifications.value = value
    }

    fun setSubtitles(value: Boolean) {
        settingsStore.setSubtitlesEnabled(value)
        _subtitles.value = value
    }

    fun setAdaptiveQuality(value: Boolean) {
        settingsStore.setAdaptiveQuality(value)
        _adaptiveQuality.value = value
    }
}

