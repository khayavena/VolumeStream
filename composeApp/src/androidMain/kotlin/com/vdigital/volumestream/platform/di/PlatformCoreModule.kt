package com.vdigital.volumestream.platform.di

import com.vdigital.volumestream.compnent.PlayerComponent
import com.vdigital.volumestream.compnent.PlayerComponentImpl
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val platformCoreModule: Module = module {
    single { androidApplication() }
    single<PlayerComponent> { PlayerComponentImpl(get()) }
    single<PlaybackStateController> { PlaybackStateController(get()) }
}
