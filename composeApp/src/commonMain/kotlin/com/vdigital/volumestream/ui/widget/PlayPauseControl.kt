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
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val GreenAccent = Color(0xFF00E676)
private val ControlsBg = Color(0xFF000000)

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlayPauseControl(onPlayPause: () -> Unit) {
    val viewModel: PlaybackViewModel = koinViewModel()
    val state = viewModel.playBackStateUI.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Skip back 10 s
        IconButton(
            onClick = { viewModel.skipBackward() },
            modifier = Modifier
                .size(44.dp)
                .border(1.5.dp, GreenAccent, CircleShape)
                .background(ControlsBg, CircleShape)
        ) {
            Text("⏮10", fontSize = 12.sp, color = GreenAccent)
        }

        Spacer(Modifier.width(24.dp))

        // Play / Pause
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(GreenAccent, CircleShape)
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                val icon = if (state.value == PlaybackState.Playing) "❚❚" else "▶"
                Text(icon, fontSize = 26.sp, color = Color.Black)
            }
        }

        Spacer(Modifier.width(24.dp))

        // Skip forward 10 s
        IconButton(
            onClick = { viewModel.skipForward() },
            modifier = Modifier
                .size(44.dp)
                .border(1.5.dp, GreenAccent, CircleShape)
                .background(ControlsBg, CircleShape)
        ) {
            Text("10⏭", fontSize = 12.sp, color = GreenAccent)
        }
    }
}