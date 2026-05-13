package com.vdigital.volumestream.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.widget.PlaybackSeekBar
import com.vdigital.volumestream.ui.widget.QualitySelectionPanel
import com.vdigital.volumestream.ui.widget.TvTracksCarousel

// Theme tokens are defined in PlaybackOverlayTheme.kt (shared across overlays).

/**
 * Android TV player overlay.
 *
 * Must be called inside a [androidx.compose.foundation.layout.BoxScope] — typically the
 * root `Box` inside [com.vdigital.volumestream.ui.view.PlaybackView].
 *
 * Responsibilities:
 * - Owns all TV chip [FocusRequester]s and the accompanying focus [LaunchedEffect]s.
 * - Renders Back chip (top-start), Zoom/Quality row (top-end), and the
 *   Rewind/Play/Forward row + seek bar + inline tracks carousel (bottom).
 * - Renders the Quality selection panel with full TV focus wiring.
 * - Tracks are surfaced via the always-visible horizontal [TvTracksCarousel] below
 *   the seek bar — no separate Tracks button is needed.
 *
 * Note: Enter/DpadCenter key events are consumed and dispatched centrally by
 * PlaybackView's root `onPreviewKeyEvent` using [PlayerOverlayState.activeTvControlKey].
 * Each chip reports its focus key to [PlayerOverlayState] via [onFocusKeyChanged].
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.PlayerOverlayTv(
    state: PlayerOverlayState,
    viewModel: PlaybackViewModel,
    playbackState: PlaybackState,
    currentQuality: PlaybackQuality,
    onBack: () -> Unit,
) {
    // ── Focus requesters (owned here — only TV needs them) ────────────────────
    val backChipFocus           = remember { FocusRequester() }
    val zoomChipFocus           = remember { FocusRequester() }
    val qualityChipFocus        = remember { FocusRequester() }
    val rewindChipFocus         = remember { FocusRequester() }
    val playChipFocus           = remember { FocusRequester() }
    val forwardChipFocus        = remember { FocusRequester() }
    val qualityPanelFirstFocus  = remember { FocusRequester() }
    val carouselFirstFocus      = remember { FocusRequester() }

    // Single cached lambda — all chips share it to avoid allocating a new
    // Function1 instance per chip on every recomposition.
    val onChipFocus: (String?) -> Unit = remember { { key -> state.activeTvControlKey = key } }

    // Auto-focus Play chip whenever the overlay becomes visible and no panel is open.
    LaunchedEffect(state.showControls, state.showQualityPanel) {
        if (!state.showControls) return@LaunchedEffect
        if (state.showQualityPanel) return@LaunchedEffect
        runCatching { playChipFocus.requestFocus() }
    }
    LaunchedEffect(state.showQualityPanel) {
        if (!state.showQualityPanel) return@LaunchedEffect
        runCatching { qualityPanelFirstFocus.requestFocus() }
    }

    // ── Back chip (top-start) ─────────────────────────────────────────────────
    AnimatedVisibility(
        visible = state.showControls || playbackState is PlaybackState.Error,
        modifier = Modifier.align(Alignment.TopStart),
        enter = fadeIn(), exit = fadeOut()
    ) {
        TvOverlayChip(
            label = "Back",
            selected = false,
            focusKey = "tv-back",
            onFocusKeyChanged = onChipFocus,
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
                .focusRequester(backChipFocus)
                .focusProperties {
                    up    = backChipFocus
                    right = zoomChipFocus
                    down  = rewindChipFocus
                },
            onClick = onBack
        )
    }

    // ── Top-end row: Zoom / Quality ───────────────────────────────────────────
    AnimatedVisibility(
        visible = state.showControls,
        modifier = Modifier.align(Alignment.TopEnd),
        enter = fadeIn(), exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .focusGroup()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TvOverlayChip(
                label = if (state.isZoomed) "FILL" else "FIT",
                selected = state.isZoomed,
                focusKey = "tv-zoom",
                onFocusKeyChanged = onChipFocus,
                modifier = Modifier
                    .focusRequester(zoomChipFocus)
                    .focusProperties {
                        left  = backChipFocus
                        right = qualityChipFocus
                        up    = zoomChipFocus
                        down  = rewindChipFocus
                    },
                onClick = {
                    state.isZoomed = !state.isZoomed
                    state.resetControlsTimer()
                }
            )
            TvOverlayChip(
                label = if (currentQuality == PlaybackQuality.Auto) "HD" else currentQuality.label,
                selected = state.showQualityPanel,
                focusKey = "tv-quality",
                onFocusKeyChanged = onChipFocus,
                modifier = Modifier
                    .focusRequester(qualityChipFocus)
                    .focusProperties {
                        left  = zoomChipFocus
                        right = qualityChipFocus   // end of row — stay put
                        up    = qualityChipFocus
                        down  = if (state.showQualityPanel) qualityPanelFirstFocus else playChipFocus
                    },
                onClick = {
                    state.showQualityPanel = !state.showQualityPanel
                    state.resetControlsTimer()
                }
            )
        }
    }

    // ── Bottom bar: Rewind / Play / Forward + seek ────────────────────────────
    AnimatedVisibility(
        visible = state.showControls,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(), exit = fadeOut()
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
                        onFocusKeyChanged = onChipFocus,
                        modifier = Modifier
                            .focusRequester(rewindChipFocus)
                            .focusProperties {
                                left  = rewindChipFocus
                                right = playChipFocus
                                up    = zoomChipFocus
                                down  = carouselFirstFocus
                            },
                        onClick = viewModel::skipBackward
                    )
                    TvOverlayChip(
                        label = if (playbackState == PlaybackState.Playing) "Pause" else "Play",
                        selected = playbackState == PlaybackState.Playing,
                        focusKey = "tv-play",
                        onFocusKeyChanged = onChipFocus,
                        modifier = Modifier
                            .focusRequester(playChipFocus)
                            .focusProperties {
                                left  = rewindChipFocus
                                right = forwardChipFocus
                                up    = qualityChipFocus
                                down  = carouselFirstFocus
                            },
                        onClick = {
                            // Enter/DpadCenter is consumed and dispatched by PlaybackView's
                            // root onPreviewKeyEvent.  This onClick fires only from
                            // touch/mouse clicks on TV.
                            viewModel.playPause()
                            state.resetControlsTimer()
                        }
                    )
                    TvOverlayChip(
                        label = "+10s",
                        selected = false,
                        focusKey = "tv-forward",
                        onFocusKeyChanged = onChipFocus,
                        modifier = Modifier
                            .focusRequester(forwardChipFocus)
                            .focusProperties {
                                left  = playChipFocus
                                right = forwardChipFocus
                                up    = qualityChipFocus
                                down  = carouselFirstFocus
                            },
                        onClick = viewModel::skipForward
                    )
                }
                Spacer(Modifier.height(10.dp))
                PlaybackSeekBar(viewModel = viewModel, isTvLayout = true)

                // ── Tracks carousel (YouTube TV-style horizontal strip) ──
                TvTracksCarousel(
                    viewModel          = viewModel,
                    firstItemFocus     = carouselFirstFocus,
                    upFocusRequester   = playChipFocus,
                    onTrackFocusKey    = { track ->
                        state.focusedCarouselTrack = track
                        state.activeTvControlKey   = if (track != null) "tv-carousel" else null
                    }
                )
            }
        }
    }

    // ── Sliding panels (with TV focus wiring) ─────────────────────────────────

    AnimatedVisibility(
        visible = state.showControls && state.showQualityPanel,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }),
        exit  = slideOutVertically(targetOffsetY  = { it })
    ) {
        QualitySelectionPanel(
            viewModel = viewModel,
            isTvLayout = true,
            initialItemFocus = qualityPanelFirstFocus,
            returnFocus = qualityChipFocus,
            onSelect = { state.showQualityPanel = false }
        )
    }
}

// ── TvOverlayChip ─────────────────────────────────────────────────────────────

/**
 * A focusable, clickable pill-shaped chip used in the TV player overlay.
 *
 * Highlights in [GreenAccent] when focused or selected.  Reports its [focusKey]
 * to [onFocusKeyChanged] so that PlaybackView's central key handler can dispatch
 * the correct action when Enter/DpadCenter is pressed.
 */
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
        selected  -> GreenAccent
        isFocused -> GreenAccent.copy(alpha = 0.85f)
        else      -> Color(0xFF444444)
    }

    Box(
        modifier = modifier
            .background(if (isFocused) TvChipFocusedBg else TvChipBg, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusKeyChanged(if (it.isFocused) focusKey else null)
            }
            // .clickable() makes the Box focusable — no separate .focusable() needed.
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

