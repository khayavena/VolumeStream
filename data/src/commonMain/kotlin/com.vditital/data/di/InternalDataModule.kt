package com.vditital.data.di

import com.vditital.data.datasource.AuthDataSource
import com.vditital.data.datasource.AuthDataSourceImpl
import com.vditital.data.datasource.RemoteApiClientFactory
import com.vditital.data.datasource.RemotePlaybackDataSource
import com.vditital.data.datasource.RemotePlaybackDataSourceImpl
import com.vditital.data.datasource.SessionDataSource
import com.vditital.data.datasource.SessionDataSourceImpl
import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.AuthRepositoryImpl
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.PlaybackMediaItemRepositoryImpl
import com.vditital.data.repository.SessionRepository
import com.vditital.data.repository.SessionRepositoryImpl
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module


val internalDataModule = module {
    // StreamVaultConfig is NOT provided here — it must be registered by the app
    // via configureKoin() (Android: KoinConfig.kt, iOS: KoinConfig.kt) BEFORE
    // appModule is loaded. Defining it here caused the default (port 8080/8081)
    // to overwrite the app-supplied config because internalDataModule is loaded last.

    single<HttpClient> { get<RemoteApiClientFactory>().create() }

    // Data sources — apiHost injected from configureKoin() (emulator vs physical device)
    single<RemotePlaybackDataSource> {
        RemotePlaybackDataSourceImpl(get(), get(named("apiHost")), get(), get())
    }
    single<AuthDataSource> {
        AuthDataSourceImpl(get(), get(named("authHost")), get())
    }
    single<SessionDataSource> {
        SessionDataSourceImpl(get(), get(named("apiHost")), get(), get(), get())
    }

    // Repositories
    single<PlaybackMediaItemRepository> { PlaybackMediaItemRepositoryImpl(get()) }
    single<AuthRepository>    { AuthRepositoryImpl(get(), get()) }
    single<SessionRepository> { SessionRepositoryImpl(get(), get()) }
}