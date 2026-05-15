package com.vditital.data.di

import com.vditital.data.datasource.AuthRefreshRetryInterceptor
import com.vditital.data.datasource.RemoteApiClientFactory
import com.vditital.data.security.DeviceCrypto
import com.vditital.data.security.SettingsStore
import com.vditital.data.security.TokenStore
import org.koin.android.ext.koin.androidApplication
import org.koin.core.qualifier.named
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDataModule: Module = module {
    single { TokenStore(androidApplication()) }
    single { AuthRefreshRetryInterceptor(get(), get(named("authHost")), get()) }
    single<RemoteApiClientFactory> { RemoteApiClientFactory(get()) }
    single { DeviceCrypto(androidApplication(), get<com.vditital.data.config.StreamVaultConfig>().deviceKeyAlias) }
    single { SettingsStore(androidApplication()) }
}