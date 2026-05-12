package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seiko.imageloader.rememberImagePainter
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vditital.data.model.PlaybackMediaItem

private val PanelGreen    = Color(0xFF00E676)
private val PanelBg       = Color(0xFF0A0A0A)
private val PanelDivider  = Color(0xFF1C1C1C)

@Composable
fun TrackSelectionPanel(
    viewModel: PlaybackViewModel,
    isTvLayout: Boolean = false,
    initialItemFocus: FocusRequester? = null,
    returnFocus: FocusRequester? = null,
) {
    val tracks     = viewModel.trackListUI.collectAsState()
    val selectedId = viewModel.selectedTrackIdUI.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(top = 12.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(40.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF444444))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "SELECT TRACK",
            color = PanelGreen, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Divider(color = PanelDivider)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(tracks.value.size) { index ->
                val track = tracks.value[index]
                TrackRow(
                    track = track,
                    isSelected = track.id == selectedId.value,
                    isTvLayout = isTvLayout,
                    modifier = Modifier
                        .then(
                            if (isTvLayout && index == 0 && initialItemFocus != null) {
                                Modifier
                                    .focusRequester(initialItemFocus)
                                    .focusProperties {
                                        up = returnFocus ?: initialItemFocus
                                    }
                            } else {
                                Modifier
                            }
                        ),
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
    isTvLayout: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isSelected -> Color(0xFF00E676)
        isFocused && isTvLayout -> Color(0x9900E676)
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = isTvLayout)
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(if (isFocused && isTvLayout) Color(0x22111111) else Color.Transparent)
            .padding(12.dp)
    ) {
        Image(
            painter = rememberImagePainter(track.artworkUrl),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isSelected) Color(0xFF00E676) else Color.White,
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
