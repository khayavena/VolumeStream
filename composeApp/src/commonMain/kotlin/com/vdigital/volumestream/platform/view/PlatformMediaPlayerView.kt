package com.vdigital.volumestream.platform.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vdigital.volumestream.platform.controller.PlaybackStateController


@Composable
expect fun PlatformMediaPlayerView(
    modifier: Modifier,
    playbackStateController: PlaybackStateController
)