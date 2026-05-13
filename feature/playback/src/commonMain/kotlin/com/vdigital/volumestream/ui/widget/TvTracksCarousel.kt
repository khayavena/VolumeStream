package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seiko.imageloader.rememberImagePainter
import com.vdigital.volumestream.ui.overlay.GreenAccent
import com.vdigital.volumestream.ui.overlay.TvCardBg
import com.vdigital.volumestream.ui.overlay.TvCarouselLabel
import com.vdigital.volumestream.ui.overlay.TvChipFocusedBg
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vditital.data.model.PlaybackMediaItem


/**
 * Horizontal track carousel for the Android TV player overlay.
 *
 * Rendered below the seek bar — always visible when the controls overlay is shown.
 * Behaves like YouTube TV's episode/chapter strip: a labelled row of focusable
 * track cards the user can navigate with the D-pad Left/Right keys.
 *
 * @param viewModel           Shared [PlaybackViewModel]; supplies track list + selected id.
 * @param firstItemFocus      [FocusRequester] attached to the first card — caller uses it
 *                            to drive initial D-pad focus into the carousel.
 * @param upFocusRequester    [FocusRequester] to navigate to when the user presses D-pad Up
 *                            from any card (typically the Play/Pause chip above).
 * @param onTrackFocusKey     Called with the currently focused card's track id (or `null`
 *                            when focus leaves the carousel) so the overlay's central
 *                            key handler can dispatch Enter/DpadCenter to [viewModel.selectTrack].
 */
@Composable
fun TvTracksCarousel(
    viewModel: PlaybackViewModel,
    firstItemFocus: FocusRequester,
    upFocusRequester: FocusRequester,
    onTrackFocusKey: (PlaybackMediaItem?) -> Unit = {},
) {
    val tracks     = viewModel.trackListUI.collectAsState()
    val selectedId = viewModel.selectedTrackIdUI.collectAsState()
    val listState  = rememberLazyListState()

    // Auto-scroll the carousel so the currently selected track is visible.
    LaunchedEffect(selectedId.value, tracks.value) {
        val idx = tracks.value.indexOfFirst { it.id == selectedId.value }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    if (tracks.value.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        // Section label – mirrors YouTube TV's "Up next" strip heading
        Text(
            text = "TRACKS",
            color = TvCarouselLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks.value.size) { index ->
                val track      = tracks.value[index]
                val isSelected = track.id == selectedId.value
                // Attach the firstItemFocus requester to the very first card so the
                // caller can drive initial D-pad focus here from the controls row above.
                val cardFocusRequester = if (index == 0) firstItemFocus else remember { FocusRequester() }

                TvTrackCard(
                    track      = track,
                    isSelected = isSelected,
                    modifier   = Modifier
                        .focusRequester(cardFocusRequester)
                        .focusProperties {
                            // Navigate Up from any card → controls row (play chip or similar)
                            up = upFocusRequester
                            // Keep left/right default — LazyRow handles neighbour focus automatically
                        },
                    onFocused  = { focused ->
                        onTrackFocusKey(if (focused) track else null)
                    },
                    onClick = {
                        viewModel.selectTrack(track)
                    }
                )
            }
        }
    }
}

// ── Track card ────────────────────────────────────────────────────────────────

@Composable
private fun TvTrackCard(
    track: PlaybackMediaItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        isSelected -> GreenAccent
        isFocused  -> GreenAccent.copy(alpha = 0.80f)
        else       -> Color(0xFF333333)
    }
    val cardBg = when {
        isFocused  -> TvChipFocusedBg
        isSelected -> Color(0x33000000)
        else       -> TvCardBg
    }

    Column(
        modifier = modifier
            .width(130.dp)
            .background(cardBg, RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected || isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                onFocused(it.isFocused)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Artwork thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            if (track.artworkUrl.isNotBlank()) {
                Image(
                    painter = rememberImagePainter(track.artworkUrl),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            // "NOW PLAYING" badge on the currently selected track
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(GreenAccent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "PLAYING",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Track title
        Text(
            text = track.title,
            color = if (isSelected || isFocused) GreenAccent else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Duration  (only when available)
        if (track.durationMs > 0L) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.durationMs.toCarouselTime(),
                color = TvCarouselLabel,
                fontSize = 10.sp
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Long.toCarouselTime(): String {
    val totalSec = (this / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "${h}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
    } else {
        "${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}"
    }
}

