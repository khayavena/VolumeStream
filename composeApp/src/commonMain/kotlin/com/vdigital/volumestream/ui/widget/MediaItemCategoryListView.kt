package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vdigital.volumestream.model.PlaybackMediaItem

@Composable
fun MediaItemCategoryListWidget(
    mediaItemCategories: Map<String, MutableList<PlaybackMediaItem>>,
    navController: NavHostController
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        mediaItemCategories.forEach { (category, playbackMediaItems) ->
            item {
                PlaybackCategoryCarousel(
                    navController =navController,
                    category = category,
                    playbackMediaItems = playbackMediaItems
                )
            }
        }
    }
}