package com.vdigital.volumestream.ui.viewmodel.state

sealed class PlaybackState {
    data object Buffering      : PlaybackState()
    data object Playing        : PlaybackState()
    data object Paused         : PlaybackState()
    data object Ended          : PlaybackState()
    /** Emitted when ExoPlayer receives a 401 mid-stream (ERROR_CODE_AUTHENTICATION_EXPIRED). */
    data object SessionExpired : PlaybackState()
    data class  Error(val errorMessage: String) : PlaybackState()
}
