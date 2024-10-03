package com.vdigital.volumestream.di

import com.vdigital.volumestream.datasource.RemoteApiClientFactory
import com.vdigital.volumestream.datasource.RemotePlaybackDataSource
import com.vdigital.volumestream.datasource.RemotePlaybackDataSourceImpl
import com.vdigital.volumestream.repository.PlaybackMediaItemRepository
import com.vdigital.volumestream.repository.PlaybackMediaItemRepositoryImpl
import com.vdigital.volumestream.ui.viewmodel.HomaPageViewModel
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import io.ktor.client.HttpClient
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module


val commonModule = module {
    single<HttpClient> { get<RemoteApiClientFactory>().create() }
    single<RemotePlaybackDataSource> { RemotePlaybackDataSourceImpl(get()) }
    single<PlaybackMediaItemRepository> { PlaybackMediaItemRepositoryImpl(get()) }
    viewModelOf(::PlaybackViewModel)
    viewModelOf(::HomaPageViewModel)
}