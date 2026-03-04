package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val SeekGreen = Color(0xFF00E676)

private fun Long.toFormattedTime(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    // Use padStart instead of String.format (JVM-only, not available in Kotlin/Native)
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackSeekBar() {
    val viewModel: PlaybackViewModel = koinViewModel()
    val progress = viewModel.progressStateUI.collectAsState()
    val durationMs = viewModel.durationMsUI.collectAsState()

    // Local drag state — thumb moves smoothly without calling seekTo every frame
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val displayValue = if (isDragging) dragValue else progress.value
    val displayPositionMs = (displayValue * durationMs.value).toLong()

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayValue,
            onValueChange = { newValue ->
                isDragging = true
                dragValue = newValue          // only update local state while dragging
            },
            onValueChangeFinished = {
                // seekTo called exactly once when the finger lifts
                viewModel.onSeekChanged(dragValue)
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = SeekGreen,
                activeTrackColor = SeekGreen,
                inactiveTrackColor = Color(0xFF2E2E2E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(displayPositionMs.toFormattedTime(), color = SeekGreen, fontSize = 12.sp)
            Text(durationMs.value.toFormattedTime(), color = SeekGreen, fontSize = 12.sp)
        }
    }
}