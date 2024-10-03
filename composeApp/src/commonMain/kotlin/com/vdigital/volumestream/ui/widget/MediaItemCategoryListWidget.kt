package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.model.PlaybackMediaItem

@Composable
fun MediaItemCategoryListWidget(mediaItemCategories: Map<String, MutableList<PlaybackMediaItem>>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        mediaItemCategories.forEach { (category, playbackMediaItems) ->
            item {
                PlaybackCategoryRowWidget(
                    category = category,
                    playbackMediaItems = playbackMediaItems
                )
            }
        }
    }
}
