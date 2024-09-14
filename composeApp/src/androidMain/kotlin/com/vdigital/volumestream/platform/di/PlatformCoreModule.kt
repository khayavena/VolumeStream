package com.vdigital.volumestream.platform.di

import com.vdigital.volumestream.AndroidApp
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactory
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactoryImpl
import com.vdigital.volumestream.compnent.PlayerComponent
import com.vdigital.volumestream.compnent.PlayerComponentImpl
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformCoreModule: Module = module {
    single<PlayerComponent> { PlayerComponentImpl(AndroidApp.getAppInstance(), get()) }
    single<PlaybackStateController> { PlaybackStateController(get()) }
    single<OsType> { OsType.ANDROID }
    single<CachedPlaybackDataSourceFactory> {
        CachedPlaybackDataSourceFactoryImpl(
            AndroidApp.getAppInstance()
        )
    }
}
