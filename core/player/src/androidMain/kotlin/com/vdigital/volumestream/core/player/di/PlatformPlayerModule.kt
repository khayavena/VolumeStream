package com.vdigital.volumestream.core.player.di

import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactory
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactoryImpl
import com.vdigital.volumestream.compnent.Media3Media3PlayerComponentImpl
import com.vdigital.volumestream.compnent.Media3PlayerComponent
import com.vdigital.volumestream.config.PlayerConfig
import com.vdigital.volumestream.core.player.download.DownloadController
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformPlayerModule: Module = module {
    // PlayerConfig: default provided here; SDK consumers can override by
    // registering their own PlayerConfig singleton before playerCoreModule.
    single<PlayerConfig> { PlayerConfig() }

    single<CachedPlaybackDataSourceFactory> {
        CachedPlaybackDataSourceFactoryImpl(androidApplication(), get())
    }
    factory<Media3PlayerComponent> {
        Media3Media3PlayerComponentImpl(androidApplication(), get())
    }
    factory { PlaybackStateController(get()) }
    single<OsType> { OsType.ANDROID }
    single { DownloadController(androidApplication()) }
}
