package com.vdigital.volumestream.di

import com.vdigital.volumestream.ui.viewmodel.HomaPageViewModel
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module


val viewModelModule = module {
    viewModelOf(::PlaybackViewModel)
    viewModelOf(::HomaPageViewModel)
}