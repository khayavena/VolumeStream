package com.vdigital.volumestream.platform.di

import com.vdigital.volumestream.platform.controller.PlaybackStateController
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformCoreModule: Module = module {
    single<PlaybackStateController> { PlaybackStateController() }
}