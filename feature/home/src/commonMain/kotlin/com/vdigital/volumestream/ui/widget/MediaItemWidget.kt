package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seiko.imageloader.rememberImagePainter
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem

private val GreenAccent = Color(0xFF00E676)

@Composable
fun MediaItemWidget(
    playbackMediaItem: PlaybackMediaItem,
    downloadViewModel: DownloadViewModel,
    onClick: () -> Unit
) {
    val downloadState = downloadViewModel.observeState(playbackMediaItem.id).collectAsState()
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(120.dp)
    ) {
        Box {
            Image(
                painter = rememberImagePainter(playbackMediaItem.artworkUrl),
                contentDescription = playbackMediaItem.title,
                modifier = Modifier
                    .height(180.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            )
            // Download overlay button (bottom-right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable {
                        when (val s = downloadState.value) {
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
                    is DownloadState.Idle, is DownloadState.Failed -> Text(
                        text = "\u2193",
                        color = Color.White,
                        style = MaterialTheme.typography.h6
                    )
                    is DownloadState.Queued -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = GreenAccent
                    )
                    is DownloadState.Downloading -> CircularProgressIndicator(
                        progress = s.progress,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = GreenAccent
                    )
                    is DownloadState.Completed -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Downloaded",
                        tint = GreenAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = playbackMediaItem.title,
            color = Color.White,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
