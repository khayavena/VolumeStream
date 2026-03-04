package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seiko.imageloader.rememberImagePainter
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vditital.data.model.PlaybackMediaItem
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val PanelGreen = Color(0xFF00E676)
private val PanelBg = Color(0xFF0A0A0A)
private val PanelDivider = Color(0xFF1C1C1C)
private val SelectedBorder = Color(0xFF00E676)
private val UnselectedBorder = Color.Transparent

@OptIn(KoinExperimentalAPI::class)
@Composable
fun TrackSelectionPanel() {
    val viewModel: PlaybackViewModel = koinViewModel()
    val tracks = viewModel.trackListUI.collectAsState()
    val selectedId = viewModel.selectedTrackIdUI.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(top = 12.dp, bottom = 8.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF444444))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "SELECT TRACK",
            color = PanelGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Divider(color = PanelDivider)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(tracks.value) { track ->
                TrackRow(
                    track = track,
                    isSelected = track.id == selectedId.value,
                    onClick = { viewModel.selectTrack(track) }
                )
                Divider(color = PanelDivider)
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: PlaybackMediaItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Color(0xFF001A0D) else Color.Transparent)
            .border(
                width = 0.dp,
                color = Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Green left bar for selected
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) SelectedBorder else UnselectedBorder)
        )
        Spacer(Modifier.width(10.dp))
        // Artwork
        Image(
            painter = rememberImagePainter(track.artworkUrl),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        // Title
        Text(
            text = track.title,
            color = if (isSelected) PanelGreen else Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Text("▶", color = PanelGreen, fontSize = 12.sp)
        }
    }
}
