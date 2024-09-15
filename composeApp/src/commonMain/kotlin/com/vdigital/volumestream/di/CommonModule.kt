package com.vdigital.volumestream.di

import com.vdigital.volumestream.repository.PlaybackMediaItemRepository
import com.vdigital.volumestream.repository.PlaybackMediaItemRepositoryImpl
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf

import org.koin.dsl.module


val commonModule = module {
    single<PlaybackMediaItemRepository> { PlaybackMediaItemRepositoryImpl() }
    viewModelOf(::PlaybackViewModel)
}