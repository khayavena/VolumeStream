package com.vdigital.volumestream.ui.view


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.platform.enum.OsType
import com.vdigital.volumestream.platform.orientation.LockLandscapeOrientation
import com.vdigital.volumestream.platform.view.PlatformMediaPlayerView
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.widget.PlayPauseControl
import com.vdigital.volumestream.ui.widget.PlaybackBufferingIndicator
import com.vdigital.volumestream.ui.widget.PlaybackSeekBar
import com.vdigital.volumestream.ui.widget.TrackSelectionPanel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val GreenAccent = Color(0xFF00E676)
private val ControlsBarBg = Color(0xCC000000)
private const val CONTROLS_HIDE_DELAY_MS = 4_000L

@OptIn(KoinExperimentalAPI::class)
@Composable
fun PlaybackView() {
    LockLandscapeOrientation()
    val viewModel: PlaybackViewModel = koinViewModel()
    val controller = remember { viewModel.getPlatformController() }

    // Screen-level safety net: release all player resources when PlaybackView
    // leaves the composition (e.g. user navigates back).
    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    var showControls by remember { mutableStateOf(true) }
    var showTrackPanel by remember { mutableStateOf(false) }
    // Ticker that resets every time the user taps — when it reaches 0 controls hide
    var controlsResetTick by remember { mutableStateOf(0) }

    // Auto-hide timer: restarts whenever controlsResetTick changes
    LaunchedEffect(controlsResetTick) {
        showControls = true
        delay(CONTROLS_HIDE_DELAY_MS)
        showControls = false
        showTrackPanel = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Any touch anywhere shows controls and resets the hide timer.
            // PointerEventPass.Initial intercepts before children consume the event,
            // and we do NOT consume it — children (buttons, seekbar) still work.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitPointerEvent(PointerEventPass.Initial)
                    controlsResetTick++
                }
            }
    ) {
        // Video surface
        when (viewModel.osType) {
            OsType.IOS -> PlatformMediaPlayerView(
                modifier = Modifier.fillMaxSize(),
                playbackStateController = controller,
                onTap = { controlsResetTick++ }  // UIControl overlay calls this on every tap
            )
            OsType.ANDROID -> PlatformMediaPlayerView(
                modifier = Modifier.fillMaxSize(), controller
            )
        }

        // Buffering spinner (always visible when buffering)
        Box(modifier = Modifier.align(Alignment.Center)) {
            PlaybackBufferingIndicator()
        }

        // Top bar — track selector button
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        showTrackPanel = !showTrackPanel
                        controlsResetTick++      // reset hide timer on interaction
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(ControlsBarBg, CircleShape)
                        .border(
                            1.dp,
                            if (showTrackPanel) GreenAccent else Color(0xFF444444),
                            CircleShape
                        )
                ) {
                    Text(
                        if (showTrackPanel) "✕" else "☰",
                        color = if (showTrackPanel) GreenAccent else Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Bottom controls bar
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ControlsBarBg)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlayPauseControl(onPlayPause = {
                    viewModel.playPause()
                    controlsResetTick++          // reset hide timer on play/pause
                })
                Spacer(modifier = Modifier.height(6.dp))
                PlaybackSeekBar()
            }
        }

        // Slide-up track selection panel
        AnimatedVisibility(
            visible = showControls && showTrackPanel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            TrackSelectionPanel()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialise()
    }
}




