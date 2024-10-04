package com.vdigital.volumestream.platform.di

import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformCoreModule: Module = module {
    single<OsType> { OsType.IOS }
    single<PlaybackStateController> { PlaybackStateController() }
}