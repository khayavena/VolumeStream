package com.vditital.data.di

import com.vditital.data.datasource.RemoteApiClientFactory
import com.vditital.data.datasource.RemotePlaybackDataSource
import com.vditital.data.datasource.RemotePlaybackDataSourceImpl
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.PlaybackMediaItemRepositoryImpl
import io.ktor.client.HttpClient
import org.koin.dsl.module

 val internalDataModule = module {
    single<HttpClient> { get<RemoteApiClientFactory>().create() }
    single<RemotePlaybackDataSource> { RemotePlaybackDataSourceImpl(get()) }
    single<PlaybackMediaItemRepository> { PlaybackMediaItemRepositoryImpl(get()) }
}