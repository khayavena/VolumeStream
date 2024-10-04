package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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


@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlayPauseControl(onPlayPause: () -> Unit) {
    val viewModel: PlaybackViewModel = koinViewModel()
    val state = viewModel.playBackStateUI.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(56.dp)
                .background(Color.Gray, CircleShape)
        ) {
            val icon = if (state.value == PlaybackState.Playing) "❚❚" else "▶"
            Text(icon, fontSize = 24.sp, color = Color.White)
        }
    }
    LaunchedEffect(state){

    }
}