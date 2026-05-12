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
    } catch (e: Exception) {
        val alreadyStarted =
            e::class.simpleName.orEmpty().contains("AlreadyStartedException") ||
                e.message.orEmpty().contains("already been started", ignoreCase = true)
        if (!alreadyStarted) throw e
    }
}
