package com.vdigital.volumestream.feature.settings.di

import com.vdigital.volumestream.ui.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    viewModelOf(::SettingsViewModel)
}

