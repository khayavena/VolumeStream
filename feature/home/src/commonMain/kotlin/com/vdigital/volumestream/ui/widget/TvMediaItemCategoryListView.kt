package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vditital.data.model.PlaybackMediaItem
import org.koin.compose.koinInject

private val TvHeroHeight = 360.dp

@Composable
fun TvMediaItemCategoryListView(
    mediaItemCategories: Map<String, MutableList<PlaybackMediaItem>>,
    downloadViewModel: DownloadViewModel,
    onPlay: () -> Unit,
) {
    val holder: SelectedMediaItemHolder = koinInject()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val featuredItem: PlaybackMediaItem? = mediaItemCategories.values.firstOrNull()?.firstOrNull()

    Box(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize()
            .focusRequester(focusRequester)
    ) {
        if (featuredItem != null) {
            HeroBannerWidget(
                item = featuredItem,
                downloadViewModel = downloadViewModel,
                isTvLayout = true,
                onPlay = {
                    holder.select(featuredItem)
                    onPlay()
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (featuredItem != null) TvHeroHeight else 0.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            mediaItemCategories.forEach { (category, items) ->
                item(key = category) {
                    TvPlaybackCategoryCarousel(
                        category = category,
                        playbackMediaItems = items,
                        downloadViewModel = downloadViewModel,
                        onPlayItem = { item ->
                            holder.select(item)
                            onPlay()
                        }
                    )
                }
            }
        }
    }
}


