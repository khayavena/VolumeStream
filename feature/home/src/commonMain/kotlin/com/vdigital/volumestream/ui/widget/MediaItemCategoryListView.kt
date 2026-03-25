package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.navigation.Screen
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem
import org.koin.compose.koinInject

@Composable
fun MediaItemCategoryListView(
    mediaItemCategories: Map<String, MutableList<PlaybackMediaItem>>,
    navController: NavHostController,
    downloadViewModel: DownloadViewModel,
) {
    val holder: SelectedMediaItemHolder = koinInject()
    val listState = rememberLazyListState()

    // Pick the very first item across all categories as the hero feature
    val featuredItem: PlaybackMediaItem? = mediaItemCategories.values.firstOrNull()?.firstOrNull()

    LazyColumn(
        state = listState,
        modifier = Modifier.background(Color.Black).fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        // Hero banner
        if (featuredItem != null) {
            item(key = "hero_banner") {
                HeroBannerWidget(
                    item = featuredItem,
                    downloadViewModel = downloadViewModel,
                    onPlay = {
                        holder.select(featuredItem)
                        navController.navigate(Screen.Play.route)
                    }
                )
            }
        }

        mediaItemCategories.forEach { (category, items) ->
            item(key = category) {
                PlaybackCategoryCarousel(
                    navController = navController,
                    category = category,
                    playbackMediaItems = items,
                    downloadViewModel = downloadViewModel
                )
            }
        }
    }
}
