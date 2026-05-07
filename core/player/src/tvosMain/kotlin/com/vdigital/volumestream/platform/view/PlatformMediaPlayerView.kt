package com.vdigital.volumestream.platform.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vdigital.volumestream.platform.controller.PlaybackStateController

@Composable
actual fun PlatformMediaPlayerView(
    modifier: Modifier,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit,
    isZoomed: Boolean
) {
    // TODO: add a native tvOS player view implementation when Compose tvOS interop is available.
    Box(modifier = modifier.background(Color.Black))
}

