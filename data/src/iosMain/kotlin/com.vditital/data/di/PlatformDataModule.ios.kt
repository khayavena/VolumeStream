package com.vditital.data.di

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.datasource.AuthRefreshRetryInterceptor
import com.vditital.data.datasource.RemoteApiClientFactory
import com.vditital.data.security.DeviceCrypto
import com.vditital.data.security.SettingsStore
import com.vditital.data.security.TokenStore
import org.koin.core.qualifier.named
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDataModule: Module = module {
    single { TokenStore() }
    single { AuthRefreshRetryInterceptor(get(), get(named("authHost")), get()) }
    single<RemoteApiClientFactory> { RemoteApiClientFactory(get()) }
    single { DeviceCrypto(get<StreamVaultConfig>().deviceKeyAlias) }
    single { SettingsStore() }
}