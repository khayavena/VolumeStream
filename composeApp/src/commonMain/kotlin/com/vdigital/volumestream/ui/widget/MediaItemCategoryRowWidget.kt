package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.model.PlaybackMediaItem

@Composable
fun PlaybackCategoryRowWidget(category: String, playbackMediaItems: List<PlaybackMediaItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        LazyRow {
            items(playbackMediaItems) { movie ->
                MediaItemWidget(playbackMediaItem = movie)
            }
        }
    }
}
