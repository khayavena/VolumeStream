package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seiko.imageloader.rememberImagePainter
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem

private val GreenAccent = Color(0xFF00E676)
private val TvCardPlaceholderTop = Color(0xFF2D4438)
private val TvCardPlaceholderBottom = Color(0xFF161B18)
// Stronger bottom-scrim overlay: hides baked-in metadata text in API thumbnails
private val TvCardScrimBottom = Color(0xD9000000)   // 85% opacity – bottom of card
private const val TvArtworkZoom = 1.18f

/**
 * TV-optimised media item card. Larger artwork (16:9 ratio), bigger text,
 * and [Modifier.focusable] so D-pad focus works on Android TV.
 *
 * Download indicator matches [MediaItemWidget] so the UX is consistent.
 */
@Composable
fun TvMediaItemWidget(
    playbackMediaItem: PlaybackMediaItem,
    downloadViewModel: DownloadViewModel,
    downloadsEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val artworkUrl = remember(playbackMediaItem.artworkUrl) { playbackMediaItem.artworkUrl.trim() }
    val placeholderBrush = remember {
        Brush.verticalGradient(colors = listOf(TvCardPlaceholderTop, TvCardPlaceholderBottom))
    }
    val scrimBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color(0x59000000),
                0.22f to Color(0x33000000),
                0.55f to Color(0x4D000000),
                1.0f to TvCardScrimBottom,
            )
        )
    }
    val focusBorderColor by animateColorAsState(
        targetValue = if (isFocused) GreenAccent else Color.Transparent,
        label = "tvCardFocusBorder"
    )
    val downloadState = downloadViewModel.observeState(playbackMediaItem.id).collectAsState()

    // Single Box — artwork + overlays (scrim, title, download button) all composited inside.
    // No separate Text below the card: baked-in metadata text in the API thumbnail is hidden
    // by the bottom scrim, and we draw our own clean title on top of it.
    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .width(240.dp)
            .height(135.dp)   // fixed 16:9 for 240 dp width
            .clip(RoundedCornerShape(10.dp))
            .border(width = 2.dp, color = focusBorderColor, shape = RoundedCornerShape(10.dp))
    ) {
        // ── Artwork / placeholder ─────────────────────────────────────────────
        if (artworkUrl.isNotBlank()) {
            Image(
                painter = rememberImagePainter(artworkUrl),
                contentDescription = playbackMediaItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // Some feed thumbnails contain a small baked-in preview card in the
                    // top-left corner. Zoom-cropping removes that visual artifact.
                    .graphicsLayer {
                        scaleX = TvArtworkZoom
                        scaleY = TvArtworkZoom
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // ── Scrims – hide baked-in text/corner overlays from source thumbnails ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush)
        )

        // ── Title label at bottom-start ───────────────────────────────────────
        Text(
            text = playbackMediaItem.title,
            color = Color.White,
            style = MaterialTheme.typography.subtitle2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = if (downloadsEnabled) 44.dp else 8.dp, bottom = 8.dp)
        )

        if (downloadsEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .clickable {
                        when (downloadState.value) {
                            is DownloadState.Idle, is DownloadState.Failed ->
                                downloadViewModel.download(playbackMediaItem)
                            is DownloadState.Queued, is DownloadState.Downloading ->
                                downloadViewModel.cancel(playbackMediaItem.id)
                            is DownloadState.Completed ->
                                downloadViewModel.remove(playbackMediaItem.id)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when (val s = downloadState.value) {
                    is DownloadState.Idle, is DownloadState.Failed -> Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    is DownloadState.Queued -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    is DownloadState.Downloading -> Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = s.progress,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                            color = GreenAccent
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    is DownloadState.Completed -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Downloaded",
                        tint = GreenAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

