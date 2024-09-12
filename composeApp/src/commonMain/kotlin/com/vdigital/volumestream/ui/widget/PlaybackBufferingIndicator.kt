package com.vdigital.volumestream.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackBufferingIndicator() {
    val viewModel: PlaybackViewModel = koinViewModel()
    val state = viewModel.playBackStateUI.collectAsState()
    AnimatedVisibility(
        visible = state.value == PlaybackState.bufferig,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        CircularProgressIndicator(
            color = Color.Blue,
            strokeWidth = 4.dp
        )
    }
}