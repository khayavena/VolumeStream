package com.vdigital.volumestream.ui.overlay

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vditital.data.model.PlaybackMediaItem

/**
 * Single source-of-truth for the player overlay UI state.
 *
 * Created once in [com.vdigital.volumestream.ui.view.PlaybackView] via `remember`,
 * then passed to [PlayerOverlayMobile] or [PlayerOverlayTv].  Both the root key-event
 * handler (TV) and the overlay composables read and write through this object, keeping
 * the state consistent without prop-drilling individual vars.
 */
@Stable
class PlayerOverlayState {
    /** Whether the controls overlay is currently visible. */
    var showControls by mutableStateOf(true)

    /** Whether the track-selection panel is open. */
    var showTrackPanel by mutableStateOf(false)

    /** Whether the quality-selection panel is open. */
    var showQualityPanel by mutableStateOf(false)

    /** True = FILL / zoom; false = FIT / letterbox. */
    var isZoomed by mutableStateOf(true)

    /**
     * Monotonically-increasing tick.  Incrementing it restarts the auto-hide
     * [androidx.compose.runtime.LaunchedEffect] countdown in PlaybackView.
     */
    var controlsResetTick by mutableStateOf(0)

    /**
     * Focus-key string of the currently focused TV chip ("tv-play", "tv-rewind", …),
     * or `null` when no chip is focused.  Written by each [PlayerOverlayTv] chip via
     * `onFocusKeyChanged`; read by PlaybackView's root key-event handler to dispatch
     * Enter/DpadCenter actions centrally without propagating to ExoPlayer.
     */
    var activeTvControlKey by mutableStateOf<String?>(null)

    /**
     * The [PlaybackMediaItem] currently focused in the inline tracks carousel, or `null`
     * when no carousel card has focus.  Written by [PlayerOverlayTv]'s `onTrackFocusKey`
     * callback; read by PlaybackView's key handler to dispatch Enter/DpadCenter as a
     * `selectTrack` call.
     */
    var focusedCarouselTrack by mutableStateOf<PlaybackMediaItem?>(null)

    /** Increment [controlsResetTick] to restart the auto-hide countdown. */
    fun resetControlsTimer() { controlsResetTick++ }
}

