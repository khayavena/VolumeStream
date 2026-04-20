package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seiko.imageloader.rememberImagePainter
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem

private val GreenAccent = Color(0xFF00E676)

/**
 * TV-optimised media item card. Larger artwork (16:9 ratio), bigger text,
 * and [Modifier.focusable] so D-pad focus works on both Android TV and tvOS.
 *
 * Download indicator matches [MediaItemWidget] so the UX is consistent.
 */
@Composable
fun TvMediaItemWidget(
    playbackMediaItem: PlaybackMediaItem,
    downloadViewModel: DownloadViewModel,
    onClick: () -> Unit,
) {
    val downloadState = downloadViewModel.observeState(playbackMediaItem.id).collectAsState()
    Column(
        modifier = Modifier
            .focusable()
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(240.dp)
    ) {
        Box {
            Image(
                painter = rememberImagePainter(playbackMediaItem.artworkUrl),
                contentDescription = playbackMediaItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(135.dp)   // 16:9 for 240dp width
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            )
            // Download overlay (bottom-right corner)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f))
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
                        modifier = Modifier.size(20.dp)
                    )
                    is DownloadState.Queued -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    is DownloadState.Downloading -> Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = s.progress,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp,
                            color = GreenAccent
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    is DownloadState.Completed -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Downloaded",
                        tint = GreenAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playbackMediaItem.title,
            color = Color.White,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

