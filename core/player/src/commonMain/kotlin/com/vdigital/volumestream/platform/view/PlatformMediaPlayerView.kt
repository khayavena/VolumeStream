package com.vdigital.volumestream.platform.view

import androidx.compose.runtime.Composable
import com.vdigital.volumestream.platform.controller.PlaybackStateController

@Composable
expect fun PlatformMediaPlayerView(
    modifier: Any,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit = {},
    isZoomed: Boolean = true
)
