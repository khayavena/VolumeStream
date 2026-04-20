package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem

private val GreenAccent = Color(0xFF00E676)

@Composable
fun TvPlaybackCategoryCarousel(
    category: String,
    playbackMediaItems: List<PlaybackMediaItem>,
    downloadViewModel: DownloadViewModel,
    onPlayItem: (PlaybackMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            color = GreenAccent,
            text = category,
            style = MaterialTheme.typography.h6.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        LazyRow(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(playbackMediaItems, key = { "${category}_${it.id}" }) { mediaItem ->
                TvMediaItemWidget(
                    playbackMediaItem = mediaItem,
                    downloadViewModel = downloadViewModel,
                ) {
                    onPlayItem(mediaItem)
                }
            }
        }
    }
}

