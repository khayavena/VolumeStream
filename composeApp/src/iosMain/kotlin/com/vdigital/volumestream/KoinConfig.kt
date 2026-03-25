package com.vdigital.volumestream

import com.vdigital.volumestream.config.API_HOST
import com.vdigital.volumestream.config.AUTH_PORT
import com.vdigital.volumestream.config.API_PORT
import com.vditital.data.config.StreamVaultConfig
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun KoinApplication.configureKoin() {
    modules(module {
        single<String>(named("apiHost"))  { API_HOST }
        single<String>(named("authHost")) { API_HOST }
        single {
            StreamVaultConfig(
                authPort = AUTH_PORT,
                apiPort  = API_PORT,
            )
        }
    })
}
