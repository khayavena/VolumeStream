package com.vdigital.volumestream.platform.view

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.widget.PlaybackBufferingIndicator
import org.koin.compose.viewmodel.koinViewModel

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