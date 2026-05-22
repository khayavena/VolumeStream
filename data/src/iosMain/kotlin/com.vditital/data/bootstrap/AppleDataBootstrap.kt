package com.vditital.data.bootstrap

import com.vditital.data.config.ArtworkProfile
import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.di.externalDataModule
import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.ProfileRepository
import com.vditital.data.repository.SessionRepository
import com.vditital.data.util.initLogging
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Initializes shared StreamVault services for iOS/tvOS hosts (SwiftUI/UIKit).
 * Safe to call multiple times; subsequent calls are ignored once Koin is active.
 */
fun initializeAppleDataLayer(
    apiHost: String,
    authHost: String = apiHost,
    apiPort: Int = 8081,
    authPort: Int = 8080,
    apiUseHttps: Boolean = false,
    authUseHttps: Boolean = false,
    artworkProfile: String = "TV",
): Boolean {
    val selectedArtworkProfile = when (artworkProfile.uppercase()) {
        "MOBILE" -> ArtworkProfile.MOBILE
        else -> ArtworkProfile.TV
    }

    initLogging()
    return try {
        startKoin {
            modules(
                module {
                    single(named("apiHost")) { apiHost }
                    single(named("authHost")) { authHost }
                    single {
                        StreamVaultConfig(
                            apiPort = apiPort,
                            authPort = authPort,
                            apiUseHttps = apiUseHttps,
                            authUseHttps = authUseHttps,
                            artworkProfile = selectedArtworkProfile,
                        )
                    }
                },
                externalDataModule,
            )
        }.also { koinApp ->
            val authRepository = koinApp.koin.get<AuthRepository>()
            val sessionRepository = koinApp.koin.get<SessionRepository>()
            ApplePlaybackBridge.bindRepositories(
                authRepository = authRepository,
                sessionRepository = sessionRepository,
            )
            AppleTvContentBridge.bindRepositories(
                authRepository = authRepository,
                playbackMediaItemRepository = koinApp.koin.get<PlaybackMediaItemRepository>(),
                profileRepository = koinApp.koin.get<ProfileRepository>(),
            )
        }
        true
    } catch (_: IllegalStateException) {
        false
    }
}

fun isAppleDataLayerReady(): Boolean =
    ApplePlaybackBridge.isBound() && AppleTvContentBridge.isBound()

fun appleDataLayerReadinessMessage(): String = when {
    ApplePlaybackBridge.isBound() && AppleTvContentBridge.isBound() ->
        "Apple data layer is ready"
    !ApplePlaybackBridge.isBound() && !AppleTvContentBridge.isBound() ->
        "Apple data layer is not initialized"
    !ApplePlaybackBridge.isBound() ->
        "Playback bridge is not wired"
    else ->
        "Content bridge is not wired"
}

