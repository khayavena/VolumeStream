package com.vditital.data.di

import com.vditital.data.datasource.RemoteApiClientFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDataModule: Module = module {
    single<RemoteApiClientFactory> { RemoteApiClientFactory() }
}