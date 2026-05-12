package com.vdigital.volumestream.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.platform.enum.OsType
import com.vdigital.volumestream.platform.orientation.LockLandscapeOrientation
import com.vdigital.volumestream.platform.view.PlatformMediaPlayerView
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.widget.PlayPauseControl
import com.vdigital.volumestream.ui.widget.PlaybackBufferingIndicator
import com.vdigital.volumestream.ui.widget.PlaybackSeekBar
import com.vdigital.volumestream.ui.widget.QualitySelectionPanel
import com.vdigital.volumestream.ui.widget.TrackSelectionPanel
import com.vditital.data.util.AppLogger
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val GreenAccent    = Color(0xFF00E676)
private val ControlsBarBg  = Color(0x8C000000)
private val ErrorOverlayBg = Color(0x99000000)
private val TopScrimBrush = Brush.verticalGradient(
    colors = listOf(Color(0x8F000000), Color.Transparent)
)
private val BottomScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0x96000000))
)
private val TvChipBg = Color(0xB3000000)
private val TvChipFocusedBg = Color(0x6600E676)
private const val CONTROLS_HIDE_DELAY_MS = 4_000L
private const val TV_CONTROLS_HIDE_DELAY_MS = 7_000L

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackView(
    playbackInstanceKey: String = "playback-default",
    isTvLayout: Boolean = false,
    onBack: () -> Unit = {}
) {
    LockLandscapeOrientation()
    val viewModel: PlaybackViewModel = koinViewModel(key = playbackInstanceKey)
    val holder: SelectedMediaItemHolder = koinInject()
    val selectedItem by holder.selectedItem.collectAsState()
    println("[PlaybackView][trace] selectedItem=${selectedItem?.id}")
    val controller = remember(viewModel) { viewModel.getPlatformController() }
    AppLogger.d(
        "Diag.UI",
        "compose key=$playbackInstanceKey vm=${viewModel.hashCode()} controller=${controller.hashCode()} selected=${selectedItem?.id}"
    )

    // Stop playback immediately then navigate — prevents audio bleeding into the
    // transition animation when the user presses back.
    val handleBack: () -> Unit = remember(controller, onBack) {
        {
            runCatching {
                AppLogger.i("Diag.UI", "back pressed controller=${controller.hashCode()} statePause=true")
                controller.pause(playbackState = {})
                onBack()
            }.onFailure {
                AppLogger.e("Diag.UI", "back handling failed", it as? Exception ?: Exception(it))
            }
        }
    }

    // Intercept Android hardware back key and predictive-back gesture.
    PlatformBackHandler(onBack = handleBack)

    // Release is handled by PlatformMediaPlayerView's own DisposableEffect.
    // A second DisposableEffect here is redundant — the released-flag in
    // PlaybackStateController guards against double-release, but removing the
    // duplicate keeps the lifecycle management in one place.

    var showControls      by remember { mutableStateOf(true) }
    var showTrackPanel    by remember { mutableStateOf(false) }
    var showQualityPanel  by remember { mutableStateOf(false) }
    var isZoomed          by remember { mutableStateOf(true) }
    var controlsResetTick by remember { mutableStateOf(0) }
    var didInitialise     by remember { mutableStateOf(false) }
    var activeTvControlKey by remember { mutableStateOf<String?>(null) }
    val currentQuality    by viewModel.qualityUI.collectAsState()
    val playbackState     by viewModel.playBackStateUI.collectAsState()

    LaunchedEffect(
        controlsResetTick,
        isTvLayout,
        showTrackPanel,
        showQualityPanel,
        playbackState is PlaybackState.Error
    ) {
        if (showTrackPanel || showQualityPanel || playbackState is PlaybackState.Error) {
            showControls = true
            return@LaunchedEffect
        }

        val hideDelayMs = if (isTvLayout) TV_CONTROLS_HIDE_DELAY_MS else CONTROLS_HIDE_DELAY_MS
        showControls = true
        delay(hideDelayMs)
        showControls = false
        showTrackPanel = false
        showQualityPanel = false
    }

    // Kick off player initialisation once per PlaybackView instance.
    // Track switching is handled by selectTrack()/handleTrackSwitch and must not
    // re-enter initialise(), otherwise iOS can rebuild player/session unnecessarily.
    LaunchedEffect(selectedItem?.id) {
        AppLogger.d(
            "Diag.UI",
            "selectedItem effect vm=${viewModel.hashCode()} controller=${controller.hashCode()} media=${selectedItem?.id}"
        )
        if (!didInitialise && selectedItem != null) {
            didInitialise = true
            viewModel.initialise()
        }
    }

    // Auto-navigate back when the stream ends.
    val backChipFocus = remember { FocusRequester() }
    val zoomChipFocus = remember { FocusRequester() }
    val qualityChipFocus = remember { FocusRequester() }
    val tracksChipFocus = remember { FocusRequester() }
    val rewindChipFocus = remember { FocusRequester() }
    val playChipFocus = remember { FocusRequester() }
    val forwardChipFocus = remember { FocusRequester() }
    val qualityPanelFirstFocus = remember { FocusRequester() }
    val trackPanelFirstFocus = remember { FocusRequester() }

    LaunchedEffect(isTvLayout, showControls, showTrackPanel, showQualityPanel) {
        if (!isTvLayout || !showControls) return@LaunchedEffect
        if (showTrackPanel || showQualityPanel) return@LaunchedEffect
        runCatching { playChipFocus.requestFocus() }
    }

    LaunchedEffect(isTvLayout, showQualityPanel) {
        if (!isTvLayout || !showQualityPanel) return@LaunchedEffect
        runCatching { qualityPanelFirstFocus.requestFocus() }
    }

    LaunchedEffect(isTvLayout, showTrackPanel) {
        if (!isTvLayout || !showTrackPanel) return@LaunchedEffect
        runCatching { trackPanelFirstFocus.requestFocus() }
    }

    LaunchedEffect(playbackState) {
        AppLogger.d(
            "Diag.UI",
            "state effect vm=${viewModel.hashCode()} controller=${controller.hashCode()} state=${playbackState::class.simpleName}"
        )
        runCatching {
            when (playbackState) {
                PlaybackState.Ended -> {
                    delay(600L)
                    handleBack()
                }
                // SessionExpired is emitted by PlaybackControllerListener when ExoPlayer
                // receives a 401 mid-stream (ERROR_CODE_AUTHENTICATION_EXPIRED).
                // SessionRevokedBus has already been signalled — the MainNavigationControllerView
                // watcher will navigate to Login once the back-stack unwinds here.
                PlaybackState.SessionExpired -> {
                    controller.release()
                    handleBack()
                }
                // Auth errors: show the message for 8 s then navigate back so the user
                // can log in again from the Home / Login screen.
                is PlaybackState.Error -> {
                    val msg = (playbackState as PlaybackState.Error).errorMessage
                    if (msg.contains("authenticated", ignoreCase = true) ||
                        msg.contains("session", ignoreCase = true) ||
                        msg.contains("Auth failed", ignoreCase = true) ||
                        msg.contains("Session failed", ignoreCase = true)) {
                        delay(8_000L)
                        handleBack()
                    }
                }
                else -> Unit
            }
        }.onFailure {
            AppLogger.e("Diag.UI", "playback state effect failed", it as? Exception ?: Exception(it))
        }
    }

    val rootModifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        // Keep Compose focus inside this Box at all times on TV.
        // Without this, when chips are hidden (AnimatedVisibility exit), the Android
        // View system reclaims focus and delivers KEYCODE_DPAD_CENTER directly to
        // ExoPlayer's PlayerView — causing spurious play/pause and the loading spinner.
        .let { if (isTvLayout) it.focusable() else it }
        .let { base ->
            if (isTvLayout) {
                base.onPreviewKeyEvent { event ->
                    // Consume KeyUp for Enter/DpadCenter so ExoPlayer's PlayerView never
                    // sees the up-event and interprets it as play/pause.
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent when (event.key) {
                            Key.Enter,
                            Key.NumPadEnter,
                            Key.DirectionCenter -> true
                            else -> false
                        }
                    }
                    val revealOnlyKey = when (event.key) {
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionLeft,
                        Key.DirectionRight,
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter -> true
                        else -> false
                    }
                    val mediaActionKey = when (event.key) {
                        Key.MediaPlayPause,
                        Key.MediaPlay,
                        Key.MediaPause,
                        Key.MediaFastForward,
                        Key.MediaRewind,
                        Key.MediaNext,
                        Key.MediaPrevious -> true
                        else -> false
                    }
                    val shouldRevealControls = revealOnlyKey || mediaActionKey
                    if (shouldRevealControls) {
                        controlsResetTick++
                        if (!showControls) {
                            showControls = true
                            if (revealOnlyKey) return@onPreviewKeyEvent true
                        }
                    }
                    val hasOpenPanel = showTrackPanel || showQualityPanel
                    when (event.key) {
                        Key.Back,
                        Key.Escape -> {
                            when {
                                hasOpenPanel -> {
                                    // Close open panel first, keep overlay visible.
                                    showTrackPanel = false
                                    showQualityPanel = false
                                    controlsResetTick++
                                }
                                showControls -> {
                                    // YouTube TV style: first Back press hides the overlay
                                    // while video keeps playing. Second Back navigates away.
                                    showControls = false
                                }
                                else -> handleBack()
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            controlsResetTick++
                            false
                        }
                        Key.DirectionRight -> {
                            controlsResetTick++
                            false
                        }
                        Key.DirectionUp,
                        Key.DirectionDown -> {
                            controlsResetTick++
                            false
                        }
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> {
                            // Always consume Enter/DpadCenter so it never reaches the
                            // underlying ExoPlayer PlayerView, which would otherwise
                            // intercept KEYCODE_DPAD_CENTER as play/pause and cause the
                            // player to stop + buffer unexpectedly.
                            controlsResetTick++
                            if (showControls && !hasOpenPanel) {
                                when (activeTvControlKey) {
                                    "tv-play"    -> viewModel.playPause()
                                    "tv-rewind"  -> viewModel.skipBackward()
                                    "tv-forward" -> viewModel.skipForward()
                                    "tv-back"    -> handleBack()
                                    "tv-zoom"    -> { isZoomed = !isZoomed }
                                    "tv-quality" -> {
                                        showQualityPanel = !showQualityPanel
                                        if (showQualityPanel) showTrackPanel = false
                                    }
                                    "tv-tracks"  -> {
                                        showTrackPanel = !showTrackPanel
                                        if (showTrackPanel) showQualityPanel = false
                                    }
                                }
                            }
                            true  // Always consume
                        }
                        Key.MediaPlayPause,
                        Key.MediaPlay,
                        Key.MediaPause -> {
                            controlsResetTick++
                            viewModel.playPause()
                            true
                        }
                        else -> false
                    }
                }
            } else {
                base.pointerInput(Unit) {
                    detectTapGestures {
                        controlsResetTick++
                    }
                }
            }
        }

    Box(modifier = rootModifier) {
        when (viewModel.osType) {
            OsType.IOS -> PlatformMediaPlayerView(
                modifier = Modifier.fillMaxSize(),
                playbackStateController = controller,
                onTap = { controlsResetTick++ },
                isZoomed = isZoomed
            )
            OsType.ANDROID -> PlatformMediaPlayerView(
                modifier = Modifier.fillMaxSize(),
                playbackStateController = controller,
                isZoomed = isZoomed
            )
        }

        Box(modifier = Modifier.align(Alignment.Center)) {
            PlaybackBufferingIndicator(playbackState)
        }

        // Visible error overlay — shows the exact error so the user is never
        // left staring at a silent black screen.
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
                    modifier = Modifier
                        .padding(32.dp)
                ) {
                    Text(
                        text = "⚠",
                        color = Color(0xFFFF5252),
                        fontSize = 48.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorState.errorMessage,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    // Tappable back link — important because showControls auto-hides
                    // after 4 s and the overlay would otherwise trap the user.
                    Text(
                        text = if (isTvLayout) "Press Back to go back" else "Tap Back to go back",
                        color = Color(0xFF00E676),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .let { textModifier ->
                                if (isTvLayout) textModifier else {
                                    textModifier.pointerInput(Unit) {
                                        detectTapGestures {
                                            handleBack()
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        if (isTvLayout) {
            AnimatedVisibility(
                visible = showControls || playbackState is PlaybackState.Error,
                modifier = Modifier.align(Alignment.TopStart),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TvOverlayChip(
                    label = "Back",
                    selected = false,
                    focusKey = "tv-back",
                    onFocusKeyChanged = { key -> activeTvControlKey = key },
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp)
                        .focusRequester(backChipFocus)
                        .focusProperties {
                            up = backChipFocus
                            right = zoomChipFocus
                            down = rewindChipFocus
                        },
                    onClick = handleBack
                )
            }

            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .focusGroup()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TvOverlayChip(
                        label = if (isZoomed) "FILL" else "FIT",
                        selected = isZoomed,
                        focusKey = "tv-zoom",
                        onFocusKeyChanged = { key -> activeTvControlKey = key },
                        modifier = Modifier
                            .focusRequester(zoomChipFocus)
                            .focusProperties {
                                left = backChipFocus
                                right = qualityChipFocus
                                up = zoomChipFocus
                                down = rewindChipFocus
                            },
                        onClick = {
                            isZoomed = !isZoomed
                            controlsResetTick++
                        }
                    )
                    TvOverlayChip(
                        label = if (currentQuality == PlaybackQuality.Auto) "HD" else currentQuality.label,
                        selected = showQualityPanel,
                        focusKey = "tv-quality",
                        onFocusKeyChanged = { key -> activeTvControlKey = key },
                        modifier = Modifier
                            .focusRequester(qualityChipFocus)
                            .focusProperties {
                                left = zoomChipFocus
                                right = tracksChipFocus
                                up = qualityChipFocus
                                down = if (showQualityPanel) qualityPanelFirstFocus else playChipFocus
                            },
                        onClick = {
                            showQualityPanel = !showQualityPanel
                            if (showQualityPanel) showTrackPanel = false
                            controlsResetTick++
                        }
                    )
                    TvOverlayChip(
                        label = if (showTrackPanel) "TRACKS ON" else "TRACKS",
                        selected = showTrackPanel,
                        focusKey = "tv-tracks",
                        onFocusKeyChanged = { key -> activeTvControlKey = key },
                        modifier = Modifier
                            .focusRequester(tracksChipFocus)
                            .focusProperties {
                                left = qualityChipFocus
                                up = tracksChipFocus
                                down = if (showTrackPanel) trackPanelFirstFocus else forwardChipFocus
                            },
                        onClick = {
                            showTrackPanel = !showTrackPanel
                            if (showTrackPanel) showQualityPanel = false
                            controlsResetTick++
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BottomScrimBrush)
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ControlsBarBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvOverlayChip(
                                label = "-10s",
                                selected = false,
                                focusKey = "tv-rewind",
                                onFocusKeyChanged = { key -> activeTvControlKey = key },
                                modifier = Modifier
                                    .focusRequester(rewindChipFocus)
                                    .focusProperties {
                                        left = rewindChipFocus
                                        right = playChipFocus
                                        up = zoomChipFocus
                                        down = rewindChipFocus
                                    },
                                onClick = viewModel::skipBackward
                            )
                            TvOverlayChip(
                                label = if (playbackState == PlaybackState.Playing) "Pause" else "Play",
                                selected = playbackState == PlaybackState.Playing,
                                focusKey = "tv-play",
                                onFocusKeyChanged = { key -> activeTvControlKey = key },
                                modifier = Modifier
                                    .focusRequester(playChipFocus)
                                    .focusProperties {
                                        left = rewindChipFocus
                                        right = forwardChipFocus
                                        up = qualityChipFocus
                                        down = playChipFocus
                                    },
                                onClick = {
                                    // Enter/DpadCenter is consumed in onPreviewKeyEvent and
                                    // dispatched centrally (no double-fire). This onClick only
                                    // fires on touch/mouse click events.
                                    viewModel.playPause()
                                    controlsResetTick++
                                }
                            )
                            TvOverlayChip(
                                label = "+10s",
                                selected = false,
                                focusKey = "tv-forward",
                                onFocusKeyChanged = { key -> activeTvControlKey = key },
                                modifier = Modifier
                                    .focusRequester(forwardChipFocus)
                                    .focusProperties {
                                        left = playChipFocus
                                        right = forwardChipFocus
                                        up = tracksChipFocus
                                        down = forwardChipFocus
                                    },
                                onClick = viewModel::skipForward
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PlaybackSeekBar(
                            viewModel = viewModel,
                            isTvLayout = true
                        )
                    }
                }
            }
        } else {
            AnimatedVisibility(
                visible = showControls || playbackState is PlaybackState.Error,
                modifier = Modifier.align(Alignment.TopStart),
                enter = fadeIn(), exit = fadeOut()
            ) {
                IconButton(
                    onClick = handleBack,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(44.dp)
                        .semantics { contentDescription = "Back" }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GreenAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = fadeIn(), exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TopScrimBrush)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isZoomed = !isZoomed
                            controlsResetTick++
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(ControlsBarBg, CircleShape)
                            .border(
                                1.dp,
                                if (isZoomed) GreenAccent else Color(0xFF444444),
                                CircleShape
                            )
                            .semantics {
                                contentDescription = if (isZoomed) {
                                    "Video mode fill. Tap to switch to fit"
                                } else {
                                    "Video mode fit. Tap to switch to fill"
                                }
                            }
                    ) {
                        Text(
                            if (isZoomed) "FILL" else "FIT",
                            color = if (isZoomed) GreenAccent else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            showQualityPanel = !showQualityPanel
                            if (showQualityPanel) showTrackPanel = false
                            controlsResetTick++
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(ControlsBarBg, CircleShape)
                            .border(
                                1.dp,
                                if (showQualityPanel) GreenAccent else Color(0xFF444444),
                                CircleShape
                            )
                            .semantics {
                                contentDescription = if (showQualityPanel) {
                                    "Close quality options"
                                } else {
                                    "Open quality options"
                                }
                            }
                    ) {
                        Text(
                            if (currentQuality == PlaybackQuality.Auto) "HD" else currentQuality.label,
                            color = if (showQualityPanel) GreenAccent else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            showTrackPanel = !showTrackPanel
                            if (showTrackPanel) showQualityPanel = false
                            controlsResetTick++
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(ControlsBarBg, CircleShape)
                            .border(
                                1.dp,
                                if (showTrackPanel) GreenAccent else Color(0xFF444444),
                                CircleShape
                            )
                            .semantics {
                                contentDescription = if (showTrackPanel) {
                                    "Close track list"
                                } else {
                                    "Open track list"
                                }
                            }
                    ) {
                        Icon(
                            imageVector = if (showTrackPanel) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = if (showTrackPanel) "Close track list" else "Open track list",
                            tint = if (showTrackPanel) GreenAccent else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(), exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BottomScrimBrush)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ControlsBarBg)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PlayPauseControl(
                            viewModel = viewModel,
                            onPlayPause = {
                                viewModel.playPause()
                                controlsResetTick++
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PlaybackSeekBar(viewModel = viewModel)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && showTrackPanel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            TrackSelectionPanel(
                viewModel = viewModel,
                isTvLayout = isTvLayout,
                initialItemFocus = trackPanelFirstFocus,
                returnFocus = tracksChipFocus
            )
        }

        AnimatedVisibility(
            visible = showControls && showQualityPanel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            QualitySelectionPanel(
                viewModel = viewModel,
                isTvLayout = isTvLayout,
                initialItemFocus = qualityPanelFirstFocus,
                returnFocus = qualityChipFocus,
                onSelect = { showQualityPanel = false }
            )
        }
    }
}

@Composable
private fun TvOverlayChip(
    label: String,
    selected: Boolean,
    focusKey: String,
    onFocusKeyChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        selected -> GreenAccent
        isFocused -> GreenAccent.copy(alpha = 0.85f)
        else -> Color(0xFF444444)
    }

    Box(
        modifier = modifier
            .background(if (isFocused) TvChipFocusedBg else TvChipBg, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusKeyChanged(if (it.isFocused) focusKey else null)
            }
            // .focusable() removed — .clickable() below already makes this node
            // focusable. Keeping both created a duplicate focus node that could
            // cause Enter/DpadCenter to be processed twice on Android TV.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected || isFocused) GreenAccent else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

