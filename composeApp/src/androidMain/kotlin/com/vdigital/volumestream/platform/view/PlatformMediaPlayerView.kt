package com.vdigital.volumestream.platform.view

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.vdigital.volumestream.platform.controller.PlaybackStateController

@Composable
actual fun PlatformMediaPlayerView(
    modifier: Modifier, playbackStateController: PlaybackStateController
) {
    val controller = remember {
        mutableStateOf(playbackStateController.getController())
    }
    DisposableEffect(Unit) {
        onDispose {
            playbackStateController.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = controller.value
                useController = false // Show playback controls
            }
        }, modifier = Modifier.fillMaxSize()
    )
}