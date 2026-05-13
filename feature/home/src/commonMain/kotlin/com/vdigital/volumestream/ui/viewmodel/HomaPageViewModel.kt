package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.SessionRepository
import com.vditital.data.repository.state.ResultState
import com.vditital.data.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomaPageViewModel(
    private val playbackMediaItemRepository: PlaybackMediaItemRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val homeDataState =
        MutableStateFlow<ResultState<Map<String, MutableList<PlaybackMediaItem>>>>(ResultState.Loading)
    val homeDataUIState = homeDataState.asStateFlow()

    /** Re-entry guard: prevents duplicate fetches triggered by Compose recompositions. */
    private var fetchJob: Job? = null

    fun fetchData(forceRefresh: Boolean = false) {
        // Skip entirely if we already have data — prevents spurious re-fetches triggered by
        // Compose recompositions (e.g. NavHost graph rebuild on back-from-player).
        if (!forceRefresh && homeDataState.value is ResultState.Success) {
            AppLogger.d("HomeVM", "fetchData skipped — data already loaded")
            return
        }
        // If a non-forced fetch is already in flight, don't launch a duplicate.
        if (!forceRefresh && fetchJob?.isActive == true) {
            AppLogger.d("HomeVM", "fetchData skipped — job already active")
            return
        }
        fetchJob = viewModelScope.launch {
            AppLogger.d("HomeVM", "fetchData called (forceRefresh=$forceRefresh)")

            // Fire-and-forget: register the device's RSA public key once per install.
            // 409 (already registered) is silently ignored by ensureDeviceRegistered().
            launch(Dispatchers.IO) {
                try {
                    when (val registration = sessionRepository.ensureDeviceRegistered()) {
                        is ResultState.Error -> {
                            AppLogger.w("HomeVM", "device registration skipped/failed: ${registration.exception.message}")
                        }
                        else -> Unit
                    }
                } catch (t: Throwable) {
                    AppLogger.e("HomeVM", "device registration coroutine failed", t as? Exception ?: Exception(t))
                }
            }

            try {
                if (forceRefresh) {
                    withContext(Dispatchers.IO) { playbackMediaItemRepository.invalidateCache() }
                }
                val result = withContext(Dispatchers.IO) {
                    playbackMediaItemRepository.getMediaItemsByCategoryState()
                }
                AppLogger.d("HomeVM", "result: $result")
                homeDataState.value = result
            } catch (e: Throwable) {
                AppLogger.e("HomeVM", "uncaught error", e as? Exception)
                homeDataState.value = ResultState.Error(Exception(e))
            }
        }
    }
}
