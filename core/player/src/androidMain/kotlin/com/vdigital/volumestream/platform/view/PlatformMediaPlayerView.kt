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
    val exoPlayer = remember { playbackStateController.getExoPlayer() }
    val playerView = remember {
        PlayerView(context).apply {
            player = exoPlayer
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
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale, clip = true)
    )
}
