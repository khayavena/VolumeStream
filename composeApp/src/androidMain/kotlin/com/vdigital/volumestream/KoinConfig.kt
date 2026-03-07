package com.vdigital.volumestream

import com.vdigital.volumestream.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun KoinApplication.configureKoin() {
    androidContext(AndroidApp.getAppInstance())
    modules(module {
        single<String>(named("apiHost")) { BuildConfig.API_HOST }
    })
}
