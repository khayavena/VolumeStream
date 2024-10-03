package com.vdigital.volumestream.platform.di

import com.vdigital.volumestream.AndroidApp
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactory
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactoryImpl
import com.vdigital.volumestream.compnent.Media3Media3PlayerComponentImpl
import com.vdigital.volumestream.compnent.Media3PlayerComponent
import com.vdigital.volumestream.datasource.RemoteApiClientFactory
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformCoreModule: Module = module {
    single<Media3PlayerComponent> {
        Media3Media3PlayerComponentImpl(
            AndroidApp.getAppInstance(),
            get()
        )
    }
    single<PlaybackStateController> { PlaybackStateController(get()) }
    single<RemoteApiClientFactory> { RemoteApiClientFactory() }
    single<OsType> { OsType.ANDROID }
    single<CachedPlaybackDataSourceFactory> {
        CachedPlaybackDataSourceFactoryImpl(
            AndroidApp.getAppInstance()
        )
    }
}
