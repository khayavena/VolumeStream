package com.vdigital.volumestream.platform.view

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vdigital.volumestream.platform.controller.PlaybackStateController

@OptIn(UnstableApi::class)
@Composable
actual fun PlatformMediaPlayerView(
    modifier: Modifier,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit,
    isZoomed: Boolean
) {
    val context = LocalContext.current
    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            // Start in FIT mode; zoom is toggled in the update block via resizeMode.
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            // NOTE: do NOT wrap this AndroidView in graphicsLayer() with a non-identity
            // transform or clip=true.  PlayerView uses SurfaceView by default, which renders
            // via a separate SurfaceFlinger layer.  Any Compose hardware layer (graphicsLayer
            // scale/clip) composites into an offscreen buffer that SurfaceView cannot punch
            // through — the result is audio-only with a black video surface.
            // Instead, zoom is achieved natively by switching resizeMode between
            // RESIZE_MODE_ZOOM (fill + crop) and RESIZE_MODE_FIT (letterbox).
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerView.player = null
            playbackStateController.release()
        }
    }

    AndroidView(
        factory = { playerView },
        update = { view ->
            // Toggle zoom via ExoPlayer's native resize mode — safe with SurfaceView.
            view.resizeMode = if (isZoomed)
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else
                AspectRatioFrameLayout.RESIZE_MODE_FIT

            // Re-attach the current ExoPlayer on every recomposition so that a
            // rebuilt player (playerReleased path) is always bound to the surface.
            val currentPlayer = playbackStateController.getExoPlayer()
            if (view.player !== currentPlayer) {
                view.player = currentPlayer
            }
        },
        modifier = modifier  // No graphicsLayer — SurfaceView must not be inside a hardware layer
    )
}
