package com.vdigital.volumestream.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.platform.enum.OsType
import com.vdigital.volumestream.platform.orientation.LockLandscapeOrientation
import com.vdigital.volumestream.platform.view.PlatformMediaPlayerView
import com.vdigital.volumestream.ui.overlay.PlayerOverlayMobile
import com.vdigital.volumestream.ui.overlay.PlayerOverlayState
import com.vdigital.volumestream.ui.overlay.PlayerOverlayTv
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.widget.PlaybackBufferingIndicator
import com.vditital.data.util.AppLogger
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val ErrorOverlayBg = Color(0x99000000)
private const val CONTROLS_HIDE_DELAY_MS    = 4_000L
private const val TV_CONTROLS_HIDE_DELAY_MS = 7_000L

/**
 * Root playback screen.
 *
 * Owns player lifecycle, the TV key-event interceptor, and the overlay auto-hide timer.
 * All overlay UI is delegated to [PlayerOverlayTv] (TV) or [PlayerOverlayMobile] (mobile)
 * via [PlayerOverlayState] — the single source of truth for overlay visibility state.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackView(
    playbackInstanceKey: String = "playback-default",
    isTvLayout: Boolean = false,
    onBack: () -> Unit = {}
) {
    LockLandscapeOrientation()

    val viewModel: PlaybackViewModel    = koinViewModel(key = playbackInstanceKey)
    val holder: SelectedMediaItemHolder = koinInject()
    val selectedItem   by holder.selectedItem.collectAsState()
    val controller     = remember(viewModel) { viewModel.getPlatformController() }
    val currentQuality by viewModel.qualityUI.collectAsState()
    val playbackState  by viewModel.playBackStateUI.collectAsState()

    AppLogger.d(
        "Diag.UI",
        "compose key=$playbackInstanceKey vm=${viewModel.hashCode()} " +
        "controller=${controller.hashCode()} selected=${selectedItem?.id}"
    )

    // ── Overlay state ─────────────────────────────────────────────────────────
    val overlay = remember { PlayerOverlayState() }

    // ── Back handler ──────────────────────────────────────────────────────────
    val handleBack: () -> Unit = remember(controller, onBack) {
        {
            runCatching {
                AppLogger.i("Diag.UI", "back pressed controller=${controller.hashCode()}")
                controller.pause(playbackState = {})
                onBack()
            }.onFailure {
                AppLogger.e("Diag.UI", "back handling failed", it as? Exception ?: Exception(it))
            }
        }
    }
    PlatformBackHandler(onBack = handleBack)

    // ── Auto-hide timer ───────────────────────────────────────────────────────
    LaunchedEffect(
        overlay.controlsResetTick,
        isTvLayout,
        overlay.showTrackPanel,
        overlay.showQualityPanel,
        playbackState is PlaybackState.Error
    ) {
        if (overlay.showTrackPanel || overlay.showQualityPanel || playbackState is PlaybackState.Error) {
            overlay.showControls = true
            return@LaunchedEffect
        }
        overlay.showControls = true
        delay(if (isTvLayout) TV_CONTROLS_HIDE_DELAY_MS else CONTROLS_HIDE_DELAY_MS)
        overlay.showControls    = false
        overlay.showTrackPanel  = false
        overlay.showQualityPanel = false
    }

    // ── Player initialisation ─────────────────────────────────────────────────
    var didInitialise by remember { mutableStateOf(false) }
    LaunchedEffect(selectedItem?.id) {
        AppLogger.d(
            "Diag.UI",
            "selectedItem effect vm=${viewModel.hashCode()} media=${selectedItem?.id}"
        )
        if (!didInitialise && selectedItem != null) {
            didInitialise = true
            viewModel.initialise()
        }
    }

    // ── Playback-state side-effects ───────────────────────────────────────────
    LaunchedEffect(playbackState) {
        AppLogger.d(
            "Diag.UI",
            "state effect vm=${viewModel.hashCode()} state=${playbackState::class.simpleName}"
        )
        runCatching {
            when (playbackState) {
                PlaybackState.Ended -> { delay(600L); handleBack() }
                PlaybackState.SessionExpired -> { controller.release(); handleBack() }
                is PlaybackState.Error -> {
                    val msg = (playbackState as PlaybackState.Error).errorMessage
                    if (msg.contains("authenticated", ignoreCase = true) ||
                        msg.contains("session",       ignoreCase = true) ||
                        msg.contains("Auth failed",   ignoreCase = true) ||
                        msg.contains("Session failed", ignoreCase = true)) {
                        delay(8_000L); handleBack()
                    }
                }
                else -> Unit
            }
        }.onFailure {
            AppLogger.e("Diag.UI", "playback state effect failed", it as? Exception ?: Exception(it))
        }
    }

    // ── Root Modifier ─────────────────────────────────────────────────────────
    val rootModifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        // Keep Compose focus inside this Box at all times on TV so the Android View
        // system never delivers key events directly to ExoPlayer's PlayerView.
        .let { if (isTvLayout) it.focusable() else it }
        .let { base ->
            if (isTvLayout) {
                base.onPreviewKeyEvent { event ->
                    // Consume KeyUp for Enter/DpadCenter so ExoPlayer never sees the
                    // up-event as a play/pause command.
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> true
                            else -> false
                        }
                    }

                    val revealOnlyKey = event.key in setOf(
                        Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter
                    )
                    val mediaActionKey = event.key in setOf(
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause,
                        Key.MediaFastForward, Key.MediaRewind,
                        Key.MediaNext, Key.MediaPrevious
                    )

                    if (revealOnlyKey || mediaActionKey) {
                        overlay.resetControlsTimer()
                        if (!overlay.showControls) {
                            overlay.showControls = true
                            if (revealOnlyKey) return@onPreviewKeyEvent true
                        }
                    }

                    val hasOpenPanel = overlay.showTrackPanel || overlay.showQualityPanel
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            when {
                                hasOpenPanel -> {
                                    overlay.showTrackPanel   = false
                                    overlay.showQualityPanel = false
                                    overlay.resetControlsTimer()
                                }
                                overlay.showControls -> overlay.showControls = false
                                else -> handleBack()
                            }
                            true
                        }
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionUp,   Key.DirectionDown -> {
                            overlay.resetControlsTimer()
                            false // pass through so focus system can move between chips
                        }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            // Always consume — prevents ExoPlayer from toggling play/pause.
                            // Dispatch the correct action for whichever chip is focused.
                            overlay.resetControlsTimer()
                            if (overlay.showControls && !hasOpenPanel) {
                                when (overlay.activeTvControlKey) {
                                    "tv-play"    -> viewModel.playPause()
                                    "tv-rewind"  -> viewModel.skipBackward()
                                    "tv-forward" -> viewModel.skipForward()
                                    "tv-back"    -> handleBack()
                                    "tv-zoom"    -> { overlay.isZoomed = !overlay.isZoomed }
                                    "tv-quality" -> {
                                        overlay.showQualityPanel = !overlay.showQualityPanel
                                        if (overlay.showQualityPanel) overlay.showTrackPanel = false
                                    }
                                    "tv-tracks"  -> {
                                        overlay.showTrackPanel = !overlay.showTrackPanel
                                        if (overlay.showTrackPanel) overlay.showQualityPanel = false
                                    }
                                }
                            }
                            true
                        }
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            overlay.resetControlsTimer()
                            viewModel.playPause()
                            true
                        }
                        else -> false
                    }
                }
            } else {
                base.pointerInput(Unit) {
                    detectTapGestures { overlay.resetControlsTimer() }
                }
            }
        }

    // ── Render ────────────────────────────────────────────────────────────────
    Box(modifier = rootModifier) {

        // Video surface
        when (viewModel.osType) {
            OsType.IOS -> PlatformMediaPlayerView(
                modifier = Modifier.fillMaxSize(),
                playbackStateController = controller,
                onTap = { overlay.resetControlsTimer() },
                isZoomed = overlay.isZoomed
            )
            OsType.ANDROID -> PlatformMediaPlayerView(
                modifier = Modifier.fillMaxSize(),
                playbackStateController = controller,
                isZoomed = overlay.isZoomed
            )
        }

        // Buffering spinner (centre)
        Box(modifier = Modifier.align(Alignment.Center)) {
            PlaybackBufferingIndicator(playbackState)
        }

        // Error overlay — shown on both TV and mobile
        val errorState = playbackState as? PlaybackState.Error
        if (errorState != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ErrorOverlayBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("⚠", color = Color(0xFFFF5252), fontSize = 48.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorState.errorMessage,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = if (isTvLayout) "Press Back to go back" else "Tap Back to go back",
                        color = Color(0xFF00E676),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        // Platform-specific overlay — TV or Mobile
        if (isTvLayout) {
            PlayerOverlayTv(
                state          = overlay,
                viewModel      = viewModel,
                playbackState  = playbackState,
                currentQuality = currentQuality,
                onBack         = handleBack
            )
        } else {
            PlayerOverlayMobile(
                state          = overlay,
                viewModel      = viewModel,
                playbackState  = playbackState,
                currentQuality = currentQuality,
                onBack         = handleBack
            )
        }
    }
}
