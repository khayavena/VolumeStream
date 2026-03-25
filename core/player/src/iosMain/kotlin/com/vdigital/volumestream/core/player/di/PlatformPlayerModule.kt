package com.vdigital.volumestream.core.player.di

import com.vdigital.volumestream.config.PlayerConfig
import com.vdigital.volumestream.core.player.download.DownloadController
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformPlayerModule: Module = module {
    // PlayerConfig: default provided here; SDK consumers override before playerCoreModule.
    single<PlayerConfig> { PlayerConfig() }

    factory { PlaybackStateController(get()) }
    single<OsType> { OsType.IOS }
    single { DownloadController() }
}
