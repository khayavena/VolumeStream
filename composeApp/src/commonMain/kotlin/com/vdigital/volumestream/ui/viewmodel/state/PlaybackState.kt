package com.vdigital.volumestream.ui.viewmodel.state

sealed class PlaybackState {
    data object bufferig : PlaybackState()
    data object playing : PlaybackState()
    data object paused : PlaybackState()
    data class error(val errorMessage: String) : PlaybackState()
}