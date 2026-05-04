package com.vdigital.volumestream.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.PlaybackViewModel
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality

private val QualityGreen   = Color(0xFF00E676)
private val QualityPanelBg = Color(0xFF0A0A0A)
private val QualityDivider = Color(0xFF1C1C1C)

@Composable
fun QualitySelectionPanel(
    viewModel: PlaybackViewModel,
    onSelect: () -> Unit = {}
) {
    val currentQuality by viewModel.qualityUI.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(QualityPanelBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(top = 12.dp, bottom = 20.dp)
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
            "QUALITY",
            color = QualityGreen, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Divider(color = QualityDivider)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PlaybackQuality.all) { q ->
                val selected = q == currentQuality
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) QualityGreen.copy(alpha = 0.15f)
                            else Color(0xFF1A1A1A)
                        )
                        .border(
                            1.dp,
                            if (selected) QualityGreen else Color(0xFF333333),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            viewModel.setQuality(q)
                            onSelect()
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = q.label,
                        color = if (selected) QualityGreen else Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
