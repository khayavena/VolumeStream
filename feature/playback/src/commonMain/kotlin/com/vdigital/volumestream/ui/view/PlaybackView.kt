package com.vdigital.volumestream.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
private const val CONTROLS_HIDE_DELAY_MS = 4_000L

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackView(
    playbackInstanceKey: String = "playback-default",
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
    val currentQuality    by viewModel.qualityUI.collectAsState()

    LaunchedEffect(controlsResetTick) {
        showControls = true
        delay(CONTROLS_HIDE_DELAY_MS)
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
    val playbackState by viewModel.playBackStateUI.collectAsState()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    controlsResetTick++
                }
            }
    ) {
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
                        text = "Tap Back to go back",
                        color = Color(0xFF00E676),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    handleBack()
                                }
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                    })
                    Spacer(modifier = Modifier.height(6.dp))
                    PlaybackSeekBar(viewModel = viewModel)
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && showTrackPanel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            TrackSelectionPanel(viewModel = viewModel)
        }

        AnimatedVisibility(
            visible = showControls && showQualityPanel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            QualitySelectionPanel(
                viewModel = viewModel,
                onSelect = { showQualityPanel = false }
            )
        }
    }
}
