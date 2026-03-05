package com.vdigital.volumestream.core.player.di

import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformPlayerModule: Module = module {
    factory { PlaybackStateController() }
    single<OsType> { OsType.IOS }
}
