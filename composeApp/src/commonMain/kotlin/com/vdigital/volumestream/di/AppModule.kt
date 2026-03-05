package com.vdigital.volumestream.di

import com.vdigital.volumestream.core.player.di.playerCoreModule
import com.vdigital.volumestream.feature.home.di.homeModule
import com.vdigital.volumestream.feature.playback.di.playbackModule
import com.vditital.data.di.externalDataModule
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module
    get() = module {
        includes(externalDataModule + playerCoreModule + homeModule + playbackModule)
    }
