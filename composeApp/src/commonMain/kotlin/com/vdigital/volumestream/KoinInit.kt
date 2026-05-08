package com.vdigital.volumestream

import com.vdigital.volumestream.di.appModule
import org.koin.core.context.startKoin

/**
 * Starts Koin only once per process.
 */
fun initKoinIfNeeded() {
    try {
        startKoin {
            configureKoin()
            modules(appModule)
        }
    } catch (e: IllegalStateException) {
        if (!e.message.orEmpty().contains("already been started", ignoreCase = true)) throw e
    }
}

