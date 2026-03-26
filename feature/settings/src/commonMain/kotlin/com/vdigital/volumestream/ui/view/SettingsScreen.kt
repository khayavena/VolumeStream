package com.vdigital.volumestream.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vdigital.volumestream.ui.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

private val GreenAccent = Color(0xFF00E676)

@OptIn(KoinExperimentalAPI::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val autoPlay        by viewModel.autoPlay.collectAsState()
    val wifiOnly        by viewModel.wifiOnly.collectAsState()
    val notifications   by viewModel.notifications.collectAsState()
    val subtitles       by viewModel.subtitles.collectAsState()
    val adaptiveQuality by viewModel.adaptiveQuality.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        SettingsSectionHeader(title = "Playback")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column {
                SettingsToggleRow(
                    label = "Auto-Play Next",
                    description = "Automatically play the next video",
                    checked = autoPlay,
                    onCheckedChange = viewModel::setAutoPlay
                )
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                SettingsToggleRow(
                    label = "Adaptive Quality",
                    description = "Adjust quality based on connection speed",
                    checked = adaptiveQuality,
                    onCheckedChange = viewModel::setAdaptiveQuality
                )
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                SettingsToggleRow(
                    label = "Subtitles",
                    description = "Show subtitles when available",
                    checked = subtitles,
                    onCheckedChange = viewModel::setSubtitles
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSectionHeader(title = "Downloads")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column {
                SettingsToggleRow(
                    label = "Wi-Fi Only",
                    description = "Download content only on Wi-Fi",
                    checked = wifiOnly,
                    onCheckedChange = viewModel::setWifiOnly
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSectionHeader(title = "Notifications")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column {
                SettingsToggleRow(
                    label = "Push Notifications",
                    description = "Get notified about new content",
                    checked = notifications,
                    onCheckedChange = viewModel::setNotifications
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSectionHeader(title = "About")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column {
                SettingsInfoRow(label = "Version", value = "1.0.0")
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                SettingsInfoRow(label = "Platform", value = "Multiplatform")
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                SettingsInfoRow(label = "Privacy Policy", value = "›")
                Divider(color = Color(0xFF2E2E2E), thickness = 0.5.dp)
                SettingsInfoRow(label = "Terms of Service", value = "›")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = Color.Gray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = GreenAccent,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF3E3E3E)
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, color = Color.Gray, fontSize = 14.sp)
    }
}
