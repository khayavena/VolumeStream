package com.vdigital.volumestream

import android.os.Build
import com.vdigital.volumestream.BuildConfig
import com.vditital.data.config.StreamVaultConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Returns true when the app is running inside an Android emulator.
 * Checks several Build fields that are reliably set on emulator images.
 */
private fun isEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
    Build.FINGERPRINT.startsWith("unknown") ||
    Build.MODEL.contains("google_sdk", ignoreCase = true) ||
    Build.MODEL.contains("Emulator", ignoreCase = true) ||
    Build.MODEL.contains("Android SDK built for x86", ignoreCase = true) ||
    Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
    (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
    Build.PRODUCT == "google_sdk"

/** Host used when running on a physical device (from local.properties). */
private const val PHYSICAL_HOST = BuildConfig.API_HOST

/** Host used when running inside the Android emulator. */
private const val EMULATOR_HOST = "10.0.2.2"

actual fun KoinApplication.configureKoin() {
    androidContext(AndroidApp.getAppInstance())
    modules(module {
        single<String>(named("apiHost"))  { if (isEmulator()) EMULATOR_HOST else PHYSICAL_HOST }
        single<String>(named("authHost")) { if (isEmulator()) EMULATOR_HOST else PHYSICAL_HOST }
        single {
            StreamVaultConfig(
                authPort = BuildConfig.AUTH_PORT,
                apiPort  = BuildConfig.API_PORT,
            )
        }
    })
}
