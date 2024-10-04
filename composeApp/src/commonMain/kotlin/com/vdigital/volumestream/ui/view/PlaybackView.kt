package com.vdigital.volumestream.ui.view


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.platform.enum.OsType
import com.vdigital.volumestream.platform.view.PlatformMediaPlayerView
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.widget.PlayPauseControl
import com.vdigital.volumestream.ui.widget.PlaybackBufferingIndicator
import com.vdigital.volumestream.ui.widget.PlaybackSeekBar
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI


@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackView() {
    val viewModel: PlaybackViewModel = koinViewModel()
    val state = viewModel.playBackStateUI.collectAsState()
    val controller = remember { viewModel.getPlatformController() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), contentAlignment = Alignment.Center
    ) {
        when (viewModel.osType) {
            OsType.IOS -> PlatformMediaPlayerView(
                modifier = Modifier
                    .fillMaxSize(), controller
            )

            OsType.ANDROID -> if (state.value == PlaybackState.Playing || state.value == PlaybackState.Paused) {
                PlatformMediaPlayerView(
                    modifier = Modifier
                        .fillMaxSize(), controller
                )
            }
        }
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
            PlaybackSeekBar()
        }
        PlaybackBufferingIndicator()
    }
    LaunchedEffect(Unit) {
        viewModel.initialise()
    }
}



