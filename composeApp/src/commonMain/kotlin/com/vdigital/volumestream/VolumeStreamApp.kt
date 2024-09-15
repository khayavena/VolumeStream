package com.vdigital.volumestream

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.vdigital.volumestream.di.appModule
import com.vdigital.volumestream.ui.view.PlaybackView
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication

@Composable
@Preview
fun VolumeStreamApp() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        MaterialTheme {
            PlaybackView()
        }
    }
}