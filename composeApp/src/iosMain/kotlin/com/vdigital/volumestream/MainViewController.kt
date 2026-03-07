package com.vdigital.volumestream

import androidx.compose.ui.window.ComposeUIViewController
import com.vditital.data.util.AppLogger

fun MainViewController() = ComposeUIViewController {
    AppLogger.init()
    VolumeStreamApp()
}