package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.navigation.Screen
import com.vditital.data.model.PlaybackMediaItem
import org.koin.compose.koinInject

private val GreenAccent = Color(0xFF00E676)

@Composable
fun PlaybackCategoryCarousel(
    category: String,
    playbackMediaItems: List<PlaybackMediaItem>,
    navController: NavHostController,
) {
    val holder: SelectedMediaItemHolder = koinInject()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            color = GreenAccent,
            text = category,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        LazyRow(state = rememberLazyListState()) {
            items(playbackMediaItems, key = { "${category}_${it.id}" }) { mediaItem ->
                MediaItemWidget(playbackMediaItem = mediaItem) {
                    holder.select(mediaItem)
                    navController.navigate(Screen.Play.route)
                }
            }
        }
    }
}
