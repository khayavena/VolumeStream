package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.security.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs [SettingsScreen] with persisted state from [SettingsStore].
 *
 * Each setting is exposed as a [StateFlow] so Compose automatically recomposes
 * when a value changes, and persisted immediately via [SettingsStore] on mutation.
 *
 * All [SettingsStore] reads/writes are dispatched to [Dispatchers.IO] so that
 * SharedPreferences (including EncryptedSharedPreferences AES operations) never
 * block the Main thread.
 */
class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    // Seeded with safe defaults; the init block overwrites them from disk off Main.
    private val _autoPlay        = MutableStateFlow(true)
    private val _wifiOnly        = MutableStateFlow(true)
    private val _notifications   = MutableStateFlow(false)
    private val _subtitles       = MutableStateFlow(false)
    private val _adaptiveQuality = MutableStateFlow(true)

    val autoPlay        = _autoPlay.asStateFlow()
    val wifiOnly        = _wifiOnly.asStateFlow()
    val notifications   = _notifications.asStateFlow()
    val subtitles       = _subtitles.asStateFlow()
    val adaptiveQuality = _adaptiveQuality.asStateFlow()

    init {
        // Read persisted values off the Main thread — SharedPreferences first-access
        // (and especially EncryptedSharedPreferences key derivation) must not stall the UI.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _autoPlay.value        = settingsStore.isAutoPlay()
                _wifiOnly.value        = settingsStore.isWifiOnlyDownloads()
                _notifications.value   = settingsStore.isNotificationsEnabled()
                _subtitles.value       = settingsStore.areSubtitlesEnabled()
                _adaptiveQuality.value = settingsStore.isAdaptiveQuality()
            }
        }
    }

    fun setAutoPlay(value: Boolean) {
        _autoPlay.value = value
        viewModelScope.launch(Dispatchers.IO) { settingsStore.setAutoPlay(value) }
    }

    fun setWifiOnly(value: Boolean) {
        _wifiOnly.value = value
        viewModelScope.launch(Dispatchers.IO) { settingsStore.setWifiOnlyDownloads(value) }
    }

    fun setNotifications(value: Boolean) {
        _notifications.value = value
        viewModelScope.launch(Dispatchers.IO) { settingsStore.setNotificationsEnabled(value) }
    }

    fun setSubtitles(value: Boolean) {
        _subtitles.value = value
        viewModelScope.launch(Dispatchers.IO) { settingsStore.setSubtitlesEnabled(value) }
    }

    fun setAdaptiveQuality(value: Boolean) {
        _adaptiveQuality.value = value
        viewModelScope.launch(Dispatchers.IO) { settingsStore.setAdaptiveQuality(value) }
    }
}
