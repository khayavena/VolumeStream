package com.vdigital.volumestream.ui.viewmodel.state

sealed class PlaybackState {
    data object Buffering : PlaybackState()
    data object Playing : PlaybackState()
    data object Paused : PlaybackState()
    data class Error(val errorMessage: String) : PlaybackState()
}