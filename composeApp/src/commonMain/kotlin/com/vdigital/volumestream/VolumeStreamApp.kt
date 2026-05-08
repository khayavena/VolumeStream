package com.vdigital.volumestream

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.vdigital.volumestream.ui.view.MainNavigationControllerView
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinContext

@Composable
@Preview
fun VolumeStreamApp() {
    KoinContext {
        MaterialTheme {
            MainNavigationControllerView()
        }
    }
}
