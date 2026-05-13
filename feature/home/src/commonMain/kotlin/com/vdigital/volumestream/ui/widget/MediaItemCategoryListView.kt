package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    val featuredItem: PlaybackMediaItem? = remember(mediaItemCategories) {
        mediaItemCategories.values.firstOrNull()?.firstOrNull()
    }

    // Skip the featured item from the first category row — it's already shown in the hero banner.
    // Deduplicated by ID to guard against any cache or server duplication.
    val carouselCategories: List<Pair<String, List<PlaybackMediaItem>>> = remember(mediaItemCategories) {
        var firstCategory = true
        mediaItemCategories.mapNotNull { (category, items) ->
            val filtered = if (firstCategory && featuredItem != null) {
                firstCategory = false
                items.distinctBy { it.id }.filter { it.id != featuredItem.id }
            } else {
                firstCategory = false
                items.distinctBy { it.id }
            }
            if (filtered.isEmpty()) null else category to filtered
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        if (featuredItem != null) {
            item(key = "hero_banner") {
                HeroBannerWidget(
                    item = featuredItem,
                    downloadViewModel = downloadViewModel,
                    isTvLayout = false,
                    onPlay = {
                        holder.select(featuredItem)
                        navController.navigate(Screen.Play.route)
                    }
                )
            }
        }
        carouselCategories.forEach { (category, items) ->
            item(key = category) {
                PlaybackCategoryCarousel(
                    navController = navController,
                    category = category,
                    playbackMediaItems = items,
                    downloadViewModel = downloadViewModel,
                )
            }
        }
    }
}
