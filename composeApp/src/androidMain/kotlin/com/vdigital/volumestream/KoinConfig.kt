package com.vdigital.volumestream

import android.os.Build
import com.vdigital.volumestream.BuildConfig
import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.config.ArtworkProfile
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

/** Host used when running inside the Android Studio emulator (AOSP/AVD). */
private const val EMULATOR_HOST_AVD = "10.0.2.2"

/** Host used when running inside Genymotion emulator. */
private const val EMULATOR_HOST_GENYMOTION = "10.0.3.2"

private fun emulatorHost(): String =
    if (Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)) {
        EMULATOR_HOST_GENYMOTION
    } else {
        EMULATOR_HOST_AVD
    }

actual fun KoinApplication.configureKoin() {
    androidContext(AndroidApp.getAppInstance())
    val runtimeHost = if (isEmulator()) emulatorHost() else PHYSICAL_HOST
    val configuredHttps = BuildConfig.USE_HTTPS ||
        BuildConfig.API_HOST.startsWith("https://", ignoreCase = true) ||
        BuildConfig.AUTH_PORT == 443 ||
        BuildConfig.API_PORT == 443
    modules(module {
        single<String>(named("apiHost"))  { runtimeHost }
        single<String>(named("authHost")) { runtimeHost }
        single {
            val profile = if (BuildConfig.FLAVOR.contains("tv", ignoreCase = true)) {
                ArtworkProfile.TV
            } else {
                ArtworkProfile.MOBILE
            }
            StreamVaultConfig(
                authPort = BuildConfig.AUTH_PORT,
                apiPort  = BuildConfig.API_PORT,
                useHttps = configuredHttps,
                artworkProfile = profile,
            )
        }
    })
}
