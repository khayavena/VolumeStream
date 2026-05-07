package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState

private val GreenAccent = Color(0xFF00E676)
private val SideButtonBg   = Color(0x8C000000)
private val CenterButtonBg = Color(0xB3000000)
private val SideButtonSize = 48.dp
private val CenterButtonSize = 60.dp
private val RingSize = 68.dp

@Composable
fun PlayPauseControl(
    viewModel: PlaybackViewModel,
    onPlayPause: () -> Unit
) {
    // Collect only playback state here — progress is isolated in ProgressRing
    // so this composable only recomposes when play/pause/buffering state changes.
    val state = viewModel.playBackStateUI.collectAsState()
    val progress = viewModel.progressStateUI.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = { viewModel.skipBackward() },
            modifier = Modifier
                .size(SideButtonSize)
                .border(1.5.dp, GreenAccent, CircleShape)
                .background(SideButtonBg, CircleShape)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Seek backward 10 seconds",
                    tint = GreenAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text("10", fontSize = 11.sp, color = GreenAccent)
            }
        }

        Spacer(Modifier.width(20.dp))

        // Box holds the progress ring (fast-changing) + play button (slow-changing)
        // in separate composable scopes so Compose can skip the button recompose
        // on every position tick.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(RingSize)
        ) {
            ProgressRing(progress = progress.value)
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(CenterButtonSize)
                    .border(1.dp, GreenAccent.copy(alpha = 0.45f), CircleShape)
                    .background(CenterButtonBg, CircleShape)
            ) {
                Icon(
                    imageVector = if (state.value == PlaybackState.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.value == PlaybackState.Playing) "Pause" else "Play",
                    tint = GreenAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(20.dp))

        IconButton(
            onClick = { viewModel.skipForward() },
            modifier = Modifier
                .size(SideButtonSize)
                .border(1.5.dp, GreenAccent, CircleShape)
                .background(SideButtonBg, CircleShape)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("10", fontSize = 11.sp, color = GreenAccent)
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Seek forward 10 seconds",
                    tint = GreenAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Isolated composable for the seek-progress ring.
 */
@Composable
private fun ProgressRing(progress: Float) {
    CircularProgressIndicator(
        progress    = progress,
        modifier    = Modifier.size(RingSize),
        strokeWidth = 3.dp,
        color       = GreenAccent
    )
}
