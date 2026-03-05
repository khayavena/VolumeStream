package com.vdigital.volumestream

import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication

actual fun KoinApplication.configureKoin() {
    androidContext(AndroidApp.getAppInstance())
}
