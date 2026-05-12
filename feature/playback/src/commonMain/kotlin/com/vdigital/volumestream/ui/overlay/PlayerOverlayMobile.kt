package com.vdigital.volumestream.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.widget.PlayPauseControl
import com.vdigital.volumestream.ui.widget.PlaybackSeekBar
import com.vdigital.volumestream.ui.widget.QualitySelectionPanel
import com.vdigital.volumestream.ui.widget.TrackSelectionPanel

// ── Private theme tokens ──────────────────────────────────────────────────────
private val GreenAccent      = Color(0xFF00E676)
private val ControlsBarBg    = Color(0x8C000000)
private val TopScrimBrush    = Brush.verticalGradient(listOf(Color(0x8F000000), Color.Transparent))
private val BottomScrimBrush = Brush.verticalGradient(listOf(Color.Transparent, Color(0x96000000)))

/**
 * Mobile (phone / tablet) player overlay.
 *
 * Must be called inside a [androidx.compose.foundation.layout.BoxScope] — typically the
 * root `Box` inside [com.vdigital.volumestream.ui.view.PlaybackView].
 *
 * Renders:
 * - Back arrow (top-start, visible while controls are shown or an error is active)
 * - Zoom / Quality / Tracks icon-buttons (top-end)
 * - Play-pause control + seek bar (bottom)
 * - Track and quality selection panels (bottom, sliding)
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.PlayerOverlayMobile(
    state: PlayerOverlayState,
    viewModel: PlaybackViewModel,
    playbackState: PlaybackState,
    currentQuality: PlaybackQuality,
    onBack: () -> Unit,
) {
    // ── Back button ───────────────────────────────────────────────────────────
    AnimatedVisibility(
        visible = state.showControls || playbackState is PlaybackState.Error,
        modifier = Modifier.align(Alignment.TopStart),
        enter = fadeIn(), exit = fadeOut()
    ) {
        IconButton(
            onClick = onBack,
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

    // ── Top-end toolbar: Zoom / Quality / Tracks ──────────────────────────────
    AnimatedVisibility(
        visible = state.showControls,
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
            // Zoom toggle
            IconButton(
                onClick = {
                    state.isZoomed = !state.isZoomed
                    state.resetControlsTimer()
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(ControlsBarBg, CircleShape)
                    .border(1.dp, if (state.isZoomed) GreenAccent else Color(0xFF444444), CircleShape)
                    .semantics {
                        contentDescription = if (state.isZoomed)
                            "Video mode fill. Tap to switch to fit"
                        else
                            "Video mode fit. Tap to switch to fill"
                    }
            ) {
                Text(
                    text = if (state.isZoomed) "FILL" else "FIT",
                    color = if (state.isZoomed) GreenAccent else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))

            // Quality selector
            IconButton(
                onClick = {
                    state.showQualityPanel = !state.showQualityPanel
                    if (state.showQualityPanel) state.showTrackPanel = false
                    state.resetControlsTimer()
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(ControlsBarBg, CircleShape)
                    .border(1.dp, if (state.showQualityPanel) GreenAccent else Color(0xFF444444), CircleShape)
                    .semantics {
                        contentDescription = if (state.showQualityPanel)
                            "Close quality options" else "Open quality options"
                    }
            ) {
                Text(
                    text = if (currentQuality == PlaybackQuality.Auto) "HD" else currentQuality.label,
                    color = if (state.showQualityPanel) GreenAccent else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))

            // Track list toggle
            IconButton(
                onClick = {
                    state.showTrackPanel = !state.showTrackPanel
                    if (state.showTrackPanel) state.showQualityPanel = false
                    state.resetControlsTimer()
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(ControlsBarBg, CircleShape)
                    .border(1.dp, if (state.showTrackPanel) GreenAccent else Color(0xFF444444), CircleShape)
                    .semantics {
                        contentDescription = if (state.showTrackPanel)
                            "Close track list" else "Open track list"
                    }
            ) {
                Icon(
                    imageVector = if (state.showTrackPanel) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = if (state.showTrackPanel) "Close track list" else "Open track list",
                    tint = if (state.showTrackPanel) GreenAccent else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // ── Bottom bar: Play-pause + seek ─────────────────────────────────────────
    AnimatedVisibility(
        visible = state.showControls,
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
                        state.resetControlsTimer()
                    }
                )
                Spacer(Modifier.height(6.dp))
                PlaybackSeekBar(viewModel = viewModel)
            }
        }
    }

    // ── Sliding panels ────────────────────────────────────────────────────────
    // On mobile these panels need no explicit TV focus management.
    val dummyFocus = remember { FocusRequester() }

    AnimatedVisibility(
        visible = state.showControls && state.showTrackPanel,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }),
        exit  = slideOutVertically(targetOffsetY  = { it })
    ) {
        TrackSelectionPanel(
            viewModel = viewModel,
            isTvLayout = false,
            initialItemFocus = dummyFocus,
            returnFocus = dummyFocus
        )
    }

    AnimatedVisibility(
        visible = state.showControls && state.showQualityPanel,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }),
        exit  = slideOutVertically(targetOffsetY  = { it })
    ) {
        QualitySelectionPanel(
            viewModel = viewModel,
            isTvLayout = false,
            initialItemFocus = dummyFocus,
            returnFocus = dummyFocus,
            onSelect = { state.showQualityPanel = false }
        )
    }
}

