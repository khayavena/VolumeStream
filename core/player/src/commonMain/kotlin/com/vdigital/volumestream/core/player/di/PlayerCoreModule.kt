package com.vdigital.volumestream.core.player.di

import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import org.koin.core.module.Module
import org.koin.dsl.module

val playerCoreModule: Module = module {
    includes(platformPlayerModule)
    single { SelectedMediaItemHolder() }
}

internal expect val platformPlayerModule: Module
