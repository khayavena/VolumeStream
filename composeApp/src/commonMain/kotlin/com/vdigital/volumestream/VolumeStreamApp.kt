package com.vdigital.volumestream

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vdigital.volumestream.di.appModule
import com.vdigital.volumestream.ui.view.MainNavigationControllerView
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.core.context.startKoin
import org.koin.dsl.koinApplication

@Composable
@Preview
fun VolumeStreamApp() {
    remember {
        val koinApp = koinApplication {
            configureKoin()
            modules(appModule)
        }
        try {
            startKoin(koinApp)
        } catch (e: IllegalStateException) {
            if (!e.message.orEmpty().contains("already been started", ignoreCase = true)) throw e
        }
    }

    MaterialTheme {
        MainNavigationControllerView()
    }
}
