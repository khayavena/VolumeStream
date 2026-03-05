package com.vdigital.volumestream.feature.playback.di

import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val playbackModule = module {
    viewModelOf(::PlaybackViewModel)
}
