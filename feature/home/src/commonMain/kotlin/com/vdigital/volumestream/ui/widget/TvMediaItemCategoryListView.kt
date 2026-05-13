package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// Must match HeroBannerWidget's TV height (isTvLayout = true → 320.dp)
private val TvHeroHeight = 320.dp

@Composable
fun TvMediaItemCategoryListView(
    // Immutable List — callers must not mutate items after passing them in.
    mediaItemCategories: Map<String, List<PlaybackMediaItem>>,
    downloadViewModel: DownloadViewModel,
    downloadsEnabled: Boolean = true,
    onPlay: () -> Unit,
) {
    val holder: SelectedMediaItemHolder = koinInject()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    // Co-compute featuredItem and carouselCategories in a single remember block so
    // the dependency between them is explicit and both are always in sync.
    // Deduplicate by ID (guards against server duplicates) and exclude the featured
    // item from its source row so it only appears in the hero banner.
    val (featuredItem, carouselCategories) = remember(mediaItemCategories) {
        val featured = mediaItemCategories.values.firstOrNull()?.firstOrNull()
        var firstCategory = true
        val carousel = mediaItemCategories.mapNotNull { (category, items) ->
            val filtered = if (firstCategory && featured != null) {
                firstCategory = false
                items.distinctBy { it.id }.filter { it.id != featured.id }
            } else {
                firstCategory = false
                items.distinctBy { it.id }
            }
            if (filtered.isEmpty()) null else category to filtered
        }
        featured to carousel
    }

    // Stable hero-play lambda — recreating it every recompose would cause
    // HeroBannerWidget to see a new reference and recompose unnecessarily.
    val heroPlayAction = remember(featuredItem, onPlay) {
        {
            if (featuredItem != null) holder.select(featuredItem)
            onPlay()
        }
    }
    val onPlayItemAction = remember(onPlay) {
        { item: PlaybackMediaItem ->
            holder.select(item)
            onPlay()
        }
    }

    Box(
        modifier = Modifier
            .background(Color.Black)
            .fillMaxSize()
            .focusRequester(focusRequester)
    ) {
        // Hero banner — Z-below the LazyColumn so it shows through the transparent
        // top padding before the first carousel row scrolls into view.
        if (featuredItem != null) {
            HeroBannerWidget(
                item              = featuredItem,
                downloadViewModel = downloadViewModel,
                isTvLayout        = true,
                showDownloadAction = downloadsEnabled,
                onPlay            = heroPlayAction,
            )
        }

        LazyColumn(
            state          = listState,
            modifier       = Modifier
                .fillMaxSize()
                .padding(top = if (featuredItem != null) TvHeroHeight else 0.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            items(
                items = carouselCategories,
                key = { (category, _) -> category },
                contentType = { "tvCategoryCarousel" },
            ) { (category, items) ->
                TvPlaybackCategoryCarousel(
                    category          = category,
                    playbackMediaItems = items,
                    downloadViewModel  = downloadViewModel,
                    downloadsEnabled   = downloadsEnabled,
                    onPlayItem         = onPlayItemAction,
                )
            }
        }
    }
}
