package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seiko.imageloader.rememberImagePainter
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem

private val GreenAccent = Color(0xFF00E676)

/**
 * Full-width hero banner for featured content.
 *
 * On TV this is a full-bleed 480dp-high hero that can be controlled with a
 * D-pad. On mobile it's a smaller 280dp banner with touch-friendly buttons.
 */
@Composable
fun HeroBannerWidget(
    item: PlaybackMediaItem,
    downloadViewModel: DownloadViewModel,
    isTvLayout: Boolean = false,
    onPlay: () -> Unit,
) {
    val downloadState = downloadViewModel.observeState(item.id).collectAsState()

    val height = if (isTvLayout) 360.dp else 280.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (item.artworkUrl.isNotBlank()) {
            Image(
                painter = rememberImagePainter(item.artworkUrl),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().background(Color(0xFF1A1A1A)))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.35f to Color.Transparent,
                            0.70f to Color.Black.copy(alpha = 0.55f),
                            1.00f to Color.Black.copy(alpha = 0.92f),
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = if (isTvLayout) 52.dp else 12.dp, top = 12.dp)
                .background(GreenAccent, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("FEATURED", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    horizontal = if (isTvLayout) 52.dp else 16.dp,
                    vertical = if (isTvLayout) 32.dp else 12.dp
                )
        ) {
            Text(item.title, color = Color.White, fontSize = if (isTvLayout) 32.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(if (isTvLayout) 8.dp else 4.dp))
                Text(item.description, color = Color.White.copy(alpha = 0.75f), fontSize = if (isTvLayout) 14.sp else 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(if (isTvLayout) 20.dp else 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(GreenAccent)
                        .clickable(onClick = onPlay)
                        .focusable()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(if (isTvLayout) 20.dp else 18.dp))
                    Spacer(Modifier.width(if (isTvLayout) 6.dp else 4.dp))
                    Text("Play", color = Color.Black, fontSize = if (isTvLayout) 15.sp else 14.sp, fontWeight = FontWeight.Bold)
                }
                val isDownloaded = downloadState.value is DownloadState.Completed
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { if (isDownloaded) downloadViewModel.remove(item.id) else downloadViewModel.download(item) }
                        .focusable()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(if (isTvLayout) 20.dp else 18.dp).background(if (isDownloaded) GreenAccent else Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (isDownloaded) Icons.Default.PlayArrow else Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(if (isTvLayout) 14.dp else 12.dp))
                    }
                    Spacer(Modifier.width(if (isTvLayout) 8.dp else 6.dp))
                    Text(if (isDownloaded) "Downloaded" else "My List", color = Color.White, fontSize = if (isTvLayout) 14.sp else 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
