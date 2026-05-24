package com.vdigital.volumestream

import androidx.compose.ui.window.ComposeUIViewController
import com.vditital.data.util.AppLogger

fun MainViewController() = ComposeUIViewController {
    println("[iOS][MainViewController] ComposeUIViewController content init")
    VolumeStreamApp()
}.also {
    println("[iOS][MainViewController] controller created")
    AppLogger.init()
    println("[iOS][MainViewController] AppLogger.init done")
    initKoinIfNeeded()
    println("[iOS][MainViewController] initKoinIfNeeded done")
}