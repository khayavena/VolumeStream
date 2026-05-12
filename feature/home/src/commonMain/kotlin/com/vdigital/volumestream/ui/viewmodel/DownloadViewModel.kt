package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.core.player.download.DownloadController
import com.vdigital.volumestream.core.player.download.DownloadItem
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vdigital.volumestream.platform.enum.OsType
import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.SessionRepository
import com.vditital.data.repository.state.ResultState
import com.vditital.data.security.SettingsStore
import com.vditital.data.util.AppLogger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadViewModel(
    private val downloadController: DownloadController,
    private val selectedMediaItemHolder: SelectedMediaItemHolder,
    private val settingsStore: SettingsStore,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val config: StreamVaultConfig,
    private val osType: OsType,
) : ViewModel() {

    private companion object {
        const val HEADER_AES_KEY_B64 = "X-Aes-Key-B64"
    }

    private data class DownloadRequest(val url: String, val headers: Map<String, String>)

    private val _allDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val allDownloads: StateFlow<List<DownloadItem>> = _allDownloads.asStateFlow()

    // Cache one StateFlow per item id so that every call to observeState(id)
    // from different Composables shares the same hot flow — and so that we can
    // force-push Idle into it immediately when remove() is called, without
    // waiting for WorkManager's async CANCELLED notification.
    // All access happens on the Main thread (ViewModel scope), so a plain
    // mutableMapOf is safe without synchronisation.
    private val _stateOverrides = mutableMapOf<String, MutableStateFlow<DownloadState?>>()

    // Cache the combined+stateIn StateFlow per id so that recompositions
    // always get the same shared hot flow rather than creating a new stateIn
    // subscription on every call.
    private val _observedStates = mutableMapOf<String, StateFlow<DownloadState>>()

    private fun overrideFor(id: String): MutableStateFlow<DownloadState?> =
        _stateOverrides.getOrPut(id) { MutableStateFlow(null) }

    /**
     * Loads the persisted download list off the Main thread to avoid disk I/O
     * (SharedPreferences / NSUserDefaults scan) on the UI thread.
     */
    fun refreshDownloads() {
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { downloadController.listDownloads() }
                _allDownloads.value = items
            } catch (e: Exception) {
                AppLogger.e("DownloadVM", "refreshDownloads failed", e)
            }
        }
    }

    fun download(item: PlaybackMediaItem) {
        overrideFor(item.id).value = null
        viewModelScope.launch {
            try {
                val request = withContext(Dispatchers.IO) { buildDownloadRequest(item) }
                if (request == null) {
                    overrideFor(item.id).value = DownloadState.Failed("Download unavailable for this item")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    downloadController.download(
                        id = item.id,
                        url = request.url,
                        title = item.title,
                        artworkUrl = item.artworkUrl,
                        wifiOnly = settingsStore.isWifiOnlyDownloads(),
                        headers = request.headers
                    )
                }
                // Wait for terminal state — Idle covers the "cancelled" path so
                // this coroutine always unblocks and refreshDownloads() always fires.
                withContext(Dispatchers.IO) {
                    downloadController.observeState(item.id)
                        .first {
                            it == DownloadState.Completed ||
                            it is DownloadState.Failed    ||
                            it == DownloadState.Idle
                        }
                }
                refreshDownloads()
            } catch (e: Exception) {
                AppLogger.e("DownloadVM", "download failed", e)
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun buildDownloadRequest(item: PlaybackMediaItem): DownloadRequest? {
        val directUrl = item.downloadUrl.trim()
        if (directUrl.isNotBlank()) {
            val normalizedManifestUrl = when (osType) {
                OsType.IOS -> DownloadUrlResolver.toHlsManifestUrl(directUrl, item.id, config)
                OsType.ANDROID -> DownloadUrlResolver.toDashManifestUrl(directUrl, item.id, config)
            }
            if (normalizedManifestUrl != null) {
                return buildManifestDownloadRequest(item.id, normalizedManifestUrl)
            }

            val jwt = authRepository.ensureValidJwt()
            val headers = if (jwt.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer $jwt")
            return DownloadRequest(directUrl, headers)
        }

        // Fallback for catalog items without direct downloadUrl:
        // request the canonical HLS user manifest so local playback stays AVPlayer-compatible.
        val sourceManifest = item.hlsStreamUrl.ifBlank { item.streamUrl }
        val downloadManifestUrl = when (osType) {
            OsType.IOS -> DownloadUrlResolver.toHlsManifestUrl(sourceManifest, item.id, config)
            OsType.ANDROID -> DownloadUrlResolver.toDashManifestUrl(sourceManifest, item.id, config)
        }
        if (downloadManifestUrl == null) {
            AppLogger.w("DownloadVM", "Skipping download for ${item.id}: no downloadUrl and download manifest rewrite failed")
            return null
        }

        return buildManifestDownloadRequest(item.id, downloadManifestUrl)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun buildManifestDownloadRequest(mediaId: String, manifestUrl: String): DownloadRequest? {
        val jwt = authRepository.ensureValidJwt() ?: return null
        when (val reg = sessionRepository.ensureDeviceRegistered()) {
            is ResultState.Error -> {
                AppLogger.w("DownloadVM", "Download device registration failed: ${reg.exception.message}")
                return null
            }
            else -> Unit
        }

        val session = sessionRepository.startSession(
            jwt = jwt,
            videoId = mediaId
        )
        if (session !is ResultState.Success) {
            val message = (session as? ResultState.Error)?.exception?.message ?: "unknown"
            AppLogger.w("DownloadVM", "Download session start failed for ${mediaId}: $message")
            return null
        }

        val baseHeaders = mutableMapOf(
            "Authorization" to "Bearer $jwt",
            "X-Session-Token" to session.data.sessionToken
        )

        if (osType == OsType.ANDROID) {
            val keyResult = sessionRepository.fetchAesKey(
                mediaId = mediaId,
                sessionId = session.data.sessionId,
                sessionToken = session.data.sessionToken
            )
            if (keyResult !is ResultState.Success) {
                val message = (keyResult as? ResultState.Error)?.exception?.message ?: "unknown"
                AppLogger.w("DownloadVM", "Download AES key fetch failed for ${mediaId}: $message")
                return null
            }
            baseHeaders[HEADER_AES_KEY_B64] = Base64.encode(keyResult.data)
        }

        return DownloadRequest(url = manifestUrl, headers = baseHeaders)
    }


    /**
     * Cancels an in-progress download off the Main thread (WorkManager Binder IPC +
     * SharedPreferences write must not block UI).
     */
    fun cancel(id: String) {
        // Force the UI to Idle immediately — before WorkManager's async CANCELLED
        // event arrives — so the button never flashes back to a "tick" state.
        AppLogger.d("DownloadVM", "[$id] cancel() — setting override=Idle")
        overrideFor(id).value = DownloadState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            downloadController.cancel(id)
        }
    }

    /**
     * Deletes the downloaded file and removes metadata off the Main thread to avoid
     * file-system I/O blocking the UI thread.
     */
    fun remove(id: String) {
        // Push Idle synchronously on the Main thread so the button updates instantly,
        // before the file deletion and WorkManager cancellation complete asynchronously.
        AppLogger.d("DownloadVM", "[$id] remove() — setting override=Idle")
        overrideFor(id).value = DownloadState.Idle
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { downloadController.remove(id) }
                refreshDownloads()
            } catch (e: Exception) {
                AppLogger.e("DownloadVM", "remove failed", e)
            }
        }
    }

    fun observeState(id: String): StateFlow<DownloadState> {
        // Return the cached flow if already created — avoids creating a new stateIn
        // subscription on every recomposition of MediaItemWidget.
        _observedStates[id]?.let { return it }

        val override = overrideFor(id)
        // Combine: if an override is present use it, otherwise fall through to the
        // controller's flow.
        //
        // Override clearing rules:
        //  • Idle override  → only cleared when the controller enters an active state
        //                     (Queued/Downloading/Completed), i.e. a re-download has begun.
        //                     It is NOT cleared when controllerState==Idle because
        //                     WorkManager emits a CANCELLED→Idle transition *after* the
        //                     override was set, which would cause the tick to flash back.
        //  • Other overrides → cleared as soon as the controller reaches the same state
        //                     or moves to any active state (normal flow).
        val flow = combine(
            downloadController.observeState(id),
            override
        ) { controllerState, overrideState ->
            AppLogger.d("DownloadVM", "[$id] combine: controller=$controllerState  override=$overrideState")
            val result = if (overrideState != null) {
                val shouldClear = when {
                    // Idle override: only clear when a fresh active download starts
                    overrideState == DownloadState.Idle ->
                        controllerState is DownloadState.Queued ||
                        controllerState is DownloadState.Downloading ||
                        controllerState == DownloadState.Completed
                    // Non-idle override: clear when controller reaches the same state
                    // or when a new active download supersedes it
                    controllerState == overrideState -> true
                    overrideState != DownloadState.Idle &&
                        (controllerState is DownloadState.Queued ||
                         controllerState is DownloadState.Downloading) -> true
                    else -> false
                }
                if (shouldClear) {
                    AppLogger.d("DownloadVM", "[$id] clearing override (was $overrideState)")
                    override.value = null
                }
                overrideState
            } else {
                controllerState
            }
            AppLogger.d("DownloadVM", "[$id] emitting: $result")
            result
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            // Seed with the override if one is already set (e.g. remove was called
            // just before the first subscriber attached), otherwise use the
            // controller's current knowledge.
            initialValue = override.value
                ?: if (downloadController.isDownloaded(id)) DownloadState.Completed
                   else DownloadState.Idle
        )
        _observedStates[id] = flow
        return flow
    }

    /**
     * Checks whether an item is already downloaded. The underlying read is lightweight
     * (in-memory prefs cache on Android; NSUserDefaults on iOS) but is still called
     * from Compose so we keep the result observable via [observeState] instead of
     * reading synchronously here where possible.
     */
    fun isDownloaded(id: String): Boolean = downloadController.isDownloaded(id)

    fun getLocalPath(id: String): String? = downloadController.getLocalPath(id)

    /** Select a downloaded item for playback via the shared holder. */
    fun selectForPlayback(item: DownloadItem) {
        val localPath = downloadController.getLocalPath(item.id)
        val useRemoteHls = osType == OsType.IOS && localPath?.lowercase()?.endsWith(".m3u8") == true
        val stream = if (useRemoteHls) item.url.ifBlank { localPath.orEmpty() } else localPath.orEmpty()
        val isDownloadedSelection = !useRemoteHls && stream.isNotBlank()
        selectedMediaItemHolder.select(
            PlaybackMediaItem(
                id         = item.id,
                title      = item.title,
                isDownloaded = isDownloadedSelection,
                streamUrl  = stream,
                hlsStreamUrl = stream,
                artworkUrl = item.artworkUrl
            )
        )
    }

    /** Release all cached StateFlow subscriptions when the ViewModel is destroyed. */
    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled by super — the stateIn coroutines it owns stop
        // automatically. Clearing the maps releases the StateFlow references too.
        _stateOverrides.clear()
        _observedStates.clear()
    }
}
