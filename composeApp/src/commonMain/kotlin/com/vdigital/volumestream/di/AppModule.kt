package com.vdigital.volumestream.di

import com.vdigital.volumestream.core.player.di.playerCoreModule
import com.vdigital.volumestream.feature.home.di.homeModule
import com.vdigital.volumestream.feature.playback.di.playbackModule
import com.vdigital.volumestream.feature.profile.di.profileModule
import com.vdigital.volumestream.feature.settings.di.settingsModule
import com.vdigital.volumestream.ui.viewmodel.AuthViewModel
import com.vditital.data.di.externalDataModule
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module
    get() = module {
        includes(externalDataModule + playerCoreModule + homeModule + playbackModule + profileModule + settingsModule)
        viewModelOf(::AuthViewModel)
    }
