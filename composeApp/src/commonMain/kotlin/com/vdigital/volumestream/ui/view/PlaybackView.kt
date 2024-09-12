package com.vdigital.volumestream.ui.view


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.platform.view.PlatformMediaPlayerView
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.widget.PlayPauseControl
import com.vdigital.volumestream.ui.widget.PlaybackBufferingIndicator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI


@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackView() {
    val viewModel: PlaybackViewModel = koinViewModel()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), contentAlignment = Alignment.Center
    ) {
        PlatformMediaPlayerView(
            modifier = Modifier
                .fillMaxSize(), viewModel.getPlatformController()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlayPauseControl(onPlayPause = {
                viewModel.playPause()
            })
            Spacer(modifier = Modifier.height(8.dp))

            SeekBar()
        }
        PlaybackBufferingIndicator()
    }
}


@OptIn(KoinExperimentalAPI::class)
@Composable
fun SeekBar() {
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
