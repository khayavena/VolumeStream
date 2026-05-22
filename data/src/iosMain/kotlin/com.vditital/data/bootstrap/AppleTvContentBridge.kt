package com.vditital.data.bootstrap

import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.model.UserProfile
import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.ProfileRepository
import com.vditital.data.repository.state.ResultState
import com.vdigital.volumestream.navigation.tvNavSpecs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Apple TV content/auth bridge for Swift hosts.
 * Keeps app/UI code in iosApp while data orchestration stays in the data module.
 */
object AppleTvContentBridge {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class BridgeResult(
        val success: Boolean,
        val message: String = "",
    )

    @Serializable
    data class TvMediaItem(
        val id: String,
        val title: String,
        val streamUrl: String,
        val hlsStreamUrl: String,
        val artworkUrl: String,
        val durationMs: Long,
        val description: String,
    )

    @Serializable
    data class TvMediaSection(
        val title: String,
        val items: List<TvMediaItem>,
    )

    @Serializable
    data class TvProfile(
        val fullName: String,
        val phone: String,
        val address: String,
    )

    @Serializable
    data class TvNavItem(
        val route: String,
        val label: String,
        val iconToken: String,
        val sfSymbol: String,
    )

    @Serializable
    data class TvShellSnapshot(
        val navItems: List<TvNavItem>,
        val homeSections: List<TvMediaSection>,
        val isLoggedIn: Boolean,
        val currentUserEmail: String,
        val profile: TvProfile? = null,
    )

    private var authRepository: AuthRepository? = null
    private var playbackMediaItemRepository: PlaybackMediaItemRepository? = null
    private var profileRepository: ProfileRepository? = null

    internal fun bindRepositories(
        authRepository: AuthRepository,
        playbackMediaItemRepository: PlaybackMediaItemRepository,
        profileRepository: ProfileRepository,
    ) {
        this.authRepository = authRepository
        this.playbackMediaItemRepository = playbackMediaItemRepository
        this.profileRepository = profileRepository
    }

    internal fun isBound(): Boolean =
        authRepository != null && playbackMediaItemRepository != null && profileRepository != null

    fun isLoggedIn(): Boolean = authRepository?.isLoggedIn() == true

    fun currentUserEmail(): String = authRepository?.getCurrentUserEmail().orEmpty()

    fun logout() {
        authRepository?.logout()
    }

    suspend fun fetchShellSnapshot(downloadsEnabled: Boolean): TvShellSnapshot {
        val navItems = tvNavSpecs(downloadsEnabled).map { spec ->
            TvNavItem(
                route = spec.route,
                label = spec.label,
                iconToken = spec.iconToken,
                sfSymbol = spec.sfSymbol,
            )
        }
        val homeSections = fetchHomeSections()
        val loggedIn = isLoggedIn()
        val email = currentUserEmail()
        val profile = if (loggedIn) loadProfile()?.toTvProfile() else null
        return TvShellSnapshot(
            navItems = navItems,
            homeSections = homeSections,
            isLoggedIn = loggedIn,
            currentUserEmail = email,
            profile = profile,
        )
    }

    suspend fun fetchShellSnapshotJson(downloadsEnabled: Boolean): String =
        json.encodeToString(fetchShellSnapshot(downloadsEnabled))

    suspend fun login(email: String, password: String): BridgeResult {
        val repo = authRepository ?: return BridgeResult(false, "Auth repository unavailable")
        return when (val result = repo.login(email, password)) {
            is ResultState.Success -> BridgeResult(true)
            is ResultState.Error -> BridgeResult(false, result.exception.message ?: "Login failed")
            ResultState.Loading -> BridgeResult(false, "Login is still loading")
        }
    }

    suspend fun loginJson(email: String, password: String): String =
        json.encodeToString(login(email, password))

    suspend fun fetchHomeSections(): List<TvMediaSection> {
        val repo = playbackMediaItemRepository ?: return emptyList()
        return when (val result = repo.getMediaItemsByCategoryState()) {
            is ResultState.Success -> result.data
                .map { (title, items) -> TvMediaSection(title = title, items = items.map { it.toTvMediaItem() }) }
                .sortedBy { it.title }
            is ResultState.Error -> emptyList()
            ResultState.Loading -> emptyList()
        }
    }

    suspend fun search(query: String): List<TvMediaItem> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        val sections = fetchHomeSections()
        return sections
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { item ->
                item.title.lowercase().contains(normalized) ||
                    item.description.lowercase().contains(normalized)
            }
            .toList()
    }

    suspend fun searchJson(query: String): String =
        json.encodeToString(search(query))

    suspend fun loadProfile(): UserProfile? {
        val repo = profileRepository ?: return null
        return when (val result = repo.loadMyProfile()) {
            is ResultState.Success -> result.data
            is ResultState.Error -> null
            ResultState.Loading -> null
        }
    }


    private fun PlaybackMediaItem.toTvMediaItem(): TvMediaItem = TvMediaItem(
        id = id,
        title = title,
        streamUrl = streamUrl,
        hlsStreamUrl = hlsStreamUrl,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        description = description,
    )

    private fun UserProfile.toTvProfile(): TvProfile = TvProfile(
        fullName = fullName,
        phone = phone,
        address = address,
    )
}

