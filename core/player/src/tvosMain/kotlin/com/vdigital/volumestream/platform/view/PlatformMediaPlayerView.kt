package com.vdigital.volumestream.platform.view

import androidx.compose.runtime.Composable
import com.vdigital.volumestream.platform.controller.PlaybackStateController

@Composable
actual fun PlatformMediaPlayerView(
    modifier: Any,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit,
    isZoomed: Boolean
) {
    // TODO: add a native tvOS player view implementation when Compose tvOS interop is available.
}
