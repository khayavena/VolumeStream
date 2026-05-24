package com.vdigital.volumestream

import androidx.compose.ui.window.ComposeUIViewController
import com.vditital.data.util.AppLogger

fun TvMainViewController() = ComposeUIViewController {
    VolumeStreamApp()
}.also {
    AppLogger.init()
    initKoinIfNeeded()
}

