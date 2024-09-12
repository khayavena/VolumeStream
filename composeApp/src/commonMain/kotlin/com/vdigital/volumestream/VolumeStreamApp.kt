package com.vdigital.volumestream

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vdigital.volumestream.di.coreModule
import com.vdigital.volumestream.ui.view.PlaybackView
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
@Preview
fun VolumeStreamApp() {
    KoinApplication(application = {
        modules(coreModule)
    }) {
        val viewModel: PlaybackViewModel = koinViewModel()
//        val state by remember { mutableStateOf(viewModel.playBackStateUI.value) }
//        val controller by remember { mutableStateOf(viewModel.getPlatformController()) }
        MaterialTheme {
            PlaybackView()
        }
        LaunchedEffect(Unit) {
            viewModel.initialise()
        }
    }
}