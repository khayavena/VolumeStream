package com.vdigital.volumestream.platform.view

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vdigital.volumestream.platform.controller.PlaybackStateController

@Composable
actual fun PlatformMediaPlayerView(
    modifier: Modifier,
    playbackStateController: PlaybackStateController,
    onTap: () -> Unit,
    isZoomed: Boolean
) {
    val context = LocalContext.current
    // Do NOT cache the ExoPlayer via remember — initPlayer() releases and rebuilds
    // it, so a remembered reference would point to a dead player (black video + audio).
    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isZoomed) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playerZoom"
    )

    DisposableEffect(Unit) {
        onDispose {
            playerView.player = null
            playbackStateController.release()
        }
    }
    AndroidView(
        factory = { playerView },
        update = { view ->
            // Re-attach the current ExoPlayer to the surface on every recomposition.
            // initPlayer() rebuilds the ExoPlayer instance; without this update block
            // the PlayerView stays bound to the old released player → black video + audio only.
            val currentPlayer = playbackStateController.getExoPlayer()
            if (view.player !== currentPlayer) {
                view.player = currentPlayer
            }
        },
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale, clip = true)
    )
}
