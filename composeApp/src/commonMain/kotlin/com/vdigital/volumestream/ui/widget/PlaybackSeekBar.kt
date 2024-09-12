package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackSeekBar() {
    val viewModel: PlaybackViewModel = koinViewModel()
    val state = viewModel.progressStateUI.collectAsState()
    Column {
        Slider(
            value = state.value,
            onValueChange = {
                viewModel.getPlatformController()
                    .seekTo((it * viewModel.getPlatformController().duration()).toLong())
                viewModel.onSeekChanged(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}