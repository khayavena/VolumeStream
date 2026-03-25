package com.vdigital.volumestream.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState

@Composable
fun PlaybackBufferingIndicator(playbackState: PlaybackState) {
    AnimatedVisibility(
        visible = playbackState == PlaybackState.Buffering,
        enter = fadeIn(), exit = fadeOut()
    ) {
        CircularProgressIndicator(color = Color(0xFF00E676), strokeWidth = 4.dp)
    }
}
