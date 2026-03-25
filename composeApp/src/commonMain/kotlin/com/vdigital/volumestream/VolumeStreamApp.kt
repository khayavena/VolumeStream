package com.vdigital.volumestream

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.vdigital.volumestream.di.appModule
import com.vdigital.volumestream.ui.view.MainNavigationControllerView
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.compose.KoinContext

@Composable
@Preview
fun VolumeStreamApp() {
    KoinApplication(application = {
        configureKoin()
        modules(appModule)
    }) {
        MaterialTheme {
            MainNavigationControllerView()
        }
    }
}
