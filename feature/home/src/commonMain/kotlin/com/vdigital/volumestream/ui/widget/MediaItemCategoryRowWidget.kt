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
import androidx.navigation.NavHostController
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.navigation.Screen
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem
import org.koin.compose.koinInject

private val GreenAccent = Color(0xFF00E676)

@Composable
fun PlaybackCategoryCarousel(
    category: String,
    playbackMediaItems: List<PlaybackMediaItem>,
    navController: NavHostController,
    downloadViewModel: DownloadViewModel,
) {
    val holder: SelectedMediaItemHolder = koinInject()

    val vertPad = 8.dp
    val titleSize = 16.sp
    val rowPad = 8.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = vertPad)
    ) {
        Text(
            color = GreenAccent,
            text = category,
            style = MaterialTheme.typography.h6.copy(
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(start = rowPad, end = rowPad, bottom = 4.dp)
        )
        LazyRow(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = rowPad)
        ) {
            items(playbackMediaItems, key = { "${category}_${it.id}" }) { mediaItem ->
                MediaItemWidget(
                    playbackMediaItem = mediaItem,
                    downloadViewModel = downloadViewModel
                ) {
                    println("[NAV][trace] tapped mediaId=${mediaItem.id} title=${mediaItem.title}")
                    holder.select(mediaItem)
                    println("[NAV][trace] holder.select done, navigating to ${Screen.Play.route}")
                    navController.navigate(Screen.Play.route)
                    println("[NAV][trace] navigate() called")
                }
            }
        }
    }
}
