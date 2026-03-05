package com.vdigital.volumestream.platform.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.vdigital.volumestream.platform.controller.PlaybackStateController

@Composable
actual fun PlatformMediaPlayerView(
    modifier: Modifier,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember { playbackStateController.getExoPlayer() }
    val playerView = remember {
        PlayerView(context).apply {
            player = exoPlayer
            useController = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            playerView.player = null
            playbackStateController.release()
        }
    }
    AndroidView(factory = { playerView }, modifier = modifier)
}
